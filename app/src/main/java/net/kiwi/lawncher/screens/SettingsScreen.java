package net.kiwi.lawncher.screens;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.L.SwordigoRuntime.PostFx;

import net.kiwi.lawncher.files.FileManager;
import net.kiwi.lawncher.ui.LauncherShell;
import net.kiwi.lawncher.ui.Screen;
import net.kiwi.lawncher.ui.Theme;
import net.kiwi.lawncher.util.Prefs;

import java.io.File;

/** Settings: appearance (accent), game options, storage and about. */
public class SettingsScreen implements Screen {

	private LinearLayout root;
	private TextView cacheValue;

	@Override
	public View build() {
		if (root == null) {
			root = new LinearLayout(Theme.activity);
			root.setOrientation(LinearLayout.VERTICAL);
		}
		root.removeAllViews();

		ScrollView scroll = new ScrollView(Theme.activity);
		LinearLayout col = new LinearLayout(Theme.activity);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(Theme.dp(20), Theme.dp(6), Theme.dp(20), Theme.dp(24));

		// ---- Appearance ----
		col.addView(sectionLabel("APPEARANCE"), matchWrap(0, 0, 0, Theme.dp(10)));

		LinearLayout swatches = new LinearLayout(Theme.activity);
		swatches.setOrientation(LinearLayout.HORIZONTAL);
		swatches.setPadding(Theme.dp(14), Theme.dp(14), Theme.dp(14), Theme.dp(14));
		swatches.setBackground(Theme.rounded(Theme.SURFACE, Theme.dp(16), Theme.BORDER, 1));
		for (int i = 0; i < Theme.accentCount(); i++) {
			final int idx = i;
			View swatch = new View(Theme.activity);
			int[] pair = Theme.accentPair(idx);
			boolean sel = idx == Theme.accentIndex();
			swatch.setBackground(Theme.rounded(pair[0], Theme.dp(16), sel ? Theme.TEXT : 0, sel ? 2 : 0));
			swatch.setClickable(true);
			swatch.setOnClickListener(v -> {
				Prefs.putInt("accent", idx);
				LauncherShell.rebuildForAppearance();
			});
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(32), Theme.dp(32));
			lp.rightMargin = Theme.dp(12);
			swatches.addView(swatch, lp);
		}
		col.addView(swatches, matchWrap(0, 0, 0, Theme.dp(16)));

		addSettingRow(col, "Accent color", "Pick a launcher-wide theme accent \u2014 applies instantly", () -> { });

		// ---- Game ----
		col.addView(sectionLabel("GAME"), matchWrap(0, Theme.dp(8), 0, Theme.dp(10)));
		row(col, "Resolution", Prefs.getString("game.res", "Auto"),
				v -> promptResolution());
		row(col, "Memory allocation", Prefs.getInt("game.mem", 1024) + " MB",
				v -> promptMemory());
		row(col, "Log level", Prefs.getString("game.loglevel", "Info"),
				v -> promptLogLevel());

		// ---- PostFX ----
		col.addView(sectionLabel("POSTFX"), matchWrap(0, Theme.dp(8), 0, Theme.dp(10)));

		LinearLayout fxRow = new LinearLayout(Theme.activity);
		fxRow.setOrientation(LinearLayout.HORIZONTAL);
		fxRow.setPadding(Theme.dp(14), Theme.dp(14), Theme.dp(14), Theme.dp(14));
		fxRow.setBackground(Theme.rounded(Theme.SURFACE, Theme.dp(16), Theme.BORDER, 1));
		for (int i = 0; i < PostFx.PRESET_COUNT; i++) {
			final int idx = i;
			boolean sel = idx == PostFx.preset();
			TextView chip = Theme.text(12, sel ? 0xFF0A0E1A : Theme.TEXT, true);
			chip.setText(PostFx.PRESET_NAMES[i]);
			chip.setGravity(Gravity.CENTER);
			chip.setPadding(Theme.dp(10), Theme.dp(6), Theme.dp(10), Theme.dp(6));
			chip.setBackground(sel
					? Theme.gradient(Theme.accentStart(), Theme.accentEnd(), Theme.dp(12))
					: Theme.rounded(Theme.SURFACE_ALT, Theme.dp(12), Theme.BORDER, 1));
			chip.setClickable(true);
			chip.setOnClickListener(v -> {
				PostFx.setPreset(idx);
				rebuildSelf();
			});
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.rightMargin = Theme.dp(8);
			fxRow.addView(chip, lp);
		}
		col.addView(fxRow, matchWrap(0, 0, 0, Theme.dp(6)));

		addSettingRow(col, "Post-processing",
				"Screen-space filters over the game \u2014 applies on next launch", () -> { });

		// ---- Storage ----
		col.addView(sectionLabel("STORAGE"), matchWrap(0, Theme.dp(8), 0, Theme.dp(10)));
		TextView dataPath = Theme.caption(11, Theme.TEXT_FAINT);
		dataPath.setText(Theme.activity.getExternalFilesDir(null) == null
				? "" : Theme.activity.getExternalFilesDir(null).getAbsolutePath());
		dataPath.setTypeface(android.graphics.Typeface.MONOSPACE);
		col.addView(dataPath, matchWrap(0, 0, 0, Theme.dp(6)));
		cacheValue = Theme.caption(11, Theme.TEXT_DIM);
		col.addView(cacheValue, matchWrap(0, 0, 0, Theme.dp(12)));
		TextView clearCache = actionPill("CLEAR CACHE", Theme.DANGER);
		clearCache.setOnClickListener(v -> clearCache());
		col.addView(clearCache, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, Theme.dp(36)));

		// ---- About ----
		col.addView(sectionLabel("ABOUT"), matchWrap(0, Theme.dp(8), 0, Theme.dp(10)));
		TextView about = Theme.text(13, Theme.TEXT_DIM, false);
		about.setText("Raijin's Lawncher \u00B7 v1.0\n"
				+ "The first dedicated Swordigo launcher for Android.\n\n"
				+ "Independent project \u2014 not affiliated with the Swordigo developers. "
				+ "The launcher contains no original game assets.");
		about.setLineSpacing(Theme.dp(3), 1f);
		col.addView(about, matchWrap(0, 0, 0, Theme.dp(16)));

		TextView storeNote = Theme.caption(10, Theme.TEXT_FAINT);
		storeNote.setText("Store catalog: "
				+ (net.kiwi.lawncher.store.StoreManager.CATALOG_URL.isEmpty()
						? "bundled demo" : "remote"));
		col.addView(storeNote, matchWrap(0, 0, 0, Theme.dp(16)));

		TextView billing = Theme.caption(10, Theme.TEXT_FAINT);
		billing.setText("Billing provider: "
				+ net.kiwi.lawncher.billing.BillingManager.get().provider().name());
		col.addView(billing, matchWrap(0, 0, 0, 0));

		scroll.addView(col, new ScrollView.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		root.addView(scroll, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		return root;
	}

	@Override
	public void onShown() {
		if (cacheValue != null) {
			File cache = Theme.activity.getCacheDir();
			cacheValue.setText("Cache: " + FileManager.humanSize(FileManager.dirSize(cache)));
		}
	}

	@Override
	public void onHidden() {
	}

	@Override
	public boolean onBack() {
		return false;
	}

	// ---- rows ----

	private TextView row(LinearLayout col, String label, String value, View.OnClickListener onClick) {
		LinearLayout row = new LinearLayout(Theme.activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setClickable(true);
		row.setFocusable(true);
		row.setPadding(Theme.dp(14), Theme.dp(13), Theme.dp(14), Theme.dp(13));
		row.setBackground(Theme.rounded(Theme.SURFACE, Theme.dp(14), Theme.BORDER, 1));
		TextView l = Theme.text(14, Theme.TEXT, false);
		l.setText(label);
		row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		TextView v = Theme.caption(11, Theme.TEXT_DIM);
		v.setText(value);
		row.addView(v);
		TextView chevron = Theme.text(14, Theme.TEXT_FAINT, false);
		chevron.setText("\u203A");
		chevron.setPadding(Theme.dp(8), 0, 0, 0);
		row.addView(chevron);
		row.setOnClickListener(onClick);
		LinearLayout.LayoutParams lp = matchWrap(0, 0, 0, Theme.dp(10));
		col.addView(row, lp);
		return v;
	}

	private void addSettingRow(LinearLayout col, String label, String value, Runnable action) {
		row(col, label, value, v -> action.run());
	}

	private TextView actionPill(String label, int color) {
		TextView t = Theme.caption(10, color);
		t.setText(label);
		t.setGravity(Gravity.CENTER);
		t.setPadding(Theme.dp(14), 0, Theme.dp(14), 0);
		t.setBackground(Theme.rounded(0x14FF5C7A, Theme.dp(9), 0, 0));
		t.setClickable(true);
		return t;
	}

	// ---- actions ----

	private void promptResolution() {
		final String[] options = {"Auto", "1080p", "720p", "Original"};
		String current = Prefs.getString("game.res", "Auto");
		int checked = 0;
		for (int i = 0; i < options.length; i++) if (options[i].equals(current)) checked = i;
		new AlertDialog.Builder(Theme.activity)
				.setTitle("Resolution")
				.setSingleChoiceItems(options, checked, (dialog, which) -> {
					Prefs.putString("game.res", options[which]);
					dialog.dismiss();
					Theme.activity.runOnUiThread(this::rebuildSelf);
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void promptLogLevel() {
		final String[] options = {"Error", "Warn", "Info", "Debug"};
		String current = Prefs.getString("game.loglevel", "Info");
		int checked = 0;
		for (int i = 0; i < options.length; i++) if (options[i].equals(current)) checked = i;
		new AlertDialog.Builder(Theme.activity)
				.setTitle("Log level")
				.setSingleChoiceItems(options, checked, (dialog, which) -> {
					Prefs.putString("game.loglevel", options[which]);
					dialog.dismiss();
					Theme.activity.runOnUiThread(this::rebuildSelf);
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void promptMemory() {
		LinearLayout content = new LinearLayout(Theme.activity);
		content.setOrientation(LinearLayout.VERTICAL);
		content.setPadding(Theme.dp(24), Theme.dp(12), Theme.dp(24), Theme.dp(8));
		final SeekBar bar = new SeekBar(Theme.activity);
		bar.setMax(14); // 512 .. 4096 MB
		bar.setProgress((Prefs.getInt("game.mem", 1024) - 512) / 256);
		bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Theme.accentStart()));
		content.addView(bar);
		final TextView memText = Theme.text(14, Theme.TEXT, true);
		memText.setGravity(Gravity.CENTER);
		memText.setText(512 + bar.getProgress() * 256 + " MB");
		content.addView(memText, matchWrap(0, Theme.dp(8), 0, 0));
		bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				memText.setText(512 + progress * 256 + " MB");
			}
			@Override public void onStartTrackingTouch(SeekBar seekBar) {}
			@Override public void onStopTrackingTouch(SeekBar seekBar) {}
		});
		new AlertDialog.Builder(Theme.activity)
				.setTitle("Memory allocation")
				.setView(content)
				.setPositiveButton("Save", (dialog, which) -> {
					Prefs.putInt("game.mem", 512 + bar.getProgress() * 256);
					rebuildSelf();
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void clearCache() {
		File cache = Theme.activity.getCacheDir();
		long freed = FileManager.dirSize(cache);
		FileManager.deleteRecursive(cache);
		if (!cache.exists()) cache.mkdirs();
		Toast.makeText(Theme.activity, "Freed " + FileManager.humanSize(freed), Toast.LENGTH_SHORT).show();
		onShown();
	}

	private void rebuildSelf() {
		LauncherShell.rebuildForAppearance();
	}

	// ---- helpers ----

	private static TextView sectionLabel(String text) {
		TextView label = Theme.caption(11, Theme.TEXT_FAINT);
		label.setText(text);
		return label;
	}

	private static LinearLayout.LayoutParams matchWrap(int l, int t, int r, int b) {
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		p.setMargins(l, t, r, b);
		return p;
	}
}
