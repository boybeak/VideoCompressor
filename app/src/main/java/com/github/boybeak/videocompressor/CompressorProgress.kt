package com.github.boybeak.videocompressor

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class CompressorProgress : LinearLayout {

    private val titleTV = TextView(context)
    private val progressBar = ProgressBar(context, null, android.R.style.Widget_Material_ProgressBar_Horizontal)
    private val textTV = TextView(context)

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    init {
        orientation = VERTICAL
        addView(titleTV, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addView(progressBar, LayoutParams(LayoutParams.MATCH_PARENT, 4))
        addView(textTV, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun setTitle(title: CharSequence) {
        titleTV.text = title
    }

    fun setText(text: CharSequence) {
        textTV.text = text
    }

    fun setProgress(progress: Int) {
        progressBar.progress = progress
    }

    fun setMax(max: Int) {
        progressBar.max = max
    }

}