package com.photons.carrycloud.utils

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

object Shell {
    private const val TAG = "Shell"

    fun exec(cmd: String, env: Array<String>?): Int {
        try {
            val process = Runtime.getRuntime().exec(cmd, env) //支持环境变量
            val out = BufferedReader(InputStreamReader(process.inputStream))
            val error = BufferedReader(InputStreamReader(process.errorStream))
            val retStr = StringBuffer()
            val buff = CharArray(1024)
            var len = 0
            while (out.read(buff).also { len = it } != -1) {
                retStr.append(buff, 0, len)
            }
            while (error.read(buff).also { len = it } != -1) {
                retStr.append("Error: ").append(buff, 0, len)
            }
            out.close()
            error.close()
            process.waitFor()
            Logger.i(TAG, "$retStr")
            return process.exitValue()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return -1
    }
}