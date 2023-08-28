package com.github.boybeak.videocompressor

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.github.boybeak.vcompressor.VideoSlimmer
import com.github.boybeak.vcompressor.VideoSlimmer.ProgressListener
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val file = copyToCacheIfNotExist()
        val dst = File(externalCacheDir, "abc.mp4")
        if (dst.exists()) {
            dst.delete()
        }
        VideoSlimmer.convertVideo(file.absolutePath, dst.absolutePath, 640, 360, 715, object : ProgressListener {
            override fun onStart() {
            }

            override fun onFinish(result: Boolean) {
            }

            override fun onProgress(progress: Float) {
            }
        })
    }

    private fun copyToCacheIfNotExist(): File {
        val out = File(externalCacheDir, "db.mp4")
        if (out.exists()) {
            return out
        }
        assets.open("Dragon Ball.mp4").copyTo(out.outputStream())
        return out
    }

}