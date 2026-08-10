package net.kiwi.lawncher.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import net.kiwi.lawncher.util.Prefs;

/**
 * Central design system: palette, gradients, typography and tiny view
 * factories. Everything is drawn in code — no bundled drawable assets.
 */
public final class Theme {

	private Theme() {}

	public static Activity activity;

	// ---- base palette: deep space navy ----
	public static final int BG = 0xFF0A0E1A;
	public static final int SURFACE = 0xFF121829;
	public static final int SURFACE_ALT = 0xFF1B2438;
	public static final int BORDER = 0xFF27314F;
	public static final int TEXT = 0xFFE9EDF9;
	public static final int TEXT_DIM = 0xFF97A1BC;
	public static final int TEXT_FAINT = 0xFF5E6886;
	public static final int SUCCESS = 0xFF3DDC97;
	public static final int WARN = 0xFFFFB454;
	public static final int DANGER = 0xFFFF5C7A;
	public static final int GOLD = 0xFFFFD166;

	/** Accent pairs (start, end) cycled from Settings → Appearance. */
	private static final int[][] ACCENTS = {
			{0xFF5B8CFF, 0xFF9C6CFF}, // Indigo → Violet
			{0xFF22D3EE, 0xFF6366F1}, // Cyan → Indigo
			{0xFF3DDC97, 0xFF22C9D0}, // Mint → Teal
			{0xFFFF8A5C, 0xFFFF5C7A}, // Coral → Rose
			{0xFFFFD166, 0xFFFF8A5C}, // Gold → Coral
	};

	public static int accentCount() {
		return ACCENTS.length;
	}

	public static int[] accentPair(int index) {
		int i = index >= 0 && index < ACCENTS.length ? index : 0;
		return ACCENTS[i];
	}

	public static int accentIndex() {
		int i = Prefs.getInt("accent", 0);
		return i >= 0 && i < ACCENTS.length ? i : 0;
	}

	public static int accentStart() { return ACCENTS[accentIndex()][0]; }
	public static int accentEnd() { return ACCENTS[accentIndex()][1]; }
	public static int accent() { return accentStart(); }

	public static int dp(int v) {
		return Math.round(v * (activity == null ? 1f : activity.getResources().getDisplayMetrics().density));
	}

	public static int sp(int v) {
		return Math.round(v * (activity == null ? 1f : activity.getResources().getDisplayMetrics().scaledDensity));
	}

	// ---- drawables ----

	public static GradientDrawable gradient(int start, int end, int radiusPx) {
		GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
		g.setCornerRadius(radiusPx);
		return g;
	}

	public static GradientDrawable rounded(int fill, int radiusPx, int strokeColor, int strokePx) {
		GradientDrawable g = new GradientDrawable();
		g.setColor(fill);
		g.setCornerRadius(radiusPx);
		if (strokeColor != 0 && strokePx > 0) g.setStroke(strokePx, strokeColor);
		return g;
	}

	public static Drawable ripple(int radiusPx) {
		return ripple(radiusPx, Theme.SURFACE);
	}

	public static Drawable ripple(int radiusPx, int baseColor) {
		GradientDrawable mask = new GradientDrawable();
		mask.setCornerRadius(radiusPx);
		mask.setColor(baseColor);
		return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), mask, null);
	}

	/** Deterministic gradient tile for mod icons / avatars. */
	public static GradientDrawable iconTile(int seed, int radiusPx) {
		int[] acc = ACCENTS[(seed & 0x7FFFFFFF) % ACCENTS.length];
		return gradient(acc[0], acc[1], radiusPx);
	}

	// ---- text ----

	public static TextView text(int sizeSp, int color, boolean bold) {
		TextView t = new TextView(activity);
		t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
		t.setTextColor(color);
		t.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
		return t;
	}

	public static TextView caption(int sizeSp, int color) {
		TextView t = text(sizeSp, color, false);
		t.setLetterSpacing(0.08f);
		return t;
	}

	// ---- avatar ----

	/** Gradient circle with the first letter of {@code name}. */
	public static View avatar(String name, int sizeDp, int accentIdx) {
		FrameLayout wrap = new FrameLayout(activity);
		wrap.setBackground(gradient(accentPair(accentIdx)[0], accentPair(accentIdx)[1], dp(sizeDp) / 2));
		TextView letter = text(Math.round(sizeDp * 0.42f), 0xFF0A0E1A, true);
		String n = name == null ? "" : name.trim();
		letter.setText(n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase(java.util.Locale.ROOT));
		letter.setGravity(Gravity.CENTER);
		wrap.addView(letter, new FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), Gravity.CENTER));
		return wrap;
	}
}
