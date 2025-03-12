package com.photons.carrycloud.task

import androidx.annotation.MainThread
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.Callable

interface Task<V, T : Callable<V>> {
    /**
     * This should return a callable to be run on a worker thread
     * The [Callable] cannot return null
     */
    fun getTask(): T

    /**
     * This function will be called on main thread if an exception is thrown
     */
    @MainThread
    fun onError(error: Throwable)

    /**
     * If the task does not return null, and doesn't throw an error this
     * function will be called with the result of the operation on main thread
     */
    @MainThread
    fun onFinish(value: V)
}

/**
 * This creates and starts a [Flowable] from a [Task].
 */
fun <V, T : Callable<V>> fromTask(task: Task<V, T>): Disposable {
    return Flowable.fromCallable(task.getTask())
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(task::onFinish, task::onError)
}
