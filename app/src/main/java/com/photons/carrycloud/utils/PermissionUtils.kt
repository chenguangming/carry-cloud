package com.photons.carrycloud.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.photons.bus.LiveEventBus
import com.photons.carrycloud.App
import com.photons.carrycloud.Constants
import com.photons.carrycloud.R
import org.slf4j.LoggerFactory

object PermissionUtils {
    private const val CODE_RQST_NOTIFICATION = 100
    private const val CODE_RQST_STORAGE = 101
    private val Logger = LoggerFactory.getLogger("PermissionUtils")

    private fun requestStoragePermission(activity: Activity) {
        CommonUtils.disableScreenRotation(activity)

        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            MaterialDialog(activity).show {
                title(R.string.grantper)
                message(R.string.grant_storage_permission)
                negativeButton(R.string.never) { _ ->
                    SPUtils.putBoolean(SPUtils.KEY_NEVER_REQUEST_STORAGE, true)
                }
                positiveButton(R.string.go_grant) { dialog ->
                    requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), CODE_RQST_STORAGE)
                    dialog.dismiss()
                }
            }
        } else {
            requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), CODE_RQST_STORAGE)
        }
    }

    private fun requestAllFilesAccess(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            MaterialDialog(activity).show {
                title(R.string.grantper)
                message(R.string.grant_all_files_permission)

                positiveButton(R.string.go_grant) { dialog ->
                    // Do something
                    CommonUtils.disableScreenRotation(activity)
                    try {
                        launcher.launch(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            .setData(Uri.parse("package:${activity.packageName}")))
                    } catch (e: java.lang.Exception) {
                        Logger.error("Failed to initial activity to grant all files access")
                        App.instance.toast(activity.getString(R.string.grantfailed))
                    }
                    dialog.dismiss()
                }
                negativeButton(R.string.never) {
                    SPUtils.putBoolean(SPUtils.KEY_NEVER_REQUEST_STORAGE, true)
                    dismiss()
                }
            }
        }
    }

    fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermissions(activity: Activity, permissions: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }

    fun requestFilePermission(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestAllFilesAccess(activity, launcher)
        } else {
            requestStoragePermission(activity)
        }
    }

    fun checkAndRequestStoragePermission(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        if (hasStoragePermission(activity) || SPUtils.getBoolean(SPUtils.KEY_NEVER_REQUEST_STORAGE)) {
            return
        }

        requestFilePermission(activity, launcher);
    }

    fun hasStoragePermission(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }

        return isGranted(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    fun postNotificationGranted() {
        LiveEventBus
            .get(Constants.NOTIFY_PERMISSION_CHANGED_KEY, Boolean::class.java)
            .post(true)
    }

    private fun goNotificationSettingPage(activity: Activity) {
        try {
            val intent = Intent()
            intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, activity.applicationInfo.uid)
            intent.putExtra("app_package", activity.packageName)
            intent.putExtra("app_uid", activity.applicationInfo.uid)
            activity.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showNotificationTipDialog(activity: Activity) {
        if (SPUtils.getBoolean(SPUtils.KEY_NEVER_REQUEST_NOTIFY)) {
            Logger.debug("never show notification")
            return
        }

        MaterialDialog(activity).show {
            title(R.string.grantper)
            message(R.string.grant_notity_permission)

            positiveButton(R.string.go_grant) { _ ->
                goNotificationSettingPage(activity)
            }
            negativeButton(R.string.never) {
                SPUtils.putBoolean(SPUtils.KEY_NEVER_REQUEST_NOTIFY, true)
            }
        }
    }

    fun hasNotifyPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 大于T, 使用POST_NOTIFICATIONS权限判断
            isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun checkAndRequestNotifyPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 大于T, 使用POST_NOTIFICATIONS权限判断
            if (isGranted(activity, Manifest.permission.POST_NOTIFICATIONS)) {
                Logger.debug("isGranted POST_NOTIFICATIONS")
                postNotificationGranted()
            } else if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) {
                showNotificationTipDialog(activity)
            } else {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), CODE_RQST_NOTIFICATION)
            }
        } else {
            if (NotificationManagerCompat.from(activity).areNotificationsEnabled()) {
                Logger.debug("NotificationManagerCompat areNotificationsEnabled")
                postNotificationGranted()
            } else {
                showNotificationTipDialog(activity)
            }
        }
    }
}