package net.kiwi.lawncher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small shared top bar: hamburger (opens {@link Sidebar}) + screen title. */
public final class TopBar {
	private TopBar() {}

	static LinearLayout build(Activity activity, String title) {
		LinearLayout bar = new LinearLayout(activity);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.CENTER_VERTICAL);
		bar.setPadding(Theme.dp(activity, 12), Theme.dp(activity, 28), Theme.dp(activity, 16), Theme.dp(activity, 16));
		bar.setOnApplyWindowInsetsListener((v, insets) -> {
			v.setPadding(
			insets.getSystemWindowInsetLeft(),
			insets.getSystemWindowInsetTop(),
			insets.getSystemWindowInsetRight(),
			insets.getSystemWindowInsetBottom()
			);
			return insets;
		});
		TextView hamburger = new TextView(activity);
		hamburger.setText("\u2630");
		hamburger.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
		hamburger.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		hamburger.setGravity(Gravity.CENTER);
		hamburger.setBackground(Theme.circleBackground(Theme.CARD));
		hamburger.setContentDescription("Open menu");
		hamburger.setOnClickListener(v -> Sidebar.toggle());
		LinearLayout.LayoutParams hbParams = new LinearLayout.LayoutParams(Theme.dp(activity, 40), Theme.dp(activity, 40));
		hbParams.rightMargin = Theme.dp(activity, 12);
		bar.addView(hamburger, hbParams);

		TextView label = new TextView(activity);
		label.setText(title);
		label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
		label.setTypeface(null, Typeface.BOLD);
		label.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		bar.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		return bar;
	}
}
