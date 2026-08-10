package com.L.SwordigoRuntime;

import android.app.Activity;
import android.content.res.Configuration;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.WindowInsets;
import com.touchfoo.swordigo.Native;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
// ============== FULL IMPLEMENTATION ============ ✅✅✅
public class GameRenderer implements GLSurfaceView.Renderer {

    private boolean applicationSetup = false;
    private double prevTime = 0;
    private int surfaceW;
    private int surfaceH;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        try {
            // New GL context: any GL objects (e.g. PostFX FBO/shaders) died with it.
            PostFx.onContextLost();
            if (!applicationSetup) {
                Native.setupNativeInterface();
                Native.setupApplication();
                applicationSetup = true;
            } else {
                Native.reloadContext();
            }
        } catch (Throwable t) {
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        try {
            surfaceW = width;
            surfaceH = height;
            gl.glViewport(0, 0, width, height);
            gl.glMatrixMode(GL10.GL_PROJECTION);
            gl.glLoadIdentity();
            gl.glMatrixMode(GL10.GL_MODELVIEW);
            gl.glLoadIdentity();

            boolean isPad = isTablet();
            int horizontalInset = getHorizontalCutoutInset();

            Native.setApplicationViewSize(width, height, isPad, horizontalInset, 0);
        } catch (Throwable t) {
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        try {
            double now = System.nanoTime() / 1_000_000_000.0;
            float dt;
            if (prevTime == 0) {
                dt = 0.016666668f;
            } else {
                dt = (float) (now - prevTime);
                if (dt > 0.0667f) {
                    dt = 0.0667f;
                }
            }
            prevTime = now;

            // PostFX: render the game into a host FBO, then run the filter
            // pass into the default framebuffer before the surface swap.
            if (PostFx.enabled()) {
                PostFx.bind(surfaceW, surfaceH);
            }
            Native.updateApplication(dt);
            Native.drawApplication();
            PostFx.apply(surfaceW, surfaceH);
        } catch (Throwable t) {
        }
    }

    private boolean isTablet() {
        Activity activity = (Activity) Native.mainActivity;
        if (activity == null) {
            return false;
        }
        boolean result = (activity.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
        return result;
    }

    private int getHorizontalCutoutInset() {
        Activity activity = (Activity) Native.mainActivity;
        if (activity == null || Build.VERSION.SDK_INT < 28) {
            return 0;
        }

        WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
        if (insets == null) {
            return 0;
        }

        DisplayCutout cutout = insets.getDisplayCutout();
        if (cutout == null) {
            return 0;
        }

        int result = Math.max(cutout.getSafeInsetLeft(), cutout.getSafeInsetRight());
        return result;
    }
}