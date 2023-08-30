package com.github.boybeak.xcmpor;

import static com.github.boybeak.xcmpor.AudioUtil.getAudioBitrate;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@TargetApi(21)
public class VideoProcessor {
    final static String TAG = "VideoProcessor";
    final static String OUTPUT_MIME_TYPE = "video/avc";

    public static int DEFAULT_FRAME_RATE = 20;
    /**
     * 只有关键帧距为0的才能方便做逆序
     */
    public final static int DEFAULT_I_FRAME_INTERVAL = 1;

    public final static int DEFAULT_AAC_BITRATE = 192 * 1000;
    /**
     * 控制音频合成时，如果输入的音频文件长度不够，是否重复填充
     */
    public static boolean AUDIO_MIX_REPEAT = true;

    final static int TIMEOUT_USEC = 2500;

    /**
     * 支持裁剪缩放快慢放
     */
    public static void processVideo(@NotNull Context context, @NotNull Processor processor) throws Exception {

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        processor.input.setDataSource(retriever);
        int originWidth = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        int originHeight = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        int rotationValue = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
        int oriBitrate = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
        int durationMs = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        retriever.release();
        if (processor.bitrate == null) {
            processor.bitrate = oriBitrate;
        }
        if (processor.iFrameInterval == null) {
            processor.iFrameInterval = DEFAULT_I_FRAME_INTERVAL;
        }

        int resultWidth = processor.outWidth == null ? originWidth : processor.outWidth;
        int resultHeight = processor.outHeight == null ? originHeight : processor.outHeight;
        resultWidth = resultWidth % 2 == 0 ? resultWidth : resultWidth + 1;
        resultHeight = resultHeight % 2 == 0 ? resultHeight : resultHeight + 1;

        if (rotationValue == 90 || rotationValue == 270) {
            int temp = resultHeight;
            resultHeight = resultWidth;
            resultWidth = temp;
        }

        MediaExtractor extractor = new MediaExtractor();
        processor.input.setDataSource(extractor);
        int videoIndex = VideoUtil.selectTrack(extractor, false);
        int audioIndex = VideoUtil.selectTrack(extractor, true);
        MediaMuxer mediaMuxer = new MediaMuxer(processor.output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int muxerAudioTrackIndex = 0;
        Integer audioEndTimeMs = processor.endTimeMs;
        if (audioIndex >= 0) {
            MediaFormat audioTrackFormat = extractor.getTrackFormat(audioIndex);
            String audioMimeType = MediaFormat.MIMETYPE_AUDIO_AAC;
            int bitrate = getAudioBitrate(audioTrackFormat);
            int channelCount = audioTrackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int sampleRate = audioTrackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int maxBufferSize = AudioUtil.getAudioMaxBufferSize(audioTrackFormat);
            MediaFormat audioEncodeFormat = MediaFormat.createAudioFormat(audioMimeType, sampleRate, channelCount);//参数对应-> mime type、采样率、声道数
            audioEncodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);//比特率
            audioEncodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioEncodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxBufferSize);

            long videoDurationUs = durationMs * 1000L;
            long audioDurationUs = audioTrackFormat.getLong(MediaFormat.KEY_DURATION);

            if (processor.startTimeMs != null || processor.endTimeMs != null) {
                if (processor.startTimeMs != null && processor.endTimeMs != null) {
                    videoDurationUs = (processor.endTimeMs - processor.startTimeMs) * 1000L;
                }
                long avDurationUs = Math.min(videoDurationUs, audioDurationUs);
                audioEncodeFormat.setLong(MediaFormat.KEY_DURATION, avDurationUs);
                audioEndTimeMs = (processor.startTimeMs == null ? 0 : processor.startTimeMs) + (int) (avDurationUs / 1000);
            }

            AudioUtil.checkCsd(audioEncodeFormat,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                    sampleRate,
                    channelCount
            );
            //提前推断出音頻格式加到MeidaMuxer，不然实际上应该到音频预处理完才能addTrack，会卡住视频编码的进度
            muxerAudioTrackIndex = mediaMuxer.addTrack(audioEncodeFormat);
        }
        extractor.selectTrack(videoIndex);
        if (processor.startTimeMs != null) {
            extractor.seekTo(processor.startTimeMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        } else {
            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        }

        VideoProgressAve progressAve = new VideoProgressAve(processor.listener);
        progressAve.setStartTimeMs(processor.startTimeMs == null ? 0 : processor.startTimeMs);
        progressAve.setEndTimeMs(processor.endTimeMs == null ? durationMs : processor.endTimeMs);
        AtomicBoolean decodeDone = new AtomicBoolean(false);
        CountDownLatch muxerStartLatch = new CountDownLatch(1);
        VideoEncodeThread encodeThread = new VideoEncodeThread(extractor, mediaMuxer,processor.bitrate,
                resultWidth, resultHeight, processor.iFrameInterval, processor.frameRate == null ? DEFAULT_FRAME_RATE : processor.frameRate, videoIndex,
                decodeDone, muxerStartLatch);
        int srcFrameRate = VideoUtil.getFrameRate(processor.input);
        if (srcFrameRate <= 0) {
            srcFrameRate = (int) Math.ceil(VideoUtil.getAveFrameRate(processor.input));
        }
        VideoDecodeThread decodeThread = new VideoDecodeThread(encodeThread, extractor, processor.startTimeMs, processor.endTimeMs,
                srcFrameRate, processor.frameRate == null ? DEFAULT_FRAME_RATE : processor.frameRate,
                processor.dropFrames, videoIndex, decodeDone);

        AudioProcessThread audioProcessThread = new AudioProcessThread(context, processor.input,
                mediaMuxer, processor.startTimeMs, audioEndTimeMs, muxerAudioTrackIndex, muxerStartLatch);
        encodeThread.setProgressAve(progressAve);
        audioProcessThread.setProgressAve(progressAve);
        decodeThread.start();
        encodeThread.start();
        audioProcessThread.start();
        try {
            long s = System.currentTimeMillis();
            decodeThread.join();
            encodeThread.join();
            long e1 = System.currentTimeMillis();
            audioProcessThread.join();
            long e2 = System.currentTimeMillis();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            mediaMuxer.release();
            extractor.release();
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        if (encodeThread.getException() != null) {
            throw encodeThread.getException();
        } else if (decodeThread.getException() != null) {
            throw decodeThread.getException();
        } else if (audioProcessThread.getException() != null) {
            throw audioProcessThread.getException();
        }
    }

    public static Processor processor(Context context) {
        return new Processor(context);
    }

    public static class Processor {
        private Context context;
        private MediaSource input;
        private String output;
        @Nullable
        private Integer outWidth;
        @Nullable
        private Integer outHeight;
        @Nullable
        private Integer startTimeMs;
        @Nullable
        private Integer endTimeMs;
        /*@Nullable
        private Float speed;*/
        /*@Nullable
        private Boolean changeAudioSpeed;*/
        @Nullable
        private Integer bitrate;
        @Nullable
        private Integer frameRate;
        @Nullable
        private Integer iFrameInterval;
        @Nullable
        private VideoProgressListener listener;
        /**
         * 帧率超过指定帧率时是否丢帧
         */
        private boolean dropFrames = true;

        public Processor(Context context) {
            this.context = context;
        }

        public Processor input(MediaSource input) {
            this.input = input;
            return this;
        }
        public Processor input(Uri input) {
            this.input = new MediaSource(context,input);
            return this;
        }

        public Processor input(String input) {
            this.input = new MediaSource(input);
            return this;
        }

        public Processor output(String output) {
            this.output = output;
            return this;
        }

        public Processor outWidth(int outWidth) {
            this.outWidth = outWidth;
            return this;
        }

        public Processor outHeight(int outHeight) {
            this.outHeight = outHeight;
            return this;
        }

        public Processor startTimeMs(int startTimeMs) {
            this.startTimeMs = startTimeMs;
            return this;
        }

        public Processor endTimeMs(int endTimeMs) {
            this.endTimeMs = endTimeMs;
            return this;
        }

        /*public Processor speed(float speed) {
            this.speed = speed;
            return this;
        }

        public Processor changeAudioSpeed(boolean changeAudioSpeed) {
            this.changeAudioSpeed = changeAudioSpeed;
            return this;
        }*/

        public Processor bitrate(int bitrate) {
            this.bitrate = bitrate;
            return this;
        }

        public Processor frameRate(int frameRate) {
            this.frameRate = frameRate;
            return this;
        }

        public Processor iFrameInterval(int iFrameInterval) {
            this.iFrameInterval = iFrameInterval;
            return this;
        }

        /**
         * 帧率超过指定帧率时是否丢帧,默认为true
         */
        public Processor dropFrames(boolean dropFrames) {
            this.dropFrames = dropFrames;
            return this;
        }

        public Processor progressListener(VideoProgressListener listener) {
            this.listener = listener;
            return this;
        }

        public void process() throws Exception {
            processVideo(context, this);
        }
    }

    public static class MediaSource {

        public Context context;
        public String inputPath;
        public Uri inputUri;

        private int width, height;
        private int bitrate;
        private int fps;

        public MediaSource(String inputPath) {
            this.inputPath = inputPath;
            try {
                initParse(inputPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public MediaSource(Context context, Uri inputUri) {
            this.context = context;
            this.inputUri = inputUri;

            try {
                initParse(context, inputUri);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void initParse(String inputPath) throws IOException {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(inputPath);
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(inputPath);

            initParse(retriever, extractor);
        }
        private void initParse(Context context, Uri inputUri) throws IOException {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(context, inputUri);
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(context, inputUri, null);

            initParse(retriever, extractor);
        }
        private void initParse(MediaMetadataRetriever retriever, MediaExtractor extractor) {
            width = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            height = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            bitrate = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
            fps = parseFPS(extractor);
            try {
                retriever.close();
                retriever.release();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            extractor.release();
        }
        private int parseFPS(MediaExtractor extractor) {
            int numTracks = extractor.getTrackCount();
            for (int i = 0; i < numTracks; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        return format.getInteger(MediaFormat.KEY_FRAME_RATE);
                    }
                }
            }
            return 0;
        }

        public void setDataSource(MediaMetadataRetriever retriever){
            if(inputPath!=null){
                retriever.setDataSource(inputPath);
            }else{
                retriever.setDataSource(context,inputUri);
            }
        }

        public void setDataSource(MediaExtractor extractor) throws IOException {
            if(inputPath!=null){
                extractor.setDataSource(inputPath);
            }else{
                extractor.setDataSource(context,inputUri,null);
            }
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getBitrate() {
            return bitrate;
        }

        public int getFps() {
            return fps;
        }
    }
}
