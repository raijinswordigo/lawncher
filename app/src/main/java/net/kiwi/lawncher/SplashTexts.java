package net.kiwi.lawncher;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;

/**
 * Minecraft-style splash lines.
 *
 * Source (raw text, one line per splash):
 *   https://raw.githubusercontent.com/raijinswordigo/requests/refs/heads/main/splashes.txt
 *
 * Cached under cacheDir/splashes.txt. Prefetch on launch; UI reads pick() which
 * is sync and never blocks on the network.
 */
final class SplashTexts {
	private SplashTexts() {}

	private static final String TAG = "LawncherSplash";
	static final String URL =
			"https://raw.githubusercontent.com/raijinswordigo/requests/refs/heads/main/splashes.txt";
	private static final String CACHE_NAME = "splashes.txt";

	private static final String[] FALLBACK = {
			"Mod all the things!",
			"FileRift approved.",
			"Also try Swordigo!",
			"Join the Discord!",
			"Kiwi powered.",
			"Watch those $end markers.",
			"POD go brrr.",
			"Vanilla required.",
	};

	private static final List<String> cached = new ArrayList<>();
	private static final Random rng = new Random();
	private static volatile boolean fetching;

	/**
	 * Background fetch whenever called — always hits the network when available,
	 * falls back to disk cache on failure. Safe to call on every launch.
	 */
	static void prefetch(Context ctx) {
		if (fetching) return;
		fetching = true;
		Context app = ctx.getApplicationContext();
		// Warm memory from disk so pick() works immediately
		loadFromDisk(app);
		new Thread(() -> {
			try {
				String raw = httpGet(URL);
				if (raw != null && !raw.trim().isEmpty()) {
					List<String> lines = parse(raw);
					if (!lines.isEmpty()) {
						synchronized (cached) {
							cached.clear();
							cached.addAll(lines);
						}
						saveDisk(app, raw);
						Log.d(TAG, "refreshed " + lines.size() + " splashes from network");
					}
				}
			} catch (Exception e) {
				Log.d(TAG, "network refresh failed, using cache", e);
			} finally {
				fetching = false;
			}
		}, "Lawncher-Splash").start();
	}

	/** Random splash — uses memory, then disk, then built-in fallbacks. Never hits the network. */
	static String pick(Context ctx) {
		synchronized (cached) {
			if (cached.isEmpty()) loadFromDisk(ctx);
			if (!cached.isEmpty()) return cached.get(rng.nextInt(cached.size()));
		}
		return FALLBACK[rng.nextInt(FALLBACK.length)];
	}

	static void clearCache(Context ctx) {
		synchronized (cached) { cached.clear(); }
		File f = new File(ctx.getCacheDir(), CACHE_NAME);
		//noinspection ResultOfMethodCallIgnored
		f.delete();
	}

	private static void loadFromDisk(Context ctx) {
		File f = new File(ctx.getCacheDir(), CACHE_NAME);
		if (!f.exists()) return;
		try (FileInputStream in = new FileInputStream(f);
		     Scanner s = new Scanner(in).useDelimiter("\\A")) {
			String raw = s.hasNext() ? s.next() : "";
			List<String> lines = parse(raw);
			if (!lines.isEmpty()) {
				synchronized (cached) {
					if (cached.isEmpty()) cached.addAll(lines);
				}
			}
		} catch (Exception ignored) {}
	}

	private static void saveDisk(Context ctx, String raw) {
		try (FileOutputStream out = new FileOutputStream(new File(ctx.getCacheDir(), CACHE_NAME))) {
			out.write(raw.getBytes());
		} catch (Exception ignored) {}
	}

	private static List<String> parse(String raw) {
		List<String> out = new ArrayList<>();
		if (raw == null) return out;
		// strip UTF-8 BOM
		if (raw.startsWith("\uFEFF")) raw = raw.substring(1);
		for (String line : raw.split("\n")) {
			String t = line.trim();
			if (t.isEmpty() || t.startsWith("#")) continue;
			out.add(t);
		}
		return out;
	}

	private static String httpGet(String urlStr) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
		conn.setRequestMethod("GET");
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(5000);
		conn.setRequestProperty("User-Agent", "Mozilla/5.0 Lawncher");
		conn.setRequestProperty("Accept-Encoding", "gzip");
		conn.setRequestProperty("Connection", "close");
		int code = conn.getResponseCode();
		if (code != 200) throw new Exception("HTTP " + code);
		InputStream in = conn.getInputStream();
		if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) in = new GZIPInputStream(in);
		try (InputStream stream = in; Scanner s = new Scanner(stream).useDelimiter("\\A")) {
			return s.hasNext() ? s.next() : "";
		}
	}
}
