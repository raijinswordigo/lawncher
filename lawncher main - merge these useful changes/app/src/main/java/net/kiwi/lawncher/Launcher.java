package net.kiwi.lawncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
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
import java.util.List;
import java.util.Locale;

/**
 * Launcher: builds and owns the UI only.
 * All mod filesystem work lives in {@link ModManager}.
 *
 * Two screens sharing one top bar / content / bottom bar shell:
 *   - grid screen: browse installed mods, install new ones
 *   - detail screen: opened by tapping a mod; shows its info + screenshots
 *     and is the only place the "Launch Game" button appears, since that's
 *     the mod the game will actually be launched with (see {@link #currentMod}).
 */
public class Launcher {

	private static Activity activity;
	private static ViewGroup rootContainer;

	public static final int FILE_PICKER_REQUEST_CODE = 9999;

	private static final int GRID_COLUMNS = 2;

	private static FrameLayout topBarContainer;
	private static FrameLayout contentContainer;
	private static FrameLayout bottomBarContainer;

	/** The whole mods screen (top+content+bottom bars), kept so Sidebar's "Mods" item can redisplay it as-is. */
	private static View screenRoot;

	private static GridLayout modGrid;
	private static TextView modCountLabel;

	/** The mod whose detail screen is currently open, or null when browsing the grid. */
	private static ModManager.ModInfo currentMod;

	// Catppuccin Mocha-ish palette
	private static final String BG = "#11111B";
	private static final String CARD = "#1E1E2E";
	private static final String BORDER = "#313244";
	private static final String TEXT_MAIN = "#CDD6F4";
	private static final String TEXT_DIM = "#BAC2DE";
	private static final String ACCENT_GREEN = "#A6E3A1";
	private static final String ACCENT_BLUE = "#89B4FA";
	private static final String ACCENT_RED = "#F38BA8";
	private static final String ACCENT_DARK_TEXT = "#11111B";

	public static void init(Activity act, ViewGroup view) {
		activity = act;
		rootContainer = view;
		act.runOnUiThread(Launcher::buildUI);
	}

	public static void initGameButtons() {}

	/** The id of the mod the "Launch Game" button was pressed for, or "" if none is open. Called from native. */
	public static String currentMod() {
		return currentMod != null && currentMod.id != null ? currentMod.id : "";
	}

	// ==========================================
	// UI construction
	// ==========================================

	private static void buildUI() {
		if (rootContainer == null) return;
		rootContainer.removeAllViews();

		LinearLayout root = new LinearLayout(activity);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(Color.parseColor(BG));
		root.setClipChildren(false);
		root.setClipToPadding(false);

		// Automatically pad root container to avoid status bar & bottom navigation bar
		root.setOnApplyWindowInsetsListener((v, insets) -> {
			v.setPadding(
					insets.getSystemWindowInsetLeft(),
					insets.getSystemWindowInsetTop(),
					insets.getSystemWindowInsetRight(),
					insets.getSystemWindowInsetBottom()
			);
			return insets;
		});

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
				new Sidebar.Item("\uD83E\uDDE9", "Mods", () -> Sidebar.showScreen(screenRoot, null)),
				new Sidebar.Item("\u2699", "Settings", () -> activity.startActivity(new Intent(activity, SettingsActivity.class))),
				new Sidebar.Item("\uD83D\uDCC1", "Files", () -> Sidebar.showScreen(FilesScreen.build(activity), null)),
				new Sidebar.Item("\uD83D\uDCDC", "Logcat", () -> Sidebar.showScreen(LogcatScreen.build(activity), LogcatScreen::stop))
		);

		renderScreen();
	}

	/** Re-renders the top bar, content, and bottom bar to match {@link #currentMod}. */
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

	// ---- Top bars ----

	private static LinearLayout buildGridTopBar() {
		LinearLayout bar = new LinearLayout(activity);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.CENTER_VERTICAL);
		bar.setClipChildren(false);
		bar.setClipToPadding(false);
		// Extra top padding + non-clipping parents fix the emoji glyph in the
		// title getting cut off at the top on some devices/fonts.
		bar.setPadding(dp(12), dp(28), dp(16), dp(16));

		TextView hamburger = new TextView(activity);
		hamburger.setText("\u2630");
		hamburger.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
		hamburger.setTextColor(Color.parseColor(TEXT_MAIN));
		hamburger.setGravity(Gravity.CENTER);
		hamburger.setBackground(circleBackground(CARD));
		hamburger.setContentDescription("Open menu");
		hamburger.setOnClickListener(v -> Sidebar.toggle());
		LinearLayout.LayoutParams hbParams = new LinearLayout.LayoutParams(dp(40), dp(40));
		hbParams.rightMargin = dp(12);
		bar.addView(hamburger, hbParams);

		TextView title = new TextView(activity);
		// Bolding an emoji glyph via setTypeface(BOLD) synthetically enlarges
		// it beyond the text's normal ascent, which is what was clipping it.
		// Bold only the "Kiwi Lawncher" text and leave the emoji regular-weight.
		String titleStr = "\uD83E\uDD5D  Kiwi Lawncher";
		SpannableString spannable = new SpannableString(titleStr);
		spannable.setSpan(new StyleSpan(Typeface.BOLD), 2, titleStr.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		title.setText(spannable);
		title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
		title.setTextColor(Color.parseColor(ACCENT_GREEN));
		title.setIncludeFontPadding(true);
		bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		return bar;
	}

	private static LinearLayout buildDetailTopBar(ModManager.ModInfo mod) {
		LinearLayout bar = new LinearLayout(activity);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.CENTER_VERTICAL);
		bar.setPadding(dp(12), dp(20), dp(16), dp(16));

		TextView back = new TextView(activity);
		back.setText("\u2190");
		back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
		back.setTextColor(Color.parseColor(TEXT_MAIN));
		back.setGravity(Gravity.CENTER);
		back.setBackground(circleBackground(CARD));
		back.setContentDescription("Back to mod list");
		back.setOnClickListener(v -> closeMod());
		LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(40), dp(40));
		backParams.rightMargin = dp(12);
		bar.addView(back, backParams);

		TextView name = new TextView(activity);
		name.setText(mod.name);
		name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
		name.setTypeface(null, Typeface.BOLD);
		name.setTextColor(Color.parseColor(TEXT_MAIN));
		name.setMaxLines(1);
		name.setEllipsize(TextUtils.TruncateAt.END);
		bar.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		TextView trash = new TextView(activity);
		trash.setText("\uD83D\uDDD1");
		trash.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
		trash.setTextColor(Color.parseColor(ACCENT_RED));
		trash.setGravity(Gravity.CENTER);
		trash.setBackground(circleBackground(CARD));
		trash.setContentDescription("Uninstall " + mod.name);
		trash.setOnClickListener(v -> confirmDeleteMod(mod));
		bar.addView(trash, new LinearLayout.LayoutParams(dp(40), dp(40)));

		return bar;
	}

	// ---- Grid screen ----

	private static LinearLayout buildModGridSection() {
		LinearLayout section = new LinearLayout(activity);
		section.setOrientation(LinearLayout.VERTICAL);
		section.setPadding(dp(20), dp(8), dp(20), dp(8));

		LinearLayout labelRow = new LinearLayout(activity);
		labelRow.setOrientation(LinearLayout.HORIZONTAL);
		labelRow.setGravity(Gravity.CENTER_VERTICAL);

		TextView label = new TextView(activity);
		label.setText("Installed Mods");
		label.setTextColor(Color.parseColor(TEXT_DIM));
		label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		label.setTypeface(null, Typeface.BOLD);
		labelRow.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		modCountLabel = new TextView(activity);
		modCountLabel.setTextColor(Color.parseColor(TEXT_DIM));
		modCountLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		labelRow.addView(modCountLabel);

		section.addView(labelRow, matchWrapParams(0, 0, 0, dp(10)));

		// GridLayout inside a ScrollView: a fixed 2-column grid that scrolls
		// vertically once there are more mods than fit on screen.
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
		bar.setPadding(dp(20), dp(12), dp(20), dp(24));

		// Launch Game only ever appears on a mod's detail screen (see
		// buildDetailBottomBar) - you have to be "inside" a mod to launch it.
		Button btnInstall = styledButton("Install Mod", ACCENT_BLUE, ACCENT_DARK_TEXT);
		btnInstall.setOnClickListener(v -> openFilePicker());
		bar.addView(btnInstall, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

		return bar;
	}

	private static void refreshModGrid() {
		if (modGrid == null) return;
		modGrid.removeAllViews();

		List<ModManager.ModInfo> mods = ModManager.listInstalledMods(activity);
		modCountLabel.setText(mods.isEmpty() ? "" : String.valueOf(mods.size()));

		if (mods.isEmpty()) {
			modGrid.addView(buildEmptyState());
			return;
		}

		for (ModManager.ModInfo mod : mods) {
			modGrid.addView(buildModCell(mod));
		}
	}

	private static TextView buildEmptyState() {
		TextView empty = new TextView(activity);
		empty.setText("No mods installed yet.\nTap \u201cInstall Mod\u201d to add your first one.");
		empty.setTextColor(Color.parseColor(TEXT_DIM));
		empty.setGravity(Gravity.CENTER);
		empty.setPadding(0, dp(40), 0, dp(40));

		GridLayout.LayoutParams params = new GridLayout.LayoutParams();
		params.width = 0;
		params.columnSpec = GridLayout.spec(0, GRID_COLUMNS, 1f);
		empty.setLayoutParams(params);
		return empty;
	}

	private static View buildModCell(ModManager.ModInfo mod) {
		LinearLayout cell = new LinearLayout(activity);
		cell.setOrientation(LinearLayout.VERTICAL);
		cell.setGravity(Gravity.CENTER_HORIZONTAL);
		cell.setPadding(dp(10), dp(14), dp(10), dp(14));
		cell.setClickable(true);
		cell.setFocusable(true);
		cell.setBackground(rippleBackground(dp(18)));

		FrameLayout iconWrap = new FrameLayout(activity);
		GradientDrawable iconBg = new GradientDrawable();
		iconBg.setColor(Color.parseColor(CARD));
		iconBg.setCornerRadius(dp(18));
		iconBg.setStroke(dp(1), Color.parseColor(BORDER));
		iconWrap.setBackground(iconBg);
		iconWrap.addView(buildIconContent(mod, dp(30)), new FrameLayout.LayoutParams(dp(84), dp(84)));
		cell.addView(iconWrap, new LinearLayout.LayoutParams(dp(84), dp(84)));

		TextView name = new TextView(activity);
		name.setText(mod.name);
		name.setTextColor(Color.parseColor(TEXT_MAIN));
		name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		name.setMaxLines(2);
		name.setEllipsize(TextUtils.TruncateAt.END);
		name.setGravity(Gravity.CENTER_HORIZONTAL);
		LinearLayout.LayoutParams nameParams =
				new LinearLayout.LayoutParams(dp(100), ViewGroup.LayoutParams.WRAP_CONTENT);
		nameParams.topMargin = dp(8);
		cell.addView(name, nameParams);

		TextView version = new TextView(activity);
		version.setText("v" + mod.version);
		version.setTextColor(Color.parseColor(TEXT_DIM));
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
		params.setMargins(dp(4), dp(4), dp(4), dp(4));
		cell.setLayoutParams(params);
		return cell;
	}

	// ---- Detail screen ----

	private static ScrollView buildModDetailSection(ModManager.ModInfo mod) {
		ScrollView scroll = new ScrollView(activity);

		LinearLayout content = new LinearLayout(activity);
		content.setOrientation(LinearLayout.VERTICAL);
		content.setPadding(dp(20), dp(4), dp(20), dp(24));

		// Big icon + name/version header.
		LinearLayout header = new LinearLayout(activity);
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);

		FrameLayout iconWrap = new FrameLayout(activity);
		GradientDrawable iconBg = new GradientDrawable();
		iconBg.setColor(Color.parseColor(CARD));
		iconBg.setCornerRadius(dp(20));
		iconBg.setStroke(dp(1), Color.parseColor(BORDER));
		iconWrap.setBackground(iconBg);
		iconWrap.addView(buildIconContent(mod, dp(34)), new FrameLayout.LayoutParams(dp(72), dp(72)));
		header.addView(iconWrap, new LinearLayout.LayoutParams(dp(72), dp(72)));

		LinearLayout headerText = new LinearLayout(activity);
		headerText.setOrientation(LinearLayout.VERTICAL);
		TextView version = new TextView(activity);
		version.setText("Version " + mod.version);
		version.setTextColor(Color.parseColor(TEXT_DIM));
		version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		headerText.addView(version);
		TextView id = new TextView(activity);
		id.setText(mod.id);
		id.setTextColor(Color.parseColor(BORDER));
		id.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
		headerText.addView(id, matchWrapParams(0, dp(4), 0, 0));
		LinearLayout.LayoutParams headerTextParams = new LinearLayout.LayoutParams(
				0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		headerTextParams.leftMargin = dp(16);
		header.addView(headerText, headerTextParams);

		content.addView(header, matchWrapParams(0, 0, 0, dp(20)));

		if (mod.description != null && !mod.description.isEmpty()) {
			content.addView(sectionLabel("About"));
			TextView desc = new TextView(activity);
			desc.setText(mod.description);
			desc.setTextColor(Color.parseColor(TEXT_MAIN));
			desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
			desc.setLineSpacing(dp(4), 1f);
			content.addView(desc, matchWrapParams(0, dp(6), 0, dp(20)));
		}

		List<File> screenshots = mod.screenshotFiles();
		if (!screenshots.isEmpty()) {
			content.addView(sectionLabel("Screenshots"));
			content.addView(buildScreenshotGallery(screenshots), matchWrapParams(0, dp(10), 0, dp(20)));
		}

		content.setLayoutParams(new ScrollView.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		scroll.addView(content);
		return scroll;
	}

	private static TextView sectionLabel(String text) {
		TextView label = new TextView(activity);
		label.setText(text);
		label.setTextColor(Color.parseColor(TEXT_DIM));
		label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		label.setTypeface(null, Typeface.BOLD);
		return label;
	}

	private static HorizontalScrollView buildScreenshotGallery(List<File> screenshots) {
		HorizontalScrollView scroll = new HorizontalScrollView(activity);
		scroll.setHorizontalScrollBarEnabled(false);

		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);

		for (File file : screenshots) {
			Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
			if (bitmap == null) continue;

			ImageView shot = new ImageView(activity);
			shot.setImageBitmap(bitmap);
			shot.setScaleType(ImageView.ScaleType.CENTER_CROP);
			GradientDrawable frame = new GradientDrawable();
			frame.setCornerRadius(dp(12));
			frame.setStroke(dp(1), Color.parseColor(BORDER));
			shot.setBackground(frame);
			shot.setClipToOutline(true);

			LinearLayout.LayoutParams shotParams = new LinearLayout.LayoutParams(dp(200), dp(120));
			shotParams.rightMargin = dp(10);
			row.addView(shot, shotParams);
		}

		scroll.addView(row);
		return scroll;
	}

	private static LinearLayout buildDetailBottomBar(ModManager.ModInfo mod) {
		LinearLayout bar = new LinearLayout(activity);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setPadding(dp(20), dp(12), dp(20), dp(24));

		// The only place this button exists - launching only makes sense
		// once you're inside a specific mod, since currentMod() (called from
		// native) reports back whichever mod you launched from here.
		Button btnLaunch = styledButton("Launch Game", ACCENT_GREEN, ACCENT_DARK_TEXT);
		btnLaunch.setOnClickListener(v -> MainActivity.launch());
		bar.addView(btnLaunch, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

		return bar;
	}

	// ==========================================
	// Dialogs
	// ==========================================

	private static void confirmDeleteMod(ModManager.ModInfo mod) {
		new AlertDialog.Builder(activity)
				.setTitle("Uninstall " + mod.name + "?")
				.setMessage("This removes the mod's files from this device.")
				.setPositiveButton("Uninstall", (dialog, which) -> {
					boolean removed = ModManager.deleteMod(mod);
					Toast.makeText(activity, removed ? "Uninstalled " + mod.name : "Couldn't remove all files",
							Toast.LENGTH_SHORT).show();
					if (currentMod != null && currentMod.id != null && currentMod.id.equals(mod.id)) {
						closeMod();
					} else {
						refreshModGrid();
					}
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	// ==========================================
	// File picker -> ModManager handoff
	// ==========================================

	private static void openFilePicker() {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("application/zip");
		activity.startActivityForResult(intent, FILE_PICKER_REQUEST_CODE);
	}

	/** Called from MainActivity#onActivityResult. */
	public static void processSelectedFile(Context context, Uri uri) {
		Toast.makeText(activity, "Installing mod\u2026", Toast.LENGTH_SHORT).show();

		ModManager.installMod(context, uri, new ModManager.InstallCallback() {
			@Override
			public void onSuccess(ModManager.ModInfo mod) {
				runOnUi(() -> {
					Toast.makeText(activity, "Installed " + mod.name + " v" + mod.version, Toast.LENGTH_LONG).show();
					if (currentMod == null) refreshModGrid();
				});
			}

			@Override
			public void onFailure(String reason) {
				runOnUi(() -> Toast.makeText(activity, "Failed to install: " + reason, Toast.LENGTH_LONG).show());
			}
		});
	}

	private static void runOnUi(Runnable r) {
		if (activity != null) activity.runOnUiThread(r);
	}

	// ==========================================
	// Small view helpers
	// ==========================================

	/** Real icon.png if the mod shipped one, otherwise a letter avatar. letterSizePx sizes the fallback text. */
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
		letter.setTextColor(Color.parseColor(ACCENT_BLUE));
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
		btn.setBackground(rippleBackground(dp(12), bgColor));
		return btn;
	}

	private static GradientDrawable circleBackground(String color) {
		GradientDrawable bg = new GradientDrawable();
		bg.setShape(GradientDrawable.OVAL);
		bg.setColor(Color.parseColor(color));
		return bg;
	}

	private static Drawable rippleBackground(int cornerRadiusPx) {
		return rippleBackground(cornerRadiusPx, CARD);
	}

	private static Drawable rippleBackground(int cornerRadiusPx, String baseColor) {
		GradientDrawable mask = new GradientDrawable();
		mask.setCornerRadius(cornerRadiusPx);
		mask.setColor(Color.parseColor(baseColor));
		return new RippleDrawable(ColorStateList.valueOf(Color.parseColor(BORDER)), mask, null);
	}

	private static LinearLayout.LayoutParams matchWrapParams(int l, int t, int r, int b) {
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		p.setMargins(l, t, r, b);
		return p;
	}

	private static int dp(int dp) {
		return (int) (dp * activity.getResources().getDisplayMetrics().density);
	}
}