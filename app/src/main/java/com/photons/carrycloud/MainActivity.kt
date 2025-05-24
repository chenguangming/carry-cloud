package com.photons.carrycloud

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.photons.bus.LiveEventBus
import com.photons.carrycloud.Constants.BUGLY_APPID
import com.photons.carrycloud.databinding.ActivityMainBinding
import com.photons.carrycloud.service.WebService
import com.photons.carrycloud.utils.PermissionUtils
import com.qmuiteam.qmui.arch.QMUIActivity
import com.qmuiteam.qmui.skin.QMUISkinManager
import com.qmuiteam.qmui.widget.dialog.QMUITipDialog
import com.tencent.bugly.crashreport.CrashReport
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.text.DecimalFormat
import java.util.UUID


class MainActivity : QMUIActivity() {
    // 使用QMUIActivity以便于QMUI的控件可以响应主题
    companion object {
        private val Logger = LoggerFactory.getLogger("MainActivity")
        private val df = DecimalFormat("#.##")
        private const val CODE_RQST_NOTIFICATION = 100
        private const val CODE_RQST_STORAGE = 101
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var launcher: ActivityResultLauncher<Intent>
    private lateinit var navController: NavController

    private var tipDialog: QMUITipDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topbar)
        supportActionBar?.title = getString(R.string.slogan)

        val navView: BottomNavigationView = binding.navView
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController
        navView.setupWithNavController(navController)

        // 皮肤管理
        val skinManager = QMUISkinManager.defaultInstance(this)
        setSkinManager(skinManager)

        // bugly
        CrashReport.initCrashReport(application, BUGLY_APPID, false)

        tipDialog = QMUITipDialog.Builder(this)
            .setIconType(QMUITipDialog.Builder.ICON_TYPE_LOADING)
            .setTipWord(getString(R.string.loading))
            .create()

        LiveEventBus
            .get(Constants.NEED_WAITING, UUID::class.java)
            .observeSticky(this) {
                showWaitingDialog(it)
            }

        launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (!PermissionUtils.hasStoragePermission(this)) {
                App.instance.toast(getString(R.string.no_storage_permission_toast))
            }
        }

        App.instance.startServer(true)

        PermissionUtils.checkAndRequestNotifyPermission(this)
        PermissionUtils.checkAndRequestStoragePermission(this, launcher)
        checkIgnoreBatteryOptimization()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        intent?.let {
            val path = intent.extras?.getString(Constants.PENDING_INTENT_PATH_KEY)
            if (Constants.PENDING_INTENT_PATH_NOTIFY == path) {
                if (navController.currentDestination?.id != R.id.navigation_notifications) {
                    navController.navigate(R.id.navigation_notifications)
                } else {
                    Logger.debug("already in navigation_notifications")
                }
            }
        }
    }

    private fun checkIgnoreBatteryOptimization() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val hasIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
        //  判断当前APP是否有加入电池优化的白名单，如果没有，弹出加入电池优化的白名单的设置对话框。
        if (!hasIgnored) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            }
        } else {
            Log.d("ignoreBattery", "hasIgnored")
        }
    }

    private fun showWaitingDialog(uuid: UUID) {
        val dialog = MaterialDialog(this).show {
            customView(R.layout.dialog_progress)
            cancelOnTouchOutside(false)
        }

        val msg = dialog.getCustomView().findViewById<TextView>(R.id.progress_msg)

        WorkManager.getInstance(App.instance)
            // requestId is the WorkRequest id
            .getWorkInfoByIdLiveData(uuid)
            .observe(this, Observer { workInfo: WorkInfo ->
                if (workInfo.state.isFinished) {
                    dialog.dismiss()

                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        Logger.debug("start http server")
                        WebService.startHttpServer(true)
                    }
                } else {
                    val progress = workInfo.progress
                    val percent = progress.getInt(Constants.WORKER_PROGRESS_KEY, 0)
                    Logger.debug("Prepare $percent %")
                    // Do something with progress information
                    msg.text = getString(R.string.prepare_cloud_env_progress, df.format(percent))
                }
            })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            CODE_RQST_NOTIFICATION -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Logger.debug("postNotificationGranted onRequestPermissionsResult")
                    PermissionUtils.postNotificationGranted()
                }
            }

            CODE_RQST_STORAGE -> {
                Logger.debug("onRequestPermissionsResult ${grantResults[0]}")
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}