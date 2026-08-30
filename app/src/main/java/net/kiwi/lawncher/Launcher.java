package net.kiwi.lawncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;

import androidx.core.content.FileProvider;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Locale;

/**
 * Launcher UI only. Filesystem work lives in ModManager.
 */
public class Launcher {

	private static Activity activity;
	private static ViewGroup rootContainer;

	public static final int FILE_PICKER_REQUEST_CODE = 9999;
	public static final int REQ_IMPORT_SAVES = 10001;
	private static ModManager.ModInfo pendingSaveImport;

	private static final int GRID_COLUMNS = 2;

	private static FrameLayout topBarContainer;
	private static FrameLayout contentContainer;
	private static FrameLayout bottomBarContainer;

	private static View screenRoot;
	private static GridLayout modGrid;
	private static TextView modCountLabel;
	private static View sGearButton;

	private static ModManager.ModInfo currentMod;

	public static void init(Activity act, ViewGroup view) {
		activity = act;
		rootContainer = view;
		Theme.setActivity(act);
		act.runOnUiThread(Launcher::buildUI);
	}

	/** Full UI rebuild so Theme colors apply to launcher + grid. */
	public static void recolor() {
		if (activity == null || rootContainer == null) return;
		buildUI();
	}

	public static void initGameButtons() {
		runOnUi(() -> {
			if (activity == null) return;
			try {
				ImageView gear = new ImageView(activity);
				gear.setImageResource(R.drawable.ic_manage);
				gear.setColorFilter(Color.WHITE);
				gear.setAlpha(0.5f);
				gear.setPadding(4, 4, 4, 4);
				gear.setOnClickListener(v -> MainActivity.returnToLauncher());

				int size = Theme.dp(activity, 28);
				FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
				params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
				params.topMargin = 0;

				activity.addContentView(gear, params);
				sGearButton = gear;
			} catch (Exception ignored) {}
		});
	}

	public static void destroyGameButtons() {
		runOnUi(() -> {
			if (sGearButton != null) {
				try {
					ViewGroup parent = (ViewGroup) sGearButton.getParent();
					if (parent != null)
						parent.removeView(sGearButton);
				} catch (Exception ignored) {}
				sGearButton = null;
			}
		});
	}

	public static String currentMod() {
		return currentMod != null && currentMod.id != null ? currentMod.id : "";
	}

	private static void buildUI() {
		if (rootContainer == null) return;
		rootContainer.removeAllViews();

		LinearLayout root = new LinearLayout(activity);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(Color.parseColor(Theme.BG));
		root.setClipChildren(false);
		root.setClipToPadding(false);

		Theme.attachToRoot(root);

		topBarContainer = new FrameLayout(activity);
		root.addView(topBarContainer, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		contentContainer = new FrameLayout(activity);
		root.addView(contentContainer, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		bottomBarContainer = new FrameLayout(activity);
		root.addView(bottomBarContainer, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		screenRoot = root;
		rootContainer.addView(Sidebar.install(activity, root));

		Sidebar.setItems(
				new Sidebar.Item(R.drawable.ic_mods, "Mods", () -> Sidebar.showScreen(screenRoot, null)),
				new Sidebar.Item(R.drawable.ic_store, "Store", () -> Sidebar.showScreen(ModStoreScreen.build(activity), null)),
				new Sidebar.Item(R.drawable.ic_settings, "Settings", () -> Sidebar.showScreen(SettingsScreen.build(activity), null)),
				new Sidebar.Item(R.drawable.ic_files, "Files", () -> Sidebar.showScreen(FilesScreen.build(activity), null)),
				new Sidebar.Item(R.drawable.ic_logcat, "Logcat", () -> Sidebar.showScreen(LogcatScreen.build(activity), LogcatScreen::stop))
		);

		renderScreen();
	}

	private static void renderScreen() {
		topBarContainer.removeAllViews();
		contentContainer.removeAllViews();
		bottomBarContainer.removeAllViews();

		if (currentMod == null) {
			topBarContainer.addView(buildGridTopBar());
			contentContainer.addView(buildModGridSection());
			bottomBarContainer.addView(buildGridBottomBar());
			refreshModGrid();
		} else {
			topBarContainer.addView(buildDetailTopBar(currentMod));
			contentContainer.addView(buildModDetailSection(currentMod));
			bottomBarContainer.addView(buildDetailBottomBar(currentMod));
		}
	}

	private static void openMod(ModManager.ModInfo mod) {
		currentMod = mod;
		renderScreen();
	}

	private static void closeMod() {
		currentMod = null;
		renderScreen();
	}

	private static LinearLayout buildGridTopBar() {
		LinearLayout bar = new LinearLayout(activity);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.CENTER_VERTICAL);
		bar.setClipChildren(false);
		bar.setClipToPadding(false);
		bar.setPadding(Theme.dp(activity, 12), Theme.dp(activity, 28), Theme.dp(activity, 16), Theme.dp(activity, 16));

		ImageView hamburger = new ImageView(activity);
		hamburger.setImageResource(R.drawable.ic_sort);
		hamburger.setColorFilter(Color.parseColor(Theme.TEXT_MAIN));
		hamburger.setBackground(Theme.circleBackground(Theme.CARD));
		hamburger.setContentDescription("Open menu");
		int pad = Theme.dp(activity, 8);
		hamburger.setPadding(pad, pad, pad, pad);
		hamburger.setOnClickListener(v -> Sidebar.toggle());
		LinearLayout.LayoutParams hbParams = new LinearLayout.LayoutParams(Theme.dp(activity, 40), Theme.dp(activity, 40));
		hbParams.rightMargin = Theme.dp(activity, 12);
		bar.addView(hamburger, hbParams);

		LinearLayout titleCol = new LinearLayout(activity);
		titleCol.setOrientation(LinearLayout.VERTICAL);
		titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		TextView title = new TextView(activity);
		title.setText("Kiwi Lawncher");
		title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
		title.setTypeface(null, Typeface.BOLD);
		title.setTextColor(Color.parseColor(Theme.ACCENT_GREEN));
		titleCol.addView(title);

		if (SettingsScreen.pref(activity, SettingsScreen.KEY_SHOW_SPLASH, true)) {
			TextView splash = new TextView(activity);
			splash.setText(SplashTexts.pick(activity));
			splash.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
			splash.setTextColor(Color.parseColor(Theme.TEXT_DIM));
			splash.setMaxLines(2);
			splash.setPadding(0, Theme.dp(activity, 2), 0, 0);
			// Soft italic / yellow-ish nod to MC splash
			splash.setTypeface(null, Typeface.ITALIC);
			splash.setTextColor(Color.parseColor(Theme.ACCENT_BLUE));
			splash.setAlpha(0.9f);
			titleCol.addView(splash);
		}

		bar.addView(titleCol);

		return bar;
	}

	private static LinearLayout buildDetailTopBar(ModManager.ModInfo mod) {
		LinearLayout bar = new LinearLayout(activity);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.CENTER_VERTICAL);
		bar.setPadding(Theme.dp(activity, 12), Theme.dp(activity, 20), Theme.dp(activity, 16), Theme.dp(activity, 16));

		ImageView back = new ImageView(activity);
		back.setImageResource(R.drawable.ic_revert);
		back.setColorFilter(Color.parseColor(Theme.TEXT_MAIN));
		back.setBackground(Theme.circleBackground(Theme.CARD));
		back.setContentDescription("Back to mod list");
		int pad = Theme.dp(activity, 8);
		back.setPadding(pad, pad, pad, pad);
		back.setOnClickListener(v -> closeMod());
		LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(Theme.dp(activity, 40), Theme.dp(activity, 40));
		backParams.rightMargin = Theme.dp(activity, 12);
		bar.addView(back, backParams);

		TextView name = new TextView(activity);
		name.setText(mod.name);
		name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
		name.setTypeface(null, Typeface.BOLD);
		name.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		name.setMaxLines(1);
		name.setEllipsize(TextUtils.TruncateAt.END);
		bar.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		ImageView trash = new ImageView(activity);
		trash.setImageResource(R.drawable.ic_delete);
		trash.setColorFilter(Color.parseColor(Theme.ACCENT_RED));
		trash.setBackground(Theme.circleBackground(Theme.CARD));
		trash.setContentDescription("Uninstall " + mod.name);
		trash.setPadding(pad, pad, pad, pad);
		trash.setOnClickListener(v -> confirmDeleteMod(mod));
		bar.addView(trash, new LinearLayout.LayoutParams(Theme.dp(activity, 40), Theme.dp(activity, 40)));

		return bar;
	}

	private static LinearLayout buildModGridSection() {
		LinearLayout section = new LinearLayout(activity);
		section.setOrientation(LinearLayout.VERTICAL);
		section.setPadding(Theme.dp(activity, 20), Theme.dp(activity, 8), Theme.dp(activity, 20), Theme.dp(activity, 8));

		LinearLayout labelRow = new LinearLayout(activity);
		labelRow.setOrientation(LinearLayout.HORIZONTAL);
		labelRow.setGravity(Gravity.CENTER_VERTICAL);

		TextView label = new TextView(activity);
		label.setText("Installed Mods");
		label.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		label.setTypeface(null, Typeface.BOLD);
		labelRow.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		modCountLabel = new TextView(activity);
		modCountLabel.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		modCountLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		labelRow.addView(modCountLabel);

		section.addView(labelRow, matchWrapParams(0, 0, 0, Theme.dp(activity, 10)));

		ScrollView scroll = new ScrollView(activity);
		modGrid = new GridLayout(activity);
		modGrid.setColumnCount(GRID_COLUMNS);
		scroll.addView(modGrid, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		section.addView(scroll, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
		return section;
	}

	private static LinearLayout buildGridBottomBar() {
		LinearLayout bar = new LinearLayout(activity);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setPadding(Theme.dp(activity, 20), Theme.dp(activity, 12), Theme.dp(activity, 20), Theme.dp(activity, 24));

		Button btnInstall = styledButton("Install Mod", Theme.ACCENT_BLUE, Theme.ACCENT_DARK_TEXT);
		btnInstall.setOnClickListener(v -> openFilePicker());
		bar.addView(btnInstall, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(activity, 52)));

		return bar;
	}

	public static void refreshModGrid() {
		if (modGrid == null) return;
		modGrid.removeAllViews();

		List<ModManager.ModInfo> mods = ModManager.listInstalledMods(activity);
		modCountLabel.setText(mods.isEmpty() ? "" : String.valueOf(mods.size()));

		if (mods.isEmpty()) {
			modGrid.addView(buildEmptyState());
			return;
		}

		for (ModManager.ModInfo mod : mods)
			modGrid.addView(buildModCell(mod));
	}

	private static TextView buildEmptyState() {
		TextView empty = new TextView(activity);
		empty.setText("No mods installed yet.\nTap \u201cInstall Mod\u201d to add your first one.");
		empty.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		empty.setGravity(Gravity.CENTER);
		empty.setPadding(0, Theme.dp(activity, 40), 0, Theme.dp(activity, 40));

		GridLayout.LayoutParams params = new GridLayout.LayoutParams();
		params.width = 0;
		params.columnSpec = GridLayout.spec(0, GRID_COLUMNS, 1f);
		empty.setLayoutParams(params);
		return empty;
	}

	private static View buildModCell(ModManager.ModInfo mod) {
		boolean compact = SettingsScreen.pref(activity, SettingsScreen.KEY_COMPACT_MODS, false);
		int pad = Theme.dp(activity, compact ? 6 : 10);
		int vpad = Theme.dp(activity, compact ? 8 : 14);
		int iconSize = Theme.dp(activity, compact ? 64 : 84);
		int letter = Theme.dp(activity, compact ? 24 : 30);
		LinearLayout cell = new LinearLayout(activity);
		cell.setOrientation(LinearLayout.VERTICAL);
		cell.setGravity(Gravity.CENTER_HORIZONTAL);
		cell.setPadding(pad, vpad, pad, vpad);
		cell.setClickable(true);
		cell.setFocusable(true);
		cell.setBackground(Theme.rippleBackground(Theme.dp(activity, 18), Theme.BG));

		FrameLayout iconWrap = new FrameLayout(activity);
		GradientDrawable iconBg = new GradientDrawable();
		iconBg.setColor(Color.parseColor(Theme.CARD));
		iconBg.setCornerRadius(Theme.dp(activity, 18));
		iconBg.setStroke(Theme.dp(activity, 1), Color.parseColor(Theme.BORDER));
		iconWrap.setBackground(iconBg);
		iconWrap.addView(buildIconContent(mod, letter),
				new FrameLayout.LayoutParams(iconSize, iconSize));
		cell.addView(iconWrap, new LinearLayout.LayoutParams(iconSize, iconSize));

		TextView name = new TextView(activity);
		name.setText(mod.name);
		name.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		name.setMaxLines(2);
		name.setEllipsize(TextUtils.TruncateAt.END);
		name.setGravity(Gravity.CENTER_HORIZONTAL);
		LinearLayout.LayoutParams nameParams =
				new LinearLayout.LayoutParams(Theme.dp(activity, compact ? 88 : 100), ViewGroup.LayoutParams.WRAP_CONTENT);
		nameParams.topMargin = Theme.dp(activity, compact ? 4 : 8);
		cell.addView(name, nameParams);

		TextView version = new TextView(activity);
		version.setText("v" + mod.version);
		version.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
		cell.addView(version, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		cell.setOnClickListener(v -> openMod(mod));
		cell.setOnLongClickListener(v -> {
			confirmDeleteMod(mod);
			return true;
		});

		GridLayout.LayoutParams params = new GridLayout.LayoutParams();
		params.width = 0;
		params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
		params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
		params.setMargins(Theme.dp(activity, 4), Theme.dp(activity, 4), Theme.dp(activity, 4), Theme.dp(activity, 4));
		cell.setLayoutParams(params);
		return cell;
	}

	private static ScrollView buildModDetailSection(ModManager.ModInfo mod) {
		ScrollView scroll = new ScrollView(activity);
		scroll.setFillViewport(true);

		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);
		content.setPadding(Theme.dp(activity, 20), Theme.dp(activity, 8),
				Theme.dp(activity, 20), Theme.dp(activity, 28));

		// ── Hero ───────────────────────────────────────────────────
		LinearLayout hero = new LinearLayout(activity);
		hero.setOrientation(LinearLayout.HORIZONTAL);
		hero.setGravity(Gravity.CENTER_VERTICAL);
		hero.setPadding(Theme.dp(activity, 16), Theme.dp(activity, 16),
				Theme.dp(activity, 16), Theme.dp(activity, 16));
		hero.setBackground(Theme.rippleBackground(Theme.dp(activity, 16), Theme.CARD));

		FrameLayout iconWrap = new FrameLayout(activity);
		GradientDrawable iconBg = new GradientDrawable();
		iconBg.setColor(Color.parseColor(Theme.BG));
		iconBg.setCornerRadius(Theme.dp(activity, 18));
		iconBg.setStroke(Theme.dp(activity, 1), Color.parseColor(Theme.BORDER));
		iconWrap.setBackground(iconBg);
		iconWrap.addView(buildIconContent(mod, Theme.dp(activity, 32)),
				new FrameLayout.LayoutParams(Theme.dp(activity, 72), Theme.dp(activity, 72)));
		hero.addView(iconWrap, new LinearLayout.LayoutParams(Theme.dp(activity, 72), Theme.dp(activity, 72)));

		LinearLayout heroText = new LinearLayout(activity);
		heroText.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams ht = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		ht.leftMargin = Theme.dp(activity, 14);
		heroText.setLayoutParams(ht);

		TextView name = new TextView(activity);
		name.setText(mod.name != null ? mod.name : "Unknown");
		name.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
		name.setTypeface(null, Typeface.BOLD);
		name.setMaxLines(2);
		heroText.addView(name);

		TextView ver = new TextView(activity);
		ver.setText("v" + (mod.version != null ? mod.version : "?"));
		ver.setTextColor(Color.parseColor(Theme.ACCENT_BLUE));
		ver.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		ver.setPadding(0, Theme.dp(activity, 2), 0, 0);
		heroText.addView(ver);

		if (mod.id != null && !mod.id.isEmpty()) {
			TextView id = new TextView(activity);
			id.setText(mod.id);
			id.setTextColor(Color.parseColor(Theme.TEXT_DIM));
			id.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
			id.setTypeface(Typeface.MONOSPACE);
			id.setPadding(0, Theme.dp(activity, 4), 0, 0);
			id.setMaxLines(1);
			id.setEllipsize(TextUtils.TruncateAt.MIDDLE);
			heroText.addView(id);
		}
		hero.addView(heroText);
		content.addView(hero, matchWrapParams(0, 0, 0, Theme.dp(activity, 16)));

		// ── About (first) ──────────────────────────────────────────
		String desc = mod.description;
		if (desc != null && !desc.trim().isEmpty()) {
			content.addView(sectionLabel("About"));
			LinearLayout aboutCard = new LinearLayout(activity);
			aboutCard.setOrientation(LinearLayout.VERTICAL);
			aboutCard.setPadding(Theme.dp(activity, 14), Theme.dp(activity, 12),
					Theme.dp(activity, 14), Theme.dp(activity, 12));
			aboutCard.setBackground(Theme.rippleBackground(Theme.dp(activity, 12), Theme.CARD));
			TextView descView = new TextView(activity);
			descView.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
			descView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
			descView.setLineSpacing(Theme.dp(activity, 3), 1.2f);
			descView.setMovementMethod(LinkMovementMethod.getInstance());
			descView.setText(renderMarkdown(desc.trim()));
			aboutCard.addView(descView);
			content.addView(aboutCard, matchWrapParams(0, Theme.dp(activity, 6), 0, Theme.dp(activity, 16)));
		}

		// ── Screenshots ────────────────────────────────────────────
		List<File> shots = mod.screenshotFiles();
		if (!shots.isEmpty()) {
			content.addView(sectionLabel("Screenshots"));
			content.addView(buildScreenshotGallery(shots),
					matchWrapParams(0, Theme.dp(activity, 8), 0, Theme.dp(activity, 16)));
		}

		content.setLayoutParams(new ScrollView.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		scroll.addView(content);
		return scroll;
	}

	/** Compact icon + label chip used on the mod detail manage row. */
	private static View iconAction(int iconRes, String label, String color, Runnable action) {
		LinearLayout col = new LinearLayout(activity);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setGravity(Gravity.CENTER_HORIZONTAL);
		col.setClickable(true);
		col.setFocusable(true);
		col.setPadding(Theme.dp(activity, 8), Theme.dp(activity, 10),
				Theme.dp(activity, 8), Theme.dp(activity, 10));
		col.setBackground(Theme.rippleBackground(Theme.dp(activity, 14), Theme.CARD));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		lp.leftMargin = Theme.dp(activity, 4);
		lp.rightMargin = Theme.dp(activity, 4);
		col.setLayoutParams(lp);

		ImageView icon = new ImageView(activity);
		icon.setImageResource(iconRes);
		icon.setColorFilter(Color.parseColor(color));
		col.addView(icon, new LinearLayout.LayoutParams(Theme.dp(activity, 26), Theme.dp(activity, 26)));

		TextView t = new TextView(activity);
		t.setText(label);
		t.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
		t.setGravity(Gravity.CENTER);
		t.setPadding(0, Theme.dp(activity, 6), 0, 0);
		col.addView(t);

		col.setOnClickListener(v -> action.run());
		return col;
	}

	/**
	 * Tiny markdown subset for mod descriptions:
	 * # / ## headings, **bold**, *italic*, `code`, [label](url), - lists, blank lines.
	 */
	private static CharSequence renderMarkdown(String src) {
		if (src == null) return "";
		String[] lines = src.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
		SpannableStringBuilder out = new SpannableStringBuilder();
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			if (i > 0) out.append('\n');

			float scale = 1f;
			boolean boldLine = false;
			String body = line;

			if (body.startsWith("### ")) {
				body = body.substring(4);
				scale = 1.1f;
				boldLine = true;
			} else if (body.startsWith("## ")) {
				body = body.substring(3);
				scale = 1.2f;
				boldLine = true;
			} else if (body.startsWith("# ")) {
				body = body.substring(2);
				scale = 1.35f;
				boldLine = true;
			} else if (body.matches("^[-*]\\s+.*")) {
				body = "• " + body.replaceFirst("^[-*]\\s+", "");
			}

			int lineStart = out.length();
			appendInlineMarkdown(out, body);
			int lineEnd = out.length();

			if (boldLine && lineEnd > lineStart) {
				out.setSpan(new StyleSpan(Typeface.BOLD), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
			if (scale != 1f && lineEnd > lineStart) {
				out.setSpan(new RelativeSizeSpan(scale), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
		}
		return out;
	}

	private static void appendInlineMarkdown(SpannableStringBuilder out, String body) {
		// Walk and apply **bold**, *italic*, `code`, [text](url)
		int i = 0;
		while (i < body.length()) {
			// link [text](url)
			if (body.charAt(i) == '[') {
				int close = body.indexOf(']', i + 1);
				if (close > i && close + 1 < body.length() && body.charAt(close + 1) == '(') {
					int urlEnd = body.indexOf(')', close + 2);
					if (urlEnd > close) {
						String label = body.substring(i + 1, close);
						String url = body.substring(close + 2, urlEnd);
						int start = out.length();
						out.append(label);
						out.setSpan(new URLSpan(url), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
						out.setSpan(new ForegroundColorSpan(Color.parseColor(Theme.ACCENT_BLUE)),
								start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
						i = urlEnd + 1;
						continue;
					}
				}
			}
			// **bold**
			if (i + 1 < body.length() && body.charAt(i) == '*' && body.charAt(i + 1) == '*') {
				int end = body.indexOf("**", i + 2);
				if (end > i) {
					int start = out.length();
					out.append(body.substring(i + 2, end));
					out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					i = end + 2;
					continue;
				}
			}
			// *italic*
			if (body.charAt(i) == '*') {
				int end = body.indexOf('*', i + 1);
				if (end > i) {
					int start = out.length();
					out.append(body.substring(i + 1, end));
					out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					i = end + 1;
					continue;
				}
			}
			// `code`
			if (body.charAt(i) == '`') {
				int end = body.indexOf('`', i + 1);
				if (end > i) {
					int start = out.length();
					out.append(body.substring(i + 1, end));
					out.setSpan(new TypefaceSpanSafe(), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					out.setSpan(new ForegroundColorSpan(Color.parseColor(Theme.ACCENT_GREEN)),
							start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					i = end + 1;
					continue;
				}
			}
			out.append(body.charAt(i));
			i++;
		}
	}

	/** Monospace span without requiring android.text.style.TypefaceSpan API quirks. */
	private static class TypefaceSpanSafe extends android.text.style.MetricAffectingSpan {
		@Override
		public void updateDrawState(android.text.TextPaint tp) {
			tp.setTypeface(Typeface.MONOSPACE);
		}
		@Override
		public void updateMeasureState(android.text.TextPaint tp) {
			tp.setTypeface(Typeface.MONOSPACE);
		}
	}

	private static TextView sectionLabel(String text) {
		TextView label = new TextView(activity);
		label.setText(text);
		label.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		label.setTypeface(null, Typeface.BOLD);
		return label;
	}

	private static HorizontalScrollView buildScreenshotGallery(List<File> screenshots) {
		HorizontalScrollView scroll = new HorizontalScrollView(activity);
		scroll.setHorizontalScrollBarEnabled(true);
		scroll.setSmoothScrollingEnabled(true);
		scroll.setPadding(0, Theme.dp(activity, 4), 0, Theme.dp(activity, 4));
		scroll.setClipToPadding(false);

		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setPadding(0, 0, Theme.dp(activity, 8), 0);

		for (File file : screenshots) {
			Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
			if (bitmap == null) continue;

			FrameLayout card = new FrameLayout(activity);
			GradientDrawable frame = new GradientDrawable();
			frame.setCornerRadius(Theme.dp(activity, 14));
			frame.setColor(Color.parseColor(Theme.CARD));
			frame.setStroke(Theme.dp(activity, 1), Color.parseColor(Theme.BORDER));
			card.setBackground(frame);
			card.setClipToOutline(true);

			ImageView shot = new ImageView(activity);
			shot.setImageBitmap(bitmap);
			shot.setScaleType(ImageView.ScaleType.CENTER_CROP);
			card.addView(shot, new FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

			final Bitmap full = bitmap;
			card.setOnClickListener(v -> showScreenshotFullscreen(full, file.getName()));

			LinearLayout.LayoutParams shotParams = new LinearLayout.LayoutParams(
					Theme.dp(activity, 220), Theme.dp(activity, 132));
			shotParams.rightMargin = Theme.dp(activity, 12);
			row.addView(card, shotParams);
		}

		scroll.addView(row);
		return scroll;
	}

	private static void showScreenshotFullscreen(Bitmap bmp, String title) {
		if (activity == null || bmp == null) return;
		Dialog d = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar);
		LinearLayout root = new LinearLayout(activity);
		Theme.attachToRoot(root);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(Color.parseColor(Theme.BG));
		LinearLayout header = new LinearLayout(activity);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setPadding(Theme.dp(activity, 16), Theme.dp(activity, 14), Theme.dp(activity, 12), Theme.dp(activity, 14));
		TextView t = new TextView(activity);
		t.setText(title != null ? title : "Screenshot");
		t.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		t.setTypeface(null, Typeface.BOLD);
		t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		header.addView(t);
		TextView close = new TextView(activity);
		close.setText("×");
		close.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
		close.setPadding(Theme.dp(activity, 12), 0, Theme.dp(activity, 4), 0);
		close.setOnClickListener(v -> d.dismiss());
		header.addView(close);
		root.addView(header);
		ImageView iv = new ImageView(activity);
		iv.setImageBitmap(bmp);
		iv.setAdjustViewBounds(true);
		iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
		root.addView(iv, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
		d.setContentView(root);
		d.show();
	}


	private static LinearLayout buildDetailBottomBar(ModManager.ModInfo mod) {
		LinearLayout bar = new LinearLayout(activity);
		bar.setOrientation(LinearLayout.VERTICAL);
		bar.setPadding(Theme.dp(activity, 16), Theme.dp(activity, 8), Theme.dp(activity, 16), Theme.dp(activity, 20));
		bar.setBackgroundColor(Color.parseColor(Theme.BG));

		// Manage always sits just above Launch (not an overlay — part of bottom chrome)
		LinearLayout tools = new LinearLayout(activity);
		tools.setOrientation(LinearLayout.HORIZONTAL);
		tools.setGravity(Gravity.CENTER);
		tools.setPadding(0, 0, 0, Theme.dp(activity, 10));
		tools.addView(iconAction(R.drawable.ic_files, "Folder", Theme.ACCENT_BLUE, () -> {
			if (mod.dir != null) FilesScreen.openDirectory(activity, mod.dir);
		}));
		tools.addView(iconAction(R.drawable.ic_upload, "Export", Theme.ACCENT_GREEN, () -> exportModSaves(mod)));
		tools.addView(iconAction(R.drawable.ic_paste, "Import", Theme.TEXT_MAIN, () -> importModSaves(mod)));
		tools.addView(iconAction(R.drawable.ic_delete, "Remove", Theme.ACCENT_RED, () -> confirmDeleteMod(mod)));
		bar.addView(tools);

		Button btnLaunch = styledButton("Launch Game", Theme.ACCENT_GREEN, Theme.ACCENT_DARK_TEXT);
		btnLaunch.setOnClickListener(v -> {
			try {
				MainActivity.launch();
			} catch (Exception e) {
				Toast.makeText(activity, "Failed to launch: " + e.getMessage(), Toast.LENGTH_LONG).show();
			}
		});
		bar.addView(btnLaunch, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(activity, 52)));

		return bar;
	}

	private static void confirmDeleteMod(ModManager.ModInfo mod) {
		Runnable doDelete = () -> {
			boolean removed = ModManager.deleteMod(mod);
			Toast.makeText(activity,
					removed ? "Uninstalled " + mod.name : "Couldn't remove all files",
					Toast.LENGTH_SHORT).show();
			if (currentMod != null && currentMod.id != null && currentMod.id.equals(mod.id))
				closeMod();
			else
				refreshModGrid();
		};
		if (!SettingsScreen.pref(activity, SettingsScreen.KEY_CONFIRM_DELETE, true)) {
			doDelete.run();
			return;
		}
		showThemedConfirm(
				"Uninstall " + mod.name + "?",
				"This removes the mod folder and everything inside it. Saves under saves/ are deleted too.",
				"Uninstall",
				Theme.ACCENT_RED,
				doDelete);
	}

	private static void showThemedConfirm(String title, String message, String okLabel,
			String okColor, Runnable onOk) {
		Dialog dialog = new Dialog(activity);
		dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
		if (dialog.getWindow() != null) {
			dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
		}
		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setBackground(Theme.rippleBackground(Theme.dp(activity, 16), Theme.CARD));
		card.setPadding(Theme.dp(activity, 20), Theme.dp(activity, 18),
				Theme.dp(activity, 20), Theme.dp(activity, 16));

		TextView t = new TextView(activity);
		t.setText(title);
		t.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
		t.setTypeface(null, Typeface.BOLD);
		card.addView(t);

		TextView m = new TextView(activity);
		m.setText(message);
		m.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		m.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		m.setPadding(0, Theme.dp(activity, 10), 0, Theme.dp(activity, 18));
		card.addView(m);

		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.END);

		TextView cancel = new TextView(activity);
		cancel.setText("Cancel");
		cancel.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		cancel.setPadding(Theme.dp(activity, 14), Theme.dp(activity, 10),
				Theme.dp(activity, 14), Theme.dp(activity, 10));
		cancel.setBackground(Theme.rippleBackground(Theme.dp(activity, 10), Theme.BG));
		cancel.setOnClickListener(v -> dialog.dismiss());
		row.addView(cancel);

		TextView ok = new TextView(activity);
		ok.setText(okLabel);
		ok.setTextColor(Color.parseColor(Theme.ACCENT_DARK_TEXT));
		ok.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		ok.setTypeface(null, Typeface.BOLD);
		ok.setPadding(Theme.dp(activity, 16), Theme.dp(activity, 10),
				Theme.dp(activity, 16), Theme.dp(activity, 10));
		ok.setBackground(Theme.rippleBackground(Theme.dp(activity, 10), okColor));
		LinearLayout.LayoutParams okp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		okp.leftMargin = Theme.dp(activity, 10);
		ok.setLayoutParams(okp);
		ok.setOnClickListener(v -> {
			dialog.dismiss();
			onOk.run();
		});
		row.addView(ok);
		card.addView(row);

		dialog.setContentView(card);
		dialog.show();
		if (dialog.getWindow() != null) {
			dialog.getWindow().setLayout(
					(int) (activity.getResources().getDisplayMetrics().widthPixels * 0.88f),
					ViewGroup.LayoutParams.WRAP_CONTENT);
		}
	}

	private static void exportModSaves(ModManager.ModInfo mod) {
		if (mod == null || mod.dir == null) return;
		File out = new File(activity.getCacheDir(),
				"saves_" + (mod.id != null ? mod.id : "mod") + ".zip");
		boolean ok = ModManager.exportSaves(mod, out);
		if (!ok) {
			Toast.makeText(activity, "No saves to export (mods/<id>/saves/ is empty)", Toast.LENGTH_LONG).show();
			return;
		}
		try {
			Uri uri = FileProvider.getUriForFile(activity,
					activity.getPackageName() + ".fileprovider", out);
			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType("application/zip");
			intent.putExtra(Intent.EXTRA_STREAM, uri);
			intent.putExtra(Intent.EXTRA_SUBJECT, mod.name + " saves");
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			activity.startActivity(Intent.createChooser(intent, "Export saves"));
		} catch (Exception e) {
			Toast.makeText(activity, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}

	private static void importModSaves(ModManager.ModInfo mod) {
		pendingSaveImport = mod;
		try {
			Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			intent.setType("*/*");
			activity.startActivityForResult(intent, REQ_IMPORT_SAVES);
		} catch (Exception e) {
			Toast.makeText(activity, "Couldn't open file picker", Toast.LENGTH_SHORT).show();
		}
	}

	/** Call from MainActivity.onActivityResult for save imports. */
	public static void handleActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode != REQ_IMPORT_SAVES) return;
		if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
			pendingSaveImport = null;
			return;
		}
		ModManager.ModInfo mod = pendingSaveImport;
		pendingSaveImport = null;
		if (mod == null) return;
		Uri uri = data.getData();
		Toast.makeText(activity, "Importing saves…", Toast.LENGTH_SHORT).show();
		new Thread(() -> {
			boolean ok = ModManager.importSaves(mod, uri, activity);
			activity.runOnUiThread(() -> Toast.makeText(activity,
					ok ? "Saves imported into saves/" : "Import failed",
					Toast.LENGTH_LONG).show());
		}).start();
	}


	private static void openFilePicker() {
		try {
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("application/zip");
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			activity.startActivityForResult(
					Intent.createChooser(intent, "Select Mod Zip"),
					FILE_PICKER_REQUEST_CODE);
		} catch (Exception e) {
			try {
				Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
				fallback.setType("*/*");
				activity.startActivityForResult(fallback, FILE_PICKER_REQUEST_CODE);
			} catch (Exception ex) {
				Toast.makeText(activity, "No file manager available.", Toast.LENGTH_LONG).show();
			}
		}
	}

	public static void processSelectedFile(Context context, Uri uri) {
		Toast.makeText(activity, "Installing mod\u2026", Toast.LENGTH_SHORT).show();

		ModManager.installMod(context, uri, new ModManager.InstallCallback() {
			@Override
			public void onSuccess(ModManager.ModInfo mod) {
				runOnUi(() -> {
					Toast.makeText(activity, "Installed " + mod.name + " v" + mod.version, Toast.LENGTH_LONG).show();
					if (currentMod == null)
						refreshModGrid();
				});
			}

			@Override
			public void onFailure(String reason) {
				runOnUi(() -> Toast.makeText(activity, "Failed: " + reason, Toast.LENGTH_LONG).show());
			}
		});
	}

	private static void runOnUi(Runnable r) {
		if (activity != null)
			activity.runOnUiThread(r);
	}

	private static View buildIconContent(ModManager.ModInfo mod, int letterSizePx) {
		File iconFile = mod.iconFile();
		Bitmap bitmap = iconFile != null ? BitmapFactory.decodeFile(iconFile.getAbsolutePath()) : null;

		if (bitmap != null) {
			ImageView icon = new ImageView(activity);
			icon.setImageBitmap(bitmap);
			icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
			return icon;
		}

		TextView letter = new TextView(activity);
		String name = mod.name != null ? mod.name.trim() : "";
		letter.setText(!name.isEmpty() ? name.substring(0, 1).toUpperCase(Locale.ROOT) : "?");
		letter.setTextColor(Color.parseColor(Theme.ACCENT_BLUE));
		letter.setTextSize(TypedValue.COMPLEX_UNIT_PX, letterSizePx);
		letter.setTypeface(null, Typeface.BOLD);
		letter.setGravity(Gravity.CENTER);
		return letter;
	}

	private static Button styledButton(String text, String bgColor, String textColor) {
		Button btn = new Button(activity);
		btn.setText(text);
		btn.setTextColor(Color.parseColor(textColor));
		btn.setAllCaps(false);
		btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		btn.setTypeface(null, Typeface.BOLD);
		btn.setBackground(Theme.rippleBackground(Theme.dp(activity, 12), bgColor));
		return btn;
	}

	private static LinearLayout.LayoutParams matchWrapParams(int l, int t, int r, int b) {
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		p.setMargins(l, t, r, b);
		return p;
	}
}