package net.kiwi.lawncher.screens;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import net.kiwi.lawncher.ModManager;
import net.kiwi.lawncher.accounts.AccountManager;
import net.kiwi.lawncher.billing.BillingManager;
import net.kiwi.lawncher.store.StoreManager;
import net.kiwi.lawncher.ui.LauncherShell;
import net.kiwi.lawncher.ui.Screen;
import net.kiwi.lawncher.ui.Theme;

import java.util.List;

/** Profile: username infrastructure, avatar, stats and purchase history. */
public class ProfileScreen implements Screen {

	private LinearLayout root;

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

		// Hero
		LinearLayout hero = new LinearLayout(Theme.activity);
		hero.setOrientation(LinearLayout.VERTICAL);
		hero.setGravity(Gravity.CENTER_HORIZONTAL);
		hero.setPadding(Theme.dp(20), Theme.dp(24), Theme.dp(20), Theme.dp(20));
		hero.setBackground(Theme.gradient(Theme.accentStart(), Theme.accentEnd(), Theme.dp(20)));

		View avatar = Theme.avatar(AccountManager.getUsername(Theme.activity), 64,
				AccountManager.getAvatarColorIndex());
		hero.addView(avatar, new LinearLayout.LayoutParams(Theme.dp(64), Theme.dp(64)));

		TextView name = Theme.text(22, 0xFF0A0E1A, true);
		name.setText(AccountManager.getUsername(Theme.activity));
		LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		np.topMargin = Theme.dp(12);
		hero.addView(name, np);

		TextView state = Theme.caption(11, 0x990A0E1A);
		state.setText(AccountManager.isSignedIn()
				? "Signed in \u00B7 profile is synced-ready"
				: "Local profile \u00B7 sign in when cloud sync launches");
		hero.addView(state, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		col.addView(hero, matchWrap(0, 0, 0, Theme.dp(16)));

		// Actions
		LinearLayout actions = new LinearLayout(Theme.activity);
		actions.setOrientation(LinearLayout.HORIZONTAL);
		actions.addView(actionPill("EDIT USERNAME"), new LinearLayout.LayoutParams(
				0, Theme.dp(40), 1f));
		TextView avatarBtn = actionPill("AVATAR COLOR");
		LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, Theme.dp(40), 1f);
		ap.leftMargin = Theme.dp(8);
		actions.addView(avatarBtn, ap);
		TextView signBtn = actionPill(AccountManager.isSignedIn() ? "SIGN OUT" : "SIGN IN");
		LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(0, Theme.dp(40), 1f);
		sp2.leftMargin = Theme.dp(8);
		actions.addView(signBtn, sp2);

		TextView usernameAction = (TextView) actions.getChildAt(0);
		usernameAction.setOnClickListener(v -> promptUsername());
		avatarBtn.setOnClickListener(v -> {
			AccountManager.cycleAvatarColor();
			Toast.makeText(Theme.activity, "Avatar color changed", Toast.LENGTH_SHORT).show();
			LauncherShell.rebuildForAppearance();
		});
		signBtn.setOnClickListener(v -> toggleSignIn());
		col.addView(actions, matchWrap(0, 0, 0, Theme.dp(16)));

		// Stats
		LinearLayout statsRow = new LinearLayout(Theme.activity);
		statsRow.setOrientation(LinearLayout.HORIZONTAL);
		statsRow.addView(statCard("MODS",
				String.valueOf(ModManager.listInstalledMods(Theme.activity,
					net.kiwi.lawncher.InstanceManager.resolveActiveInstance(Theme.activity)).size())), new LinearLayout.LayoutParams(
				0, Theme.dp(88), 1f));
		statsRow.addView(statCard("STORE",
				String.valueOf(StoreManager.storeInstallsCount())), new LinearLayout.LayoutParams(
				0, Theme.dp(88), 1f));
		LinearLayout.LayoutParams statsGap = new LinearLayout.LayoutParams(0, Theme.dp(88), 1f);
		statsGap.leftMargin = Theme.dp(8);
		statsRow.addView(statCard("PURCHASES",
				String.valueOf(BillingManager.get().purchaseHistory().size())), statsGap);
		col.addView(statsRow, matchWrap(0, 0, 0, Theme.dp(16)));

		// Device
		TextView deviceLabel = Theme.caption(11, Theme.TEXT_FAINT);
		deviceLabel.setText("DEVICE ID");
		col.addView(deviceLabel, matchWrap(0, 0, 0, Theme.dp(8)));
		TextView device = Theme.caption(12, Theme.TEXT_DIM);
		device.setText(AccountManager.getDeviceId(Theme.activity));
		device.setTypeface(android.graphics.Typeface.MONOSPACE);
		device.setPadding(Theme.dp(14), Theme.dp(12), Theme.dp(14), Theme.dp(12));
		device.setBackground(Theme.rounded(Theme.SURFACE, Theme.dp(12), Theme.BORDER, 1));
		device.setClickable(true);
		device.setOnClickListener(v -> {
			ClipboardManager cm = (ClipboardManager) Theme.activity.getSystemService(
					android.content.Context.CLIPBOARD_SERVICE);
			if (cm != null) {
				cm.setPrimaryClip(ClipData.newPlainText("device_id", device.getText()));
				Toast.makeText(Theme.activity, "Device ID copied", Toast.LENGTH_SHORT).show();
			}
		});
		col.addView(device, matchWrap(0, 0, 0, Theme.dp(20)));

		// Purchases
		TextView purchasesLabel = Theme.caption(11, Theme.TEXT_FAINT);
		purchasesLabel.setText("PURCHASE HISTORY");
		col.addView(purchasesLabel, matchWrap(0, 0, 0, Theme.dp(8)));
		List<String> history = BillingManager.get().purchaseHistory();
		if (history.isEmpty()) {
			TextView empty = Theme.text(12, Theme.TEXT_DIM, false);
			empty.setText("No purchases yet \u2014 store mods, donations and Premium\nwill show up here.");
			empty.setLineSpacing(Theme.dp(3), 1f);
			col.addView(empty, matchWrap(0, 0, 0, Theme.dp(8)));
		} else {
			for (final String entry : history) {
				TextView row = Theme.caption(11, Theme.TEXT_DIM);
				String[] parts = entry.split("\\|");
				row.setText((parts.length > 0 ? parts[0] : entry) + " \u00B7 order "
						+ (parts.length > 1 ? parts[1] : "?"));
				row.setPadding(Theme.dp(14), Theme.dp(11), Theme.dp(14), Theme.dp(11));
				row.setBackground(Theme.rounded(Theme.SURFACE, Theme.dp(12), Theme.BORDER, 1));
				col.addView(row, matchWrap(0, 0, 0, Theme.dp(8)));
			}
		}

		TextView support = Theme.text(13, Theme.TEXT_DIM, false);
		support.setText("Support the project \u2014 donations and Lawncher Premium live in the store.");
		support.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams sp3 = matchWrap(0, Theme.dp(18), 0, 0);
		col.addView(support, sp3);

		scroll.addView(col, new ScrollView.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		root.addView(scroll, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		return root;
	}

	@Override
	public void onShown() {
	}

	@Override
	public void onHidden() {
	}

	@Override
	public boolean onBack() {
		return false;
	}

	// ---- actions ----

	private void promptUsername() {
		final EditText input = new EditText(Theme.activity);
		input.setSingleLine(true);
		input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		input.setHint("3\u201316 letters, numbers, _");
		final TextView error = Theme.caption(11, Theme.DANGER);
		error.setVisibility(View.GONE);

		LinearLayout content = new LinearLayout(Theme.activity);
		content.setOrientation(LinearLayout.VERTICAL);
		content.setPadding(Theme.dp(24), Theme.dp(8), Theme.dp(24), 0);
		content.addView(input);
		content.addView(error, matchWrap(0, Theme.dp(6), 0, 0));

		final AlertDialog dialog = new AlertDialog.Builder(Theme.activity)
				.setTitle("Choose a username")
				.setView(content)
				.setPositiveButton("Save", (d, which) -> { })
				.setNegativeButton("Cancel", null)
				.create();
		dialog.setOnShowListener(d -> {
			dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
				String value = input.getText().toString().trim();
				if (!AccountManager.isValidUsername(value)) {
					error.setVisibility(View.VISIBLE);
					error.setText("Use 3\u201316 characters: letters, numbers, underscore.");
					return;
				}
				if (!AccountManager.isUsernameAvailable(value)) {
					error.setVisibility(View.VISIBLE);
					error.setText("That username is taken.");
					return;
				}
				AccountManager.setUsername(Theme.activity, value);
				dialog.dismiss();
				Toast.makeText(Theme.activity, "Username set: " + value, Toast.LENGTH_SHORT).show();
				LauncherShell.rebuildForAppearance();
			});
		});
		dialog.show();
	}

	private void toggleSignIn() {
		if (AccountManager.isSignedIn()) {
			AccountManager.setSignedIn(false);
			Toast.makeText(Theme.activity, "Signed out", Toast.LENGTH_SHORT).show();
		} else {
			new AlertDialog.Builder(Theme.activity)
					.setTitle("Sign in")
					.setMessage("Cloud sync isn't live yet, so signing in just marks this "
							+ "device as signed in for \u201C" + AccountManager.getUsername(Theme.activity)
							+ "\u201D.\n\nWhen the account backend ships, this becomes a real "
							+ "Kiwi ID login.")
					.setPositiveButton("Sign in locally", (d, which) -> {
						AccountManager.setSignedIn(true);
						Toast.makeText(Theme.activity, "Signed in as "
								+ AccountManager.getUsername(Theme.activity), Toast.LENGTH_SHORT).show();
						LauncherShell.rebuildForAppearance();
					})
					.setNegativeButton("Cancel", null)
					.show();
		}
	}

	// ---- helpers ----

	private static TextView actionPill(String label) {
		TextView t = Theme.caption(10, Theme.TEXT);
		t.setText(label);
		t.setGravity(Gravity.CENTER);
		t.setBackground(Theme.rounded(0x1F5B8CFF, Theme.dp(10), 0, 0));
		t.setClickable(true);
		return t;
	}

	private static View statCard(String label, String value) {
		LinearLayout card = new LinearLayout(Theme.activity);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setGravity(Gravity.CENTER);
		card.setBackground(Theme.rounded(Theme.SURFACE, Theme.dp(14), Theme.BORDER, 1));
		TextView v = Theme.text(22, Theme.TEXT, true);
		v.setText(value);
		card.addView(v);
		TextView l = Theme.caption(9, Theme.TEXT_FAINT);
		l.setText(label);
		card.addView(l, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return card;
	}

	private static LinearLayout.LayoutParams matchWrap(int l, int t, int r, int b) {
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		p.setMargins(l, t, r, b);
		return p;
	}
}
