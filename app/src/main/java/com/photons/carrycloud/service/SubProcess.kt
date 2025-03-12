package com.photons.carrycloud.service

import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*

class SubProcess(private val tag:String,
                 private val cmd:String,
                 private val env:Array<String>?,
                 private val listener: Listener) {
    companion object {
        private val Logger = LoggerFactory.getLogger("SubProcess")
    }

    var process: Process? = null

    interface Listener {
        fun onStarted()
        fun onStopped(exitValue: Int)
    }

    fun run() {
        Thread {
            try {
                process = Runtime.getRuntime().exec(cmd, env) //支持环境变量
                process?.apply {
                    Logger.debug("$tag run $cmd ${Arrays.toString(env)}")
                    readProcessOutput("stdout", inputStream)
                    readProcessOutput("stderr", errorStream)
                    listener.onStarted()
                    waitFor()
                    listener.onStopped(exitValue())
                    Logger.debug("$tag exit ${exitValue()}")
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.start()
    }

    fun kill() {
        process?.destroy()
    }

    private fun readProcessOutput(label: String, stream: InputStream){
        Thread {
            val buff = CharArray(1024)
            val reader = BufferedReader(InputStreamReader(stream))
            try {
                while (true) {
                    val len = reader.read(buff)
                    if (len == -1) {
                        break
                    }

                    Logger.debug("$tag: ${String(buff, 0, len)}")
                }
            } catch (_: Exception) {
                Logger.debug("$tag: abort read $label")
            }

            reader.close()
            Logger.debug("$tag: finish read $label")
        }.start()
    }
}