package net.kiwi.lawncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.touchfoo.swordigo.Native;
import com.L.SwordigoRuntime.GameView;
import com.L.SwordigoRuntime.GameRenderer;

public class MainActivity extends Activity {

    private static final String TAG = "Launcher";

    private static MainActivity instance;
    private GameView glSurfaceView;
    private String targetApkPath;
    private boolean hooksLoaded = false;

    // From liblawncher.so (launcher/main.c)
    public static native void init();

    public static native void initPaths(String internalFiles, String externalFiles);

    public static String currentMod() {
        return Launcher.currentMod();
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        String internalPath = getFilesDir().getAbsolutePath();
        File extDir = getExternalFilesDir(null);
        String externalPath = (extDir != null) ? extDir.getAbsolutePath() : "";

        if (extDir != null) {
            File resourcesDir = new File(extDir, "resources");
            if (!resourcesDir.exists()) {
                resourcesDir.mkdirs();
            }
        }

        System.loadLibrary("lawncher");
        instance = this;

        Launcher.init(this, findViewById(android.R.id.content));

        String targetPackage = "com.touchfoo.swordigo";
        File internalLibDir = new File(getFilesDir(), "Extracted/lib/arm64-v8a");
        if (!internalLibDir.exists()) {
            internalLibDir.mkdirs();
        }

        File openAlFile = new File(internalLibDir, "libopenal-soft.so");
        File swordigoFile = new File(internalLibDir, "libswordigo.so");

        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(targetPackage, 0);
            targetApkPath = info.sourceDir;

            extractLibFromZip(targetApkPath, "lib/arm64-v8a/libopenal-soft.so", openAlFile);
            extractLibFromZip(targetApkPath, "lib/arm64-v8a/libswordigo.so", swordigoFile);

            System.load(openAlFile.getAbsolutePath());
            System.load(swordigoFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract/load libraries", e);
            finish();
            return;
        }

        initPaths(internalPath, externalPath);
    }

    public static void launch() {
        if (instance != null) {
            instance.startGame();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Check if the result is coming from our file picker
        if (requestCode == Launcher.FILE_PICKER_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri selectedFileUri = data.getData();

                // Pass the URI back to the Launcher to extract it in the background
                Launcher.processSelectedFile(this, selectedFileUri);
            }
        }
    }

    public static native void preload();

    private void startGame() {
        if (targetApkPath == null) return;

        // 0. Load libhooks.so and install hooks BEFORE the game's native
        //    init/update loop can start calling into libswordigo.so.

        preload();

        setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        );

        // 1. Setup the OpenGL Surface View
        glSurfaceView = new GameView(this);
        glSurfaceView.setEGLConfigChooser(5, 6, 5, 0, 16, 0);
        glSurfaceView.setPreserveEGLContextOnPause(true);
        glSurfaceView.setRenderer(new GameRenderer());

        // Replace the launcher view with the Game View
        setContentView(glSurfaceView);
        enableImmersiveMode();

        // 2. Setup the Native Environment using the extracted APK path
        setupNativeEnvironment(targetApkPath);
        Launcher.initGameButtons();

        // 3. Now that hooks are in and env is set up, run the game's own init
        init();
    }

    private void unloadGameHooks() {
        if (!hooksLoaded) return;
        try {

        } catch (Throwable t) {
            Log.e(TAG, "Failed to unload hooks", t);
        } finally {
            hooksLoaded = false;
        }
    }

    /**
     * Call this to leave the game session and go back to the launcher UI
     * (e.g. from a menu button), as opposed to onDestroy() tearing
     * everything down for good.
     */
    public static void returnToLauncher() {
        if (instance != null) {
            instance.stopGame();
        }
    }

    private void stopGame() {
        unloadGameHooks();

        if (glSurfaceView != null) {
            glSurfaceView.onPause();
        }
        glSurfaceView = null;

        // Swap back to the launcher's own view.
        setContentView(findViewById(android.R.id.content));
        Launcher.init(this, findViewById(android.R.id.content));
    }

    private void setupNativeEnvironment(String apkPath) {
        try {
            Native.mainActivity = this;

            // Use the standard launcher directories for the game data
            Native.setFilesDir(getApplicationContext().getFilesDir().toString());
            Native.setCacheDir(getApplicationContext().getCacheDir().toString());

            // Build AssetManager directly using the known APK path
            AssetManager assetManagerToUse = buildDetectedAssetManager(apkPath);
            if (assetManagerToUse == null) {
                finish();
                return;
            }

            Native.setAssetManager(assetManagerToUse);

            Native.handleApplicationLaunch();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to setup native environment", t);
        }
    }

    private AssetManager buildDetectedAssetManager(String apkPath) {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            java.lang.reflect.Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
            Object result = addAssetPath.invoke(assetManager, apkPath);
            boolean success = (result instanceof Integer) && ((Integer) result) != 0;
            return success ? assetManager : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void enableImmersiveMode() {
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (glSurfaceView != null) {
            glSurfaceView.setSystemUiVisibility(flags);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (glSurfaceView != null) {
            glSurfaceView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glSurfaceView != null) {
            glSurfaceView.onResume();
            enableImmersiveMode();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unloadGameHooks();
        if (instance == this) {
            instance = null;
        }
    }

    /**
     * Extracts a specific file from a Zip/APK archive directly to internal storage.
     */
    private void extractLibFromZip(String apkPath, String zipEntryPath, File destFile) throws IOException {
        if (destFile.exists() && destFile.length() > 0) {
            destFile.setReadable(true, false);
            destFile.setExecutable(true, false);
            return;
        }

        try (ZipFile zipFile = new ZipFile(apkPath)) {
            ZipEntry entry = zipFile.getEntry(zipEntryPath);
            if (entry == null) {
                throw new java.io.FileNotFoundException(
                        "Could not find " + zipEntryPath + " inside " + apkPath);
            }

            try (InputStream in = zipFile.getInputStream(entry);
                 FileOutputStream out = new FileOutputStream(destFile)) {

                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }

            destFile.setReadable(true, false);
            destFile.setExecutable(true, false);
        }
    }
}