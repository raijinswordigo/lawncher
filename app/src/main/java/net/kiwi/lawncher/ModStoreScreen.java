package net.kiwi.lawncher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;

public class ModStoreScreen {

	static {
		System.setProperty("java.net.preferIPv4Stack", "true");
	}

	private static final String STORE_URL = "https://raw.githubusercontent.com/raijinswordigo/requests/refs/heads/main/store.json";
	private static final String CACHE_FILE = "store_cache.json";
	private static final int ICON_CACHE_CAP = 60;

	private static JSONArray sPreloadedMods = null;

	// LRU cache of decoded icon bitmaps, keyed by icon URL, shared across screen opens.
	private static final Map<String, Bitmap> sIconCache = Collections.synchronizedMap(
	new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
			return size() > ICON_CACHE_CAP;
		}
	});

	/** Always try the network when called; on failure keep existing memory/disk cache. */
	public static void prefetch(Context context) {
		final Context app = context.getApplicationContext();
		new Thread(() -> {
			try {
				String raw = httpGet(STORE_URL);
				String safe = sanitizeJson(raw);
				JSONArray arr = new JSONArray(safe);
				sPreloadedMods = arr;
				saveCache(app, safe);
			} catch (Exception ignored) {
				// offline — leave sPreloadedMods / disk cache as-is
			}
		}, "Lawncher-StorePrefetch").start();
	}

	static View build(Activity activity) {
		// Keep the keyboard as an overlay instead of resizing/panning the window -
		// otherwise everything below the search bar shifts when it gains focus.
		activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

		LinearLayout screen = new LinearLayout(activity);
		Theme.attachToRoot(screen);
		screen.setOrientation(LinearLayout.VERTICAL);
		screen.setBackgroundColor(Color.parseColor(Theme.BG));

		screen.addView(TopBar.build(activity, "Mod Store"), new LinearLayout.LayoutParams(-1, -2));

		LinearLayout list = new LinearLayout(activity);
		list.setOrientation(LinearLayout.VERTICAL);
		list.setPadding(Theme.dp(activity, 20), Theme.dp(activity, 4), Theme.dp(activity, 20), Theme.dp(activity, 20));

		ScrollView scroll = new ScrollView(activity);
		scroll.addView(list, new ScrollView.LayoutParams(-1, -2));

		ModStore store = new ModStore(activity, list, scroll);

		screen.addView(buildSearchBar(activity, store), searchBarParams(activity));
		screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

		store.load();
		return screen;
	}

	private static LinearLayout.LayoutParams searchBarParams(Activity activity) {
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Theme.dp(activity, 52));
		p.setMargins(Theme.dp(activity, 20), Theme.dp(activity, 16), Theme.dp(activity, 20), Theme.dp(activity, 12));
		return p;
	}

	// ==========================================
	// Search bar (custom drawn search icon, no drawable assets needed)
	// ==========================================

	private static View buildSearchBar(Activity activity, ModStore store) {
		LinearLayout bar = new LinearLayout(activity);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.CENTER_VERTICAL);
		int pad = Theme.dp(activity, 12);
		bar.setPadding(pad, 0, pad, 0);

		GradientDrawable bg = new GradientDrawable();
		bg.setCornerRadius(Theme.dp(activity, 24));
		bg.setColor(Color.parseColor(Theme.CARD));
		bg.setStroke(Theme.dp(activity, 1), Color.parseColor(Theme.TEXT_DIM) & 0x33FFFFFF);
		bar.setBackground(bg);

		SearchIconView icon = new SearchIconView(activity);
		int iconSize = Theme.dp(activity, 18);
		LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
		iconParams.rightMargin = Theme.dp(activity, 10);
		bar.addView(icon, iconParams);

		// A real, natural EditText. By setting height to MATCH_PARENT and removing the background,
		// we prevent the cursor or focus states from altering its dimensions and shifting the layout.
		EditText input = new EditText(activity);
		input.setHint("Search mods...");
		input.setHintTextColor(Color.parseColor(Theme.TEXT_DIM));
		input.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		input.setSingleLine(true);
		input.setBackground(null); // Removes default underline padding & boundaries
		input.setPadding(0, 0, 0, 0);
		input.setGravity(Gravity.CENTER_VERTICAL);
		input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
		input.setInputType(InputType.TYPE_CLASS_TEXT);

		// Use MATCH_PARENT so the view physically cannot grow when focused
		LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
		bar.addView(input, inputParams);

		Handler debounce = new Handler(Looper.getMainLooper());
		Runnable[] pending = new Runnable[1];
		input.addTextChangedListener(new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
			@Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
			@Override public void afterTextChanged(Editable s) {
				String query = s.toString();
				if (pending[0] != null) debounce.removeCallbacks(pending[0]);
				pending[0] = () -> store.setQuery(query);
				debounce.postDelayed(pending[0], 200);
			}
		});

		return bar;
	}

	/** Minimal magnifying-glass icon drawn with Canvas - crisp at any density, no assets required. */
	private static class SearchIconView extends View {
		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

		SearchIconView(Context ctx) {
			super(ctx);
			paint.setStyle(Paint.Style.STROKE);
			paint.setColor(Color.parseColor(Theme.TEXT_DIM));
			paint.setStrokeCap(Paint.Cap.ROUND);
		}

		@Override
		protected void onDraw(Canvas canvas) {
			float w = getWidth(), h = getHeight();
			float stroke = w * 0.11f;
			paint.setStrokeWidth(stroke);

			float radius = w * 0.34f;
			float cx = w * 0.42f, cy = h * 0.42f;
			canvas.drawCircle(cx, cy, radius, paint);

			float handleStart = cx + radius * 0.75f;
			float handleEnd = w * 0.92f;
			canvas.drawLine(handleStart, cy + radius * 0.75f, handleEnd, h * 0.92f, paint);
		}
	}

	// ==========================================
	// Store state: fetching, filtering, lazy icon loading
	// ==========================================

	private static class ModStore {
		final Activity activity;
		final LinearLayout list;
		final ScrollView scroll;
		final List<CardRef> visibleCards = new ArrayList<>();
		List<JSONObject> allMods = new ArrayList<>();
		String query = "";

		ModStore(Activity activity, LinearLayout list, ScrollView scroll) {
			this.activity = activity;
			this.list = list;
			this.scroll = scroll;
			scroll.getViewTreeObserver().addOnScrollChangedListener(this::checkVisibleIcons);
		}

		void setQuery(String q) {
			query = q;
			render();
		}

		void load() {
			JSONArray cached = sPreloadedMods;
			if (cached == null) {
				String raw = readCache(activity);
				if (raw != null) {
					try { cached = new JSONArray(sanitizeJson(raw)); sPreloadedMods = cached; }
					catch (Exception e) { new File(activity.getCacheDir(), CACHE_FILE).delete(); }
				}
			}

			if (cached != null) {
				allMods = toList(cached);
				render();
			} else {
				showLoading();
			}

			new Thread(() -> {
				try {
					String raw = httpGet(STORE_URL);
					String safe = sanitizeJson(raw);
					JSONArray fresh = new JSONArray(safe);
					sPreloadedMods = fresh;
					saveCache(activity, safe);
					activity.runOnUiThread(() -> { allMods = toList(fresh); render(); });
				} catch (Exception e) {
					if (allMods.isEmpty()) activity.runOnUiThread(() -> showError("Failed to load mods."));
				}
			}).start();
		}

		void render() {
			list.removeAllViews();
			visibleCards.clear();

			List<JSONObject> filtered = filter(allMods, query);
			if (filtered.isEmpty()) {
				showError(allMods.isEmpty() ? "Loading mods..." : "No mods match your search.");
				return;
			}

			for (JSONObject mod : filtered) {
				CardRef ref = buildModCard(activity, mod);
				list.addView(ref.root);
				visibleCards.add(ref);
			}
			list.post(this::checkVisibleIcons);
		}

		void checkVisibleIcons() {
			for (CardRef ref : visibleCards) {
				if (ref.loaded || ref.loading || ref.iconUrl.isEmpty()) continue;
				if (ref.root.getLocalVisibleRect(new android.graphics.Rect())) {
					loadIcon(activity, ref);
				}
			}
		}

		void showLoading() {
			list.removeAllViews();
			TextView t = new TextView(activity);
			t.setText("Loading mods...");
			t.setTextColor(Color.parseColor(Theme.TEXT_DIM));
			t.setGravity(Gravity.CENTER);
			t.setPadding(0, Theme.dp(activity, 40), 0, 0);
			list.addView(t);
		}

		void showError(String msg) {
			list.removeAllViews();
			TextView err = new TextView(activity);
			err.setText(msg);
			err.setTextColor(Color.parseColor(Theme.ACCENT_RED));
			err.setGravity(Gravity.CENTER);
			err.setPadding(0, Theme.dp(activity, 40), 0, 0);
			list.addView(err);
		}
	}

	private static List<JSONObject> toList(JSONArray arr) {
		List<JSONObject> out = new ArrayList<>();
		for (int i = 0; i < arr.length(); i++) {
			try { out.add(arr.getJSONObject(i)); } catch (Exception ignored) {}
		}
		return out;
	}

	private static List<JSONObject> filter(List<JSONObject> all, String query) {
		if (query == null || query.trim().isEmpty()) return all;
		String needle = query.trim().toLowerCase(Locale.ROOT);
		List<JSONObject> out = new ArrayList<>();
		for (JSONObject m : all) {
			String name = m.optString("name", "").toLowerCase(Locale.ROOT);
			String author = m.optString("author", "").toLowerCase(Locale.ROOT);
			String desc = m.optString("description", "").toLowerCase(Locale.ROOT);
			if (name.contains(needle) || author.contains(needle) || desc.contains(needle)) out.add(m);
		}
		return out;
	}

	// ==========================================
	// Mod card + lazy icon loading
	// ==========================================

	/** Holds refs to a rendered card so its icon can be loaded later, once actually visible. */
	private static class CardRef {
		View root;
		ImageView img;
		String iconUrl;
		boolean loading;
		boolean loaded;
	}

	private static CardRef buildModCard(Activity activity, JSONObject mod) {
		CardRef ref = new CardRef();

		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.HORIZONTAL);
		card.setBackground(Theme.rippleBackground(Theme.dp(activity, 12), Theme.CARD));
		card.setPadding(Theme.dp(activity, 16), Theme.dp(activity, 16), Theme.dp(activity, 16), Theme.dp(activity, 16));

		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
		params.bottomMargin = Theme.dp(activity, 12);
		card.setLayoutParams(params);

		String name = mod.optString("name", "Unknown Mod");

		FrameLayout iconFrame = new FrameLayout(activity);
		int iconSize = Theme.dp(activity, 54);
		LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
		iconParams.rightMargin = Theme.dp(activity, 16);
		iconFrame.setLayoutParams(iconParams);

		GradientDrawable bgShape = new GradientDrawable();
		bgShape.setShape(GradientDrawable.OVAL);
		bgShape.setColor(fallbackColor(name));

		TextView fallbackIcon = new TextView(activity);
		fallbackIcon.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT));
		fallbackIcon.setTextColor(Color.WHITE);
		fallbackIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
		fallbackIcon.setGravity(Gravity.CENTER);
		fallbackIcon.setTypeface(null, Typeface.BOLD);
		fallbackIcon.setBackground(bgShape);
		iconFrame.addView(fallbackIcon, new FrameLayout.LayoutParams(-1, -1));

		ImageView img = new ImageView(activity);
		img.setScaleType(ImageView.ScaleType.CENTER_CROP);
		img.setClipToOutline(true);
		img.setVisibility(View.GONE);
		iconFrame.addView(img, new FrameLayout.LayoutParams(-1, -1));

		card.addView(iconFrame);
		ref.img = img;
		ref.iconUrl = mod.optString("icon", "");

		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);
		content.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

		TextView title = new TextView(activity);
		title.setText(name + " v" + mod.optString("version", "1.0"));
		title.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		title.setTypeface(null, Typeface.BOLD);
		content.addView(title);

		String author = mod.optString("author", "");
		if (!author.isEmpty()) {
			TextView authorView = new TextView(activity);
			authorView.setText("By " + author);
			authorView.setTextColor(Color.parseColor(Theme.TEXT_DIM));
			authorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
			authorView.setPadding(0, 0, 0, Theme.dp(activity, 4));
			content.addView(authorView);
		}

		TextView desc = new TextView(activity);
		desc.setText(mod.optString("description", "No description provided."));
		desc.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		desc.setPadding(0, 0, 0, Theme.dp(activity, 8));
		content.addView(desc);

		ProgressBar pb = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
		pb.setMax(100);
		pb.setVisibility(View.GONE);
		LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(-1, Theme.dp(activity, 8));
		pbParams.bottomMargin = Theme.dp(activity, 8);
		content.addView(pb, pbParams);

		String dlUrl = mod.optString("downloadUrl", "");
		if (!dlUrl.isEmpty()) {
			Button btn = new Button(activity);
			btn.setText("Install Mod");
			btn.setTextColor(Color.parseColor(Theme.ACCENT_DARK_TEXT));
			btn.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.ACCENT_BLUE));
			btn.setAllCaps(false);
			btn.setOnClickListener(v -> downloadAndInstall(activity, dlUrl, ref.iconUrl, btn, pb));
			content.addView(btn, new LinearLayout.LayoutParams(-1, -2));
		}

		card.addView(content);
		ref.root = card;
		return ref;
	}

	private static int fallbackColor(String name) {
		int hash = Math.abs(name.hashCode());
		int r = (int) (((hash & 0xFF0000) >> 16) * 0.7f);
		int g = (int) (((hash & 0x00FF00) >> 8) * 0.7f);
		int b = (int) ((hash & 0x0000FF) * 0.7f);
		return Color.rgb(r, g, b);
	}

	private static void loadIcon(Activity activity, CardRef ref) {
		Bitmap cached = sIconCache.get(ref.iconUrl);
		if (cached != null) {
			applyIcon(ref, cached);
			return;
		}

		ref.loading = true;
		int targetPx = Theme.dp(activity, 54);
		new Thread(() -> {
			Bitmap bmp = downloadSampledBitmap(ref.iconUrl, targetPx);
			if (bmp != null) sIconCache.put(ref.iconUrl, bmp);
			activity.runOnUiThread(() -> {
				ref.loading = false;
				if (bmp != null) applyIcon(ref, bmp);
			});
		}).start();
	}

	private static void applyIcon(CardRef ref, Bitmap bmp) {
		ref.img.setImageBitmap(bmp);
		ref.img.setVisibility(View.VISIBLE);
		ref.loaded = true;
	}

	/** Downloads an image and decodes it pre-scaled to ~targetPx, to avoid loading full-res icons into memory. */
	private static Bitmap downloadSampledBitmap(String url, int targetPx) {
		try {
			byte[] bytes = httpGetBytes(url);
			if (bytes == null) return null;

			BitmapFactory.Options bounds = new BitmapFactory.Options();
			bounds.inJustDecodeBounds = true;
			BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);

			int sample = 1;
			while ((bounds.outWidth / (sample * 2)) >= targetPx && (bounds.outHeight / (sample * 2)) >= targetPx) {
				sample *= 2;
			}

			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inSampleSize = sample;
			return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
		} catch (Exception e) {
			return null;
		}
	}

	// ==========================================
	// Install
	// ==========================================

	private static void downloadAndInstall(Activity activity, String zipUrl, String iconUrl, Button btn, ProgressBar pb) {
		btn.setEnabled(false);
		btn.setText("Downloading...");
		btn.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.TEXT_DIM));
		pb.setVisibility(View.VISIBLE);
		pb.setProgress(0);

		new Thread(() -> {
			File file = new File(activity.getCacheDir(), "store_mod_" + System.currentTimeMillis() + ".zip");
			try {
				HttpURLConnection conn = openFollowingRedirects(zipUrl);
				int length = conn.getContentLength();

				try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(file)) {
					byte[] buffer = new byte[8192];
					int read;
					long total = 0;
					while ((read = in.read(buffer)) != -1) {
						total += read;
						if (length > 0) {
							int progress = (int) (total * 100 / length);
							activity.runOnUiThread(() -> pb.setProgress(progress));
						}
						out.write(buffer, 0, read);
					}
				}

				ModManager.installMod(activity, Uri.fromFile(file), iconUrl, new ModManager.InstallCallback() {
					@Override
					public void onSuccess(ModManager.ModInfo mod) {
						activity.runOnUiThread(() -> {
							pb.setVisibility(View.GONE);
							btn.setText("Installed");
							btn.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.ACCENT_GREEN));
							Toast.makeText(activity, "Successfully installed " + mod.name, Toast.LENGTH_LONG).show();
						});
						file.delete();
					}

					@Override
					public void onFailure(String reason) {
						activity.runOnUiThread(() -> {
							pb.setVisibility(View.GONE);
							btn.setEnabled(true);
							btn.setText("Retry Install");
							btn.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.ACCENT_RED));
							Toast.makeText(activity, "Failed to install: " + reason, Toast.LENGTH_LONG).show();
						});
						file.delete();
					}
				});
			} catch (Exception e) {
				activity.runOnUiThread(() -> {
					pb.setVisibility(View.GONE);
					btn.setEnabled(true);
					btn.setText("Download Failed");
					btn.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.ACCENT_RED));
				});
				file.delete();
			}
		}).start();
	}

	// ==========================================
	// HTTP + cache helpers
	// ==========================================

	private static HttpURLConnection openFollowingRedirects(String urlStr) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(5000);
		conn.setRequestProperty("User-Agent", "Mozilla/5.0");
		conn.connect();

		int code = conn.getResponseCode();
		if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM
		|| code == HttpURLConnection.HTTP_SEE_OTHER) {
			String location = conn.getHeaderField("Location");
			conn = (HttpURLConnection) new URL(location).openConnection();
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(5000);
			conn.connect();
		}
		return conn;
	}

	private static String httpGet(String urlStr) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
		conn.setRequestMethod("GET");
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(5000);
		conn.setRequestProperty("User-Agent", "Mozilla/5.0");
		conn.setRequestProperty("Accept-Encoding", "gzip");
		conn.setRequestProperty("Connection", "close");

		if (conn.getResponseCode() != 200) throw new Exception("HTTP " + conn.getResponseCode());

		InputStream in = conn.getInputStream();
		if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) in = new GZIPInputStream(in);

		try (InputStream stream = in; Scanner s = new Scanner(stream).useDelimiter("\\A")) {
			return s.hasNext() ? s.next() : "";
		}
	}

	private static byte[] httpGetBytes(String urlStr) {
		try {
			HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(5000);
			conn.setRequestProperty("User-Agent", "Mozilla/5.0");
			try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
				return out.toByteArray();
			}
		} catch (Exception e) {
			return null;
		}
	}

	private static String sanitizeJson(String raw) {
		if (raw == null) return null;
		return raw.replaceAll(",(\\s*[\\]}])", "$1").trim();
	}

	private static void saveCache(Context context, String data) {
		try (FileOutputStream fos = new FileOutputStream(new File(context.getCacheDir(), CACHE_FILE))) {
			fos.write(data.getBytes());
		} catch (Exception ignored) {}
	}

	private static String readCache(Context context) {
		File f = new File(context.getCacheDir(), CACHE_FILE);
		if (!f.exists()) return null;
		try (FileInputStream fis = new FileInputStream(f); Scanner s = new Scanner(fis).useDelimiter("\\A")) {
			return s.hasNext() ? s.next() : null;
		} catch (Exception e) {
			return null;
		}
	}
}