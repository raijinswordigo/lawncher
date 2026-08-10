package net.kiwi.lawncher.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny async image loader with an in-memory LRU + disk cache. */
public final class RemoteImage {

	private static final int MAX_MEM = 48;
	private static final Handler UI = new Handler(Looper.getMainLooper());

	private static final Map<String, Bitmap> MEM = new LinkedHashMap<String, Bitmap>(MAX_MEM, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
			return size() > MAX_MEM;
		}
	};

	private RemoteImage() {}

	public static void load(final Context context, final String url, final ImageView into) {
		if (url == null || url.isEmpty()) return;
		into.setTag(url);
		Bitmap hit = MEM.get(url);
		if (hit != null) {
			into.setImageBitmap(hit);
			return;
		}
		new Thread(() -> {
			try {
				File cached = new File(context.getCacheDir(), "img/"
						+ Integer.toHexString(url.hashCode()) + ".img");
				Bitmap bmp = null;
				if (cached.exists()) {
					bmp = BitmapFactory.decodeFile(cached.getAbsolutePath());
				}
				if (bmp == null) {
					HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
					conn.setConnectTimeout(12000);
					conn.setReadTimeout(20000);
					conn.setRequestProperty("User-Agent", "Raijin-Lawncher/1.0");
					if (conn.getResponseCode() != 200) return;
					InputStream in = conn.getInputStream();
					File parent = cached.getParentFile();
					if (parent != null) parent.mkdirs();
					FileOutputStream out = new FileOutputStream(cached);
					byte[] buf = new byte[8192];
					int n;
					while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
					out.close();
					in.close();
					conn.disconnect();
					bmp = BitmapFactory.decodeFile(cached.getAbsolutePath());
				}
				if (bmp == null) return;
				MEM.put(url, bmp);
				final Bitmap fb = bmp;
				UI.post(() -> {
					Object tag = into.getTag();
					if (tag == null || url.equals(tag)) into.setImageBitmap(fb);
				});
			} catch (Throwable ignored) {
				// network / decode failures are non-fatal: caller keeps placeholder
			}
		}).start();
	}
}
