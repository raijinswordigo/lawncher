package net.kiwi.lawncher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared palette + helpers. Accents are mutable so Settings can switch presets
 * at runtime. Launcher/Sidebar recolor on change via notifyThemeChanged().
 */
final class Theme {
	private Theme() {}

	static String BG = "#11111B";
	static String CARD = "#1E1E2E";
	static String BORDER = "#313244";
	static String TEXT_MAIN = "#CDD6F4";
	static String TEXT_DIM = "#BAC2DE";
	static String ACCENT_GREEN = "#A6E3A1";
	static String ACCENT_BLUE = "#89B4FA";
	static String ACCENT_RED = "#F38BA8";
	static String ACCENT_DARK_TEXT = "#11111B";

	static final String PREFS = "lawncher_prefs";
	static final String KEY_THEME = "theme_preset";

	static final String[] PRESET_NAMES = {
			"Catppuccin Mocha",
			"Nord",
			"Dracula",
			"Rose Pine",
			"Gruvbox",
			"Blurple"
	};

	private static final String[][] PRESETS = {
			{"#11111B", "#1E1E2E", "#313244", "#CDD6F4", "#BAC2DE", "#A6E3A1", "#89B4FA", "#F38BA8", "#11111B"},
			{"#2E3440", "#3B4252", "#4C566A", "#ECEFF4", "#D8DEE9", "#A3BE8C", "#88C0D0", "#BF616A", "#2E3440"},
			{"#282A36", "#44475A", "#6272A4", "#F8F8F2", "#BD93F9", "#50FA7B", "#8BE9FD", "#FF5555", "#282A36"},
			{"#191724", "#1F1D2E", "#26233A", "#E0DEF4", "#908CAA", "#9CCFD8", "#C4A7E7", "#EB6F92", "#191724"},
			{"#282828", "#3C3836", "#504945", "#EBDBB2", "#A89984", "#B8BB26", "#83A598", "#FB4934", "#282828"},
			{"#0F0E17", "#1A1826", "#2A2740", "#E8E6F2", "#A39EC0", "#57F287", "#5865F2", "#ED4245", "#0F0E17"},
	};

	interface Refreshable {
		void onThemeChanged();
	}

	private static final List<WeakReference<Refreshable>> listeners = new ArrayList<>();
	private static WeakReference<Activity> activityRef;

	static void register(Refreshable r) {
		listeners.add(new WeakReference<>(r));
	}

	static void setActivity(Activity act) {
		activityRef = new WeakReference<>(act);
	}

	static void load(Context ctx) {
		SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		applyPreset(p.getInt(KEY_THEME, 0));
	}

	static void applyPreset(int index) {
		if (index < 0 || index >= PRESETS.length) index = 0;
		String[] c = PRESETS[index];
		BG = c[0];
		CARD = c[1];
		BORDER = c[2];
		TEXT_MAIN = c[3];
		TEXT_DIM = c[4];
		ACCENT_GREEN = c[5];
		ACCENT_BLUE = c[6];
		ACCENT_RED = c[7];
		ACCENT_DARK_TEXT = c[8];
	}

	static int currentPreset(Context ctx) {
		return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_THEME, 0);
	}

	static void savePreset(Context ctx, int index) {
		ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
				.edit().putInt(KEY_THEME, index).apply();
		applyPreset(index);
		notifyThemeChanged();
	}

	static void notifyThemeChanged() {
		for (int i = listeners.size() - 1; i >= 0; i--) {
			Refreshable r = listeners.get(i).get();
			if (r == null) listeners.remove(i);
			else try { r.onThemeChanged(); } catch (Exception ignored) {}
		}
		Activity act = activityRef != null ? activityRef.get() : null;
		if (act != null) {
			act.runOnUiThread(() -> {
				try {
					Sidebar.recolor();
					Launcher.recolor();
				} catch (Exception ignored) {}
			});
		}
	}

	static int dp(Context ctx, int dp) {
		return (int) (dp * ctx.getResources().getDisplayMetrics().density);
	}

	public static void attachToRoot(View root) {
		// Only pad for system bars — never for the IME. Including the keyboard
		// inset here is what shoved search bars / lists down and left a huge gap.
		root.setOnApplyWindowInsetsListener((v, insets) -> {
			int left = insets.getSystemWindowInsetLeft();
			int top = insets.getSystemWindowInsetTop();
			int right = insets.getSystemWindowInsetRight();
			int bottom = insets.getSystemWindowInsetBottom();
			// On API 30+ systemWindowInsetBottom includes IME; prefer stable/systemBars only.
			if (android.os.Build.VERSION.SDK_INT >= 30) {
				android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
				left = bars.left;
				top = bars.top;
				right = bars.right;
				bottom = bars.bottom;
			}
			v.setPadding(left, top, right, bottom);
			return insets;
		});
		root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
			@Override public void onViewAttachedToWindow(View v) { v.requestApplyInsets(); }
			@Override public void onViewDetachedFromWindow(View v) {}
		});
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
