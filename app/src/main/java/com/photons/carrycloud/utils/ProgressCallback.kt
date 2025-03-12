package com.photons.carrycloud.utils

interface ProgressCallback {
    fun onProgress(percent: Int)
    fun onCompleted(success: Boolean)
}