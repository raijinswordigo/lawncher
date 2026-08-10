package net.kiwi.lawncher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

/**
 * Shared Catppuccin Mocha-ish palette + small view helpers, reused by
 * {@link Sidebar}, {@link TopBar}, {@link FilesScreen}, {@link LogcatScreen}
 * and {@link SettingsActivity}. Launcher.java keeps its own copy of these
 * constants (untouched here) to avoid a risky refactor of working code.
 */
final class Theme {
	private Theme() {}

	static final String BG = "#11111B";
	static final String CARD = "#1E1E2E";
	static final String BORDER = "#313244";
	static final String TEXT_MAIN = "#CDD6F4";
	static final String TEXT_DIM = "#BAC2DE";
	static final String ACCENT_GREEN = "#A6E3A1";
	static final String ACCENT_BLUE = "#89B4FA";
	static final String ACCENT_RED = "#F38BA8";
	static final String ACCENT_DARK_TEXT = "#11111B";

	static int dp(Context ctx, int dp) {
		return (int) (dp * ctx.getResources().getDisplayMetrics().density);
	}

	static GradientDrawable circleBackground(String color) {
		GradientDrawable bg = new GradientDrawable();
		bg.setShape(GradientDrawable.OVAL);
		bg.setColor(Color.parseColor(color));
		return bg;
	}

	static Drawable rippleBackground(int cornerRadiusPx, String baseColor) {
		GradientDrawable mask = new GradientDrawable();
		mask.setCornerRadius(cornerRadiusPx);
		mask.setColor(Color.parseColor(baseColor));
		return new RippleDrawable(ColorStateList.valueOf(Color.parseColor(BORDER)), mask, null);
	}
}
