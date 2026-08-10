package net.kiwi.lawncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.File;

import com.touchfoo.swordigo.Native;
import com.L.SwordigoRuntime.GameView;
import com.L.SwordigoRuntime.GameRenderer;
import net.kiwi.lawncher.ui.LauncherShell;

public class MainActivity extends Activity {

    private static final String TAG = "Launcher";

    private static MainActivity instance;
    private GameView glSurfaceView;
    private String targetApkPath;
    private boolean hooksLoaded = false;

    /**
     * Id of the instance whose native libs are loaded in this process.
     * Switching instances mid-process is unsafe (same lib sonames already
     * mapped) — the launcher asks for a restart instead.
     */
    private static String loadedInstanceId = "";

    // From liblawncher.so (launcher/main.c)
    public static native void init();

    public static native void initPaths(String internalFiles, String externalFiles);

    /** Tells native code which instance + mod this session runs (asset/save overrides). */
    public static native void setSession(String instanceId, String modId);

    public static String currentMod() {
        return Launcher.currentMod();
    }

    /** Queried by native code (assets.c) to resolve the active instance. */
    public static String currentInstance() {
        return LauncherShell.activeInstanceId();
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
        initPaths(internalPath, externalPath);

        // The game is no longer required to start the launcher. If no
        // instance exists yet, snapshot the installed game in the
        // background; when it isn't installed, the Mods screen prompts the
        // user to provide an APK.
        ensureInstanceOnBoot();
    }

    private void ensureInstanceOnBoot() {
        new Thread(() -> {
            try {
                // Backfill internal libs for any pre-existing instances (e.g.
                // created by an earlier build that stored libs externally) so
                // the first launch after an update stays a fast no-op.
                for (InstanceManager.InstanceInfo existing : InstanceManager.listInstances(this)) {
                    InstanceManager.ensureLibs(this, existing);
                }
                if (!InstanceManager.listInstances(this).isEmpty()) return;
                if (InstanceManager.scanInstalledGame(this) == null) return; // Mods screen shows the prompt

                final InstanceManager.InstanceInfo created =
                        InstanceManager.ensureVanillaInstance(this);
                if (created == null) return;
                LauncherShell.setActiveInstance(created.id);                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    Toast.makeText(this, "Game found — created instance \u201C" + created.name + "\u201D",
                            Toast.LENGTH_LONG).show();
                    LauncherShell.refreshCurrentScreen();
                });
            } catch (Throwable t) {
                Log.e(TAG, "boot instance scan failed", t);
            }
        }).start();
    }

    public static void launch() {
        if (instance != null) {
            instance.startGame();
        }
    }

    @Override
    public void onBackPressed() {
        // Let the launcher UI consume back presses (drawer, overlays, file nav)
        // while it is on screen; otherwise fall back to the default.
        if (Launcher.onBackPressed()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == Launcher.INSTANCE_PICKER_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                LauncherShell.handleInstanceApkPicked(this, data.getData());
            }
            return;
        }

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
        InstanceManager.InstanceInfo inst = InstanceManager.resolveActiveInstance(this);
        if (inst == null) {
            Toast.makeText(this, "No game instance yet \u2014 add one in the Mods screen first",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Loading a different instance's native libs mid-process is not safe
        // (same sonames already mapped) — require a relaunch when switching.
        if (!loadedInstanceId.isEmpty() && !loadedInstanceId.equals(inst.id)) {
            Toast.makeText(this, "Switching instances needs a restart \u2014 close Lawncher and open it again",
                    Toast.LENGTH_LONG).show();
            return;
        }

        File libDir = InstanceManager.ensureLibs(this, inst);
        if (libDir == null) {
            Toast.makeText(this, "Couldn't prepare the game libraries for this instance",
                    Toast.LENGTH_LONG).show();
            return;
        }

        File swordigo = new File(libDir, "libswordigo.so");
        File openal = new File(libDir, "libopenal-soft.so");
        if (!openal.exists()) openal = new File(libDir, "libopenal.so");

        if (!swordigo.exists()) {
            Toast.makeText(this, "No libswordigo.so found in this instance's APK",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            if (openal.exists()) System.load(openal.getAbsolutePath());
            System.load(swordigo.getAbsolutePath());
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load game libraries for instance " + inst.id, t);
            Toast.makeText(this, "Failed to load the game libraries from this instance\n"
                    + "(APK: " + inst.name + ")", Toast.LENGTH_LONG).show();
            return;
        }
        loadedInstanceId = inst.id;
        targetApkPath = inst.sourceApk().getAbsolutePath();

        // 0. Install hooks BEFORE the game's init/update loop can start.
        preload();
        setSession(inst.id, LauncherShell.launchModId());
        Launcher.setGameMode(true);

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

        // 2. Setup the Native Environment using the instance's APK path
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
        Launcher.setGameMode(false);

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
            // Keep the mounted game assets reachable from Java too (MusicPlayer reads music/*.mp3 from it).
            Native.gameAssets = assetManagerToUse;

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
}
