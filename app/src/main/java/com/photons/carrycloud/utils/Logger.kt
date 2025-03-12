package com.photons.carrycloud.utils

import com.photons.carrycloud.BuildConfig
import org.slf4j.LoggerFactory

object Logger {
    private val logger = LoggerFactory.getLogger("CarryCloud")

    fun d(tag: String, msg: String) {
        logger.debug("$tag $msg")
    }

    fun i(tag: String, msg: String) {
        logger.info("$tag $msg")
    }

    fun w(tag: String, msg: String) {
        logger.warn("$tag $msg")
    }

    fun v(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            logger.debug("$tag $msg")
        }
    }
}