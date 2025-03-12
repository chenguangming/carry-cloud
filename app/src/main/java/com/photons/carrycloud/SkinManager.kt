package com.photons.carrycloud

import android.content.Context
import android.content.res.Configuration
import com.qmuiteam.qmui.skin.QMUISkinManager

object SkinManager {
    val SKIN_DARK = 1
    val SKIN_WHITE = 2

    fun install(context: Context) {
        val skinManager = QMUISkinManager.defaultInstance(context)
        skinManager.addSkin(SKIN_DARK, R.style.app_skin_dark)
        skinManager.addSkin(SKIN_WHITE, R.style.app_skin_white)
        val isDarkMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (isDarkMode) {
            skinManager.changeSkin(SKIN_DARK)
        } else {
            skinManager.changeSkin(SKIN_WHITE)
        }
    }

    fun changeSkin(index: Int) {
        QMUISkinManager.defaultInstance(App.instance).changeSkin(index)
    }

    fun getCurrentSkin(): Int {
        return QMUISkinManager.defaultInstance(App.instance).currentSkin
    }
}