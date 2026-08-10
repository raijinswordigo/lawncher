package net.kiwi.lawncher.ui;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.kiwi.lawncher.InstanceManager;
import net.kiwi.lawncher.accounts.AccountManager;
import net.kiwi.lawncher.screens.FilesScreen;
import net.kiwi.lawncher.screens.LogcatScreen;
import net.kiwi.lawncher.screens.ModsScreen;
import net.kiwi.lawncher.screens.ProfileScreen;
import net.kiwi.lawncher.screens.SettingsScreen;
import net.kiwi.lawncher.screens.StoreScreen;
import net.kiwi.lawncher.util.Prefs;

/**
 * Owns the whole launcher UI: top bar, screen router and the animated
 * sidebar drawer. All screens are hosted in one content area.
 */
public final class LauncherShell {

	public static final int SCREEN_MODS = 0;
	public static final int SCREEN_STORE = 1;
	public static final int SCREEN_FILES = 2;
	public static final int SCREEN_LOGS = 3;
	public static final int SCREEN_SETTINGS = 4;
	public static final int SCREEN_PROFILE = 5;

	private static final String[] TITLES = {"Mods", "Mod Store", "Files", "Logcat", "Settings", "Profile"};
	private static final String[] SUBTITLES = {
			"Your mod library", "Discover & install", "Browse storage",
			"Live log stream", "Tune Lawncher", "Your identity"
	};

	private static Activity activity;
	private static ViewGroup root;
	private static LinearLayout column;
	private static FrameLayout topBarFrame;
	private static FrameLayout contentFrame;
	private static FrameLayout drawerOverlay;
	private static View scrim;
	private static FrameLayout drawer;
	private static int drawerWidth;
	private static TextView barTitle;
	private static TextView barSubtitle;
	private static HamburgerView hamburger;
	private static Sidebar sidebar;
	private static final Screen[] screens = new Screen[6];
	private static int current = -1;
	private static boolean gameMode = false;
	private static String launchModId = "";
	private static String activeInstanceId = "";

	private LauncherShell() {}

	public static void init(Activity act, ViewGroup rootView) {
		activity = act;
		root = rootView;
		Prefs.init(act);
		Theme.activity = act;
		gameMode = false;
		launchModId = "";
		activeInstanceId = InstanceManager.activeInstanceId(act);
		current = -1;
		screens[SCREEN_MODS] = new ModsScreen();
		screens[SCREEN_STORE] = new StoreScreen();
		screens[SCREEN_FILES] = new FilesScreen();
		screens[SCREEN_LOGS] = new LogcatScreen();
		screens[SCREEN_SETTINGS] = new SettingsScreen();
		screens[SCREEN_PROFILE] = new ProfileScreen();
		build();
		openScreen(SCREEN_MODS);
	}

	private static void build() {
		root.removeAllViews();

		FrameLayout shellRoot = new FrameLayout(activity);
		shellRoot.setBackgroundColor(Theme.BG);
		shellRoot.setOnApplyWindowInsetsListener((v, insets) -> {
			v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
					insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
			return insets;
		});
		root.addView(shellRoot, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		column = new LinearLayout(activity);
		column.setOrientation(LinearLayout.VERTICAL);
		shellRoot.addView(column, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		topBarFrame = new FrameLayout(activity);
		topBarFrame.setBackgroundColor(Theme.BG);
		column.addView(topBarFrame, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(62)));
		buildTopBar();

		contentFrame = new FrameLayout(activity);
		column.addView(contentFrame, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		// Drawer overlay sits on top of everything.
		drawerOverlay = new FrameLayout(activity);
		drawerOverlay.setVisibility(View.GONE);
		scrim = new View(activity);
		scrim.setBackgroundColor(0x99000000);
		scrim.setAlpha(0f);
		scrim.setOnClickListener(v -> closeDrawer());
		drawerOverlay.addView(scrim, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		drawerWidth = Math.min(Theme.dp(300),
				activity.getResources().getDisplayMetrics().widthPixels * 84 / 100);
		drawer = new FrameLayout(activity);
		drawer.setBackgroundColor(Theme.SURFACE);
		drawer.setElevation(Theme.dp(24));
		drawer.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
		drawer.setTranslationX(-drawerWidth);
		drawerOverlay.addView(drawer, new FrameLayout.LayoutParams(
				drawerWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));

		sidebar = new Sidebar(activity, new Sidebar.Callback() {
			@Override public void onNavigate(int screen) { openScreen(screen); }
			@Override public void onProfile() { openScreen(SCREEN_PROFILE); }
		});
		drawer.addView(sidebar.build(), new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		shellRoot.addView(drawerOverlay, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
	}

	private static void buildTopBar() {
		topBarFrame.removeAllViews();
		LinearLayout bar = new LinearLayout(activity);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.CENTER_VERTICAL);
		bar.setPadding(Theme.dp(6), 0, Theme.dp(14), 0);

		hamburger = new HamburgerView(activity);
		hamburger.setClickable(true);
		hamburger.setFocusable(true);
		hamburger.setBackground(Theme.ripple(Theme.dp(22), 0x00000000));
		hamburger.setOnClickListener(v -> toggleDrawer());
		bar.addView(hamburger, new LinearLayout.LayoutParams(Theme.dp(44), Theme.dp(44)));

		LinearLayout titles = new LinearLayout(activity);
		titles.setOrientation(LinearLayout.VERTICAL);
		titles.setGravity(Gravity.CENTER_VERTICAL);
		barTitle = Theme.text(18, Theme.TEXT, true);
		barSubtitle = Theme.caption(10, Theme.TEXT_FAINT);
		titles.addView(barTitle, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		titles.addView(barSubtitle, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		View avatar = Theme.avatar(AccountManager.getUsername(activity), 30,
				AccountManager.getAvatarColorIndex());
		avatar.setClickable(true);
		avatar.setBackground(Theme.ripple(Theme.dp(15), 0x00000000));
		avatar.setOnClickListener(v -> openScreen(SCREEN_PROFILE));
		bar.addView(avatar, new LinearLayout.LayoutParams(Theme.dp(30), Theme.dp(30)));

		topBarFrame.addView(bar, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
	}

	// ---- routing ----

	public static void openScreen(int idx) {
		if (idx < 0 || idx >= screens.length || screens[idx] == null) return;
		if (current == idx) {
			closeDrawer();
			return;
		}
		if (current >= 0) screens[current].onHidden();
		current = idx;
		contentFrame.removeAllViews();
		View v = screens[idx].build();
		contentFrame.addView(v, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		setBarTitle(idx);
		if (sidebar != null) sidebar.setSelected(idx);
		screens[idx].onShown();
		animateContent(v);
		closeDrawer();
	}

	/** Rebuilds the current screen + top bar after an appearance change. */
	public static void rebuildForAppearance() {
		buildTopBar();
		if (current >= 0) {
			contentFrame.removeAllViews();
			View v = screens[current].build();
			contentFrame.addView(v, new FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
			setBarTitle(current);
			screens[current].onShown();
			animateContent(v);
		}
		if (sidebar != null) sidebar.rebuild();
	}

	public static void refreshSidebar() {
		if (sidebar != null) sidebar.refreshProfile();
	}

	private static void setBarTitle(int idx) {
		if (barTitle == null) return;
		barTitle.setText(TITLES[idx]);
		barSubtitle.setText(SUBTITLES[idx]);
	}

	private static void animateContent(View v) {
		v.setAlpha(0f);
		v.setTranslationY(Theme.dp(14));
		v.animate().alpha(1f).translationY(0f).setDuration(280)
				.setInterpolator(new DecelerateInterpolator(1.3f)).start();
	}

	// ---- drawer ----

	public static void toggleDrawer() {
		if (drawerOverlay == null || drawer == null) return;
		if (drawerOverlay.getVisibility() == View.VISIBLE && drawer.getTranslationX() == 0f) {
			closeDrawer();
		} else {
			openDrawer();
		}
	}

	private static void openDrawer() {
		drawerOverlay.setVisibility(View.VISIBLE);
		scrim.setAlpha(0f);
		scrim.animate().alpha(1f).setDuration(240).start();
		drawer.animate().translationX(0f).setDuration(300)
				.setInterpolator(new DecelerateInterpolator(1.2f)).start();
		hamburger.setOpen(true);
		sidebar.refreshProfile();
	}

	private static void closeDrawer() {
		if (drawerOverlay == null || drawerOverlay.getVisibility() != View.VISIBLE) return;
		scrim.animate().alpha(0f).setDuration(220).start();
		drawer.animate().translationX(-drawerWidth).setDuration(260)
				.setInterpolator(new DecelerateInterpolator(1.1f))
				.withEndAction(() -> drawerOverlay.setVisibility(View.GONE)).start();
		hamburger.setOpen(false);
	}

	/** Back-press routing. Returns true when the press was consumed. */
	public static boolean dispatchBack() {
		if (gameMode) return false;
		if (drawerOverlay != null && drawerOverlay.getVisibility() == View.VISIBLE) {
			closeDrawer();
			return true;
		}
		if (current >= 0 && screens[current].onBack()) return true;
		return false;
	}

	// ---- game bridge ----

	public static void setLaunchMod(String id) {
		launchModId = id == null ? "" : id;
	}

	/** The id of the mod whose detail screen is open (queried by native code). */
	public static String launchModId() {
		return launchModId;
	}

	// ---- game instances ----

	/** The active game instance id (queried by native code via MainActivity.currentInstance). */
	public static String activeInstanceId() {
		return activeInstanceId;
	}

	public static void setActiveInstance(String id) {
		activeInstanceId = id == null ? "" : id;
		if (activity != null) InstanceManager.setActiveInstance(activity, activeInstanceId);
	}

	/** Re-triggers the current screen's onShown (e.g. after an instance was created on boot). */
	public static void refreshCurrentScreen() {
		if (current >= 0 && screens[current] != null) screens[current].onShown();
	}

	public static void setGameMode(boolean inGame) {
		gameMode = inGame;
	}

	/** File picker result → install into the Mods screen. */
	public static void handleFilePicked(Context context, Uri uri) {
		openScreen(SCREEN_MODS);
		Screen screen = screens[SCREEN_MODS];
		if (screen instanceof ModsScreen) {
			((ModsScreen) screen).installUri(context, uri);
		}
	}

	/** APK picker result → import as a new game instance in the Mods screen. */
	public static void handleInstanceApkPicked(Context context, Uri uri) {
		openScreen(SCREEN_MODS);
		Screen screen = screens[SCREEN_MODS];
		if (screen instanceof ModsScreen) {
			((ModsScreen) screen).createInstanceFromApk(context, uri);
		}
	}
}
