package com.photons.carrycloud.task

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.photons.carrycloud.App
import com.photons.carrycloud.Constants.WORKER_PROGRESS_KEY
import com.photons.carrycloud.R
import com.photons.carrycloud.service.WebService
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.progress.ProgressMonitor
import org.slf4j.LoggerFactory
import java.io.*
import java.util.UUID


object ZipTask {
    private val Logger = LoggerFactory.getLogger("ZipTask")
    private const val ZIP_NOTIFICATION_ID = 11
    private const val ZIP_NOTIFICATION_COMPLETE = 12

    abstract class AbstractZipWorker(protected val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        protected var cur = 0L

        abstract fun getTitle(): String

        override suspend fun getForegroundInfo(): ForegroundInfo {
            return createForegroundInfo(0)
        }

        fun createForegroundInfo(progress: Int): ForegroundInfo {

            val notify = buildProgressNotification(progress)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(ZIP_NOTIFICATION_ID, notify.build(), FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(ZIP_NOTIFICATION_ID, notify.build())
            }
        }

        private fun buildProgressNotification(progress: Int): NotificationCompat.Builder {
            val intent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)

            return NotificationCompat.Builder(context, WebService.DEFAULT_CHANNEL_ID).apply {
                setContentTitle(getTitle())
                setSilent(true)
                setOngoing(false)
                setAutoCancel(true)
                setProgress(100, progress, false)
                addAction(android.R.drawable.ic_delete, context.getString(R.string.cancel), intent)
                setSmallIcon(R.mipmap.ic_launcher)
            }
        }

        protected fun sendCompleteNotification(title: String) {
            val notification = NotificationCompat.Builder(context, WebService.DEFAULT_CHANNEL_ID).apply {
                setContentTitle(title)
                setSilent(false)
                setOngoing(false)
                setAutoCancel(true)
                setSmallIcon(R.mipmap.ic_launcher)
            }.build()
            val notifyMgr = context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
            notifyMgr.notify(ZIP_NOTIFICATION_COMPLETE, notification)
        }

        protected suspend fun updateProgress(percent: Int) {
            setForeground(createForegroundInfo(percent))
            setProgress(workDataOf(WORKER_PROGRESS_KEY to percent))
        }
    }

    class DecompressWorker(context: Context, params: WorkerParameters) : AbstractZipWorker(context, params) {
        override fun getTitle(): String {
            return context.getString(R.string.cloud_import)
        }

        override suspend fun doWork(): Result {
            val zipName = inputData.getString("zipName") ?: return Result.failure()
            val directory = inputData.getString("directory") ?: return Result.failure()

            setForeground(createForegroundInfo(0))

            ZipFile(zipName).apply {
                isRunInThread = true

                extractAll(directory)

                while (!progressMonitor.state.equals(ProgressMonitor.State.READY)) {
                    Logger.debug("percent: " + progressMonitor.percentDone)
                    Logger.debug("file: " + progressMonitor.fileName)

                    updateProgress(progressMonitor.percentDone)

                    Thread.sleep(100)
                }
            }

            sendCompleteNotification(context.getString(R.string.cloud_import_completed))

            Logger.info("decompress done")
            return Result.success()
        }
    }

    class CompressWorker(context: Context, params: WorkerParameters) : AbstractZipWorker(context, params) {
        override fun getTitle(): String {
            return context.getString(R.string.cloud_export)
        }

        override suspend fun doWork(): Result {
            val zipName = inputData.getString("zipName") ?: return Result.failure()
            val directory = inputData.getString("directory") ?: return Result.failure()

            setForeground(createForegroundInfo(0))

            ZipFile(zipName).apply {
                isRunInThread = true

                addFolder(File(directory), ZipParameters().apply { isIncludeRootFolder = false })

                while (!progressMonitor.state.equals(ProgressMonitor.State.READY)) {
                    Logger.debug("percent: " + progressMonitor.percentDone)
                    Logger.debug("file: " + progressMonitor.fileName)

                    updateProgress(progressMonitor.percentDone)

                    Thread.sleep(100)
                }
            }

            sendCompleteNotification(context.getString(R.string.cloud_export_completed, zipName))

            return Result.success()
        }
    }

    private fun commitWork(directory: String, zipName: String, isUnzip: Boolean): UUID {
        val data = Data.Builder()
            .putString("directory", directory)
            .putString("zipName", zipName)
            .build()

        val oneTimeWorkRequest = OneTimeWorkRequest.Builder(
            if (isUnzip) DecompressWorker::class.java
            else CompressWorker::class.java
        )
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(App.instance).enqueue(oneTimeWorkRequest)

        return oneTimeWorkRequest.id
    }

    fun decompress(directory: String, zipName: String): UUID {
        Logger.debug("decompress $zipName to $directory")

        return commitWork(directory, zipName, true)
    }

    fun compress(directory: String, zipName: String): UUID {
        Logger.debug("compress $directory to $zipName")

        return commitWork(directory, zipName, false)
    }
}