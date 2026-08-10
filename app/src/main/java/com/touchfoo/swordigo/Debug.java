package com.touchfoo.swordigo;

import android.util.Log;

public class Debug {
    private static final String TAG = "Swordigo";

    public static void Log(String message) {
        Log.d(TAG, message);
    }

    public static void Log(String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
    }
}