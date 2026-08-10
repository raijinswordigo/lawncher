package net.kiwi.lawncher.util;

import android.content.Context;
import android.content.SharedPreferences;

/** Tiny typed wrapper around the launcher's SharedPreferences file. */
public final class Prefs {

	private static final String FILE = "lawncher_prefs";
	private static SharedPreferences prefs;

	private Prefs() {}

	public static void init(Context context) {
		prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
	}

	public static String getString(String key, String def) {
		return prefs != null ? prefs.getString(key, def) : def;
	}

	public static void putString(String key, String value) {
		if (prefs != null) prefs.edit().putString(key, value).apply();
	}

	public static int getInt(String key, int def) {
		return prefs != null ? prefs.getInt(key, def) : def;
	}

	public static void putInt(String key, int value) {
		if (prefs != null) prefs.edit().putInt(key, value).apply();
	}

	public static boolean getBool(String key, boolean def) {
		return prefs != null ? prefs.getBoolean(key, def) : def;
	}

	public static void putBool(String key, boolean value) {
		if (prefs != null) prefs.edit().putBoolean(key, value).apply();
	}

	public static long getLong(String key, long def) {
		return prefs != null ? prefs.getLong(key, def) : def;
	}

	public static void putLong(String key, long value) {
		if (prefs != null) prefs.edit().putLong(key, value).apply();
	}

	public static void remove(String key) {
		if (prefs != null) prefs.edit().remove(key).apply();
	}
}
