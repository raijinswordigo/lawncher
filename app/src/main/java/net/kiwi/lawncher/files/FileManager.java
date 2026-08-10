package net.kiwi.lawncher.files;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * FileManager: filesystem engine for the Files screen — listing, stats,
 * delete / rename / create. No UI code here.
 */
public final class FileManager {

	private FileManager() {}

	public static class Entry {
		public final File file;
		public final String name;
		public final boolean directory;
		public final long size;
		public final long modified;
		public int childCount;

		Entry(File f) {
			file = f;
			name = f.getName();
			directory = f.isDirectory();
			size = directory ? 0 : f.length();
			modified = f.lastModified();
		}
	}

	public static List<Entry> list(File dir, boolean dirsFirst, boolean bySize) {
		List<Entry> entries = new ArrayList<>();
		File[] files = dir.listFiles();
		if (files != null) {
			for (File f : files) {
				try {
					entries.add(new Entry(f));
				} catch (Exception ignored) {
				}
			}
		}
		Collections.sort(entries, (a, b) -> {
			if (dirsFirst && a.directory != b.directory) return a.directory ? -1 : 1;
			if (bySize) return Long.compare(a.size, b.size);
			return a.name.compareToIgnoreCase(b.name);
		});
		return entries;
	}

	public static long dirSize(File dir) {
		if (dir == null || !dir.exists()) return 0;
		long total = 0;
		File[] files = dir.listFiles();
		if (files != null) {
			for (File f : files) {
				if (f.isDirectory()) total += dirSize(f);
				else total += f.length();
			}
		}
		return total;
	}

	public static int childCount(File dir) {
		File[] files = dir.listFiles();
		return files == null ? 0 : files.length;
	}

	public static boolean deleteRecursive(File f) {
		if (f == null || !f.exists()) return true;
		if (f.isDirectory()) {
			File[] children = f.listFiles();
			if (children != null) for (File c : children) deleteRecursive(c);
		}
		return f.delete();
	}

	public static boolean rename(File f, String newName) {
		if (f == null || newName == null || newName.trim().isEmpty()) return false;
		String name = newName.trim();
		if (name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) return false;
		File target = new File(f.getParentFile(), name);
		return f.renameTo(target);
	}

	public static boolean createFolder(File parent, String name) {
		if (parent == null || name == null || name.trim().isEmpty()) return false;
		String n = name.trim();
		if (n.contains("/") || n.contains("\\") || n.equals(".") || n.equals("..")) return false;
		File dir = new File(parent, n);
		return !dir.exists() && dir.mkdirs();
	}

	public static String humanSize(long bytes) {
		if (bytes < 1024) return bytes + " B";
		double kb = bytes / 1024.0;
		if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
		double mb = kb / 1024.0;
		if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
		return String.format(Locale.US, "%.2f GB", mb / 1024.0);
	}

	public static String humanDate(long millis) {
		if (millis <= 0) return "\u2014";
		return new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US).format(new Date(millis));
	}
}
