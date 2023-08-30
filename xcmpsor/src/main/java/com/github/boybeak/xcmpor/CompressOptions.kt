package com.github.boybeak.xcmpor

import android.content.Context
import android.net.Uri
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CompressOptions private constructor(
    val source: VideoProcessor.MediaSource,
    val output: String,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val fps: Int
) {

    companion object {

        private const val TAG = "CompressOptions"

        fun from(src: String): Builder {
            return Builder(VideoProcessor.MediaSource(src))
        }
        fun from(context: Context, uri: Uri): Builder {
            return Builder(VideoProcessor.MediaSource(context, uri))
        }
        // 一切参数先按照原参数设定，可以稍后修改
        fun asUsual(src: String): Builder {
            val source = VideoProcessor.MediaSource(src)
            return Builder(source).bitrate(source.bitrate)
                .frameRate(source.fps)
                .size(source.width, source.height)
        }
        fun asUsual(context: Context, uri: Uri): Builder {
            val source = VideoProcessor.MediaSource(context, uri)
            return Builder(source).bitrate(source.bitrate)
                .frameRate(source.fps)
                .size(source.width, source.height)
        }
        fun chooseVideoOptions(src: String, dst: String): CompressOptions {
            return chooseVideoOptions(VideoProcessor.MediaSource(src), dst)
        }
        fun chooseVideoOptions(context: Context, src: Uri, dst: String): CompressOptions {
            return chooseVideoOptions(VideoProcessor.MediaSource(context, src), dst)
        }
        fun chooseMediaOptions(src: String, dst: String): CompressOptions {
            return chooseVideoOptions(src, dst)
        }
        fun chooseMediaOptions(context: Context, src: Uri, dst: String): CompressOptions {
            return chooseVideoOptions(context, src, dst)
        }
        private fun chooseVideoOptions(source: VideoProcessor.MediaSource, dst: String): CompressOptions {
            val (dstWidth, dstHeight) = if (source.width < source.height) {
                var pendingHeight = (540F / source.width * source.height).toInt()
                if (pendingHeight / 2 == 1) {
                    pendingHeight -= 1
                }
                Pair(540, pendingHeight)
            } else {
                var pendingWidth = (540F / source.height * source.width).toInt()
                if (pendingWidth / 2 == 1) {
                    pendingWidth -= 1
                }
                Pair(pendingWidth, 540)
            }
            val dstBitrate = min(550 * 1000, source.bitrate)
            val dstFPS = max(min(source.fps, 25), 12) // 避免srcFps为0的情况
            return Builder(source)
                .output(dst)
                .size(dstWidth, dstHeight)
                .bitrate(dstBitrate)
                .frameRate(dstFPS)
                .build()
        }
        fun cameraRecordOptions(src: String, dst: String): CompressOptions {
            val source = VideoProcessor.MediaSource(src)
            val minWidth = 176
            val pendingSizeScale = 0.1F
            val minScale = minWidth * 1F / source.width
            return Builder(source)
                .output(dst)
                .sizeScale(
                    if (minScale <= 1) {
                        max(pendingSizeScale, minScale)
                    } else {
                        pendingSizeScale
                    }
                )
                .bitrate(source.bitrate / 10)
                .build()
        }
    }

    class Builder internal constructor(private val source: VideoProcessor.MediaSource) {

        private var output: String? = null
        private var dstWidth: Int = source.width
        private var dstHeight: Int = source.height
        private var dstBitrate: Int = source.bitrate
        private var dstFPS: Int = source.fps

        fun output(dst: String): Builder {
            this.output = dst
            return this
        }

        fun width(width: Int): Builder {
            require(width > 0 && width % 2 != 1) { "width must > 0 and even number" }
            dstWidth = min(width, source.width)
            return this
        }

        fun height(height: Int): Builder {
            require(height > 0) { "height must > 0" }
            dstHeight = min(height, source.height)
            return this
        }

        fun size(width: Int, height: Int): Builder {
            width(width)
            height(height)
            return this
        }

        fun sizeScale(scale: Float): Builder {
            return sizeScale(scale, scale)
        }

        fun sizeScale(widthScale: Float, heightScale: Float): Builder {
            widthScale(widthScale)
            heightScale(heightScale)
            return this
        }

        fun widthScale(scale: Float): Builder {
            require(scale > 0F && scale <= 1F) { "scale must be in (0.0, 1.0]" }
            var pendingWidth = (source.width * scale).roundToInt()
            if (pendingWidth % 2 == 1) {
                pendingWidth += 1
            }
            return width(pendingWidth)
        }

        fun heightScale(scale: Float): Builder {
            require(scale > 0F && scale <= 1F) { "scale must be in (0.0, 1.0]" }
            return height((source.height * scale).roundToInt())
        }

        fun bitrate(bitrate: Int): Builder {
            require(bitrate > 0) { "bitrate must be > 0" }
            this.dstBitrate = min(bitrate, source.bitrate)
            return this
        }

        fun frameRate(fps: Int): Builder {
            require(fps > 0) { "fps must be > 0" }
            this.dstFPS = min(fps, source.fps)
            return this
        }

        fun build(): CompressOptions {
            require(output != null) { "You must set an output path" }
            return CompressOptions(source, output!!, dstWidth, dstHeight, dstBitrate, dstFPS)
        }

    }
}