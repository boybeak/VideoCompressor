package com.github.boybeak.xcmpor;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;

public class VideoUtil {

    public static int selectTrack(MediaExtractor extractor, boolean audio) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (audio) {
                if (mime.startsWith("audio/")) {
                    return i;
                }
            } else {
                if (mime.startsWith("video/")) {
                    return i;
                }
            }
        }
        return -5;
    }

    public static int getFrameRate(VideoProcessor.MediaSource mediaSource) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            mediaSource.setDataSource(extractor);
            int trackIndex = VideoUtil.selectTrack(extractor, false);
            MediaFormat format = extractor.getTrackFormat(trackIndex);
            return format.containsKey(MediaFormat.KEY_FRAME_RATE) ? format.getInteger(MediaFormat.KEY_FRAME_RATE) : -1;
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        } finally {
            extractor.release();
        }
    }

    public static float getAveFrameRate(VideoProcessor.MediaSource mediaSource) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        mediaSource.setDataSource(extractor);
        int trackIndex = VideoUtil.selectTrack(extractor, false);
        extractor.selectTrack(trackIndex);
        long lastSampleTimeUs = 0;
        int frameCount = 0;
        while (true) {
            long sampleTime = extractor.getSampleTime();
            if (sampleTime < 0) {
                break;
            } else {
                lastSampleTimeUs = sampleTime;
            }
            frameCount++;
            extractor.advance();
        }
        extractor.release();
        return frameCount / (lastSampleTimeUs / 1000f / 1000f);
    }

    public static File getVideoCacheDir(Context context) {
        File cacheDir = new File(context.getCacheDir(), "video/");
        //noinspection ResultOfMethodCallIgnored
        cacheDir.mkdirs();
        return cacheDir;
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static boolean trySetProfileAndLevel(MediaCodec codec, String mime, MediaFormat format, int profileInt, int levelInt) {
        MediaCodecInfo codecInfo = codec.getCodecInfo();
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mime);
        MediaCodecInfo.CodecProfileLevel[] profileLevels = capabilities.profileLevels;
        if (profileLevels == null) {
            return false;
        }
        for (MediaCodecInfo.CodecProfileLevel level : profileLevels) {
            if (level.profile == profileInt) {
                if (level.level == levelInt) {
                    format.setInteger(MediaFormat.KEY_PROFILE, profileInt);
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        format.setInteger(MediaFormat.KEY_LEVEL, levelInt);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static int getMaxSupportBitrate(MediaCodec codec, String mime) {
        try {
            MediaCodecInfo codecInfo = codec.getCodecInfo();
            MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mime);
            Integer maxBitrate = capabilities.getVideoCapabilities().getBitrateRange().getUpper();
            return maxBitrate;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

}
