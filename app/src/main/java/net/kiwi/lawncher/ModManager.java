package net.kiwi.lawncher;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * ModManager: all filesystem / mod-installation logic. No UI code here -
 * Launcher.java owns that and only talks to this class.
 *
 * On-disk layout mirrors the zip layout exactly — the entire zip is inflated
 * into the mod directory with no filtering:
 *   <externalFilesDir>/mods/<modId>/
 *       icon.png
 *       properties.toml
 *       resources/
 *           ...mod resources...
 *       music/
 *           ...mod music tracks...
 *       (any other files/dirs from the zip)
 *
 * Icon source: if an iconUrl is supplied at install time (e.g. the "icon"
 * field from the store listing), it is downloaded and used as icon.png,
 * taking priority over any icon.png bundled in the zip. This means a mod
 * author only needs to maintain one icon - the one referenced in store.json -
 * instead of keeping a store icon and a zip-bundled icon in sync by hand.
 *
 *   [mod]
 *   id="com.raijin.sinon"
 *   name="Sinon"
 *   version="1.0.0"
 *   description="A simple utility mod."
 */
public class ModManager {

	private static final String MODS_DIR = "mods";
	private static final String PROPERTIES_FILE = "properties.toml";
	private static final String ICON_FILE = "icon.png";

	public interface InstallCallback {
		void onSuccess(ModInfo mod);
		void onFailure(String reason);
	}

	public static class ModInfo {
		public String id;
		public String name;
		public String version;
		public String description;
		public File dir;
		/**
		 * Filenames from properties.toml's optional "screenshots" key, relative to dir.
		 * Screenshots shipped inside the zip's resources/ folder should be referenced as
		 * "resources/preview.png", not just "preview.png".
		 */
		public List<String> screenshots = new ArrayList<>();

		public File iconFile() {
			File f = new File(dir, ICON_FILE);
			return f.exists() ? f : null;
		}

		/** Resolves screenshots against dir and drops any that don't actually exist. */
		public List<File> screenshotFiles() {
			List<File> files = new ArrayList<>();
			for (String name : screenshots) {
				File f = new File(dir, name);
				if (f.exists()) files.add(f);
			}
			// Also pick up loose images under screenshots/ if listed folder missing
			if (files.isEmpty() && dir != null) {
				File shotDir = new File(dir, "screenshots");
				if (shotDir.isDirectory()) {
					File[] kids = shotDir.listFiles();
					if (kids != null) {
						for (File f : kids) {
							String n = f.getName().toLowerCase(Locale.ROOT);
							if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp"))
								files.add(f);
						}
					}
				}
			}
			return files;
		}

		/** Preferred folder for mod save data (created on demand). */
		public File savesDir() {
			File s = new File(dir, "saves");
			if (!s.exists()) s.mkdirs();
			return s;
		}
	}

	private static class InstallException extends Exception {
		InstallException(String msg) { super(msg); }
	}

	// ==========================================
	// Mod listing / removal
	// ==========================================

	public static List<ModInfo> listInstalledMods(Context context) {
		List<ModInfo> mods = new ArrayList<>();
		File[] modDirs = getModsDir(context).listFiles(File::isDirectory);
		if (modDirs == null) return mods;

		for (File modDir : modDirs) {
			File propsFile = new File(modDir, PROPERTIES_FILE);
			if (!propsFile.exists()) continue;

			ModInfo info = parseProperties(propsFile);
			if (info == null) continue;
			info.dir = modDir;
			mods.add(info);
		}
		Collections.sort(mods, (a, b) -> a.name.toLowerCase(Locale.ROOT).compareTo(b.name.toLowerCase(Locale.ROOT)));
		return mods;
	}

	/** Deletes a mod's whole directory. Returns false if it couldn't be fully removed. */
	public static boolean deleteMod(ModInfo mod) {
		return mod != null && deleteRecursive(mod.dir);
	}

	private static File getModsDir(Context context) {
		File dir = new File(context.getExternalFilesDir(null), MODS_DIR);
		if (!dir.exists()) dir.mkdirs();
		return dir;
	}

	// ==========================================
	// Install (runs on a background thread; callback is posted by the caller)
	// ==========================================

	/** Back-compat overload: installs with no store icon, falling back to any icon.png in the zip. */
	public static void installMod(Context context, Uri zipUri, InstallCallback callback) {
		installMod(context, zipUri, null, callback);
	}

	public static void installMod(Context context, Uri zipUri, String iconUrl, InstallCallback callback) {
		new Thread(() -> {
			try {
				callback.onSuccess(doInstall(context, zipUri, iconUrl));
			} catch (InstallException e) {
				callback.onFailure(e.getMessage());
			} catch (Exception e) {
				e.printStackTrace();
				callback.onFailure("unexpected error");
			}
		}).start();
	}

	private static ModInfo doInstall(Context context, Uri uri, String iconUrl) throws Exception {
		File staging = new File(context.getCacheDir(), "mod_staging_" + System.currentTimeMillis());
		staging.mkdirs();

		try {
			// Inflate the entire zip into staging (no filtering).
			extractZip(context, uri, staging);

			File propsFile = new File(staging, PROPERTIES_FILE);
			if (!propsFile.exists()) {
				throw new InstallException("missing properties.toml");
			}

			ModInfo info = parseProperties(propsFile);
			if (info == null || info.id == null || info.id.isEmpty()) {
				throw new InstallException("properties.toml missing [mod] id");
			}

			File targetDir = new File(getModsDir(context), sanitizeName(info.id));
			deleteRecursive(targetDir);
			targetDir.mkdirs();

			// Copy everything from the zip straight into the mod directory.
			// No special-casing for resources/, music/, or any other folder.
			copyRecursive(staging, targetDir);

			// Store-provided icon overrides whatever came from the zip.
			resolveIcon(staging, targetDir, iconUrl);

			info.dir = targetDir;
			return info;
		} finally {
			deleteRecursive(staging);
		}
	}

	/** Prefers the store-provided icon URL; falls back to icon.png bundled in the zip, if any. */
	private static void resolveIcon(File staging, File targetDir, String iconUrl) {
		File iconOut = new File(targetDir, ICON_FILE);

		if (iconUrl != null && !iconUrl.isEmpty() && downloadIcon(iconUrl, iconOut)) {
			return;
		}

		// Already copied from zip via copyRecursive; nothing more to do.
	}

	private static boolean downloadIcon(String urlStr, File dest) {
		try {
			HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(5000);
			conn.setRequestProperty("User-Agent", "Mozilla/5.0");

			if (conn.getResponseCode() != 200) return false;

			try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(dest)) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static void extractZip(Context context, Uri uri, File destDir) throws Exception {
		try (InputStream is = context.getContentResolver().openInputStream(uri)) {
			if (is == null) throw new InstallException("could not open file");

			try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {
				ZipEntry entry;
				while ((entry = zis.getNextEntry()) != null) {
					if (entry.isDirectory()) continue;

					String entryName = entry.getName();
					// Security precaution: prevent Zip Path Traversal attacks
					if (entryName.contains("..")) continue;

					File target = new File(destDir, entryName);
					target.getParentFile().mkdirs();
					writeEntry(zis, target);
					zis.closeEntry();
				}
			}
		}
	}

	private static void writeEntry(ZipInputStream zis, File target) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(target)) {
			byte[] buffer = new byte[8192];
			int count;
			while ((count = zis.read(buffer)) != -1) {
				fos.write(buffer, 0, count);
			}
		}
	}

	// ==========================================
	// properties.toml parsing ([mod] section)
	// Supports: key = "value", key = 'value', key = """multiline"""
	// ==========================================

	private static final Pattern KV_BASIC =
			Pattern.compile("^\\s*(\\w+)\\s*=\\s*\"([^\"]*)\"\\s*$");
	private static final Pattern KV_SINGLE =
			Pattern.compile("^\\s*(\\w+)\\s*=\\s*'([^']*)'\\s*$");
	private static final Pattern KV_TRIPLE_OPEN =
			Pattern.compile("^\\s*(\\w+)\\s*=\\s*\"\"\"(.*)$");

	private static ModInfo parseProperties(File propsFile) {
		ModInfo info = new ModInfo();
		boolean inModSection = false;

		try (BufferedReader reader = new BufferedReader(new FileReader(propsFile))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

				if (trimmed.startsWith("[")) {
					inModSection = trimmed.equalsIgnoreCase("[mod]");
					continue;
				}
				if (!inModSection) continue;

				// Multiline: key = """ ... """
				Matcher triple = KV_TRIPLE_OPEN.matcher(line);
				if (triple.matches()) {
					String key = triple.group(1);
					String rest = triple.group(2);
					StringBuilder body = new StringBuilder();
					// Same-line close: """text"""
					int close = rest.indexOf("\"\"\"");
					if (close >= 0) {
						body.append(rest.substring(0, close));
					} else {
						if (!rest.isEmpty()) body.append(rest).append('\n');
						while ((line = reader.readLine()) != null) {
							int c = line.indexOf("\"\"\"");
							if (c >= 0) {
								body.append(line.substring(0, c));
								break;
							}
							body.append(line).append('\n');
						}
					}
					applyProp(info, key, body.toString().replace("\r", "").trim());
					continue;
				}

				Matcher m = KV_BASIC.matcher(trimmed);
				if (!m.matches()) m = KV_SINGLE.matcher(trimmed);
				if (!m.matches()) continue;
				applyProp(info, m.group(1), m.group(2));
			}
		} catch (IOException e) {
			return null;
		}

		if (info.name == null) info.name = info.id;
		return info;
	}

	private static void applyProp(ModInfo info, String key, String value) {
		if (key == null || value == null) return;
		switch (key) {
			case "id": info.id = value; break;
			case "name": info.name = value; break;
			case "version": info.version = value; break;
			case "description": info.description = value; break;
			case "screenshots":
				for (String part : value.split(",")) {
					String s = part.trim();
					if (!s.isEmpty()) info.screenshots.add(s);
				}
				break;
			default: break;
		}
	}

	// ==========================================
	// File utilities
	// ==========================================

	private static String sanitizeName(String raw) {
		if (raw == null) return "";
		return raw.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private static void copyFile(File src, File dst) throws IOException {
		try (InputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst)) {
			byte[] buffer = new byte[8192];
			int count;
			while ((count = in.read(buffer)) != -1) {
				out.write(buffer, 0, count);
			}
		}
	}

	private static void copyRecursive(File src, File dst) throws IOException {
		if (src.isDirectory()) {
			if (!dst.exists()) dst.mkdirs();
			File[] children = src.listFiles();
			if (children != null) {
				for (File child : children) {
					copyRecursive(child, new File(dst, child.getName()));
				}
			}
		} else {
			copyFile(src, dst);
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

	// ==========================================
	// Saves import / export
	// ==========================================

	/** Zip everything under mod/saves/ into destZip. Returns false if nothing to export. */
	public static boolean exportSaves(ModInfo mod, File destZip) {
		if (mod == null || mod.dir == null) return false;
		File saves = new File(mod.dir, "saves");
		if (!saves.isDirectory()) return false;
		File[] kids = saves.listFiles();
		if (kids == null || kids.length == 0) return false;
		try {
			zipDirectory(saves, destZip);
			return destZip.exists() && destZip.length() > 0;
		} catch (Exception e) {
			return false;
		}
	}

	/** Extract a zip (or copy a single file) into mod/saves/. */
	public static boolean importSaves(ModInfo mod, Uri uri, Context context) {
		if (mod == null || mod.dir == null || uri == null || context == null) return false;
		File saves = mod.savesDir();
		try {
			String name = uri.getLastPathSegment();
			if (name == null) name = "import";
			name = name.toLowerCase(Locale.ROOT);
			if (name.endsWith(".zip") || name.contains(".zip")) {
				extractZip(context, uri, saves);
			} else {
				// single file copy
				String fileName = "save_" + System.currentTimeMillis();
				try {
					android.database.Cursor c = context.getContentResolver().query(uri, null, null, null, null);
					if (c != null) {
						if (c.moveToFirst()) {
							int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
							if (idx >= 0) {
								String dn = c.getString(idx);
								if (dn != null && !dn.isEmpty()) fileName = dn;
							}
						}
						c.close();
					}
				} catch (Exception ignored) {}
				File dest = new File(saves, fileName);
				try (InputStream in = context.getContentResolver().openInputStream(uri);
				     FileOutputStream out = new FileOutputStream(dest)) {
					if (in == null) return false;
					byte[] buf = new byte[8192];
					int n;
					while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
				}
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static void zipDirectory(File sourceDir, File zipFile) throws IOException {
		try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(zipFile))) {
			zipWalk(sourceDir, sourceDir, zos);
		}
	}

	private static void zipWalk(File root, File current, java.util.zip.ZipOutputStream zos) throws IOException {
		File[] kids = current.listFiles();
		if (kids == null) return;
		for (File f : kids) {
			String rel = root.toURI().relativize(f.toURI()).getPath();
			if (f.isDirectory()) {
				if (!rel.endsWith("/")) rel += "/";
				zos.putNextEntry(new ZipEntry(rel));
				zos.closeEntry();
				zipWalk(root, f, zos);
			} else {
				zos.putNextEntry(new ZipEntry(rel));
				try (FileInputStream in = new FileInputStream(f)) {
					byte[] buf = new byte[8192];
					int n;
					while ((n = in.read(buf)) != -1) zos.write(buf, 0, n);
				}
				zos.closeEntry();
			}
		}
	}
}
