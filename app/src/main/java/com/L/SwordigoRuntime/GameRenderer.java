package com.L.SwordigoRuntime;

import android.app.Activity;
import android.content.res.Configuration;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import com.touchfoo.swordigo.Native;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GameRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "GameRenderer";

    private boolean applicationSetup = false;
    private double prevTime = 0;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        try {
            requestHighestRefreshRate();

            if (!applicationSetup) {
                Native.setupNativeInterface();
                Native.setupApplication();
                applicationSetup = true;
            } else {
                Native.reloadContext();
            }
        } catch (Throwable t) {
            Log.e(TAG, "onSurfaceCreated failed", t);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        try {
            gl.glViewport(0, 0, width, height);
            gl.glMatrixMode(GL10.GL_PROJECTION);
            gl.glLoadIdentity();
            gl.glMatrixMode(GL10.GL_MODELVIEW);
            gl.glLoadIdentity();
            boolean isPad = isTablet();
            int horizontalInset = getHorizontalCutoutInset();
            Native.setApplicationViewSize(width, height, isPad, horizontalInset, 0);
        } catch (Throwable t) {
            Log.e(TAG, "onSurfaceChanged failed", t);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        try {
            double now = System.nanoTime() / 1_000_000_000.0;
            float dt;
            if (prevTime == 0) {
                dt = 1f / 120f;          // safe first-frame step
            } else {
                dt = (float) (now - prevTime);
                if (dt > 0.0667f) {      // clamp huge hitches
                    dt = 0.0667f;
                }
            }
            prevTime = now;
            Native.updateApplication(dt);
            Native.drawApplication();
        } catch (Throwable t) {
            Log.e(TAG, "onDrawFrame failed", t);
        }
    }

    /**
     * Safely requests the highest refresh rate that has the SAME resolution
     * as the currently active mode.  This avoids the black-screen problem
     * that occurs when a high-Hz mode has a different width/height.
     */
    private void requestHighestRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.i(TAG, "Refresh-rate request skipped (API < 23)");
            return;
        }

        Activity activity = (Activity) Native.mainActivity;
        if (activity == null) {
            Log.w(TAG, "mainActivity is null – cannot request refresh rate");
            return;
        }

        Window window = activity.getWindow();
        if (window == null) {
            Log.w(TAG, "Window is null – cannot request refresh rate");
            return;
        }

        try {
            Display display = window.getWindowManager().getDefaultDisplay();
            Display.Mode currentMode = display.getMode();          // API 23+
            Display.Mode[] modes = display.getSupportedModes();

            if (modes == null || modes.length == 0) {
                Log.w(TAG, "No supported modes");
                return;
            }

            int curWidth  = currentMode.getPhysicalWidth();
            int curHeight = currentMode.getPhysicalHeight();
            float curRate = currentMode.getRefreshRate();

            Log.i(TAG, String.format(
            "Current mode: %dx%d @ %.1f Hz (modeId=%d)",
            curWidth, curHeight, curRate, currentMode.getModeId()));

            // Find the highest refresh rate that keeps the same resolution
            Display.Mode best = null;
            float bestRate = curRate;   // never go lower

            StringBuilder sb = new StringBuilder();
            for (Display.Mode m : modes) {
                float rate = m.getRefreshRate();
                sb.append(String.format("%dx%d@%.1fHz ",
                m.getPhysicalWidth(), m.getPhysicalHeight(), rate));

                // MUST match resolution, otherwise the surface breaks
                if (m.getPhysicalWidth()  == curWidth &&
                m.getPhysicalHeight() == curHeight &&
                rate > bestRate) {
                    best = m;
                    bestRate = rate;
                }
            }
            Log.i(TAG, "All supported modes: " + sb.toString().trim());

            if (best == null) {
                Log.i(TAG, "Already at the highest rate for this resolution (" + curRate + " Hz)");
                return;
            }

            WindowManager.LayoutParams params = window.getAttributes();
            int oldId = params.preferredDisplayModeId;
            params.preferredDisplayModeId = best.getModeId();
            window.setAttributes(params);

            Log.i(TAG, String.format(
            "Switched to %.1f Hz (modeId=%d, previous preferredModeId=%d)",
            bestRate, best.getModeId(), oldId));

        } catch (Throwable t) {
            Log.e(TAG, "Failed to request higher refresh rate bruh staying at current rate", t);
        }
    }

    private boolean isTablet() {
        Activity activity = (Activity) Native.mainActivity;
        if (activity == null) return false;
        return (activity.getResources().getConfiguration().screenLayout
        & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    private int getHorizontalCutoutInset() {
        Activity activity = (Activity) Native.mainActivity;
        if (activity == null || Build.VERSION.SDK_INT < 28) return 0;

        WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
        if (insets == null) return 0;

        DisplayCutout cutout = insets.getDisplayCutout();
        if (cutout == null) return 0;

        return Math.max(cutout.getSafeInsetLeft(), cutout.getSafeInsetRight());
    }
}