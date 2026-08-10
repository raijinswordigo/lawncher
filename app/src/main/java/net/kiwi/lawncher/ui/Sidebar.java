package net.kiwi.lawncher.ui;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.kiwi.lawncher.accounts.AccountManager;

/** Slide-in navigation drawer content (profile header + nav items + footer). */
public class Sidebar {

	public interface Callback {
		void onNavigate(int screen);
		void onProfile();
	}

	private static final int[] ITEM_ICONS = {'\u25A6', '\u25C8', '\u25A4', '\u2263', '\u2699'};
	private static final int[] ITEM_COLORS = {0xFF5B8CFF, 0xFF22D3EE, 0xFF3DDC97, 0xFFFFB454, 0xFF97A1BC};
	private static final String[] ITEM_LABELS = {"Mods", "Mod Store", "Files", "Logcat", "Settings"};

	private final Activity activity;
	private final Callback callback;
	private LinearLayout root;
	private int selected = 0;

	public Sidebar(Activity activity, Callback callback) {
		this.activity = activity;
		this.callback = callback;
	}

	public View build() {
		if (root == null) {
			root = new LinearLayout(activity);
			root.setOrientation(LinearLayout.VERTICAL);
			root.setBackgroundColor(Theme.SURFACE);
		}
		root.removeAllViews();
		root.addView(buildHeader(), new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, Theme.dp(150)));

		LinearLayout items = new LinearLayout(activity);
		items.setOrientation(LinearLayout.VERTICAL);
		items.setPadding(Theme.dp(12), Theme.dp(10), Theme.dp(12), Theme.dp(6));
		for (int i = 0; i < ITEM_LABELS.length; i++) {
			items.addView(buildItem(i), new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, Theme.dp(52)));
		}
		root.addView(items, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

		TextView version = Theme.caption(10, Theme.TEXT_FAINT);
		version.setText("Raijin's Lawncher  ·  v1.0");
		version.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		vp.topMargin = Theme.dp(18);
		vp.bottomMargin = Theme.dp(10);
		root.addView(version, vp);
		return root;
	}

	public void setSelected(int index) {
		selected = index;
		rebuild();
	}

	public void refreshProfile() {
		rebuild();
	}

	public void rebuild() {
		if (root != null) build();
	}

	private View buildHeader() {
		LinearLayout header = new LinearLayout(activity);
		header.setOrientation(LinearLayout.VERTICAL);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setPadding(Theme.dp(20), Theme.dp(16), Theme.dp(20), Theme.dp(14));
		header.setBackground(Theme.gradient(Theme.accentStart(), Theme.accentEnd(), 0));
		header.setClickable(true);
		header.setOnClickListener(v -> callback.onProfile());

		View avatar = Theme.avatar(AccountManager.getUsername(activity), 50,
				AccountManager.getAvatarColorIndex());
		header.addView(avatar, new LinearLayout.LayoutParams(Theme.dp(50), Theme.dp(50)));

		TextView name = Theme.text(19, 0xFF0A0E1A, true);
		name.setText(AccountManager.getUsername(activity));
		LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		np.topMargin = Theme.dp(12);
		header.addView(name, np);

		TextView state = Theme.caption(11, 0x990A0E1A);
		state.setText(AccountManager.isSignedIn()
				? "Signed in · tap for profile"
				: "Local profile · tap to edit");
		header.addView(state, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		return header;
	}

	private View buildItem(int idx) {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(Theme.dp(12), 0, Theme.dp(12), 0);
		boolean sel = idx == selected;
		row.setBackground(Theme.rounded(sel ? 0x1F5B8CFF : 0x00000000, Theme.dp(14),
				sel ? Theme.accentStart() : 0, sel ? 1 : 0));

		TextView icon = Theme.text(15, sel ? Theme.accentStart() : ITEM_COLORS[idx], true);
		icon.setText(String.valueOf((char) ITEM_ICONS[idx]));
		icon.setGravity(Gravity.CENTER);
		icon.setBackground(Theme.rounded(sel ? 0x2E5B8CFF : 0x12FFFFFF, Theme.dp(10), 0, 0));
		row.addView(icon, new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36)));

		TextView label = Theme.text(15, sel ? Theme.TEXT : Theme.TEXT_DIM, sel);
		label.setText(ITEM_LABELS[idx]);
		label.setPadding(Theme.dp(14), 0, 0, 0);
		row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

		row.setOnClickListener(v -> {
			selected = idx;
			rebuild();
			callback.onNavigate(idx);
		});
		return row;
	}
}
