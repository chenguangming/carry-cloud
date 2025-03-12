package com.photons.carrycloud.ui.editor

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import com.photons.carrycloud.R
import com.photons.carrycloud.databinding.ActivityTextEditorBinding
import com.photons.carrycloud.localfile.OnProgressUpdate
import com.photons.carrycloud.localfile.objects.EditableFileAbstraction
import com.photons.carrycloud.localfile.objects.SearchResultIndex
import com.photons.carrycloud.localfile.read.ReadTextFileTask
import com.photons.carrycloud.localfile.write.WriteTextFileTask
import com.photons.carrycloud.task.OnAsyncTaskFinished
import com.photons.carrycloud.task.SearchTextTask
import com.photons.carrycloud.task.fromTask
import com.photons.carrycloud.utils.Utils
import java.io.File
import java.io.IOException
import java.lang.Integer.max
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.round

class TextEditorActivity : AppCompatActivity(), TextWatcher, View.OnClickListener {
    companion object {
        private const val TAG = "EditorActivity"
        private const val KEY_MODIFIED_TEXT = "modified"
        private const val KEY_INDEX = "index"
        private const val KEY_ORIGINAL_TEXT = "original"
        private const val TEXT_CHANGE_DETECT_INTERVAL_MS = 250L

        fun saveFile(activity: TextEditorActivity, editTextString: String) {
            val textEditorActivityWR = WeakReference(activity)
            val appContextWR = WeakReference(activity.applicationContext)
            fromTask(
                WriteTextFileTask(activity, editTextString, textEditorActivityWR, appContextWR)
            )
        }

        /**
         * Initiates loading of file/uri by getting an input stream associated with it on a worker thread
         */
        fun load(activity: TextEditorActivity) {
            activity.dismissLoadingSnackbar()
            activity.loadingSnackBar = Snackbar.make(activity.scrollView, R.string.loading, Snackbar.LENGTH_SHORT)
            activity.loadingSnackBar?.show()
            val textEditorActivityWR = WeakReference(activity)
            val appContextWR = WeakReference(activity.applicationContext)
            fromTask(ReadTextFileTask(activity, textEditorActivityWR, appContextWR))
        }
    }

    lateinit var mainTextView: EditText
    private lateinit var searchEditText: EditText
    private lateinit var toolbar: Toolbar
    private lateinit var scrollView: ScrollView
    private lateinit var searchViewLayout: RelativeLayout
    private lateinit var upButton: ImageButton
    private lateinit var downButton: ImageButton
    private lateinit var closeButton: ImageButton
    private lateinit var binding: ActivityTextEditorBinding

    private var loadingSnackBar: Snackbar? = null
    private var searchTextTask: SearchTextTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = getColor(R.color.colorPrimary)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val viewModel = ViewModelProvider(this)[TextEditorViewModel::class.java]
        searchViewLayout = binding.searchview
        searchViewLayout.setBackgroundColor(getColor(R.color.colorPrimary))
        searchEditText = binding.searchBox
        upButton = binding.prev
        downButton = binding.next
        closeButton = binding.close
        searchEditText.addTextChangedListener(this)
        upButton.setOnClickListener(this)
        downButton.setOnClickListener(this)
        closeButton.setOnClickListener(this)
        mainTextView = binding.fname
        scrollView = binding.editScroll

        val path = intent?.extras?.getString("path")
        path?.let {
            val editFile = File(path)
            if (!editFile.exists()) {
                try {
                    editFile.createNewFile()
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
            viewModel.file = EditableFileAbstraction(this, Uri.fromFile(editFile))
        } ?: {
            Toast.makeText(
                this,
                "Something went wrong, there\\'s nothing to open",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }

        intent?.extras?.getBoolean("editable")?.let {
            if (!it) {
                setReadOnly()
            }
        }

        supportActionBar?.title = viewModel.file!!.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mainTextView.addTextChangedListener(this)
        mainTextView.setBackgroundColor(Utils.getColor(this, R.color.editorBgColor))
        mainTextView.setTextColor(Utils.getColor(this, R.color.editorTextColor))
        mainTextView.typeface = Typeface.MONOSPACE

        if (savedInstanceState != null) {
            viewModel.original = savedInstanceState.getString(KEY_ORIGINAL_TEXT)
            val index = savedInstanceState.getInt(KEY_INDEX)
            mainTextView.setText(savedInstanceState.getString(KEY_MODIFIED_TEXT))
            mainTextView.scrollY = index
        } else {
            load(this)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val viewModel = ViewModelProvider(this)[TextEditorViewModel::class.java]
        outState.putString(KEY_MODIFIED_TEXT, mainTextView.text.toString())
        outState.putInt(KEY_INDEX, mainTextView.scrollY)
        outState.putString(KEY_ORIGINAL_TEXT, viewModel.original)
    }

    private fun checkUnsavedChanges() {
        val viewModel = ViewModelProvider(this)[TextEditorViewModel::class.java]
        if (viewModel.original != null && mainTextView.isShown
            && viewModel.original != mainTextView.text.toString()) {
            MaterialDialog(this).show {
                title(R.string.unsaved_changes)
                message(R.string.unsaved_changes_description)

                positiveButton(R.string.yes) { dialog ->
                    saveFile(this@TextEditorActivity, mainTextView.text.toString())
                    dialog.dismiss()
                    finish()
                }
                negativeButton(R.string.no) {
                    finish()
                }
            }
        } else {
            finish()
        }
    }

    /**
     * Method initiates a worker thread which writes the [.mainTextView] bytes to the defined
     * file/uri 's output stream
     *
     * @param activity a reference to the current activity
     * @param editTextString the edit text string
     */

    fun setReadOnly() {
        mainTextView.inputType = EditorInfo.TYPE_NULL
        mainTextView.isSingleLine = false
        mainTextView.imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
    }

    fun dismissLoadingSnackbar() {
        if (loadingSnackBar != null) {
            loadingSnackBar!!.dismiss()
            loadingSnackBar = null
        }
    }

    override fun onBackPressed() {
        checkUnsavedChanges()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.text, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        ViewModelProvider(this)[TextEditorViewModel::class.java].let {
            menu.findItem(R.id.save).isVisible = it.modified
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> checkUnsavedChanges()
            R.id.save -> saveFile(this, mainTextView.text.toString())
            R.id.find -> if (searchViewLayout.isShown) hideSearchView() else revealSearchView()
            else -> return false
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        ViewModelProvider(this)[TextEditorViewModel::class.java].cacheFile?.let {
            if (it.exists()) {
                it.delete()
            }
        }
    }

    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
        // condition to check if callback is called in search editText
        if (charSequence.hashCode() == searchEditText.text.hashCode()) {
            val viewModel = ViewModelProvider(this)[TextEditorViewModel::class.java]

            searchTextTask?.let {
                it.cancel(true)
                searchTextTask = null // dereference the task for GC
            }

            cleanSpans(viewModel)
        }
    }

    override fun onTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
        if (charSequence.hashCode() == mainTextView.text.hashCode()) {
            val viewModel = ViewModelProvider(this)[TextEditorViewModel::class.java]
            val oldTimer = viewModel.timer
            viewModel.timer = null
            oldTimer?.let {
                it.cancel()
                it.purge()
            }

            val textEditorActivityWR = WeakReference(this)
            Timer().let { timer ->
                timer.schedule(
                    object : TimerTask() {
                        var modified = false
                        override fun run() {
                            val activity = textEditorActivityWR.get() ?: return
                            ViewModelProvider(activity)[TextEditorViewModel::class.java] .let {
                                modified = activity.mainTextView
                                    .text
                                    .toString() != it.original
                                if (it.modified != modified) {
                                    it.modified = modified
                                    invalidateOptionsMenu()
                                }
                            }
                        }
                    },
                    TEXT_CHANGE_DETECT_INTERVAL_MS
                )
                viewModel.timer = timer
            }
        }
    }

    override fun afterTextChanged(editable: Editable) {
        // searchBox callback block
        if (editable.hashCode() == searchEditText.text.hashCode()) {
            val textEditorActivityWR = WeakReference(this)
            val onProgressUpdate = object : OnProgressUpdate<SearchResultIndex> {
                    override fun onUpdate(index: SearchResultIndex) {
                        textEditorActivityWR.get()?.unhighlightSearchResult(index)
                    }
                }

            val onAsyncTaskFinished = object : OnAsyncTaskFinished<List<SearchResultIndex>> {
                override fun onAsyncTaskFinished(data: List<SearchResultIndex>) {
                    textEditorActivityWR.get()?.let { activity ->
                        val viewModel = ViewModelProvider(activity)[TextEditorViewModel::class.java]
                        viewModel.searchResultIndices = data
                        data.forEach { searchResultIndex ->
                            activity.unhighlightSearchResult(searchResultIndex)
                        }

                        if (data.isNotEmpty()) {
                            activity.upButton.isEnabled = true
                            activity.downButton.isEnabled = true

                            // downButton
                            activity.onClick(activity.downButton)
                        } else {
                            activity.upButton.isEnabled = false
                            activity.downButton.isEnabled = false
                        }
                    }
                }
            }

            searchTextTask = SearchTextTask(
                mainTextView.text.toString(),
                editable.toString(),
                onProgressUpdate,
                onAsyncTaskFinished
            )
            searchTextTask?.execute()
        }
    }

    /** 搜索框展开动画  */
    private fun revealSearchView() {
        val startRadius = 4
        val endRadius = max(searchViewLayout.width, searchViewLayout.height)
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        // hardcoded and completely random
        val cx = metrics.widthPixels - 160
        val cy = toolbar.bottom
        // FIXME: 2016/11/18   ViewAnimationUtils Compatibility
        val animator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ViewAnimationUtils.createCircularReveal(
                    searchViewLayout,
                    cx,
                    cy,
                    startRadius.toFloat(),
                    endRadius.toFloat()
                )
            } else  {
                ObjectAnimator.ofFloat(searchViewLayout, "alpha", 0f, 1f)
            }
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.duration = 600
        searchViewLayout.visibility = View.VISIBLE
        searchEditText.setText("")
        animator.start()
        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    searchEditText.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
                }
            })
    }

    /** 搜索框收起动画  */
    private fun hideSearchView() {
        val endRadius = 4
        val startRadius = max(searchViewLayout.width, searchViewLayout.height)
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        // hardcoded and completely random
        val cx = metrics.widthPixels - 160
        val cy = toolbar.bottom
        // FIXME: 2016/11/18   ViewAnimationUtils Compatibility
        val animator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ViewAnimationUtils.createCircularReveal(
                searchViewLayout,
                cx,
                cy,
                startRadius.toFloat(),
                endRadius.toFloat()
            )
        } else {
            ObjectAnimator.ofFloat(searchViewLayout, "alpha", 0f, 1f)
        }
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.duration = 600
        animator.start()
        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    searchViewLayout.visibility = View.GONE
                    val inputMethodManager =
                        getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.hideSoftInputFromWindow(
                        searchEditText.windowToken, InputMethodManager.HIDE_IMPLICIT_ONLY
                    )
                }
            })
    }

    override fun onClick(v: View) {
        val viewModel = ViewModelProvider(this)[TextEditorViewModel::class.java]
        when (v.id) {
            R.id.prev ->         // upButton
                if (viewModel.current > 0) {
                    unhighlightCurrentSearchResult(viewModel)

                    // highlighting previous element in list
                    viewModel.current = viewModel.current - 1
                    highlightCurrentSearchResult(viewModel)
                }
            R.id.next ->         // downButton
                if (viewModel.current < viewModel.searchResultIndices.size - 1) {
                    unhighlightCurrentSearchResult(viewModel)
                    viewModel.current = viewModel.current + 1
                    highlightCurrentSearchResult(viewModel)
                }
            R.id.close -> {
                // closeButton
                findViewById<View>(R.id.searchview).setVisibility(View.GONE)
                cleanSpans(viewModel)
            }
            else -> throw IllegalStateException()
        }
    }

    private fun unhighlightCurrentSearchResult(viewModel: TextEditorViewModel) {
        if (viewModel.current == -1) {
            return
        }
        val resultIndex = viewModel.searchResultIndices[viewModel.current]
        unhighlightSearchResult(resultIndex)
    }

    private fun highlightCurrentSearchResult(viewModel: TextEditorViewModel) {
        val keyValueNew = viewModel.searchResultIndices[viewModel.current]
        colorSearchResult(keyValueNew, Utils.getColor(this, R.color.search_text_highlight))

        // scrolling to the highlighted element
        scrollView.scrollTo(
            0, (keyValueNew.lineNumber
                    + mainTextView.lineHeight
                    + round(mainTextView.lineSpacingExtra)).toInt()
                    - (supportActionBar?.height ?: 0)
        )
    }

    private fun unhighlightSearchResult(resultIndex: SearchResultIndex) {
        colorSearchResult(resultIndex, Color.LTGRAY)
    }

    private fun colorSearchResult(resultIndex: SearchResultIndex, @ColorInt color: Int) {
        mainTextView.text.setSpan(
                BackgroundColorSpan(color),
                resultIndex.startCharNumber,
                resultIndex.endCharNumber,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )
    }

    private fun cleanSpans(viewModel: TextEditorViewModel) {
        // resetting current highlight and line number
        viewModel.searchResultIndices = emptyList()
        viewModel.current = -1
        viewModel.line = 0

        // clearing textView spans
        val colorSpans = mainTextView.text.getSpans(
            0, mainTextView.length(),
            BackgroundColorSpan::class.java
        )
        for (colorSpan in colorSpans) {
            mainTextView.text.removeSpan(colorSpan)
        }
    }
}
