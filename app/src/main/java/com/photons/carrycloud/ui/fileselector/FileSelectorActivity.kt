package com.photons.carrycloud.ui.fileselector

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.*
import android.widget.*
import android.widget.AdapterView.OnItemLongClickListener
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import com.photons.carrycloud.R
import com.photons.carrycloud.databinding.ActivityFileSelectorBinding
import com.photons.carrycloud.localfile.objects.FileInfo
import com.photons.carrycloud.localfile.ListFileThread
import com.photons.carrycloud.localfile.ListFileThread.FileListHandler
import com.photons.carrycloud.ui.fileselector.adapter.FileListAdapter
import com.photons.carrycloud.ui.fileselector.adapter.NavigationAdapter
import com.photons.carrycloud.utils.FileUtils.changeToUriAndroidOrigin
import com.photons.carrycloud.utils.FileUtils.getDocumentFilePath
import com.photons.carrycloud.utils.FileUtils.getRelativePaths
import com.photons.carrycloud.utils.FileUtils.isProtectedFileGranted
import com.photons.carrycloud.utils.FileUtils.mergeAbsolutePath
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CountDownLatch

class FileSelectorActivity : AppCompatActivity() {
    private enum class Orientation {
        Forward, Backward, Init, Skip
    }

    private lateinit var binding: ActivityFileSelectorBinding
    private lateinit var fileListViewModel: FileListViewModel
    private var currentFileList = ArrayList<FileInfo>()
    private val fileSelected = ArrayList<String>()
    private var relativePaths: ArrayList<String> = ArrayList()
    private var currentPath: String? = null
    private lateinit var lvFileList: ListView
    private lateinit var navigationView: RecyclerView
    private lateinit var llRoot: LinearLayout
    private lateinit var navigationAdapter: NavigationAdapter
    private lateinit var refreshLayout: SwipeRefreshLayout

    private lateinit var fileListAdapter: FileListAdapter
    private lateinit var sharedPreferences: SharedPreferences
    private var handler: FileListHandler? = null
    private var isFirst = true //初次加载文件目录
    private var onSelect = false
    private var selectNum = 0
    private var parentListPos = 0 //父级文件列表的顶部元素索引
    private var orientation = Orientation.Init

    private val onItemClicked = AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View, position: Int, _: Long ->
        val fileSelect = currentFileList[position]

        when (fileSelect.fileType) {
            FileInfo.FileType.Folder -> {
                parentListPos = lvFileList.firstVisiblePosition
                Logger.debug("parent_list_pos=$parentListPos")
                refreshFileList(
                    fileSelect,
                    Orientation.Forward
                )
            }

            FileInfo.FileType.Parent -> {
                refreshFileList(
                    fileSelect,
                    Orientation.Backward
                )
            }

            else -> {
                MaterialDialog(this).show {
                    title(R.string.file_selector)
                    message(null, getString(R.string.file_selector_desc, fileSelect.fileName))
                    negativeButton(R.string.cancel) { dialog ->
                        dialog.dismiss()
                    }
                    positiveButton(R.string.ok) { dialog ->
                        intent.apply {
                            putExtra(FileSelectOptions.SELECTED_PATH, fileSelect.filePath)
                        }

                        Logger.debug("selected ${fileSelect.filePath}")
                        setResult(FileSelectOptions.BACK_WITH_SELECTIONS, intent)
                        dialog.dismiss()
                        finish()
                    }
                }
            }
        }
    }

    private val onItemLongClicked = OnItemLongClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
        if (currentFileList[position].fileType != FileInfo.FileType.Parent) {
            showBottomView(View.VISIBLE, BOTTOM_VIEW_HEIGHT)
            fileListAdapter.clearSelections()
            fileListAdapter.setSelect(true)
        }
        true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.file, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.create_folder -> {
                MaterialDialog(this).show {
                    title(R.string.create_new_folder)
                    input(null, R.string.input_folder_name) { _, text ->
                        Logger.debug("text $text")
                        File("$currentPath/$text").let {
                            if (it.exists()) {
                                return@let
                            }

                            it.mkdirs()

                            refreshFileList(
                                it.absolutePath,
                                Orientation.Skip
                            )
                        }
                    }
                    positiveButton(R.string.ok)
                    negativeButton(R.string.cancel)
                }
            }
            else -> return false
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // fixme 全局管理Preferences
        sharedPreferences = getSharedPreferences("global", MODE_PRIVATE)

        binding = ActivityFileSelectorBinding.inflate(layoutInflater)
        setContentView(binding.getRoot())

        setSupportActionBar(binding.topbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        lvFileList = binding.fileList
        navigationView = binding.navigationView
        llRoot = binding.root

        //init views
        initSelectNum()
        initBottomView()
        initRootButton()

        refreshLayout = binding.fsRefresh
        refreshLayout.apply {
            setColorSchemeResources(R.color.colorPrimary)
            setOnRefreshListener {
                currentPath?.let {
                    refreshFileList(
                        it,
                        Orientation.Init
                    )
                }
            }
        }

        fileListViewModel = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[FileListViewModel::class.java]
        fileListViewModel.fileList.observe(this) { fileInfo: ArrayList<FileInfo> ->
            currentFileList = fileInfo
            if (isFirst) {
                fileListAdapter = FileListAdapter(currentFileList, this)

                lvFileList.apply {
                    adapter = fileListAdapter
                    onItemClickListener = onItemClicked

                    if (FileSelectOptions.getInstance().maxSelectNum > 1) {
                        // 需要多选时才设置长按事件监听
                        onItemLongClickListener = onItemLongClicked
                    }
                }

                isFirst = false
            }
            fileListAdapter.updateFileList(currentFileList)
            if (onSelect) {
                fileListAdapter.clearSelections()
            }
        }

        handler = FileListHandler(object : ListFileThread.FileListListener {
            override fun onFileListGenerated(fileInfoList: ArrayList<FileInfo>?) {
                fileInfoList ?: return
                dissRefreshing()
                Logger.debug("onFileListGenerated ${fileInfoList.size}")
                if (fileInfoList.size > 0) {
                    fileListViewModel.addToResult(fileInfoList)
                    fileListViewModel.sortByName()
                }
            }

        })
        initFileList()
    }

    override fun onBackPressed() {
        if (onSelect) {
            quitSelectMod()
        } else if (currentPath != FileSelectOptions.BasicPath) {
            refreshFileList(File(currentPath!!).parent!!, Orientation.Backward)
        } else {
            super.onBackPressed()
        }
    }

    private fun initRootButton() {
        binding.root.setOnClickListener {
            val rootPath: String = FileSelectOptions.BasicPath
            refreshFileList(
                rootPath,
                Orientation.Skip
            )
        }
    }

    private fun setNavigationBar(initPath: String) {
        relativePaths = getRelativePaths(initPath)
        navigationView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        navigationAdapter = NavigationAdapter(this, relativePaths)
        navigationAdapter.setRecycleItemClickListener { _: View?, position: Int ->
            val sublist = relativePaths.subList(0, position + 1)
            relativePaths = ArrayList(sublist)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                refreshFileList(
                    mergeAbsolutePath(
                        relativePaths
                    ), Orientation.Skip
                )
            }
        }
        navigationView.adapter = navigationAdapter
    }

    private fun initBottomView() {
        if (FileSelectOptions.getInstance().isSelectDirectory) {
            showBottomView(View.VISIBLE, BOTTOM_VIEW_HEIGHT)
        } else {
            showBottomView(View.INVISIBLE, 0)
        }

        binding.selectConfirm.setOnClickListener {
            intent.apply {
                putExtra(FileSelectOptions.SELECTED_PATH, currentPath)
            }

            Logger.debug("selected $currentPath")
            setResult(FileSelectOptions.BACK_WITH_SELECTIONS, intent)
            finish()
        }

        binding.selectCancel.setOnClickListener {
            setResult(FileSelectOptions.BACK_WITHOUT_SELECT)
            finish()
        }
    }

    private fun quitSelectMod() {
        fileListAdapter.clearSelections()
        fileListAdapter.setSelect(false)
        showBottomView(View.INVISIBLE, 0)
        onSelect = false
        selectNum = 0
        fileSelected.clear()
        changeSelectNum(0)
    }

    private fun showBottomView(visible: Int, i: Int) {
        binding.bottomView.apply {
            visibility = visible
            layoutParams.height = i
        }
    }

    private fun initSelectNum() {
        changeSelectNum(0)
    }

    private fun changeSelectNum(num: Int) {
        var selectNum: String = getString(R.string.selectNum)
        selectNum = String.format(selectNum, num, FileSelectOptions.getInstance().maxSelectNum)
        // fixme 多选模式，暂时无用
        Logger.debug("$selectNum")
    }

    private fun reachSelectNumLimit(selectNum: Int): Boolean {
        val maxSelectNum = FileSelectOptions.getInstance().maxSelectNum
        return if (maxSelectNum == -1) false else selectNum >= maxSelectNum
    }

    private fun refreshFileList(parent: FileInfo, orientation: Orientation) {
        showRefreshing()

        this.orientation = orientation
        val path = parent.filePath
        currentPath = path
        startListFileThread(parent)
        relativePaths = getRelativePaths(path)
        navigationAdapter.UpdatePathList(relativePaths)
        navigationView.scrollToPosition(navigationAdapter.itemCount - 1)
        if (onSelect) {
            fileListAdapter.clearSelections()
        }
    }

    private fun refreshFileList(parent_path: String, orientation: Orientation) {
        showRefreshing()

        this.orientation = orientation
        currentPath = parent_path
        startListFileThread(parent_path)
        relativePaths = getRelativePaths(parent_path)
        navigationAdapter.UpdatePathList(relativePaths)
        navigationView.scrollToPosition(navigationAdapter.itemCount - 1)
        if (onSelect) {
            fileListAdapter.clearSelections()
        }
    }

    private fun startListFileThread(initPath: String) {
        FileInfo().apply {
            val file = File(initPath)
            fileName = file.name
            accessType = FileInfo.judgeAccess(initPath)
            fileType = if (file.isDirectory) {
                FileInfo.FileType.Folder
            } else {
                FileInfo.FileType.Unknown
            }
            filePath = initPath
            startListFileThread(this)
        }
    }

    private fun startListFileThread(parent: FileInfo) {
        fileListViewModel.clear()
        val initFile = File(parent.filePath)
        if (parent.filePath != FileSelectOptions.BasicPath) {
            FileInfo().apply {
                fileName = getString(R.string.go_back)
                lastUpdateTime = ""
                fileType = FileInfo.FileType.Parent
                filePath = initFile.parent
                setFileCount(-1)
                accessType = FileInfo.judgeAccess(filePath)
                fileListViewModel.addToResult(this)
            }
        }
        if (parent.accessType == FileInfo.AccessType.Open) {
            val files = initFile.listFiles()
            files?.let {
                handleNormalFiles(it)
            } ?: {
                if (parent.filePath.contains("Android/data") ||
                    parent.filePath.contains("Android/obb")) {
                    handleProtectedFiles(parent)
                } else {
                    Logger.debug("can not list file")
                    dissRefreshing()
                }
            }
        } else {
            handleProtectedFiles(parent)
        }
    }

    private fun handleNormalFiles(files: Array<File>) {
        if (files.isEmpty()) {
            dissRefreshing()
            return
        }

        val parts = files.size / LIST_PAGE_SIZE
        val countDownLatch = CountDownLatch(parts + 1)
        var index = 0
        for (i in 0 until parts + 1) {
            val size = if (i == parts) {
                files.size % LIST_PAGE_SIZE
            } else {
                LIST_PAGE_SIZE
            }

            if (size == 0) {
                continue
            }

            ListFileThread(Array(size) {
                files[index++]
            }).apply {
                setHandler(handler)
                setCountDownLatch(countDownLatch)
                start()
            }
        }
        Thread {
            try {
                countDownLatch.await()
                runOnUiThread {
                    if (orientation == Orientation.Backward) {
                        lvFileList.post {
                            lvFileList.setSelection(
                                parentListPos
                            )
                        }
                    }

                    dissRefreshing()
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun dissRefreshing() {
        if (refreshLayout.isRefreshing) {
            refreshLayout.isRefreshing = false
        }
    }

    private fun showRefreshing() {
        if (!refreshLayout.isRefreshing) {
            refreshLayout.isRefreshing = true
        }
    }

    private fun getProtectedFileGrantKey(path: String): String {
        return if (path.contains("Android/data")) {
            "data_path_granted"
        } else if (path.contains("Android/obb")) {
            "obb_path_granted"
        } else {
            ""
        }
    }

    private fun getProtectedFileGranted(path: String): Boolean {
        return sharedPreferences.getBoolean(getProtectedFileGrantKey(path), false)
    }

    private fun saveProtectedFileGranted(path: String?) {
        path?.let {
            sharedPreferences.edit().putBoolean(getProtectedFileGrantKey(it), true).apply()
        }
    }

    private fun handleProtectedFiles(parent: FileInfo) {
        if (!isProtectedFileGranted(this, parent.filePath) &&
            !getProtectedFileGranted(parent.filePath)) {
            grantPermissionForProtectedFile(parent.filePath)
        } else {
            val documentFile = getDocumentFilePath(this, parent.filePath)
            if (documentFile == null) {
                Toast.makeText(this, R.string.bad_path, Toast.LENGTH_SHORT).show()
                dissRefreshing()
                return
            }

            // 如果是目录，加载其子文件列表
            if (documentFile.isDirectory) {
                val documentFiles = documentFile.listFiles()
                val parts = documentFiles.size / LIST_PAGE_SIZE
                val countDownLatch = CountDownLatch(parts + 1)
                var index = 0
                var isEmpty = false
                for (i in 0 until parts + 1) {
                    val size = if (i == parts) {
                        documentFiles.size % LIST_PAGE_SIZE
                    } else {
                        LIST_PAGE_SIZE
                    }

                    if (size == 0) {
                        isEmpty = true
                        break
                    }

                    ListFileThread(Array(size) {
                        documentFiles[index++]
                    }).apply {
                        setHandler(handler)
                        setCountDownLatch(countDownLatch)
                        start()
                    }
                }
                if (isEmpty) {
                    dissRefreshing()
                    return
                }

                Thread {
                    try {
                        countDownLatch.await()
                        runOnUiThread {
                            if (orientation == Orientation.Backward) {
                                lvFileList.post {
                                    lvFileList.setSelection(
                                        parentListPos
                                    )
                                }
                            }

                            dissRefreshing()
                        }
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }.start()
            }
        }
    }

    private fun grantPermissionForProtectedFile(path: String) {
        val uri = changeToUriAndroidOrigin(path) //调用方法，把path转换成可解析的uri文本
        val parse = Uri.parse(uri)
        val intent = Intent("android.intent.action.OPEN_DOCUMENT_TREE")
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, parse)
        startActivityForResult(intent, REQUEST_FOR_DATA_PATH) //开始授权
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Logger.debug(("onRequestPermissionsResult requestCode ： " + requestCode
                    + " Permission: " + permissions[0] + " was " + grantResults[0]
                    + " Permission: " + permissions[1] + " was " + grantResults[1])
        )
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            //resume tasks needing this permission
            Logger.info("permission granted")
            initFileList()
        } else {
            Toast.makeText(this, "未获得文件读写权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initFileList() {
        showRefreshing()

        var initPath = FileSelectOptions.getInstance().rootPath
        val test = (File(initPath)).listFiles()
        val documentFile = getDocumentFilePath(this, initPath)
        if (test == null && documentFile == null) {
            initPath = FileSelectOptions.BasicPath
        }
        startListFileThread(initPath)
        currentPath = initPath
        setNavigationBar(initPath)
    }

    @SuppressLint("WrongConstant")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        dissRefreshing()

        if (requestCode == REQUEST_FOR_DATA_PATH) {
            data?.data?.apply {
                contentResolver.takePersistableUriPermission(
                    this, data.flags and ((Intent.FLAG_GRANT_READ_URI_PERMISSION
                            or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                )

                //保存目录的访问权限
                currentPath?.let {
                    saveProtectedFileGranted(it)
                    refreshFileList(it, Orientation.Forward)
                }
            } ?: {
                Logger.debug("request permission failed")
            }
        }
    }

    companion object {
        private const val REQUEST_FOR_DATA_PATH = 20
        private const val LIST_PAGE_SIZE = 20
        private const val BOTTOM_VIEW_HEIGHT = 140
        private val Logger = LoggerFactory.getLogger("FileSelectorActivity")
    }
}