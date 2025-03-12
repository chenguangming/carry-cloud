package com.photons.carrycloud.localfile.read

import android.content.Context
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar
import com.photons.carrycloud.R
import com.photons.carrycloud.localfile.objects.ReturnedValueOnReadFile
import com.photons.carrycloud.task.Task
import com.photons.carrycloud.ui.editor.TextEditorActivity
import com.photons.carrycloud.ui.editor.TextEditorViewModel
import com.photons.carrycloud.utils.Logger
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*

class ReadTextFileTask(
    activity: TextEditorActivity,
    private val textEditorActivityWR: WeakReference<TextEditorActivity>,
    private val appContextWR: WeakReference<Context>
) : Task<ReturnedValueOnReadFile, ReadTextFileCallable> {
    private val task: ReadTextFileCallable

    init {
        val viewModel: TextEditorViewModel by activity.viewModels()
        task = ReadTextFileCallable(
            activity.contentResolver,
            viewModel.file,
            activity.externalCacheDir,
            false
        )
    }

    override fun getTask(): ReadTextFileCallable = task

    @MainThread
    override fun onError(error: Throwable) {
        Logger.d("Error on text read", error.toString())
        val applicationContext = appContextWR.get() ?: return

        @StringRes val errorMessage: Int = when (error) {
            is FileNotFoundException -> {
                R.string.error_file_not_found
            }
            is IOException -> {
                R.string.error_io
            }
            is OutOfMemoryError -> {
                R.string.error_file_too_large
            }
            else -> {
                R.string.error
            }
        }
        Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_SHORT).show()
        val textEditorActivity = textEditorActivityWR.get() ?: return
        textEditorActivity.dismissLoadingSnackbar()
        textEditorActivity.finish()
    }

    @MainThread
    override fun onFinish(value: ReturnedValueOnReadFile) {
        val textEditorActivity = textEditorActivityWR.get() ?: return
        val viewModel: TextEditorViewModel by textEditorActivity.viewModels()
        textEditorActivity.dismissLoadingSnackbar()
        viewModel.cacheFile = value.cachedFile
        viewModel.original = value.fileContents
        val file = viewModel.file ?: return
        val externalCacheDir = textEditorActivity.externalCacheDir

        textEditorActivity.mainTextView.setText(value.fileContents)

        // file in cache, and not a root temporary file
//        val isFileInCacheAndNotRoot =
//            file.scheme == EditableFileAbstraction.Scheme.FILE &&
//                externalCacheDir != null &&
//                viewModel.cacheFile == null

        if (false) {
            textEditorActivity.setReadOnly()
            val snackbar = Snackbar.make(
                textEditorActivity.mainTextView,
                R.string.file_read_only,
                Snackbar.LENGTH_INDEFINITE
            )
            snackbar.setAction(
                textEditorActivity.resources.getString(R.string.got_it)
                    .uppercase(Locale.getDefault())
            ) { snackbar.dismiss() }
            snackbar.show()
        }

        if (value.fileContents.isEmpty()) {
            textEditorActivity.mainTextView.setHint(R.string.file_empty)
        } else {
            textEditorActivity.mainTextView.hint = null
        }

        if (value.fileIsTooLong) {
            textEditorActivity.setReadOnly()
            val snackbar = Snackbar.make(
                textEditorActivity.mainTextView,
                textEditorActivity.resources
                    .getString(R.string.file_too_long, ReadTextFileCallable.MAX_FILE_SIZE_CHARS),
                Snackbar.LENGTH_INDEFINITE
            )
            snackbar.setAction(
                textEditorActivity.resources.getString(R.string.got_it)
                    .uppercase(Locale.getDefault())
            ) { snackbar.dismiss() }
            snackbar.show()
        }
    }
}
