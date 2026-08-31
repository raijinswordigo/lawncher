package net.kiwi.lawncher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * Settings — Theme, toggles, performance, storage, community, credits, about.
 * Prefs: SharedPreferences "lawncher_prefs".
 */
public class SettingsScreen {

	private static final String PREFS = "lawncher_prefs";
	static final String KEY_CHECK_UPDATES = "check_updates";
	static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
	static final String KEY_CRASH_DIALOGS = "crash_dialogs";
	static final String KEY_CONFIRM_DELETE = "confirm_delete";
	static final String KEY_COMPACT_MODS = "compact_mods";
	static final String KEY_SHOW_SPLASH = "show_splash";
	static final String KEY_HAPTICS = "haptics";
	static final String KEY_AUTO_REFRESH_STORE = "auto_refresh_store";
	static final String KEY_TARGET_FPS = "target_fps";

	/** Public invite — also used from the crash dialog. */
	static final String DISCORD_URL = "https://discord.gg/t5cMNQRK9E";

	static View build(Activity activity) {
		LinearLayout screen = new LinearLayout(activity);
		screen.setOrientation(LinearLayout.VERTICAL);
		Theme.attachToRoot(screen);
		screen.setBackgroundColor(Color.parseColor(Theme.BG));

		screen.addView(TopBar.build(activity, "Settings"),
		new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT,
		ViewGroup.LayoutParams.WRAP_CONTENT
		)
		);

		ScrollView scroll = new ScrollView(activity);
		LinearLayout list = new LinearLayout(activity);
		list.setOrientation(LinearLayout.VERTICAL);
		list.setPadding(Theme.dp(activity, 20), Theme.dp(activity, 8),
		Theme.dp(activity, 20), Theme.dp(activity, 32));

		SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

		list.addView(sectionLabel(activity, "Community"));
		list.addView(actionRow(activity, "Discord server",
		"Join the Lawncher / Swordigo modding community",
		() -> openUrl(activity, DISCORD_URL)));

		list.addView(sectionLabel(activity, "Credits"));
		list.addView(creditRow(activity, "@realraijin", "Lead Dev"));
		list.addView(creditRow(activity, "@kiziyon", "Major Lua API"));
		list.addView(creditRow(activity, "@mrsinup", "C FileRift, POD and PVR Renderer"));
		list.addView(creditRow(activity, "@4t1w", "SwordigoRuntime (Java Rendering Support)"));

		list.addView(sectionLabel(activity, "Appearance"));
		list.addView(themePicker(activity));
		list.addView(toggleRow(activity, prefs, KEY_SHOW_SPLASH, "Splash texts",
		"Minecraft-style tips under the title (fetched & cached from GitHub)", true));
		list.addView(toggleRow(activity, prefs, KEY_COMPACT_MODS, "Compact mod grid",
		"Tighter spacing on the installed-mods grid", false));

		list.addView(sectionLabel(activity, "Performance"));
		list.addView(fpsPicker(activity, prefs));

		list.addView(sectionLabel(activity, "General"));
		list.addView(toggleRow(activity, prefs, KEY_CHECK_UPDATES, "Check for updates",
		"Look for Lawncher updates on launch", true));
		list.addView(toggleRow(activity, prefs, KEY_AUTO_REFRESH_STORE, "Prefetch mod store",
		"Download the store listing in the background on launch", true));
		list.addView(toggleRow(activity, prefs, KEY_KEEP_SCREEN_ON, "Keep screen on",
		"Prevent sleep while the launcher is open", false));
		list.addView(toggleRow(activity, prefs, KEY_HAPTICS, "Haptic feedback",
		"Vibrate lightly on important actions (when available)", true));
		list.addView(toggleRow(activity, prefs, KEY_CRASH_DIALOGS, "Crash recovery dialogs",
		"Show a dialog after a native crash with export / Discord options", true));
		list.addView(toggleRow(activity, prefs, KEY_CONFIRM_DELETE, "Confirm before delete",
		"Ask before uninstalling mods or deleting files", true));

		list.addView(sectionLabel(activity, "Storage"));
		list.addView(actionRow(activity, "Clear icon / store cache",
		"Frees temporary bitmaps and the store JSON cache", () -> {
			clearCache(activity);
			Toast.makeText(activity, "Cache cleared", Toast.LENGTH_SHORT).show();
		}));
		list.addView(actionRow(activity, "Clear splash cache",
		"Forces splash texts to re-download next launch", () -> {
			SplashTexts.clearCache(activity);
			Toast.makeText(activity, "Splash cache cleared", Toast.LENGTH_SHORT).show();
		}));
		list.addView(actionRow(activity, "Clear extracted libraries",
		"Removes copied libswordigo / openal so they are re-extracted next launch", () -> {
			File libRoot = new File(activity.getFilesDir(), "Extracted");
			boolean ok = FileManager.delete(libRoot);
			Toast.makeText(activity, ok ? "Libraries cleared" : "Nothing to clear", Toast.LENGTH_SHORT).show();
		}));
		list.addView(actionRow(activity, "Clear music extracts",
		"Deletes extracted soundtrack files under external files/music", () -> {
			File music = new File(activity.getExternalFilesDir(null), "music");
			boolean ok = FileManager.delete(music);
			Toast.makeText(activity, ok ? "Music cleared" : "Nothing to clear", Toast.LENGTH_SHORT).show();
		}));

		int Version = MainActivity.getVersion();
		list.addView(sectionLabel(activity, "About"));
		list.addView(infoRow(activity, "Version", Version + ""));
		list.addView(infoRow(activity, "Package", activity.getPackageName()));
		list.addView(actionRow(activity, "Open crash log",
		"If a previous crash was recorded you can export it or jump to Discord", () -> {
			File crash = new File(activity.getFilesDir(), "last_crash.log");
			if (!crash.exists() || crash.length() == 0) {
				Toast.makeText(activity, "No crash log present", Toast.LENGTH_SHORT).show();
				return;
			}
			showCrashLogActions(activity, crash);
		}));

		scroll.addView(list, new ScrollView.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		screen.addView(scroll, new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		return screen;
	}

	static boolean pref(Context ctx, String key, boolean def) {
		return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, def);
	}

	static void openUrl(Context ctx, String url) {
		try {
			ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
		} catch (Exception e) {
			Toast.makeText(ctx, "Couldn't open link", Toast.LENGTH_SHORT).show();
		}
	}

	/** Shared crash-log actions used by Settings and MainActivity. */
	static void showCrashLogActions(Activity activity, File crashLog) {
		new AlertDialog.Builder(activity)
		.setTitle("Crash log")
		.setMessage(crashLog.length() + " bytes · " + crashLog.getName()
		+ "\n\nExport the log, or open Discord to share it with the team.")
		.setPositiveButton("Export", (d, w) -> exportCrash(activity, crashLog))
		.setNeutralButton("Discord", (d, w) -> openUrl(activity, DISCORD_URL))
		.setNegativeButton("Delete", (d, w) -> crashLog.delete())
		.show();
	}

	static void exportCrash(Activity activity, File crashLog) {
		try {
			Uri uri = androidx.core.content.FileProvider.getUriForFile(
			activity, activity.getPackageName() + ".fileprovider", crashLog);
			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType("text/plain");
			intent.putExtra(Intent.EXTRA_STREAM, uri);
			intent.putExtra(Intent.EXTRA_SUBJECT, "Lawncher crash log");
			intent.putExtra(Intent.EXTRA_TEXT,
			"Lawncher crash log — feel free to drop this in Discord: " + DISCORD_URL);
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			activity.startActivity(Intent.createChooser(intent, "Export crash log"));
		} catch (Exception e) {
			Toast.makeText(activity, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}

	private static void clearCache(Activity activity) {
		File cache = activity.getCacheDir();
		if (cache == null) return;
		File[] kids = cache.listFiles();
		if (kids == null) return;
		for (File f : kids) {
			if (f.getName().startsWith("store_") || f.getName().equals("store_cache.json")
			|| f.getName().startsWith("mod_staging_")
			|| f.getName().startsWith("lawncher_update_")) {
				FileManager.delete(f);
			}
		}
	}

	@SuppressLint("SetTextI18n")
	private static View fpsPicker(Activity act, SharedPreferences prefs) {
		LinearLayout wrap = new LinearLayout(act);
		wrap.setOrientation(LinearLayout.VERTICAL);
		wrap.setBackground(Theme.rippleBackground(Theme.dp(act, 12), Theme.CARD));
		wrap.setPadding(Theme.dp(act, 16), Theme.dp(act, 14), Theme.dp(act, 16), Theme.dp(act, 14));
		LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		wp.bottomMargin = Theme.dp(act, 8);
		wrap.setLayoutParams(wp);

		TextView title = new TextView(act);
		title.setText("Target frame rate");
		title.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		wrap.addView(title);

		TextView sub = new TextView(act);
		sub.setText("Configures frame cap and screen refresh rate");
		sub.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		sub.setPadding(0, 0, 0, Theme.dp(act, 12));
		wrap.addView(sub);

		LinearLayout row = new LinearLayout(act);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER);

		int currentFps = prefs.getInt(KEY_TARGET_FPS, 60);
		int[] rates = {30, 60, 90, 120, 180};

		for (int i = 0; i < rates.length; i++) {
			final int rate = rates[i];
			boolean active = rate == currentFps;

			TextView btn = new TextView(act);
			btn.setText(rate + " FPS");
			btn.setGravity(Gravity.CENTER);
			btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
			btn.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
			btn.setTextColor(Color.parseColor(active ? Theme.ACCENT_DARK_TEXT : Theme.TEXT_MAIN));

			GradientDrawable shape = new GradientDrawable();
			shape.setCornerRadius(Theme.dp(act, 8));
			shape.setColor(Color.parseColor(active ? Theme.ACCENT_BLUE : Theme.BORDER));
			btn.setBackground(shape);

			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Theme.dp(act, 36), 1f);
			if (i < rates.length - 1) {
				lp.rightMargin = Theme.dp(act, 8);
			}
			btn.setLayoutParams(lp);

			btn.setOnClickListener(v -> {
				prefs.edit().putInt(KEY_TARGET_FPS, rate).apply();
				Sidebar.showScreen(SettingsScreen.build(act), null);
			});
			row.addView(btn);
		}
		wrap.addView(row);

		return wrap;
	}

	// ─── Theme picker ───────────────────────────────────────────────────

	private static View themePicker(Activity act) {
		LinearLayout wrap = new LinearLayout(act);
		wrap.setOrientation(LinearLayout.VERTICAL);
		wrap.setBackground(Theme.rippleBackground(Theme.dp(act, 12), Theme.CARD));
		wrap.setPadding(Theme.dp(act, 16), Theme.dp(act, 14), Theme.dp(act, 16), Theme.dp(act, 14));
		LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		wp.bottomMargin = Theme.dp(act, 8);
		wrap.setLayoutParams(wp);

		TextView title = new TextView(act);
		title.setText("Color theme");
		title.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		wrap.addView(title);

		TextView sub = new TextView(act);
		sub.setText("Applies immediately. Some screens refresh on next open.");
		sub.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		sub.setPadding(0, 0, 0, Theme.dp(act, 10));
		wrap.addView(sub);

		LinearLayout row = new LinearLayout(act);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);

		int current = Theme.currentPreset(act);
		String[] blues = {"#89B4FA", "#88C0D0", "#8BE9FD", "#C4A7E7", "#83A598", "#5865F2"};
		for (int i = 0; i < Theme.PRESET_NAMES.length; i++) {
			final int idx = i;
			View swatch = new View(act);
			GradientDrawable gd = new GradientDrawable();
			gd.setShape(GradientDrawable.OVAL);
			gd.setColor(Color.parseColor(blues[i % blues.length]));
			if (i == current) {
				gd.setStroke(Theme.dp(act, 3), Color.parseColor(Theme.TEXT_MAIN));
			}
			swatch.setBackground(gd);
			int size = Theme.dp(act, 32);
			LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(size, size);
			sp.rightMargin = Theme.dp(act, 10);
			swatch.setLayoutParams(sp);
			swatch.setOnClickListener(v -> {
				Theme.savePreset(act, idx);
				Toast.makeText(act, Theme.PRESET_NAMES[idx] + " applied", Toast.LENGTH_SHORT).show();
				Sidebar.showScreen(SettingsScreen.build(act), null);
			});
			row.addView(swatch);
		}
		wrap.addView(row);

		TextView name = new TextView(act);
		name.setText(Theme.PRESET_NAMES[current]);
		name.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		name.setPadding(0, Theme.dp(act, 8), 0, 0);
		wrap.addView(name);

		return wrap;
	}

	// ─── UI builders ────────────────────────────────────────────────────

	private static TextView sectionLabel(Activity act, String text) {
		TextView t = new TextView(act);
		t.setText(text.toUpperCase());
		t.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		t.setTypeface(null, Typeface.BOLD);
		t.setPadding(0, Theme.dp(act, 20), 0, Theme.dp(act, 8));
		return t;
	}

	private static View toggleRow(Activity act, SharedPreferences prefs, String key,
	                              String title, String subtitle, boolean def) {
		LinearLayout row = cardRow(act);

		LinearLayout textCol = new LinearLayout(act);
		textCol.setOrientation(LinearLayout.VERTICAL);
		textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		TextView titleView = new TextView(act);
		titleView.setText(title);
		titleView.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		textCol.addView(titleView);

		TextView sub = new TextView(act);
		sub.setText(subtitle);
		sub.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		textCol.addView(sub);

		row.addView(textCol);

		Switch sw = new Switch(act);
		sw.setChecked(prefs.getBoolean(key, def));
		sw.setOnCheckedChangeListener((btn, checked) -> {
			prefs.edit().putBoolean(key, checked).apply();
			if (KEY_KEEP_SCREEN_ON.equals(key)) {
				if (checked)
					act.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				else
					act.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			}
		});
		row.addView(sw);
		return row;
	}

	private static View actionRow(Activity act, String title, String subtitle, Runnable action) {
		LinearLayout row = cardRow(act);
		row.setOnClickListener(v -> action.run());

		LinearLayout textCol = new LinearLayout(act);
		textCol.setOrientation(LinearLayout.VERTICAL);
		textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		TextView titleView = new TextView(act);
		titleView.setText(title);
		titleView.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		textCol.addView(titleView);

		TextView sub = new TextView(act);
		sub.setText(subtitle);
		sub.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		textCol.addView(sub);

		row.addView(textCol);

		TextView chevron = new TextView(act);
		chevron.setText("›");
		chevron.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
		row.addView(chevron);
		return row;
	}

	private static View creditRow(Activity act, String handle, String role) {
		LinearLayout row = cardRow(act);

		LinearLayout textCol = new LinearLayout(act);
		textCol.setOrientation(LinearLayout.VERTICAL);
		textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		TextView h = new TextView(act);
		h.setText(handle);
		h.setTextColor(Color.parseColor(Theme.ACCENT_BLUE));
		h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		h.setTypeface(null, Typeface.BOLD);
		textCol.addView(h);

		TextView r = new TextView(act);
		r.setText(role);
		r.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		r.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		textCol.addView(r);

		row.addView(textCol);
		return row;
	}

	private static View infoRow(Activity act, String label, String value) {
		LinearLayout row = cardRow(act);

		TextView l = new TextView(act);
		l.setText(label);
		l.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		TextView v = new TextView(act);
		v.setText(value);
		v.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		v.setTypeface(Typeface.MONOSPACE);
		row.addView(v);
		return row;
	}

	private static LinearLayout cardRow(Activity act) {
		LinearLayout row = new LinearLayout(act);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(Theme.dp(act, 16), Theme.dp(act, 14), Theme.dp(act, 16), Theme.dp(act, 14));
		row.setBackground(Theme.rippleBackground(Theme.dp(act, 12), Theme.CARD));
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		p.bottomMargin = Theme.dp(act, 8);
		row.setLayoutParams(p);
		return row;
	}
}