package com.photons.carrycloud.localfile

interface OnProgressUpdate<T> {
    @Suppress("UndocumentedPublicFunction")
    fun onUpdate(data: T)
}
