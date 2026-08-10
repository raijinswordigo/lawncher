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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ModManager: all filesystem / mod-installation logic. No UI code here -
 * Launcher.java owns that and only talks to this class.
 *
 * On-disk layout (flat - no instances). This mirrors the zip layout exactly,
 * so the native side can resolve an asset path like "resources/foo.png" by
 * just prepending this mod's directory - see hook.h's asset override:
 *   <externalFilesDir>/mods/<modId>/
 *       icon.png
 *       properties.toml
 *       resources/
 *           ...mod resources, same tree as inside the zip...
 *
 * Expected mod.zip layout (identical to the installed layout above):
 *   icon.png
 *   resources/
 *   properties.toml
 *
 *   [mod]
 *   id="com.raijin.sinon"
 *   name="Sinon"
 *   version="1.0.0"
 *   description="A simple utility mod."
 */
public class ModManager {

	private static final String MODS_DIR = "mods";
	private static final String RESOURCES_DIR = "resources";
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
		public String author;
		public String category;
		public File dir;
		/**
		 * Filenames from properties.toml's optional "screenshots" key, relative to dir.
		 * Since resources/ is now a real subfolder, screenshots shipped inside the zip's
		 * resources/ folder should be referenced as "resources/preview.png", not just "preview.png".
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
			return files;
		}

		/** Total on-disk size of this mod's directory. */
		public long dirSize() {
			return dir == null ? 0 : sizeOf(dir);
		}
	}

	private static class InstallException extends Exception {
		InstallException(String msg) { super(msg); }
	}

	// ==========================================
	// Mod listing / removal
	// ==========================================

	/**
	 * Resolves the mods directory: per-instance (instances/<id>/mods) when an
	 * instance is given, otherwise the legacy global mods/ dir.
	 */
	public static File getModsDir(Context context, InstanceManager.InstanceInfo instance) {
		if (instance != null && instance.dir != null) return instance.modsDir();
		return getModsDir(context);
	}

	public static List<ModInfo> listInstalledMods(Context context) {
		return listInstalledMods(context, null);
	}

	public static List<ModInfo> listInstalledMods(Context context, InstanceManager.InstanceInfo instance) {
		List<ModInfo> mods = new ArrayList<>();
		File[] modDirs = getModsDir(context, instance).listFiles(File::isDirectory);
		if (modDirs == null) return mods;

		for (File modDir : modDirs) {
			File propsFile = new File(modDir, PROPERTIES_FILE);
			if (!propsFile.exists()) continue;

			ModInfo info = parseProperties(propsFile);
			if (info == null) continue;
			info.dir = modDir;
			mods.add(info);
		}
		mods.sort(Comparator.comparing(m -> m.name.toLowerCase(Locale.ROOT)));
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

	public static void installMod(Context context, Uri uri, InstallCallback callback) {
		installModInto(context, uri, null, callback);
	}

	/** Installs a mod zip into a specific instance's mods dir (or the global dir when instance is null). */
	public static void installModInto(Context context, Uri uri, InstanceManager.InstanceInfo instance, InstallCallback callback) {
		runInstall(context, callback, () -> {
			try (InputStream is = context.getContentResolver().openInputStream(uri)) {
				if (is == null) throw new InstallException("could not open file");
				return doInstall(context, is, getModsDir(context, instance));
			}
		});
	}

	/** Installs a mod from a local zip file (used by the Mod Store). */
	public static void installZipFile(Context context, File zipFile, InstallCallback callback) {
		installZipFileInto(context, zipFile, null, callback);
	}

	/** Installs a mod from a local zip file into a specific instance's mods dir. */
	public static void installZipFileInto(Context context, File zipFile, InstanceManager.InstanceInfo instance, InstallCallback callback) {
		runInstall(context, callback, () -> {
			try (InputStream is = new FileInputStream(zipFile)) {
				return doInstall(context, is, getModsDir(context, instance));
			}
		});
	}

	private interface InstallTask {
		ModInfo run() throws Exception;
	}

	private static void runInstall(Context context, InstallCallback callback, InstallTask task) {
		new Thread(() -> {
			try {
				callback.onSuccess(task.run());
			} catch (InstallException e) {
				callback.onFailure(e.getMessage());
			} catch (Exception e) {
				e.printStackTrace();
				callback.onFailure("unexpected error");
			}
		}).start();
	}

	private static ModInfo doInstall(Context context, InputStream source, File modsDir) throws Exception {
		File staging = new File(context.getCacheDir(), "mod_staging_" + System.currentTimeMillis());
		staging.mkdirs();

		try {
			extractZip(source, staging);

			File propsFile = new File(staging, PROPERTIES_FILE);
			if (!propsFile.exists()) {
				throw new InstallException("missing properties.toml");
			}

			ModInfo info = parseProperties(propsFile);
			if (info == null || info.id == null || info.id.isEmpty()) {
				throw new InstallException("properties.toml missing [mod] id");
			}

			File targetDir = new File(modsDir, sanitizeName(info.id));
			deleteRecursive(targetDir);
			targetDir.mkdirs();

			// Keep resources/ as a real subfolder (mods/<id>/resources/...) rather
			// than flattening it - the native side loads assets by joining the
			// mod's directory with an asset path that already starts with
			// "resources/", so the on-disk tree needs to match the zip's tree.
			File stagedResources = new File(staging, RESOURCES_DIR);
			if (stagedResources.exists()) {
				copyRecursive(stagedResources, new File(targetDir, RESOURCES_DIR));
			}

			// Keep icon + metadata alongside the resources for later listing.
			File icon = new File(staging, ICON_FILE);
			if (icon.exists()) copyFile(icon, new File(targetDir, ICON_FILE));
			copyFile(propsFile, new File(targetDir, PROPERTIES_FILE));

			info.dir = targetDir;
			return info;
		} finally {
			deleteRecursive(staging);
		}
	}

	private static void extractZip(InputStream is, File destDir) throws Exception {
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
	// properties.toml parsing (minimal: flat key="value" pairs in [mod])
	// ==========================================

	private static final Pattern KV_PATTERN = Pattern.compile("^\\s*(\\w+)\\s*=\\s*\"(.*)\"\\s*$");

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

				Matcher m = KV_PATTERN.matcher(trimmed);
				if (!m.matches()) continue;

				switch (m.group(1)) {
					case "id": info.id = m.group(2); break;
					case "name": info.name = m.group(2); break;
					case "version": info.version = m.group(2); break;
					case "author": info.author = m.group(2); break;
					case "category": info.category = m.group(2); break;
					case "description": info.description = m.group(2); break;
					case "screenshots": // comma-separated filenames, relative to the mod's own dir
						for (String part : m.group(2).split(",")) {
							String trimmed2 = part.trim();
							if (!trimmed2.isEmpty()) info.screenshots.add(trimmed2);
						}
						break;
					default: break; // ignore unknown keys
				}
			}
		} catch (IOException e) {
			return null;
		}

		if (info.name == null) info.name = info.id;
		if (info.category == null || info.category.isEmpty()) info.category = "General";
		if (info.author == null) info.author = "";
		return info;
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

	private static long sizeOf(File file) {
		if (file == null || !file.exists()) return 0;
		if (file.isDirectory()) {
			long total = 0;
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) total += sizeOf(child);
			}
			return total;
		}
		return file.length();
	}
}