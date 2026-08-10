package net.kiwi.lawncher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import net.kiwi.lawncher.util.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * InstanceManager: the launcher's game-instance system.
 *
 * An instance is a self-contained copy of the game (source APK + extracted
 * native libs + its own mods/) that the launcher can run without depending
 * on the Play Store install. Sources can be:
 *   - the installed com.touchfoo.swordigo app (created on boot, "vanilla"),
 *   - any APK the user provides (backup, modded build, custom engine, ...)
 *     imported via the file picker (kind: vanilla / modded / custom).
 *
 * On-disk layout (under externalFilesDir):
 *   instances/<id>/
 *     instance.json   metadata (id, name, kind, version, mod refs)
 *     source.apk      copy of the source APK (mounted at launch for assets)
 *     libs/           libswordigo.so + libopenal-soft.so (System.load'ed)
 *     mods/<modId>/   per-instance mods (same layout as ModManager)
 */
public class InstanceManager {

	private static final String TAG = "InstanceManager";

	public static final String GAME_PACKAGE = "com.touchfoo.swordigo";
	public static final String INSTANCES_DIR = "instances";
	public static final String SOURCE_APK = "source.apk";
	public static final String INSTANCE_JSON = "instance.json";

	public static final String KIND_VANILLA = "vanilla";
	public static final String KIND_MODDED = "modded";
	public static final String KIND_CUSTOM = "custom";

	private static final String PREF_ACTIVE = "active_instance";
	private static final String[] REQUIRED_LIBS = {
			"libswordigo.so", "libopenal-soft.so", "libopenal.so"
	};

	private InstanceManager() {}

	// ---------------------------------------------------------------- info --

	public static class InstanceInfo {
		public String id;
		public String name;
		public String kind = KIND_VANILLA;
		public String sourcePkg = GAME_PACKAGE;
		public String versionName = "";
		public long createdAt;
		public File dir;
		public List<String> modIds = new ArrayList<>();

		public File sourceApk() { return new File(dir, SOURCE_APK); }
		public File modsDir() { return new File(dir, "mods"); }

		public String kindLabel() {
			switch (kind) {
				case KIND_MODDED: return "Modded";
				case KIND_CUSTOM: return "Custom";
				default: return "Vanilla";
			}
		}
	}

	// -------------------------------------------------------------- scan --

	/** The installed game app, or null if it isn't installed. */
	public static ApplicationInfo scanInstalledGame(Context context) {
		try {
			return context.getPackageManager().getApplicationInfo(GAME_PACKAGE, 0);
		} catch (PackageManager.NameNotFoundException e) {
			return null;
		}
	}

	public static File getInstancesDir(Context context) {
		File base = context.getExternalFilesDir(null);
		if (base == null) base = context.getFilesDir(); // no external storage — keep everything internal
		File dir = new File(base, INSTANCES_DIR);
		if (!dir.exists()) dir.mkdirs();
		return dir;
	}

	/**
	 * Where the instance's native libs live: INTERNAL storage only.
	 *
	 * System.load()/dlopen() cannot load libraries from the external volume
	 * (it is usually mounted noexec) — the old code extracted to the external
	 * instance dir and launch failed with "failed to load libraries" even
	 * though the APK copy had succeeded. Source APK + assets stay external;
	 * the small native libs must be exec'able, so they go to filesDir.
	 */
	public static File libsDir(Context context, InstanceManager.InstanceInfo info) {
		return new File(context.getFilesDir(), INSTANCES_DIR + "/" + info.id + "/libs");
	}

	// ------------------------------------------------------------ create --

	/** Creates an instance from the currently installed game. Heavy (copies the APK) — run off the UI thread. */
	public static InstanceInfo createFromInstalled(Context context, String name, String kind) {
		ApplicationInfo game = scanInstalledGame(context);
		if (game == null) return null;

		InstanceInfo info = createInstance(context, new File(game.sourceDir),
				kind, (name == null || name.trim().isEmpty()) ? "Vanilla" : name.trim());
		if (info == null) return null;

		info.sourcePkg = GAME_PACKAGE;
		try {
			info.versionName = context.getPackageManager().getPackageInfo(GAME_PACKAGE, 0).versionName;
		} catch (Exception ignored) {}
		save(context, info);
		return info;
	}

	/** Imports any APK the user picked (modded build, custom engine, backup...). Heavy — run off the UI thread. */
	public static InstanceInfo importApk(Context context, Uri uri, String kind, String name) {
		File tmp = new File(context.getCacheDir(), "import_" + System.currentTimeMillis() + ".apk");
		try (InputStream in = context.getContentResolver().openInputStream(uri);
			 OutputStream out = new FileOutputStream(tmp)) {
			if (in == null) return null;
			byte[] buffer = new byte[65536];
			int n;
			while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
		} catch (IOException e) {
			Log.e(TAG, "importApk: failed to read picked file", e);
			return null;
		}

		InstanceInfo info = createInstance(context, tmp,
				kind, (name == null || name.trim().isEmpty()) ? "Imported" : name.trim());
		if (info != null) info.sourcePkg = GAME_PACKAGE;
		tmp.delete();
		return info;
	}

	private static InstanceInfo createInstance(Context context, File sourceApk, String kind, String name) {
		if (sourceApk == null || !sourceApk.exists()) return null;

		String id = UUID.randomUUID().toString().substring(0, 8);
		File dir = new File(getInstancesDir(context), id);
		if (!dir.mkdirs() && !dir.isDirectory()) return null;

		InstanceInfo info = new InstanceInfo();
		info.id = id;
		info.name = name;
		info.kind = kind == null ? KIND_VANILLA : kind;
		info.dir = dir;
		info.createdAt = System.currentTimeMillis();

		try {
			copyFile(sourceApk, info.sourceApk());
			extractLibs(info.sourceApk(), libsDir(context, info));
			save(context, info);
			return info;
		} catch (IOException e) {
			Log.e(TAG, "createInstance: failed for " + sourceApk.getAbsolutePath(), e);
			deleteInstance(context, info);
			return null;
		}
	}

	// --------------------------------------------------------------- libs --

	/** Extracts the game's native libs (to INTERNAL storage) from the instance's source APK if missing. */
	public static File ensureLibs(Context context, InstanceInfo info) {
		if (info == null) return null;
		File libs = libsDir(context, info);
		File[] existing = libs.listFiles();
		if ((existing == null || existing.length == 0) && info.sourceApk().exists()) {
			try {
				extractLibs(info.sourceApk(), libs);
			} catch (IOException e) {
				Log.e(TAG, "ensureLibs: extraction failed", e);
			}
		}
		return libs;
	}

	private static void extractLibs(File apk, File libsDir) throws IOException {
		try (ZipFile zip = new ZipFile(apk)) {
			libsDir.mkdirs();
			for (String lib : REQUIRED_LIBS) {
				ZipEntry entry = pickLibEntry(zip, lib);
				if (entry == null) continue;
				File out = new File(libsDir, lib);
				try (InputStream in = zip.getInputStream(entry);
					 FileOutputStream fos = new FileOutputStream(out)) {
					byte[] buffer = new byte[65536];
					int n;
					while ((n = in.read(buffer)) != -1) fos.write(buffer, 0, n);
				}
				out.setReadable(true, false);
				out.setExecutable(true, false);
			}
		}
		// A game instance without its engine lib can never launch — fail the
		// creation here with a clear reason instead of deferring to a
		// confusing error at launch time.
		if (!new File(libsDir, "libswordigo.so").exists()) {
			throw new IOException("no libswordigo.so inside the APK");
		}
	}

	/**
	 * Prefers the lib built for this device's ABI (Build.SUPPORTED_ABIS, in
	 * priority order), then falls back to any lib/<abi>/ match. Picking the
	 * wrong ABI (e.g. an emulator APK's arm64 lib on an x86 device, or the
	 * first zip entry) makes System.load fail at launch.
	 */
	private static ZipEntry pickLibEntry(ZipFile zip, String libName) {
		for (String abi : Build.SUPPORTED_ABIS) {
			ZipEntry entry = zip.getEntry("lib/" + abi + "/" + libName);
			if (entry != null) return entry;
		}
		return findFirstEntry(zip, "lib/", libName);
	}

	private static ZipEntry findFirstEntry(ZipFile zip, String prefix, String suffix) {
		Enumeration<? extends ZipEntry> entries = zip.entries();
		while (entries.hasMoreElements()) {
			ZipEntry e = entries.nextElement();
			if (e.getName().startsWith(prefix) && e.getName().endsWith(suffix)) return e;
		}
		return null;
	}

	// --------------------------------------------------------------- list --

	public static List<InstanceInfo> listInstances(Context context) {
		List<InstanceInfo> out = new ArrayList<>();
		File[] dirs = getInstancesDir(context).listFiles(File::isDirectory);
		if (dirs != null) {
			for (File dir : dirs) {
				InstanceInfo info = parse(dir);
				if (info != null) out.add(info);
			}
		}
		return out;
	}

	public static InstanceInfo getInstance(Context context, String id) {
		if (id == null || id.isEmpty()) return null;
		for (InstanceInfo info : listInstances(context)) {
			if (info.id.equals(id)) return info;
		}
		return null;
	}

	public static boolean deleteInstance(Context context, InstanceInfo info) {
		if (info == null) return false;
		boolean ok = deleteRecursive(info.dir);
		deleteRecursive(new File(context.getFilesDir(), INSTANCES_DIR + "/" + info.id)); // internal libs
		if (ok && info.id.equals(activeInstanceId(context))) {
			Prefs.remove(PREF_ACTIVE);
		}
		return ok;
	}

	// ------------------------------------------------------------- active --

	public static String activeInstanceId(Context context) {
		String id = Prefs.getString(PREF_ACTIVE, "");
		return getInstance(context, id) != null ? id : "";
	}

	public static void setActiveInstance(Context context, String id) {
		Prefs.putString(PREF_ACTIVE, id == null ? "" : id);
	}

	/** Active instance, or the first one, or null if none exist. */
	public static InstanceInfo resolveActiveInstance(Context context) {
		List<InstanceInfo> all = listInstances(context);
		if (all.isEmpty()) return null;
		String active = activeInstanceId(context);
		for (InstanceInfo info : all) {
			if (info.id.equals(active)) return info;
		}
		InstanceInfo first = all.get(0);
		setActiveInstance(context, first.id);
		return first;
	}

	/**
	 * The auto-creation step from the boot flow: if any instance exists,
	 * resolve the active one; otherwise create a vanilla instance from the
	 * installed game. Returns null when neither exists (caller should prompt
	 * the user to provide an APK).
	 */
	public static InstanceInfo ensureVanillaInstance(Context context) {
		List<InstanceInfo> all = listInstances(context);
		if (!all.isEmpty()) return resolveActiveInstance(context);

		if (scanInstalledGame(context) == null) return null;
		InstanceInfo created = createFromInstalled(context, "Vanilla", KIND_VANILLA);
		if (created != null) setActiveInstance(context, created.id);
		return created;
	}

	// -------------------------------------------------- per-instance mods --

	public static void addModRef(Context context, InstanceInfo instance, String modId) {
		if (instance == null || modId == null || modId.isEmpty()) return;
		if (!instance.modIds.contains(modId)) {
			instance.modIds.add(modId);
			save(context, instance);
		}
	}

	public static void removeModRef(Context context, InstanceInfo instance, String modId) {
		if (instance == null) return;
		if (instance.modIds.remove(modId)) save(context, instance);
	}

	// --------------------------------------------------------------- json --

	public static void save(Context context, InstanceInfo info) {
		try {
			JSONObject o = new JSONObject();
			o.put("id", info.id);
			o.put("name", info.name);
			o.put("kind", info.kind);
			o.put("sourcePkg", info.sourcePkg);
			o.put("versionName", info.versionName);
			o.put("createdAt", info.createdAt);
			JSONArray mods = new JSONArray();
			for (String mod : info.modIds) mods.put(mod);
			o.put("mods", mods);
			try (FileOutputStream fos = new FileOutputStream(new File(info.dir, INSTANCE_JSON))) {
				fos.write(o.toString().getBytes("UTF-8"));
			}
		} catch (Exception e) {
			Log.e(TAG, "save: failed to persist instance", e);
		}
	}

	private static InstanceInfo parse(File dir) {
		File file = new File(dir, INSTANCE_JSON);
		if (!file.exists()) return null;
		try (FileInputStream in = new FileInputStream(file)) {
			byte[] buffer = new byte[(int) Math.min(file.length(), 1 << 20)];
			int n = in.read(buffer);
			JSONObject o = new JSONObject(new String(buffer, 0, n, "UTF-8"));

			InstanceInfo info = new InstanceInfo();
			info.id = o.optString("id", dir.getName());
			info.name = o.optString("name", info.id);
			info.kind = o.optString("kind", KIND_VANILLA);
			info.sourcePkg = o.optString("sourcePkg", GAME_PACKAGE);
			info.versionName = o.optString("versionName", "");
			info.createdAt = o.optLong("createdAt");
			info.dir = dir;
			JSONArray mods = o.optJSONArray("mods");
			if (mods != null) {
				for (int i = 0; i < mods.length(); i++) info.modIds.add(mods.optString(i));
			}
			return info;
		} catch (Exception e) {
			return null;
		}
	}

	// ------------------------------------------------------------ helpers --

	private static void copyFile(File src, File dst) throws IOException {
		try (InputStream in = new FileInputStream(src);
			 OutputStream out = new FileOutputStream(dst)) {
			byte[] buffer = new byte[65536];
			int n;
			while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
		}
	}

	private static boolean deleteRecursive(File file) {
		if (file == null || !file.exists()) return true;
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) deleteRecursive(child);
			}
		}
		return file.delete();
	}
}
