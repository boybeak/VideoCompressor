package com.github.boybeak.videocompressor

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.config.AppSpecificStorageConfiguration
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.github.boybeak.vcompressor.VideoSlimmer
import com.github.boybeak.vcompressor.VideoSlimmer.ProgressListener
import com.github.boybeak.xcmpor.CompressOptions
import com.iceteck.silicompressorr.SiliCompressor
import com.vincent.videocompressor.VideoCompress
import nl.bravobit.ffmpeg.FFcommandExecuteResponseHandler
import nl.bravobit.ffmpeg.FFmpeg
import java.io.File


class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val videoInfoTV: TextView by lazy { findViewById(R.id.videoInfo) }

    private val sdkBtn: Button by lazy { findViewById(R.id.sdkBtn) }
    private val sdkCP: CompressorProgress by lazy { findViewById(R.id.sdkCMP) }

    private val slimmerBtn: Button by lazy { findViewById(R.id.slimmerBtn) }
    private val slimmerCP: CompressorProgress by lazy { findViewById(R.id.slimmerCMP) }

    private val lighterBtn: Button by lazy { findViewById(R.id.lighterBtn) }
    private val lightCP: CompressorProgress by lazy { findViewById(R.id.lightCMP) }

    private val fishBtn: Button by lazy { findViewById(R.id.fishBtn) }
    private val fishCP: CompressorProgress by lazy { findViewById(R.id.fishCMP) }

    private val siliBtn: Button by lazy { findViewById(R.id.siliBtn) }
    private val siliCP: CompressorProgress by lazy { findViewById(R.id.siliCMP) }

    private val brvFFBtn: Button by lazy { findViewById(R.id.brvFFBtn) }
    private val brvFFCP: CompressorProgress by lazy { findViewById(R.id.brvFFCMP) }

    private val arthFFBtn: Button by lazy { findViewById(R.id.arthFFBtn) }
    private val arthFFCP: CompressorProgress by lazy { findViewById(R.id.arthFFCMP) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val file = copyToCacheIfNotExist()

        videoInfoTV.text = getVideoInfo(file)

        sdkCP.setTitle("SDK")
        sdkBtn.setOnClickListener {
            sdkCompress(file)
        }

        slimmerCP.setTitle("Slimmer")
        slimmerBtn.setOnClickListener {
            compress(file)
        }

        lightCP.setTitle("Lighter")
        lighterBtn.setOnClickListener {
            lightCompress(file)
        }

        fishCP.setTitle("Fish")
        fishBtn.setOnClickListener {
            fishWjyCompress(file)
        }

        siliCP.setTitle("Sili")
        siliBtn.setOnClickListener {
            siliCompress(file)
        }

        brvFFCP.setTitle("BrvFF")
        brvFFBtn.setOnClickListener {
            bravobitFFMpeg(file)
        }

        arthFFCP.setTitle("Arth")
        arthFFBtn.setOnClickListener {
            arthenicaFFmpeg(file)
        }
    }

    private fun copyToCacheIfNotExist(): File {
        val out = File(externalCacheDir, "db.mp4")
        if (out.exists()) {
            return out
        }
        assets.open("Dragon Ball.mp4").copyTo(out.outputStream())
        return out
    }

    private fun getVideoInfo(video: File): String {

        val videoRetriever = MediaMetadataRetriever()
        videoRetriever.setDataSource(video.absolutePath)
        val width = videoRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val height = videoRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val bitrate = videoRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
        videoRetriever.release()

        val strBuilder = StringBuilder("Size($width * $height) Bitrate: $bitrate")

        val extractor = MediaExtractor()
        extractor.setDataSource(video.absolutePath)
        val numTracks = extractor.trackCount
        for (i in 0 until numTracks) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: break
            if (mime.startsWith("video/")) {
                if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    strBuilder.append(" FPS: ${format.getInteger(MediaFormat.KEY_FRAME_RATE)}")
                }
            }
        }
        extractor.release()
        return strBuilder.append(" FileSize: ").append(Formatter.formatFileSize(this, video.length())).toString()
    }

    private fun sdkCompress(file: File) {
        val dst = File(externalCacheDir, "sdk.mp4")
        if (dst.exists()) {
            dst.delete()
        }
        com.github.boybeak.xcmpor.VideoCompressor.compressAsync(
            this,
            CompressOptions.from(file.absolutePath)
                .sizeScale(0.5F)
                .bitrate(715 * 1000)
                .output(dst.absolutePath)
                .build()
        ).onStart {
            sdkCP.setMax(100)
        }.onProgress {
            sdkCP.setProgress(it)
            sdkCP.setText(it.toString())
        }.onSuccess {
            sdkCP.setText("Cost: ${it.timeCostMills / 1000F} ${getVideoInfo(it.output)}")
        }.onError {

        }.onComplete {

        }.onCancel {

        }.start()
    }

    private fun compress(file: File) {
        val dst = File(externalCacheDir, "slimmer.mp4")
        if (dst.exists()) {
            dst.delete()
        }

        VideoSlimmer.convertVideo(file.absolutePath, dst.absolutePath, 640, 360, 715, object : ProgressListener {

            private var startAt = 0L

            override fun onStart() {
                slimmerCP.setMax(100)
                startAt = System.currentTimeMillis()
            }

            override fun onFinish(result: Boolean) {
                slimmerCP.setText("Cost:${(System.currentTimeMillis() - startAt) / 1000F} ${getVideoInfo(dst)}")
            }

            override fun onProgress(progress: Float) {
                slimmerCP.setProgress(progress.toInt())
                slimmerCP.setText(progress.toString())
            }
        })
    }

    private fun lightCompress(file: File) {
        VideoCompressor.start(
            context = this,
            uris = listOf<Uri>(Uri.fromFile(file)),
            appSpecificStorageConfiguration = AppSpecificStorageConfiguration(),
            configureWith = Configuration(
                videoNames = listOf<String>(file.name),
                quality = VideoQuality.LOW,
                isMinBitrateCheckEnabled = false,
                disableAudio = false,
                videoWidth = 640.0,
                videoHeight = 360.0
            ),
            listener = object : CompressionListener {
                private var startAt = 0L
                override fun onCancelled(index: Int) {
                }

                override fun onFailure(index: Int, failureMessage: String) {
                }

                override fun onProgress(index: Int, percent: Float) {
                    lightCP.setMax(percent.toInt())
                    lightCP.setText(percent.toString())
                }

                override fun onStart(index: Int) {
                    startAt = System.currentTimeMillis()
                    lightCP.setMax(100)
                }

                override fun onSuccess(index: Int, size: Long, path: String?) {
                    lightCP.setText("Cost: ${(System.currentTimeMillis() - startAt) / 1000F} ${getVideoInfo(
                        File(path)
                    )}")
                }
            }
        )
    }

    private fun fishWjyCompress(file: File) {
        val dst = File(externalCacheDir, "fishWjy.mp4")
        val task = VideoCompress.compressVideoLow(file.absolutePath, dst.absolutePath, object : VideoCompress.CompressListener {
            private var startAt = 0L
            override fun onStart() {
                startAt = System.currentTimeMillis()
                fishCP.setMax(100)
            }

            override fun onSuccess() {
                fishCP.setText("Cost: ${(System.currentTimeMillis() - startAt) / 1000F} ${getVideoInfo(dst)}")
            }

            override fun onFail() {
            }

            override fun onProgress(percent: Float) {
                fishCP.setMax(percent.toInt())
                fishCP.setText(percent.toString())
            }
        })
    }

    private fun siliCompress(file: File) {
        val dst = File(externalCacheDir, "sili.mp4")
        Thread {
            val startAt = System.currentTimeMillis()
            val out = SiliCompressor.with(this).compressVideo(Uri.fromFile(file), dst.absolutePath, 640, 360, 715)
            Log.d(TAG, "siliCompress output=$out")
            val outFile = File(out)
            if (outFile.exists()) {
                runOnUiThread {
                    siliCP.setText("Cost:${(System.currentTimeMillis() - startAt) / 1000F} ${getVideoInfo(outFile)}")
                }
            }
        }.start()
    }

    private fun bravobitFFMpeg(file: File) {
        if (!FFmpeg.getInstance(this).isSupported) {
            Toast.makeText(this, "FFMpeg is not supported", Toast.LENGTH_SHORT).show()
            return
        }
        val dst = File(externalCacheDir, "bravobitFFMpeg.mp4")
        val cmd = arrayOf("-i", file.absolutePath, "-s", "640*360", "-b:v", "715k", dst.absolutePath)
        FFmpeg.getInstance(this).execute(cmd, object : FFcommandExecuteResponseHandler {
            private var startAt = 0L
            override fun onStart() {
                startAt = System.currentTimeMillis()
                brvFFCP.setMax(100)
            }

            override fun onFinish() {}

            override fun onSuccess(p0: String?) {
                Log.d(TAG, "bravobitFFMpeg onSuccess=$p0")
                brvFFCP.setText("Cost: ${(System.currentTimeMillis() - startAt) / 1000F} ${getVideoInfo(dst)}")
            }

            override fun onProgress(p0: String?) {
                Log.d(TAG, "bravobitFFMpeg onProgress=$p0")
//                lightCP.setMax(percent.toInt())
//                lightCP.setText(percent.toString())
            }

            override fun onFailure(p0: String?) {
                Log.e(TAG, "bravobitFFMpeg onFailure=$p0")
            }
        })
    }

    private fun arthenicaFFmpeg(file: File) {
        val dst = File(externalCacheDir, "ffmpeg.mp4")
        if (dst.exists()) {
            dst.delete()
        }
        val startAt = System.currentTimeMillis()
        val ffSession = FFmpegKit.execute("-i ${file.absolutePath} -s 640*360 -b:v 715k ${dst.absolutePath}")
        if (ReturnCode.isSuccess(ffSession.returnCode)) {
            arthFFCP.setText("Cost:${(System.currentTimeMillis() - startAt) / 1000F} ${getVideoInfo(dst)}")
            Log.d(TAG, "ffmpeg success cost:${(System.currentTimeMillis() - startAt) / 1000F} info:${getVideoInfo(dst)}")
        } else if (ReturnCode.isCancel(ffSession.returnCode)) {
            Log.d(TAG, "ffmpeg cancel")
        } else {
            Log.d(TAG, "ffmpeg error")
        }
    }

}