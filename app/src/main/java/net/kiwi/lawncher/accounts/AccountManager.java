package net.kiwi.lawncher.accounts;

import android.content.Context;
import android.provider.Settings;

import net.kiwi.lawncher.util.Prefs;

/**
 * AccountManager: username / identity infrastructure. Local-first today
 * (persisted in SharedPreferences) with a clean seam for a cloud backend:
 * see {@link #isUsernameAvailable(String)}.
 */
public final class AccountManager {

	public static final String DEFAULT_USERNAME = "Guest";

	private static final String PREF_USERNAME = "account.username";
	private static final String PREF_AVATAR = "account.avatar";
	private static final String PREF_SIGNED_IN = "account.signed_in";
	private static final String PREF_DEVICE = "account.device";

	private AccountManager() {}

	public static boolean isValidUsername(String name) {
		return name != null && name.matches("^[A-Za-z0-9_]{3,16}$");
	}

	public static String getUsername(Context context) {
		String u = Prefs.getString(PREF_USERNAME, "");
		return u.isEmpty() ? DEFAULT_USERNAME : u;
	}

	public static void setUsername(Context context, String name) {
		Prefs.putString(PREF_USERNAME, name);
	}

	public static int getAvatarColorIndex() {
		int i = Prefs.getInt(PREF_AVATAR, 0) % 5;
		return i < 0 ? 0 : i;
	}

	public static void setAvatarColorIndex(int index) {
		Prefs.putInt(PREF_AVATAR, ((index % 5) + 5) % 5);
	}

	public static void cycleAvatarColor() {
		setAvatarColorIndex(getAvatarColorIndex() + 1);
	}

	public static boolean isSignedIn() {
		return Prefs.getBool(PREF_SIGNED_IN, false);
	}

	public static void setSignedIn(boolean signedIn) {
		Prefs.putBool(PREF_SIGNED_IN, signedIn);
	}

	public static String getDeviceId(Context context) {
		String cached = Prefs.getString(PREF_DEVICE, "");
		if (!cached.isEmpty()) return cached;
		String id = "";
		try {
			id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
		} catch (Throwable ignored) {
		}
		if (id == null || id.isEmpty()) id = "unknown";
		Prefs.putString(PREF_DEVICE, id);
		return id;
	}

	/**
	 * Seam for a real backend: hit the auth / username API here.
	 * Currently a local rule — unique usernames are "available".
	 */
	public static boolean isUsernameAvailable(String name) {
		return isValidUsername(name);
	}
}
