package com.github.boybeak.xcmpor;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class AudioUtil {
    private final static Map<Integer, Integer> freqIdxMap = new HashMap<Integer, Integer>();

    static {
        freqIdxMap.put(96000, 0);
        freqIdxMap.put(88200, 1);
        freqIdxMap.put(64000, 2);
        freqIdxMap.put(48000, 3);
        freqIdxMap.put(44100, 4);
        freqIdxMap.put(32000, 5);
        freqIdxMap.put(24000, 6);
        freqIdxMap.put(22050, 7);
        freqIdxMap.put(16000, 8);
        freqIdxMap.put(12000, 9);
        freqIdxMap.put(11025, 10);
        freqIdxMap.put(8000, 11);
        freqIdxMap.put(7350, 12);
    }

    final static String TAG = "VideoProcessor";
    public static int VOLUMN_MAX_RATIO = 1;

    public static long writeAudioTrack(MediaExtractor extractor, MediaMuxer mediaMuxer, int muxerAudioTrackIndex,
                                       Integer startTimeUs, Integer endTimeUs, VideoProgressListener listener) throws IOException {
        return writeAudioTrack(extractor, mediaMuxer, muxerAudioTrackIndex, startTimeUs, endTimeUs, 0, listener);
    }

    /**
     * 不需要改变音频速率的情况下，直接读写就可
     */
    public static long writeAudioTrack(MediaExtractor extractor, MediaMuxer mediaMuxer, int muxerAudioTrackIndex,
                                       Integer startTimeUs, Integer endTimeUs, long baseMuxerFrameTimeUs, VideoProgressListener listener) throws IOException {
        int audioTrack = VideoUtil.selectTrack(extractor, true);
        extractor.selectTrack(audioTrack);
        if (startTimeUs == null) {
            startTimeUs = 0;
        }
        extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        MediaFormat audioFormat = extractor.getTrackFormat(audioTrack);
        long durationUs = audioFormat.getLong(MediaFormat.KEY_DURATION);
        int maxBufferSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        long lastFrametimeUs = baseMuxerFrameTimeUs;
        while (true) {
            long sampleTimeUs = extractor.getSampleTime();
            if (sampleTimeUs == -1) {
                break;
            }
            if (sampleTimeUs < startTimeUs) {
                extractor.advance();
                continue;
            }
            if (endTimeUs != null && sampleTimeUs > endTimeUs) {
                break;
            }
            if (listener != null) {
                float progress = (sampleTimeUs - startTimeUs) / (float) (endTimeUs == null ? durationUs : endTimeUs - startTimeUs);
                progress = progress < 0 ? 0 : progress;
                progress = progress > 1 ? 1 : progress;
                listener.onProgress(progress);
            }
            info.presentationTimeUs = sampleTimeUs - startTimeUs + baseMuxerFrameTimeUs;
            info.flags = extractor.getSampleFlags();
            info.size = extractor.readSampleData(buffer, 0);
            if (info.size < 0) {
                break;
            }
            mediaMuxer.writeSampleData(muxerAudioTrackIndex, buffer, info);
            lastFrametimeUs = info.presentationTimeUs;
            extractor.advance();
        }
        return lastFrametimeUs;
    }

    public static int getAudioMaxBufferSize(MediaFormat format) {
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            return format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        } else {
            return 100 * 1000;
        }
    }

    public static int getAudioBitrate(MediaFormat format) {
        if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
            return format.getInteger(MediaFormat.KEY_BIT_RATE);
        } else {
            return VideoProcessor.DEFAULT_AAC_BITRATE;
        }
    }

    public static void checkCsd(MediaFormat audioMediaFormat, int profile, int sampleRate, int channel) {
        int freqIdx = freqIdxMap.containsKey(sampleRate) ? freqIdxMap.get(sampleRate) : 4;
//        byte[] bytes = new byte[]{(byte) 0x11, (byte) 0x90};
//        ByteBuffer bb = ByteBuffer.wrap(bytes);
        ByteBuffer csd = ByteBuffer.allocate(2);
        csd.put(0, (byte) (profile << 3 | freqIdx >> 1));
        csd.put(1, (byte) ((freqIdx & 0x01) << 7 | channel << 3));
        audioMediaFormat.setByteBuffer("csd-0", csd);
    }
}
