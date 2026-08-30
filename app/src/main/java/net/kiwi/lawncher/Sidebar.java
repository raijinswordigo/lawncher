package net.kiwi.lawncher;

import static net.kiwi.lawncher.Launcher.refreshModGrid;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
		final int iconRes;
		final String label;
		final Runnable action;

		Item(int iconRes, String label, Runnable action) {
			this.iconRes = iconRes;
			this.label = label;
			this.action = action;
		}
	}

	private static final int PANEL_WIDTH_DP = 250;
	private static final int ANIM_DURATION = 200;

	private static Activity activity;
	private static FrameLayout contentHost;
	private static View scrim;
	private static LinearLayout panel;
	private static LinearLayout itemList;
	private static Item[] lastItems;
	private static TextView panelTitle;
	private static boolean open;

	private static Runnable onLeaveCurrent;

	/** Builds the drawer shell around initialScreen and returns the combined view to add to your root container. */
	static View install(Activity act, View initialScreen) {
		activity = act;
		open = false;
		onLeaveCurrent = null;

		// Custom FrameLayout to intercept touches and build our own drag-to-open gesture
		FrameLayout root = new FrameLayout(act) {
			private float startX, startY;
			private boolean draggingDrawer = false;

			// Leave the first 30dp alone so the system swipe-to-go-back gesture still works!
			private final int sysEdge = Theme.dp(act, 30);
			private final int touchSlop = Theme.dp(act, 10);

			@Override
			public boolean onInterceptTouchEvent(MotionEvent ev) {
				switch(ev.getActionMasked()) {
					case MotionEvent.ACTION_DOWN:
						startX = ev.getRawX();
						startY = ev.getRawY();
						draggingDrawer = false;
						break;
					case MotionEvent.ACTION_MOVE:
						float dx = ev.getRawX() - startX;
						float dy = Math.abs(ev.getRawY() - startY);

						// If moving mostly horizontally...
						if (Math.abs(dx) > dy && Math.abs(dx) > touchSlop) {
							// Swipe to open: Start touch MUST be past the system gesture edge (30dp) but within 80dp
							if (!open && dx > 0 && startX > sysEdge && startX < sysEdge + Theme.dp(act, 50)) {
								draggingDrawer = true;
								scrim.setVisibility(View.VISIBLE);
								return true; // Steal the touch
							}
							// Swipe to close: Swipe left anywhere while open
							if (open && dx < 0) {
								draggingDrawer = true;
								return true; // Steal the touch
							}
						}
						break;
				}
				return super.onInterceptTouchEvent(ev);
			}

			@Override
			public boolean onTouchEvent(MotionEvent ev) {
				if (!draggingDrawer) return super.onTouchEvent(ev);

				float dx = ev.getRawX() - startX;
				int panelW = Theme.dp(act, PANEL_WIDTH_DP);

				switch(ev.getActionMasked()) {
					case MotionEvent.ACTION_MOVE:
						// Calculate where the panel should be based on finger position
						float newX = open ? dx : (-panelW + dx);
						newX = Math.max(-panelW, Math.min(0, newX)); // Clamp between closed and open
						panel.setTranslationX(newX);

						// Dynamically fade the dark overlay based on drawer position
						float progress = 1f - (Math.abs(newX) / (float)panelW);
						scrim.setAlpha(progress);
						break;
					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_CANCEL:
						draggingDrawer = false;
						// Snap open or close depending on how far the user dragged it
						if (panel.getTranslationX() > -panelW * 0.5f) {
							open();
						} else {
							close();
						}
						break;
				}
				return true;
			}
		};

		contentHost = new FrameLayout(act);
		root.addView(contentHost, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		contentHost.addView(initialScreen, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		scrim = new View(act);
		scrim.setBackgroundColor(Color.parseColor("#99000000"));
		scrim.setVisibility(View.GONE);
		scrim.setAlpha(0f);
		scrim.setOnClickListener(v -> close());
		root.addView(scrim, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		panel = buildPanel(act);
		root.addView(panel, new FrameLayout.LayoutParams(Theme.dp(act, PANEL_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT));
		panel.setTranslationX(-Theme.dp(act, PANEL_WIDTH_DP));

		return root;
	}

	static void setItems(Item... items) {
		lastItems = items;
		if (itemList == null) return;
		itemList.removeAllViews();
		for (Item item : items) itemList.addView(buildRow(item));
	}

	/** Recolor panel + rows after Theme.savePreset. */
	static void recolor() {
		if (panel == null || activity == null) return;
		panel.setBackgroundColor(android.graphics.Color.parseColor(Theme.CARD));
		if (panelTitle != null) panelTitle.setTextColor(android.graphics.Color.parseColor(Theme.ACCENT_GREEN));
		if (lastItems != null) setItems(lastItems);
	}

	/** Swaps the visible screen. onLeave (nullable) fires right before the old screen is dropped. */
	static void showScreen(View screen, Runnable onLeave) {
		Launcher.refreshModGrid();
		if (onLeaveCurrent != null) onLeaveCurrent.run();
		onLeaveCurrent = onLeave;

		if (screen.getParent() instanceof ViewGroup) {
			((ViewGroup) screen.getParent()).removeView(screen);
		}

		contentHost.removeAllViews();
		contentHost.addView(screen, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		// CLEAN ANIMATION: Simple alpha fade in, no weird layout shifts
		screen.setAlpha(0f);
		screen.animate().alpha(1f).setDuration(150).start();
	}

	static void toggle() {
		if (open) close(); else open();
	}

	static void open() {
		scrim.setVisibility(View.VISIBLE);
		scrim.animate().alpha(1f).setDuration(ANIM_DURATION).start();
		panel.animate().translationX(0).setDuration(ANIM_DURATION).start();
		open = true;
	}

	static void close() {
		panel.animate().translationX(-Theme.dp(activity, PANEL_WIDTH_DP)).setDuration(ANIM_DURATION).start();
		scrim.animate().alpha(0f).setDuration(ANIM_DURATION)
				.withEndAction(() -> {
					if (!open) scrim.setVisibility(View.GONE);
				}).start();
		open = false;
	}

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

		panelTitle = new TextView(act);
		panelTitle.setText("Kiwi Lawncher");
		panelTitle.setTextColor(Color.parseColor(Theme.ACCENT_GREEN));
		panelTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		panelTitle.setPadding(Theme.dp(act, 20), 0, Theme.dp(act, 20), Theme.dp(act, 24));
		p.addView(panelTitle);

		itemList = new LinearLayout(act);
		itemList.setOrientation(LinearLayout.VERTICAL);
		p.addView(itemList, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		// Social links pinned to the bottom of the drawer
		LinearLayout social = new LinearLayout(act);
		social.setOrientation(LinearLayout.HORIZONTAL);
		social.setGravity(Gravity.CENTER);
		social.setPadding(Theme.dp(act, 16), Theme.dp(act, 12),
				Theme.dp(act, 16), Theme.dp(act, 24));

		social.addView(socialIconBtn(act, R.drawable.ic_discord, Theme.ACCENT_BLUE,
				"Discord", () -> {
			close();
			openUrl(SettingsScreen.DISCORD_URL);
		}));
		social.addView(socialIconBtn(act, R.drawable.ic_youtube, Theme.ACCENT_RED,
				"YouTube", () -> {
			close();
			openUrl("https://www.youtube.com/channel/UCL_PHcgWnFqbQFf_VTXDD3w");
		}));
		p.addView(social);

		return p;
	}

	private static View socialIconBtn(Activity act, int iconRes, String color, String desc, Runnable action) {
		ImageView iv = new ImageView(act);
		iv.setImageResource(iconRes);
		iv.setColorFilter(Color.parseColor(color));
		iv.setContentDescription(desc);
		int pad = Theme.dp(act, 12);
		iv.setPadding(pad, pad, pad, pad);
		iv.setBackground(Theme.rippleBackground(Theme.dp(act, 12), Theme.BG));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				Theme.dp(act, 48), Theme.dp(act, 48));
		lp.leftMargin = Theme.dp(act, 8);
		lp.rightMargin = Theme.dp(act, 8);
		iv.setLayoutParams(lp);
		iv.setOnClickListener(v -> action.run());
		return iv;
	}

	private static void openUrl(String url) {
		if (activity == null) return;
		try {
			activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
		} catch (Exception e) {
			Toast.makeText(activity, "Couldn't open link", Toast.LENGTH_SHORT).show();
		}
	}


	private static View buildRow(Item item) {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(Theme.dp(activity, 20), Theme.dp(activity, 14), Theme.dp(activity, 20), Theme.dp(activity, 14));
		row.setClickable(true);
		row.setFocusable(true);
		row.setBackground(Theme.rippleBackground(0, Theme.CARD));

		ImageView icon = new ImageView(activity);
		icon.setImageResource(item.iconRes);
		icon.setColorFilter(Color.parseColor(Theme.TEXT_MAIN));
		LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
				Theme.dp(activity, 22), Theme.dp(activity, 22));
		iconParams.rightMargin = Theme.dp(activity, 16);
		row.addView(icon, iconParams);

		TextView label = new TextView(activity);
		label.setText(item.label);
		label.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		row.addView(label);

		row.setOnClickListener(v -> {
			close();
			// Let the drawer close animation finish smoothly before triggering the action
			row.postDelayed(item.action, ANIM_DURATION - 50);
		});
		return row;
	}
}
