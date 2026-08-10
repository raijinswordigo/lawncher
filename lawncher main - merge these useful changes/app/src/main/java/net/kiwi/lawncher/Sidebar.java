package net.kiwi.lawncher;

import android.app.Activity;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Left-side nav drawer. Wraps whatever screen is currently on display with a
 * scrim + slide-in panel - plain Views, no androidx drawer dependency.
 *
 * Wiring: call {@link #install} once with the app's initial screen, then
 * {@link #setItems} to populate the menu. From anywhere else,
 * {@link #showScreen} swaps what's visible (e.g. Files, Logcat), and picking
 * a menu item always closes the drawer first, then runs that item's action.
 */
class Sidebar {

	static class Item {
		final String icon;
		final String label;
		final Runnable action;

		Item(String icon, String label, Runnable action) {
			this.icon = icon;
			this.label = label;
			this.action = action;
		}
	}

	private static final int PANEL_WIDTH_DP = 250;

	private static Activity activity;
	private static FrameLayout contentHost;
	private static View scrim;
	private static LinearLayout panel;
	private static LinearLayout itemList;
	private static boolean open;

	/** Runs right before the currently-visible screen is swapped away, e.g. to stop LogcatScreen's reader. May be null. */
	private static Runnable onLeaveCurrent;

	/** Builds the drawer shell around initialScreen and returns the combined view to add to your root container. */
	static View install(Activity act, View initialScreen) {
		activity = act;
		open = false;
		onLeaveCurrent = null;

		FrameLayout root = new FrameLayout(act);

		contentHost = new FrameLayout(act);
		root.addView(contentHost, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		contentHost.addView(initialScreen, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		scrim = new View(act);
		scrim.setBackgroundColor(Color.parseColor("#99000000"));
		scrim.setVisibility(View.GONE);
		scrim.setOnClickListener(v -> close());
		root.addView(scrim, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		panel = buildPanel(act);
		root.addView(panel, new FrameLayout.LayoutParams(Theme.dp(act, PANEL_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT));
		panel.setTranslationX(-Theme.dp(act, PANEL_WIDTH_DP));

		return root;
	}

	static void setItems(Item... items) {
		itemList.removeAllViews();
		for (Item item : items) itemList.addView(buildRow(item));
	}

	/** Swaps the visible screen. onLeave (nullable) fires right before the old screen is dropped. */
	static void showScreen(View screen, Runnable onLeave) {
		if (onLeaveCurrent != null) onLeaveCurrent.run();
		onLeaveCurrent = onLeave;

		if (screen.getParent() instanceof ViewGroup) {
			((ViewGroup) screen.getParent()).removeView(screen);
		}
		contentHost.removeAllViews();
		contentHost.addView(screen, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
	}

	static void toggle() {
		if (open) close(); else open();
	}

	static void open() {
		scrim.setVisibility(View.VISIBLE);
		panel.animate().translationX(0).setDuration(200).start();
		open = true;
	}

	static void close() {
		panel.animate().translationX(-Theme.dp(activity, PANEL_WIDTH_DP)).setDuration(200)
				.withEndAction(() -> scrim.setVisibility(View.GONE)).start();
		open = false;
	}

	/** Lets an Activity#onBackPressed close the drawer instead of exiting, if it's open. */
	static boolean isOpen() {
		return open;
	}

	// ==========================================
	// Panel construction
	// ==========================================

	private static LinearLayout buildPanel(Activity act) {
		LinearLayout p = new LinearLayout(act);
		p.setOrientation(LinearLayout.VERTICAL);
		p.setBackgroundColor(Color.parseColor(Theme.CARD));
		p.setPadding(0, Theme.dp(act, 48), 0, 0);

		TextView title = new TextView(act);
		title.setText("Kiwi Lawncher");
		title.setTextColor(Color.parseColor(Theme.ACCENT_GREEN));
		title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		title.setPadding(Theme.dp(act, 20), 0, Theme.dp(act, 20), Theme.dp(act, 24));
		p.addView(title);

		itemList = new LinearLayout(act);
		itemList.setOrientation(LinearLayout.VERTICAL);
		p.addView(itemList);

		return p;
	}

	private static View buildRow(Item item) {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(Theme.dp(activity, 20), Theme.dp(activity, 14), Theme.dp(activity, 20), Theme.dp(activity, 14));
		row.setClickable(true);
		row.setFocusable(true);
		row.setBackground(Theme.rippleBackground(0, Theme.CARD));

		TextView icon = new TextView(activity);
		icon.setText(item.icon);
		icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
		LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		iconParams.rightMargin = Theme.dp(activity, 16);
		row.addView(icon, iconParams);

		TextView label = new TextView(activity);
		label.setText(item.label);
		label.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		row.addView(label);

		row.setOnClickListener(v -> {
			close();
			item.action.run();
		});
		return row;
	}
}
