package com.photons.carrycloud.ui.advance

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import com.photons.carrycloud.App
import com.photons.carrycloud.R
import com.photons.carrycloud.databinding.ActivityAdvanceBinding
import com.photons.carrycloud.ui.editor.TextEditorActivity
import com.photons.carrycloud.utils.FileUtils
import com.photons.carrycloud.utils.NetworkUtils
import com.qmuiteam.qmui.arch.QMUIActivity
import com.qmuiteam.qmui.skin.QMUISkinManager
import com.qmuiteam.qmui.util.QMUIDisplayHelper
import com.qmuiteam.qmui.widget.grouplist.QMUICommonListItemView
import com.qmuiteam.qmui.widget.grouplist.QMUICommonListItemView.ACCESSORY_TYPE_NONE
import com.qmuiteam.qmui.widget.grouplist.QMUIGroupListView
import java.io.File


class AdvanceActivity : QMUIActivity() {
    private lateinit var binding: ActivityAdvanceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAdvanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 皮肤管理
        val skinManager = QMUISkinManager.defaultInstance(this)
        setSkinManager(skinManager)

        init()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> return false
        }
        return super.onOptionsItemSelected(item)
    }

    private fun goEditorPage(path: String, showWarn: Boolean, editable: Boolean = true) {
        if (showWarn) {
            MaterialDialog(this).show {
                title(R.string.warning)
                message(null, getString(R.string.modify_warning))
                negativeButton(R.string.cancel) { dialog ->
                    dialog.dismiss()
                }
                positiveButton(R.string.confirm) { _ ->
                    goEditorPage(path, editable)
                }
            }
        } else {
            goEditorPage(path, editable)
        }
    }

    private fun goEditorPage(path: String, editable: Boolean) {
        Intent(this@AdvanceActivity, TextEditorActivity::class.java).let {
            it.putExtra("path", App.instance.getRootPath() + "/" + path)
            it.putExtra("editable", editable)
            startActivity(it)
        }
    }

    @SuppressLint("CheckResult")
    private val onModifyLighttpdPortClicked = View.OnClickListener {
        MaterialDialog(this).show {
            title(R.string.modify_httpd_port)
            input(hint = "1024 ... 65535", inputType = InputType.TYPE_CLASS_NUMBER) { _, text ->
                // 1024 ... 65535
                try {
                    val port = text.toString().toInt()
                    if (port < 1024 || port > 65535) {
                        App.instance.toast("not in range 1024 ... 65535")
                        return@input
                    }

                    if (!NetworkUtils.checkPortAvailable(port)) {
                        App.instance.toast("$port busy")
                        return@input
                    } else {
                        App.instance.changePort(port)
                    }

                } catch (e: Exception) {
                    App.instance.toast("error input $text")
                }
            }
            positiveButton(R.string.ok)
            negativeButton(R.string.cancel)
        }
    }

    private val onModifyLighttpdConfigClicked = View.OnClickListener {
        goEditorPage("conf/lighttpd.conf", true)
    }


    private val onModifyPhpConfigClicked = View.OnClickListener {
        goEditorPage("conf/php.ini", true)
    }

    private val onHttpLogClicked = View.OnClickListener {
        goEditorPage("error.log", false, editable = false)
    }

    private val onPhpLogClicked = View.OnClickListener {
        goEditorPage("php.log", false, editable = false)
    }

    private val onClearLogClicked = View.OnClickListener {
        val dataRoot = App.instance.getRootPath()
        FileUtils.deleteFile(File("$dataRoot/error.log"))
        FileUtils.deleteFile(File("$dataRoot/access.log"))
        FileUtils.deleteFile(File("$dataRoot/php.log"))
        Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
    }

    private val onResetAllConfigurationClicked = View.OnClickListener {
        val dataRoot = App.instance.getRootPath()

        // lighttpd 配置
        FileUtils.buildServerConfFile(this, "conf/lighttpd.conf", "$dataRoot/conf/lighttpd.conf")
        // php 配置
        FileUtils.buildServerConfFile(this, "conf/php.ini", "$dataRoot/conf/php.ini")
        Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
    }

    private fun createItemView(title: Int, desc: Int, accessory: Int = QMUICommonListItemView.ACCESSORY_TYPE_CHEVRON): QMUICommonListItemView {
        return binding.groupListView.createItemView(getString(title)).apply {
            detailText = getString(desc)
            orientation = QMUICommonListItemView.VERTICAL
            accessoryType = accessory
        }
    }

    private fun init() {
        setSupportActionBar(binding.topbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.advance_settings)

        val httpConfig = createItemView(R.string.modify_httpd_configuration, R.string.click_to_open)
        val httpPort = createItemView(R.string.modify_httpd_port, R.string.modify_httpd_port_desc, ACCESSORY_TYPE_NONE)
        val phpConfig = createItemView(R.string.modify_php_configuration, R.string.click_to_open)
        val httpLog = createItemView(R.string.show_http_err_log, R.string.click_to_open)
        val phpLog = createItemView(R.string.show_php_err_log, R.string.click_to_open)
        val clearAllLog = createItemView(R.string.clear_all_log, R.string.clear_all_log_desc, ACCESSORY_TYPE_NONE)
        val resetAllConfiguration = createItemView(R.string.reset_factory_configuration, R.string.reset_factory_configuration_desc, ACCESSORY_TYPE_NONE)

        val size = QMUIDisplayHelper.dp2px(this, 20)
        QMUIGroupListView.newSection(this)
            .setTitle(getString(R.string.server_advance_configuration))
            .setLeftIconSize(size, ViewGroup.LayoutParams.WRAP_CONTENT)
            .addItemView(httpPort, onModifyLighttpdPortClicked)
            .addItemView(httpConfig, onModifyLighttpdConfigClicked)
            .addItemView(phpConfig, onModifyPhpConfigClicked)
            .addItemView(httpLog, onHttpLogClicked)
            .addItemView(phpLog, onPhpLogClicked)
            .addItemView(clearAllLog, onClearLogClicked)
            .addItemView(resetAllConfiguration, onResetAllConfigurationClicked)
            .setMiddleSeparatorInset(QMUIDisplayHelper.dp2px(this, 8), 0)
            .addTo(binding.groupListView)
    }
}