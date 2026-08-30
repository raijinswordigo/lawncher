package net.kiwi.lawncher;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * FileManager: filesystem logic for the "Files" screen. No UI code here -
 * {@link FilesScreen} owns that and only talks to this class, same split as
 * ModManager / Launcher.
 *
 * Covers Lawncher's own two storage roots:
 *   EXTERNAL - context.getExternalFilesDir(null), i.e.
 *              /Android/data/net.kiwi.lawncher/files on shared storage
 *              (this is also where ModManager keeps mods/, see MODS_DIR)
 *   INTERNAL - context.getFilesDir(), private app storage
 *
 * No special storage permission is needed for either: both are always
 * writable/readable by the app itself, pre- and post-scoped-storage.
 */
class FileManager {

	enum Root { EXTERNAL, INTERNAL }
	static File clipboardFile = null;
	static boolean isCutOperation = false;

	static class Entry {
		final File file;
		final boolean isDirectory;
		final long size;

		Entry(File file) {
			this.file = file;
			this.isDirectory = file.isDirectory();
			this.size = isDirectory ? 0 : file.length();
		}

		String name() {
			return file.getName();
		}
	}

	static File rootDir(Context context, Root root) {
		if (root == Root.EXTERNAL) {
			File dir = context.getExternalFilesDir(null);
			return dir != null ? dir : context.getFilesDir();
		}
		return context.getFilesDir();
	}

	static boolean copyFileOrDirectory(File src, File dst) {
		if (src.isDirectory()) {
			if (!dst.exists() && !dst.mkdirs()) return false;
			String[] children = src.list();
			if (children != null) {
				for (String child : children) {
					copyFileOrDirectory(new File(src, child), new File(dst, child));
				}
			}
			return true;
		} else {
			try (InputStream in = new FileInputStream(src);
			     OutputStream out = new FileOutputStream(dst)) {
				byte[] buf = new byte[1024];
				int len;
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
				return true;
			} catch (Exception e) {
				return false;
			}
		}
	}

	/** Lists dir's direct children, directories first then alphabetical. Empty list if dir is empty/unreadable. */
	static List<Entry> list(File dir) {
		List<Entry> entries = new ArrayList<>();
		File[] children = dir.listFiles();
		if (children == null) return entries;

		for (File f : children) entries.add(new Entry(f));
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			entries.sort((a, b) -> {
				if (a.isDirectory != b.isDirectory) return a.isDirectory ? -1 : 1;
				return a.name().compareToIgnoreCase(b.name());
			});
		}
		return entries;
	}

	/** Deletes a file, or a directory and everything in it. Returns false if it couldn't be fully removed. */
	static boolean delete(File file) {
		return deleteRecursive(file);
	}

	static String humanSize(long bytes) {
		if (bytes < 1024) return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(1024));
		char unit = "KMGT".charAt(exp - 1);
		return String.format(Locale.US, "%.1f %cB", bytes / Math.pow(1024, exp), unit);
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
