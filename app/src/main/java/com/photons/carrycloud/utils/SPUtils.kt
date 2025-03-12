package com.photons.carrycloud.utils

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.photons.carrycloud.App

object SPUtils {
    const val KEY_SERVER_PORT = "server.port"
    const val KEY_NEVER_REQUEST_NOTIFY = "never_request_notify"
    const val KEY_NEVER_REQUEST_STORAGE = "never_request_storage"

    val db: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(App.instance)

    fun getInt(key: String): Int {
        return db.getInt(key, 0)
    }

    fun getInt(key: String, def: Int): Int {
        return db.getInt(key, def)
    }

    fun putInt(key: String, value: Int) {
        db.edit().putInt(key, value).apply()
    }

    fun getBoolean(key: String): Boolean {
        return db.getBoolean(key, false)
    }

    fun getBoolean(key: String, def: Boolean): Boolean {
        return db.getBoolean(key, def)
    }

    fun putBoolean(key: String, value: Boolean) {
        db.edit().putBoolean(key, value).apply()
    }
}