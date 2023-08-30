package com.github.boybeak.xcmpor

import android.content.Context
import java.io.File
import kotlin.math.roundToInt

fun File.mkdirsIfNotExists() {
    if (exists()) {
        return
    }
    mkdirs()
}

object VideoCompressor {
    fun compressSync(context: Context, options: CompressOptions, block: ((Float) -> Unit)? = null): CompressResult {
        val startAt = System.currentTimeMillis()
        val outputFile = File(options.output)
        outputFile.parentFile.mkdirsIfNotExists()
        if (outputFile.exists()) {
            outputFile.delete()
        }

        VideoProcessor.processor(context)
            .input(options.source)
            .output(options.output)
            .outWidth(options.width)
            .outHeight(options.height)
            .bitrate(options.bitrate)
            .frameRate(options.fps)
            .dropFrames(true)
            .progressListener(block)
            .process()

        val timeCostMills = System.currentTimeMillis() - startAt
        val success = outputFile.exists()

        return CompressResult(outputFile, timeCostMills, success)
    }
    fun compressAsync(context: Context, options: CompressOptions): Async<Unit, CompressResult> {
        return async { progressUpdater ->
            compressSync(context, options) { progress ->
                progressUpdater.updateProgress((progress * 100).roundToInt())
            }
        }
    }

    class CompressResult(val output: File, val timeCostMills: Long, val success: Boolean) {
        override fun toString(): String {
            return "CompressResult(output=${output.absolutePath}, timeCostMills=${timeCostMills / 1000} sec, success=$success)"
        }
    }

}