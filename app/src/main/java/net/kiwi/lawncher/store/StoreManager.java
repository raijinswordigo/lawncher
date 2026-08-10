package net.kiwi.lawncher.store;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import net.kiwi.lawncher.InstanceManager;
import net.kiwi.lawncher.ModManager;
import net.kiwi.lawncher.util.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * StoreManager: loads the mod catalog (bundled asset, with a remote URL
 * override), downloads mod zips with progress, and hands them to
 * {@link ModManager} for installation.
 */
public final class StoreManager {

	private static final String TAG = "StoreManager";
	private static final String ASSET_CATALOG = "modstore/catalog.json";
	private static final String PREF_INSTALLED = "store.installed";

	/**
	 * Point this at a hosted catalog to go live. When empty the bundled
	 * assets/modstore/catalog.json is used (demo mode).
	 */
	public static final String CATALOG_URL = "";

	public interface CatalogCallback {
		void onResult(boolean success, List<StoreMod> mods, String error);
	}

	public interface ProgressCallback {
		void onProgress(long done, long total);
	}

	private StoreManager() {}

	// ---- catalog ----

	public static void loadCatalog(final Context context, final CatalogCallback callback) {
		new Thread(() -> {
			try {
				List<StoreMod> mods = parseCatalog(readAsset(context));
				post(context, () -> callback.onResult(true, mods, null));
			} catch (Exception e) {
				Log.e(TAG, "catalog load failed", e);
				post(context, () -> callback.onResult(false, null, "catalog unavailable"));
			}
		}).start();
	}

	public static void fetchCatalog(final Context context, final String url, final CatalogCallback callback) {
		new Thread(() -> {
			try {
				String json = httpGet(url);
				List<StoreMod> mods = parseCatalog(json);
				post(context, () -> callback.onResult(true, mods, null));
			} catch (Exception e) {
				Log.e(TAG, "catalog fetch failed", e);
				post(context, () -> callback.onResult(false, null, e.getMessage()));
			}
		}).start();
	}

	private static List<StoreMod> parseCatalog(String json) throws Exception {
		List<StoreMod> mods = new ArrayList<>();
		JSONObject root = new JSONObject(json);
		JSONArray arr = root.optJSONArray("mods");
		if (arr == null) return mods;
		for (int i = 0; i < arr.length(); i++) {
			JSONObject o = arr.optJSONObject(i);
			if (o == null) continue;
			StoreMod m = new StoreMod();
			m.id = o.optString("id", "");
			m.name = o.optString("name", m.id);
			m.author = o.optString("author", "Unknown");
			m.version = o.optString("version", "1.0.0");
			m.description = o.optString("description", "");
			m.longDescription = o.optString("long_description", m.description);
			m.category = o.optString("category", "General");
			m.priceCents = o.optLong("price_cents", 0);
			m.currency = o.optString("currency", "USD");
			m.downloadUrl = o.optString("download_url", "");
			m.sizeBytes = o.optLong("size_bytes", 0);
			m.installs = o.optInt("installs", 0);
			m.rating = o.optDouble("rating", 0);
			m.featured = o.optBoolean("featured", false);
			JSONArray shots = o.optJSONArray("screenshots");
			if (shots != null) for (int j = 0; j < shots.length(); j++) m.screenshots.add(shots.optString(j, ""));
			JSONArray tags = o.optJSONArray("tags");
			if (tags != null) for (int j = 0; j < tags.length(); j++) m.tags.add(tags.optString(j, ""));
			if (m.id.isEmpty()) continue;
			mods.add(m);
		}
		return mods;
	}

	private static String readAsset(Context context) throws IOException {
		InputStream in = context.getAssets().open(ASSET_CATALOG);
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		StringBuilder sb = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) sb.append(line).append('\n');
		reader.close();
		return sb.toString();
	}

	private static String httpGet(String url) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setConnectTimeout(15000);
		conn.setReadTimeout(30000);
		conn.setRequestProperty("User-Agent", "Raijin-Lawncher/1.0");
		int code = conn.getResponseCode();
		if (code != 200) throw new IOException("HTTP " + code);
		InputStream in = conn.getInputStream();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		StringBuilder sb = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) sb.append(line).append('\n');
		reader.close();
		conn.disconnect();
		return sb.toString();
	}

	// ---- install flow ----

	/** Downloads the mod zip and installs it through ModManager. */
	public static void installFromStore(final Context context, final StoreMod mod,
			final ProgressCallback progress, final ModManager.InstallCallback callback) {
		new Thread(() -> {
			try {
				File zip = download(context, mod, progress);

				// Mods install into the active game instance; when none exists,
				// auto-create a vanilla one first (this thread, so the APK copy
				// never blocks the UI).
				InstanceManager.InstanceInfo instance = InstanceManager.resolveActiveInstance(context);
				if (instance == null) instance = InstanceManager.ensureVanillaInstance(context);
				if (instance == null) {
					post(context, () -> callback.onFailure("no game instance \u2014 add one in the Mods screen first"));
					return;
				}

				final InstanceManager.InstanceInfo inst = instance;
				ModManager.InstallCallback wrapped = new ModManager.InstallCallback() {
					@Override public void onSuccess(ModManager.ModInfo info) {
						InstanceManager.addModRef(context, inst, info.id);
						markInstalled(context, mod.id);
						callback.onSuccess(info);
					}

					@Override public void onFailure(String reason) {
						callback.onFailure(reason);
					}
				};
				ModManager.installZipFileInto(context, zip, inst, wrapped);
			} catch (Exception e) {
				Log.e(TAG, "store install failed", e);
				post(context, () -> callback.onFailure("download failed: " + e.getMessage()));
			}
		}).start();
	}

	private static File download(Context context, StoreMod mod, ProgressCallback progress) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL(mod.downloadUrl).openConnection();
		conn.setConnectTimeout(15000);
		conn.setReadTimeout(60000);
		conn.setRequestProperty("User-Agent", "Raijin-Lawncher/1.0");
		int code = conn.getResponseCode();
		if (code != 200) throw new IOException("HTTP " + code);
		long total = conn.getContentLengthLong();
		File zip = new File(context.getCacheDir(), "store_" + mod.id + ".zip");
		InputStream in = conn.getInputStream();
		FileOutputStream out = new FileOutputStream(zip);
		byte[] buf = new byte[8192];
		long done = 0;
		long lastPost = 0;
		int n;
		while ((n = in.read(buf)) != -1) {
			out.write(buf, 0, n);
			done += n;
			if (progress != null && done - lastPost > 64 * 1024) {
				lastPost = done;
				final long d = done, t = total;
				post(context, () -> progress.onProgress(d, t));
			}
		}
		out.flush();
		out.close();
		in.close();
		conn.disconnect();
		if (progress != null) {
			final long d = done, t = total;
			post(context, () -> progress.onProgress(d, t));
		}
		return zip;
	}

	// ---- installed ledger ----

	public static boolean isInstalled(Context context, String modId) {
		if (modId == null || modId.isEmpty()) return false;
		for (String id : installedList()) if (id.equals(modId)) return true;
		return false;
	}

	public static int storeInstallsCount() {
		return installedList().size();
	}

	private static void markInstalled(Context context, String modId) {
		if (isInstalled(context, modId)) return;
		String joined = Prefs.getString(PREF_INSTALLED, "");
		Prefs.putString(PREF_INSTALLED, joined.isEmpty() ? modId : joined + "," + modId);
	}

	private static List<String> installedList() {
		List<String> out = new ArrayList<>();
		String raw = Prefs.getString(PREF_INSTALLED, "");
		if (!raw.isEmpty()) {
			for (String s : raw.split(",")) if (!s.isEmpty()) out.add(s);
		}
		return out;
	}

	private static final Handler UI = new Handler(Looper.getMainLooper());

	private static void post(Context context, Runnable r) {
		UI.post(r);
	}
}
