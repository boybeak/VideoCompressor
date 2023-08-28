package com.github.boybeak.vcompressor;

import android.media.MediaMuxer;
import android.util.Log;

import java.lang.reflect.Field;

public class MediaMuxerDebugger {

    private static final String TAG = "MediaMuxerDebugger";

    public static void showState(MediaMuxer muxer) {
        try {
            Field stateField = MediaMuxer.class.getDeclaredField("mState");
            stateField.setAccessible(true);
            int state = stateField.getInt(muxer);
            Log.d(TAG, "showState state=" + state);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

    }
}
