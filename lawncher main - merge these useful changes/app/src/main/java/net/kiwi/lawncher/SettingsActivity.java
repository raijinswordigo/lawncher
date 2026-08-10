package net.kiwi.lawncher;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Settings screen. Empty for now - wire up real options here (theme,
 * resolution, memory allocation, etc.) as they're built out.
 *
 * Must be declared in AndroidManifest.xml:
 *   <activity android:name=".SettingsActivity" />
 */
public class SettingsActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		FrameLayout root = new FrameLayout(this);
		root.setBackgroundColor(Color.parseColor(Theme.BG));

		TextView placeholder = new TextView(this);
		placeholder.setText("Settings coming soon.");
		placeholder.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		placeholder.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);

		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
		params.gravity = Gravity.CENTER;
		root.addView(placeholder, params);

		setContentView(root);
	}
}
