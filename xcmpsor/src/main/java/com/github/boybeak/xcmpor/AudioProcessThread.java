package com.github.boybeak.xcmpor;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AudioProcessThread extends Thread implements VideoProgressListener {

    private final VideoProcessor.MediaSource mMediaSource;
    private final Integer mStartTimeMs;
    private final Integer mEndTimeMs;
    private final Context mContext;
    private Exception mException;
    private final MediaMuxer mMuxer;
    private final int mMuxerAudioTrackIndex;
    private final MediaExtractor mExtractor;
    private final CountDownLatch mMuxerStartLatch;
    private VideoProgressAve mProgressAve;

    public AudioProcessThread(Context context, VideoProcessor.MediaSource mediaSource, MediaMuxer muxer,
                              @Nullable Integer startTimeMs, @Nullable Integer endTimeMs, int muxerAudioTrackIndex,
                              CountDownLatch muxerStartLatch

    ) {
        super("VideoProcessDecodeThread");
        mMediaSource = mediaSource;
        mStartTimeMs = startTimeMs;
        mEndTimeMs = endTimeMs;
        mMuxer = muxer;
        mContext = context;
        mMuxerAudioTrackIndex = muxerAudioTrackIndex;
        mExtractor = new MediaExtractor();
        mMuxerStartLatch = muxerStartLatch;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void run() {
        super.run();
        try {
            doProcessAudio();
        } catch (Exception e) {
            e.printStackTrace();
            mException = e;
        } finally {
            mExtractor.release();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void doProcessAudio() throws Exception {
        mMediaSource.setDataSource(mExtractor);
        int audioTrackIndex = VideoUtil.selectTrack(mExtractor, true);
        if (audioTrackIndex >= 0) {
            //处理音频
            mExtractor.selectTrack(audioTrackIndex);
            MediaFormat mediaFormat = mExtractor.getTrackFormat(audioTrackIndex);
            String inputMimeType = mediaFormat.containsKey(MediaFormat.KEY_MIME)?mediaFormat.getString(MediaFormat.KEY_MIME):MediaFormat.MIMETYPE_AUDIO_AAC;
            String outputMimeType = MediaFormat.MIMETYPE_AUDIO_AAC;
            //音频暂不支持变速
            Integer startTimeUs = mStartTimeMs == null ? null : mStartTimeMs * 1000;
            Integer endTimeUs = mEndTimeMs == null ? null : mEndTimeMs * 1000;
            boolean await = mMuxerStartLatch.await(3, TimeUnit.SECONDS);
            if (!await) {
                throw new TimeoutException("wait muxerStartLatch timeout!");
            }
            AudioUtil.writeAudioTrack(mExtractor, mMuxer, mMuxerAudioTrackIndex, startTimeUs, endTimeUs, this);
        }
        if (mProgressAve != null) {
            mProgressAve.setAudioProgress(1);
        }
    }

    public Exception getException() {
        return mException;
    }

    public void setProgressAve(VideoProgressAve progressAve) {
        mProgressAve = progressAve;
    }

    @Override
    public void onProgress(float progress) {
        if (mProgressAve != null) {
            mProgressAve.setAudioProgress(progress);
        }
    }
}