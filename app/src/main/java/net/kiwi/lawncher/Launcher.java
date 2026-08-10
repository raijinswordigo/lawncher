package net.kiwi.lawncher;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.view.ViewGroup;

import net.kiwi.lawncher.ui.LauncherShell;

/**
 * Public entry point used by {@link MainActivity} and native code.
 *
 * All UI lives in the shell ({@link LauncherShell}): a sleek top bar, an
 * animated sidebar drawer (Mods / Mod Store / Files / Logcat / Settings /
 * Profile) and per-screen content. Files and mods work live in
 * {@link FileManager} / {@link ModManager}.
 */
public class Launcher {

	/** Request code used for the SAF zip picker (see MainActivity#onActivityResult). */
	public static final int FILE_PICKER_REQUEST_CODE = 9999;

	/** Request code used for the instance-APK picker (see MainActivity#onActivityResult). */
	public static final int INSTANCE_PICKER_REQUEST_CODE = 9998;

	private Launcher() {}

	public static void init(Activity act, ViewGroup view) {
		LauncherShell.init(act, view);
	}

	public static void initGameButtons() {
	}

	/** The id of the mod whose detail screen is open, or "" if none. Called from native. */
	public static String currentMod() {
		return LauncherShell.launchModId();
	}

	/**
	 * Back-press routing from MainActivity. Returns true when the press was
	 * consumed by the launcher UI (drawer / overlays / file navigation).
	 */
	public static boolean onBackPressed() {
		return LauncherShell.dispatchBack();
	}

	/** Tells the shell whether the game is currently in the foreground. */
	public static void setGameMode(boolean inGame) {
		LauncherShell.setGameMode(inGame);
	}

	/** Called from MainActivity#onActivityResult: install the picked zip. */
	public static void processSelectedFile(Context context, Uri uri) {
		LauncherShell.handleFilePicked(context, uri);
	}
}
