package com.github.boybeak.xcmpor

import android.os.AsyncTask
import android.os.Handler
import android.os.Looper

fun <O> async(block: (ProgressUpdater) -> O): Async<Unit, O> {
    return async(Unit) { progressUpdater, _ ->
        block.invoke(progressUpdater)
    }
}

fun <I, O> async(params: I, block: (ProgressUpdater, input: I) -> O): Async<I, O> {
    return Async(params, block)
}

interface ProgressUpdater {
    fun updateProgress(progress: Int)
}

class Async<I, O>(private val input: I, private val block: (ProgressUpdater, input: I) -> O) : AsyncTask<I, Int, O>(){

    private var onStart: (() -> Unit)? = null
    private var onProgress: ((Int) -> Unit)? = null
    private var onSuccess: ((result: O) -> Unit)? = null
    private var onError: ((e: Throwable) -> Unit)? = null
    private var onCancel: (() -> Unit)? = null
    private var onComplete: (() -> Unit)? = null
    private var isExecuting = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val progressUpdater = object : ProgressUpdater {
        override fun updateProgress(progress: Int) {
            publishProgress(progress)
        }
    }

    override fun doInBackground(vararg params: I): O? {
        return try {
            block.invoke(progressUpdater, params[0])
        } catch (e: Throwable) {
            e.printStackTrace()
            mainHandler.post {
                onError?.invoke(e)
                onComplete?.invoke()
                isExecuting = false
                clear()
            }
            null
        }
    }

    override fun onProgressUpdate(vararg values: Int?) {
        values[0]?.run {
            onProgress?.invoke(this)
        }
    }

    fun onStart(onStart: () -> Unit): Async<I, O> {
        this.onStart = onStart
        return this
    }

    fun onProgress(onProgress: (Int) -> Unit): Async<I, O> {
        this.onProgress = onProgress
        return this
    }

    fun onSuccess(onSuccess: (result: O) -> Unit): Async<I, O> {
        this.onSuccess = onSuccess
        return this
    }

    fun onError(onError: (e: Throwable) -> Unit): Async<I, O> {
        this.onError = onError
        return this
    }

    fun onCancel(onCancel: () -> Unit): Async<I, O> {
        this.onCancel = onCancel
        return this
    }

    fun onComplete(onComplete: () -> Unit): Async<I, O> {
        this.onComplete = onComplete
        return this
    }

    fun start(): Async<I, O> {
        execute(input)
        return this
    }

    override fun onPreExecute() {
        isExecuting = true
        mainHandler.post { this.onStart?.invoke() }
    }

    override fun onPostExecute(result: O?) {
        isExecuting = false
        if (result != null) {
            onSuccess?.invoke(result)
            onComplete?.invoke()

            clear()
        }
    }

    override fun onCancelled() {
        isExecuting = false
        onCancel?.invoke()
        onComplete?.invoke()

        clear()
    }

    private fun clear() {
        onStart = null
        onProgress = null
        onSuccess = null
        onError = null
        onCancel = null
        onComplete = null
    }

}