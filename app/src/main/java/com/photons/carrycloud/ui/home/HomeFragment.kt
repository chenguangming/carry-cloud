package com.photons.carrycloud.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.content.ContextCompat.startForegroundService
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.photons.bus.LiveEventBus
import com.photons.carrycloud.*
import com.photons.carrycloud.Constants.GLOBAL_IPV4
import com.photons.carrycloud.Constants.GLOBAL_IPV6
import com.photons.carrycloud.Constants.HELP_URL
import com.photons.carrycloud.databinding.FragmentHomeBinding
import com.photons.carrycloud.service.HttpServerState
import com.photons.carrycloud.service.WebService
import com.photons.carrycloud.task.ZipTask
import com.photons.carrycloud.ui.advance.AdvanceActivity
import com.photons.carrycloud.ui.fileselector.FileSelectOptions
import com.photons.carrycloud.ui.fileselector.FileSelectorActivity
import com.photons.carrycloud.ui.webview.WebViewActivity
import com.photons.carrycloud.utils.NetworkUtils
import com.photons.carrycloud.utils.PermissionUtils
import com.photons.carrycloud.utils.ProgressCallback
import com.qmuiteam.qmui.util.QMUIDisplayHelper
import com.qmuiteam.qmui.widget.grouplist.QMUICommonListItemView
import com.qmuiteam.qmui.widget.grouplist.QMUICommonListItemView.ACCESSORY_TYPE_CHEVRON
import com.qmuiteam.qmui.widget.grouplist.QMUICommonListItemView.ACCESSORY_TYPE_NONE
import com.qmuiteam.qmui.widget.grouplist.QMUICommonListItemView.ACCESSORY_TYPE_SWITCH
import com.qmuiteam.qmui.widget.grouplist.QMUIGroupListView
import org.slf4j.LoggerFactory
import java.text.DecimalFormat


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    companion object {
        private val Logger = LoggerFactory.getLogger("HomeFragment")
    }

    private var fileSelectorLauncher: ActivityResultLauncher<Intent>? = null
    private var filePermissonLauncher: ActivityResultLauncher<Intent>? = null
    private val df = DecimalFormat("#.##")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        initGroupListView()

        return root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        fileSelectorLauncher = registerForActivityResult(StartActivityForResult()) { result ->
            result?.data?.let {
                val path = it.getStringExtra(FileSelectOptions.SELECTED_PATH)
                Logger.debug("FileSelect $path")

                path?.let { srcPath ->
                    val dialog = MaterialDialog(requireActivity()).show {
                        customView(R.layout.dialog_progress)
                        cancelOnTouchOutside(false)
                    }

                    val msg = dialog.getCustomView().findViewById<TextView>(R.id.progress_msg)
                    val requestId = ZipTask.decompress(App.instance.getServerRoot(), srcPath)

                    WorkManager.getInstance(App.instance)
                        // requestId is the WorkRequest id
                        .getWorkInfoByIdLiveData(requestId)
                        .observe(this, Observer { workInfo: WorkInfo ->
                            if (workInfo.state.isFinished) {
                                dialog.dismiss()

                                when (workInfo.state) {
                                    WorkInfo.State.FAILED -> App.instance.toast("fail.")
                                    WorkInfo.State.SUCCEEDED -> {
                                        MaterialDialog(requireActivity()).show {
                                            title(R.string.cloud_import_completed)
                                            positiveButton(R.string.ok) { dialog ->
                                                dialog.dismiss()
                                            }
                                        }
                                    }
                                    else -> return@Observer
                                }
                            } else {
                                val progress = workInfo.progress
                                val percent = progress.getFloat("Progress", 0f)
                                Logger.debug("Progress $percent %")
                                // Do something with progress information
                                msg.text = getString(R.string.progress_text, df.format(percent))
                            }
                        })
                }
            }
        }

        filePermissonLauncher = registerForActivityResult(StartActivityForResult()) {
            if (!PermissionUtils.hasStoragePermission(requireActivity())) {
                App.instance.toast(getString(R.string.no_storage_permission_toast))
            }
        }
    }

    override fun onDetach() {
        super.onDetach()

        fileSelectorLauncher?.unregister()
    }

    private val onAccessOverInternetClicked = View.OnClickListener {
        App.instance.toast(getString(R.string.coming_soon))
    }

    private val onPhoneVisibleClicked = View.OnClickListener {
        if (PermissionUtils.hasStoragePermission(requireActivity())) {
            App.instance.toast("has Ready, access in Cloud UI")
            return@OnClickListener
        }

        filePermissonLauncher?.let {
            PermissionUtils.requestFilePermission(requireActivity(), it)
        }
    }

    private val onServerExportClicked = View.OnClickListener {
        if (!PermissionUtils.hasStoragePermission(requireActivity())) {
            filePermissonLauncher?.let {
                PermissionUtils.requestFilePermission(requireActivity(), it)
            } ?: {
                App.instance.toast("No permission")
            }

            return@OnClickListener
        }

        val target = Environment.getExternalStorageDirectory().absolutePath + "/export.zip"
        val progressCallback = if (PermissionUtils.hasNotifyPermission(requireActivity())) {
            App.instance.toast(getString(R.string.run_in_bg))
            null
        } else {
            val dialog = MaterialDialog(requireActivity()).show {
                customView(R.layout.dialog_progress)
                cancelOnTouchOutside(false)
                cancelable(false)
            }

            val msg = dialog.getCustomView().findViewById<TextView>(R.id.progress_msg)

            object : ProgressCallback {
                override fun onProgress(percent: Int) {
                    activity?.runOnUiThread {
                        msg.text = getString(R.string.progress_text, df.format(percent))
                    }
                }

                override fun onCompleted(success: Boolean) {
                    Logger.debug("compress success $success")
                    dialog.dismiss()
                    activity?.runOnUiThread {
                        MaterialDialog(activity!!).show {
                            title(null, getString(R.string.cloud_export_completed, target))
                            positiveButton(R.string.ok) { _ ->
                                dismiss()
                            }
                        }
                    }
                }}
        }

        WebService.instance?.get()?.exportCloudRoot(target, progressCallback) ?: {
            startForegroundService(requireContext(), Intent(requireContext(), WebService::class.java))
            App.instance.toast("Service not start, try again.")
        }

        return@OnClickListener
    }

    private val onServerImportClicked = View.OnClickListener {
        if (!PermissionUtils.hasStoragePermission(requireActivity())) {
            filePermissonLauncher?.let {
                PermissionUtils.requestFilePermission(requireActivity(), it)
            } ?: {
                App.instance.toast("No permission")
            }

            return@OnClickListener
        }

        val options = FileSelectOptions.getInitInstance()
        options.fileTypeFilter = arrayOf("zip")
        options.tips = getString(R.string.cloud_import)

        val intent = Intent(activity, FileSelectorActivity::class.java)
        fileSelectorLauncher?.launch(intent)
    }

    private val onAdvanceSettingsClicked = View.OnClickListener {
        startActivity(Intent(activity, AdvanceActivity::class.java))
    }

    private val onHelpClicked = View.OnClickListener {
        val intent = Intent(activity, WebViewActivity::class.java)
        intent.putExtra("html", HELP_URL)
        intent.putExtra("title", getString(R.string.help))
        startActivity(intent)
    }

    private val onAboutClicked = View.OnClickListener {
        MaterialDialog(requireActivity()).show {
            title(R.string.about)
            message(R.string.about_detail)
            positiveButton(R.string.ok) { dialog ->
                dialog.dismiss()
            }
        }
    }

    private fun createItemView(title: Int, desc: Int, accessory: Int = ACCESSORY_TYPE_CHEVRON): QMUICommonListItemView {
        return createItemView(getString(title), getString(desc), accessory)
    }

    private fun createItemView(title: String, desc: String, accessory: Int = ACCESSORY_TYPE_CHEVRON): QMUICommonListItemView {
        return binding.groupListView.createItemView(title).apply {
            detailText = desc
            orientation = QMUICommonListItemView.VERTICAL
            accessoryType = accessory
        }
    }

    private fun initGroupListView() {
        val serverSwitch = binding.groupListView.createItemView(App.instance.getServerPath())
        serverSwitch.detailText = getString(R.string.stopped)
        serverSwitch.orientation = QMUICommonListItemView.VERTICAL
        serverSwitch.accessoryType = ACCESSORY_TYPE_SWITCH
        serverSwitch.switch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (NetworkUtils.isReady()) {
                    App.instance.startServer(true)
                } else {
                    startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                    serverSwitch.switch.isChecked = false
                    App.instance.toast(getString(R.string.wifi_disconnected))
                }
            } else {
                App.instance.startServer(false)
            }
        }

        serverSwitch.switch.isChecked = WebService.isHttpServerStart()

        LiveEventBus
            .get(Constants.NETWORK_STATE_CHANGED_KEY, String::class.java)
            .observeSticky(this) {
                if (it == GLOBAL_IPV4) {
                    serverSwitch.text = getString(R.string.wifi_disconnected)
                } else {
                    serverSwitch.text = App.instance.getServerPath()
                }
            }


        LiveEventBus
            .get(Constants.NETWORK_V6_STATE_CHANGED_KEY, String::class.java)
            .observeSticky(this) {
                if (it == GLOBAL_IPV6) {
                    binding.ipv6Addr.visibility = View.GONE
                } else {
                    binding.ipv6Addr.text = getString(R.string.support_ipv6, App.instance.getServerPathV6())
                    binding.ipv6Addr.visibility = View.VISIBLE
                }
            }

        LiveEventBus
            .get(Constants.HTTP_SERVER_CHANGED_KEY, HttpServerState::class.java)
            .observeSticky(this) {
                Logger.debug("server start $it")
                serverSwitch.detailText = if (it.isStarted) {
                    getString(R.string.running)
                } else if (it.errNo == 0) {
                    getString(R.string.stopped)
                } else {
                    getString(R.string.error)
                }

                serverSwitch.switch.isChecked = it.isStarted

                binding.usageText.text = if (it.isStarted) {
                    getString(R.string.notification_content, App.instance.getServerPath())
                } else if (it.errNo != 0) {
                    getString(R.string.error_desc)
                } else {
                    getString(R.string.prepare_cloud_env_done)
                }
            }

        val accessOverInternet = createItemView(R.string.remote_access, R.string.remote_access_desc)
        val serverExport = createItemView(R.string.cloud_export, R.string.cloud_export_desc)
        val serverImport = createItemView(R.string.cloud_import, R.string.cloud_import_desc)
        val advanceSettings = createItemView(R.string.advance_settings, R.string.click_to_open)
        val help = createItemView(R.string.help, R.string.click_to_open)
        val about = createItemView(getString(R.string.about), "v${BuildConfig.VERSION_NAME}", ACCESSORY_TYPE_NONE)

        val phoneVisible = if (PermissionUtils.hasStoragePermission(requireActivity())) {
            createItemView(R.string.phone_visible, R.string.phone_visible_desc, ACCESSORY_TYPE_NONE)
        } else {
            createItemView(R.string.phone_visible, R.string.click_to_enable)
        }

        val size = QMUIDisplayHelper.dp2px(context, 20)
        QMUIGroupListView.newSection(context)
            .setTitle(getString(R.string.server_configuration))
            .setLeftIconSize(size, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                addItemView(serverSwitch, null)
                addItemView(phoneVisible, onPhoneVisibleClicked)
                addItemView(accessOverInternet, onAccessOverInternetClicked)
                addItemView(serverExport, onServerExportClicked)
                addItemView(serverImport, onServerImportClicked)
                addItemView(advanceSettings, onAdvanceSettingsClicked)
                addItemView(help, onHelpClicked)
                addItemView(about, onAboutClicked)
            }
            .setMiddleSeparatorInset(QMUIDisplayHelper.dp2px(context, 8), 0)
            .addTo(binding.groupListView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}