package net.kiwi.lawncher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.L.SwordigoRuntime.GameView;
import com.L.SwordigoRuntime.GameRenderer;
import com.touchfoo.swordigo.MusicPlayer;
import com.touchfoo.swordigo.Native;

import org.json.JSONArray;
import org.json.JSONObject;

@Keep
public class MainActivity extends Activity {

    private static final String TAG = "Lawncher";
    private static final String CRASH_LOG_NAME = "last_crash.log";

    private static MainActivity instance;
    private GameView glSurfaceView;
    private String targetApkPath;
    private boolean hooksLoaded = false;
    private boolean swordigoReady = false;

    private static final int Version = 72;

    public static int getVersion() { return Version; }

    public static Activity getCurrentActivity() {
        return instance;
    }

    void check_ver() {
        if (!SettingsScreen.pref(this, SettingsScreen.KEY_CHECK_UPDATES, true)) return;
        new Thread(() -> {
            try {
                String raw = httpGet("https://raw.githubusercontent.com/raijinswordigo/requests/refs/heads/main/lawncher.json");
                JSONObject o = new JSONObject(raw);
                int versionCode = o.optInt("versionCode");
                if (versionCode <= Version) return;

                UpdateDialog.UpdateInfo info = new UpdateDialog.UpdateInfo();
                info.versionCode = versionCode;
                info.versionName = o.optString("versionName", "");
                info.minSupportedVersionCode = o.optInt("minSupportedVersionCode", 0);
                info.forceUpdate = o.optBoolean("forceUpdate", false) || Version < info.minSupportedVersionCode;
                info.title = o.optString("title", "Update Available");
                info.downloadUrl = o.optString("downloadUrl", "");
                info.releaseUrl = o.optString("releaseUrl", "");

                JSONArray notes = o.optJSONArray("changelog");
                if (notes != null) {
                    for (int i = 0; i < notes.length(); i++) {
                        String line = notes.optString(i, null);
                        if (line != null && !line.isEmpty()) info.changelog.add(line);
                    }
                }

                runOnUiThread(() -> UpdateDialog.show(this, info));
            } catch (Exception e) {
                Log.d(TAG, "Update failed", e);
            }
        }).start();
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Accept-Encoding", "gzip");
        conn.setRequestProperty("Connection", "close");

        int code = conn.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);

        InputStream in = conn.getInputStream();
        if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) in = new GZIPInputStream(in);

        try (InputStream stream = in; Scanner s = new Scanner(stream).useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "{}";
        }
    }

    public static native void init();
    public static native void initPaths(String internalFiles, String externalFiles);
    public static native void loadHooks();
    public static native void onModExit();

    public static String currentMod() {
        return Launcher.currentMod();
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        // Prevent IME from resizing the whole activity (ugly gap under search bars).
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

        instance = this;
        Theme.load(this);
        check_ver();

        String internalPath = getFilesDir().getAbsolutePath();
        File extDir = getExternalFilesDir(null);
        String externalPath = (extDir != null) ? extDir.getAbsolutePath() : "";

        if (extDir != null) {
            File resourcesDir = new File(extDir, "resources");
            if (!resourcesDir.exists()) {
                if (resourcesDir.mkdirs())
                    Log.d(TAG, "created resources dir");
            }
        }

        System.loadLibrary("lawncher");

        // Point the native crash catcher at a writable log file so the next
        // launch can surface "Oops, the launcher crashed." with an export.
        File crashLog = new File(getFilesDir(), CRASH_LOG_NAME);
        try {
            setCrashLogPath(crashLog.getAbsolutePath());
        } catch (Throwable ignored) {}

        if (SettingsScreen.pref(this, SettingsScreen.KEY_AUTO_REFRESH_STORE, true))
            ModStoreScreen.prefetch(this);
        SplashTexts.prefetch(this);
        Launcher.init(this, findViewById(android.R.id.content));

        checkPreviousCrash();

        String targetPackage = "com.touchfoo.swordigo";

        String abiFolder = "armeabi-v7a";
        if (android.os.Build.SUPPORTED_ABIS.length > 0) {
            String primary = android.os.Build.SUPPORTED_ABIS[0];
            if (primary.equals("arm64-v8a") || primary.contains("arm64"))
                abiFolder = "arm64-v8a";
        }

        File internalLibDir = new File(getFilesDir(), "Extracted/lib/" + abiFolder);
        if (!internalLibDir.exists()) {
            if (internalLibDir.mkdirs())
                Log.d(TAG, "created lib dir for " + abiFolder);
        }

        File openAlFile = new File(internalLibDir, "libopenal-soft.so");
        File swordigoFile = new File(internalLibDir, "libswordigo.so");

        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(targetPackage, 0);
            targetApkPath = info.sourceDir;

            extractLibFromZip(targetApkPath, "lib/" + abiFolder + "/libopenal-soft.so", openAlFile);
            extractLibFromZip(targetApkPath, "lib/" + abiFolder + "/libswordigo.so", swordigoFile);
            extractMusicFiles(targetApkPath);

            System.load(openAlFile.getAbsolutePath());
            System.load(swordigoFile.getAbsolutePath());
            swordigoReady = true;
        } catch (Exception e) {
            Log.e(TAG, "failed to extract/load libs for " + abiFolder, e);
            swordigoReady = false;
            targetApkPath = null;
            // Don't finish() — keep the launcher UI alive and show a clear dialog.
            runOnUiThread(() -> showErrorDialog(e));
        }

        if (swordigoReady) {
            initPaths(internalPath, externalPath);
            init();
        }
    }

    /** Native: tell the crash catcher where to write last_crash.log */
    public static native void setCrashLogPath(String path);

    private void checkPreviousCrash() {
        if (!SettingsScreen.pref(this, SettingsScreen.KEY_CRASH_DIALOGS, true)) return;

        File crashLog = new File(getFilesDir(), CRASH_LOG_NAME);
        if (!crashLog.exists() || crashLog.length() == 0) return;

        new AlertDialog.Builder(this)
        .setTitle("Oops, the launcher crashed.")
        .setMessage("A previous session ended with a native crash.\n\n"
        + "Export the log, or open Discord to share it with the team.")
        .setPositiveButton("Export log", (d, w) -> SettingsScreen.exportCrash(this, crashLog))
        .setNeutralButton("Discord", (d, w) -> SettingsScreen.openUrl(this, SettingsScreen.DISCORD_URL))
        .setNegativeButton("Dismiss", (d, w) -> crashLog.delete())
        .setOnCancelListener(d -> crashLog.delete())
        .show();
    }

    private void showErrorDialog(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String stackTrace = sw.toString();

        String message;
        if (e instanceof android.content.pm.PackageManager.NameNotFoundException) {
            message = "Vanilla Swordigo (com.touchfoo.swordigo) is not installed.\n\n"
            + "Lawncher needs the official APK to extract libraries and assets. "
            + "Install Swordigo from the Play Store, then reopen Lawncher.\n\n"
            + "Error: Package not found.";
        } else {
            message = "Failed to extract/load Swordigo resources.\n\n"
            + "Exception: " + e.getClass().getSimpleName() + "\n"
            + "Message: " + e.getMessage() + "\n\n"
            + "Trace:\n" + (stackTrace.length() > 300 ? stackTrace.substring(0, 300) + "..." : stackTrace);
        }

        new AlertDialog.Builder(this)
        .setTitle("Swordigo Initialization Failed")
        .setMessage(message)
        .setPositiveButton("Got it", null)
        .setCancelable(true)
        .show();
    }

    private void extractMusicFiles(String apkPath) {
        File extDir = getExternalFilesDir(null);
        if (extDir == null) return;

        File musicDir = new File(extDir, "music");
        if (!musicDir.exists() && !musicDir.mkdirs()) {
            Log.e(TAG, "Failed to create music directory: " + musicDir);
            return;
        }

        String[][] musicFiles = {
        {"res/7c.mp3", "1_boss23"},
        {"res/s7.mp3", "1_dung73"},
        {"res/jy.mp3", "1_hero2"},
        {"res/3H.mp3", "1_plainstest2"},
        {"res/Fc.mp3", "2cave2"},
        {"res/md.mp3", "gameover"},
        {"res/R2.mp3", "heartbeat"},
        {"res/LM.mp3", "momentofwonder"},
        {"res/lU.mp3", "squire_new2"}
        };

        try (ZipFile zipFile = new ZipFile(apkPath)) {
            for (String[] music : musicFiles) {
                String source = music[0];
                String name   = music[1];
                File dest     = new File(musicDir, name);

                // already extracted → skip
                if (dest.exists() && dest.length() > 0) continue;

                ZipEntry entry = zipFile.getEntry(source);
                if (entry == null) {
                    Log.w(TAG, "Missing music in APK: " + source);
                    continue;
                }

                try (InputStream in = zipFile.getInputStream(entry);
                     FileOutputStream out = new FileOutputStream(dest)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1)
                        out.write(buffer, 0, n);
                    out.flush();
                }
                Log.d(TAG, "Extracted music: " + source + " → " + dest);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract music", e);
        }
    }

    public static void launch() {
        if (instance == null) return;
        if (!instance.swordigoReady || instance.targetApkPath == null) {
            instance.runOnUiThread(() -> {
                Toast.makeText(instance, "Swordigo is not installed — can't launch.", Toast.LENGTH_LONG).show();
            });
            return;
        }
        instance.startGame();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == Launcher.FILE_PICKER_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null)
                Launcher.processSelectedFile(this, data.getData());
        }
        // Files screen system import
        FilesScreen.handleActivityResult(requestCode, resultCode, data);
        TextEditor.handleActivityResult(requestCode, resultCode, data);
        Launcher.handleActivityResult(requestCode, resultCode, data);
    }

    private void startGame() {
        if (targetApkPath == null) return;

        loadHooks();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        FrameLayout gameRoot = new FrameLayout(this);
        gameRoot.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));

        glSurfaceView = new GameView(this);
        glSurfaceView.setEGLConfigChooser(5, 6, 5, 0, 16, 0);
        glSurfaceView.setPreserveEGLContextOnPause(true);
        glSurfaceView.setRenderer(new GameRenderer());

        gameRoot.addView(glSurfaceView, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(gameRoot);
        enableImmersiveMode();

        ButtonController.init(this, gameRoot);

        setupNativeEnvironment(targetApkPath);
        Launcher.initGameButtons();
        hooksLoaded = true;
    }

    private void unloadGameHooks() {
        if (!hooksLoaded) return;
        try {
            onModExit();
        } catch (Throwable t) {
            Log.e(TAG, "onModExit failed", t);
        } finally {
            hooksLoaded = false;
        }
    }

    public static void returnToLauncher() {
        if (instance != null)
            instance.stopGame();
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private void stopGame() {
        unloadGameHooks();

        // fully stop music when returning to the launcher
        MusicPlayer mp = MusicPlayer.get();
        if (mp != null) mp.onGameStop();

        if (glSurfaceView != null)
            glSurfaceView.onPause();
        Launcher.destroyGameButtons();
        glSurfaceView = null;

        ButtonController.removeAll();

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        FrameLayout launcherContainer = new FrameLayout(this);
        launcherContainer.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(launcherContainer);

        ButtonController.init(this, launcherContainer);
        Launcher.init(this, launcherContainer);
    }

    private static AssetManager gameAssetManager;

    public static String getTargetApkPath() {
        return instance != null ? instance.targetApkPath : null;
    }

    public static AssetManager getGameAssetManager() {
        if (gameAssetManager != null) return gameAssetManager;
        String apk = getTargetApkPath();
        if (apk == null || instance == null) return null;
        try {
            AssetManager am = AssetManager.class.newInstance();
            java.lang.reflect.Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
            Object result = addAssetPath.invoke(am, apk);
            boolean ok = (result instanceof Integer) && ((Integer) result) != 0;
            if (ok) {
                gameAssetManager = am;
                return am;
            }
        } catch (Exception e) {
            Log.e(TAG, "getGameAssetManager failed", e);
        }
        return null;
    }

    private void setupNativeEnvironment(String apkPath) {
        try {
            Native.mainActivity = this;

            Native.setFilesDir(getApplicationContext().getFilesDir().toString());
            Native.setCacheDir(getApplicationContext().getCacheDir().toString());

            AssetManager am = buildDetectedAssetManager(apkPath);
            if (am == null) {
                finish();
                return;
            }

            Native.setAssetManager(am);
            Native.handleApplicationLaunch();
        } catch (Throwable t) {
            Log.e(TAG, "setupNativeEnvironment failed", t);
        }
    }

    private AssetManager buildDetectedAssetManager(String apkPath) {
        try {
            AssetManager am = AssetManager.class.newInstance();
            java.lang.reflect.Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
            Object result = addAssetPath.invoke(am, apkPath);
            boolean ok = (result instanceof Integer) && ((Integer) result) != 0;
            return ok ? am : null;
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
        if (glSurfaceView != null)
            glSurfaceView.setSystemUiVisibility(flags);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (glSurfaceView != null)
            glSurfaceView.onPause();

        // pause music whenever the activity is no longer in the foreground
        MusicPlayer mp = MusicPlayer.get();
        if (mp != null) mp.onGamePause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glSurfaceView != null) {
            glSurfaceView.onResume();
            enableImmersiveMode();

            // only resume music while we are still in-game
            MusicPlayer mp = MusicPlayer.get();
            if (mp != null) mp.onGameResume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unloadGameHooks();
        if (instance == this)
            instance = null;
    }

    private void extractLibFromZip(String apkPath, String zipEntryPath, File destFile) throws IOException {
        if (destFile.exists() && destFile.length() > 0) {
            destFile.setReadable(true, false);
            destFile.setExecutable(true, false);
            return;
        }

        try (ZipFile zipFile = new ZipFile(apkPath)) {
            ZipEntry entry = zipFile.getEntry(zipEntryPath);
            if (entry == null)
                throw new java.io.FileNotFoundException("missing " + zipEntryPath + " in " + apkPath);

            try (InputStream in = zipFile.getInputStream(entry);
                 FileOutputStream out = new FileOutputStream(destFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1)
                    out.write(buf, 0, n);
                out.flush();
            }

            destFile.setReadable(true, false);
            destFile.setExecutable(true, false);
        }
    }
}