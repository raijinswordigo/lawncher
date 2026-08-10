package net.kiwi.lawncher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Process;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Live "Logcat" screen. Runs `logcat --pid=<our pid>` in the background and
 * streams it into a scrolling TextView. Filtering by pid (rather than by tag)
 * means this picks up everything from this process - Java Log calls *and*
 * native __android_log_print output (LOGV/LOGD/LOGI/LOGW/LOGE/LOGF), since
 * native code runs in the same process. No READ_LOGS permission is needed:
 * that restriction only applies to reading *other* apps' logs.
 *
 * Note: --pid requires Android 7.0+ (API 24). On older devices this falls
 * back to showing the whole system log if the filtered command fails.
 */

class LogcatScreen {

	private static java.lang.Process process;
	private static volatile boolean running;

	static View build(Activity activity) {
		stop(); // in case a previous instance is somehow still running

		LinearLayout screen = new LinearLayout(activity);
		screen.setOrientation(LinearLayout.VERTICAL);
		screen.setBackgroundColor(Color.parseColor(Theme.BG));

		screen.addView(TopBar.build(activity, "Logcat"), new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		TextView log = new TextView(activity);
		log.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		log.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
		log.setTypeface(Typeface.MONOSPACE);
		log.setPadding(Theme.dp(activity, 16), Theme.dp(activity, 4), Theme.dp(activity, 16), Theme.dp(activity, 24));
		log.setTextIsSelectable(true);

		ScrollView scroll = new ScrollView(activity);
		scroll.addView(log, new ScrollView.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		screen.addView(scroll, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		start(activity, log, scroll);
		return screen;
	}

	private static void start(Activity activity, TextView log, ScrollView scroll) {
		running = true;
		StringBuilder buffer = new StringBuilder();

		new Thread(() -> {
			try {
				process = Runtime.getRuntime().exec("logcat --pid=" + Process.myPid() + " -v brief");
				readLoop(process, activity, log, scroll, buffer);
			} catch (IOException e) {
				try {
					// Pre-API 24 fallback: no --pid support, show the unfiltered log instead.
					process = Runtime.getRuntime().exec("logcat -v brief");
					readLoop(process, activity, log, scroll, buffer);
				} catch (IOException ignored) {
				}
			}
		}).start();
	}

	private static void readLoop(java.lang.Process proc, Activity activity, TextView log, ScrollView scroll, StringBuilder buffer) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
		String line;
		while (running && (line = reader.readLine()) != null) {
			buffer.append(line).append('\n');
			if (buffer.length() > 20000) buffer.delete(0, buffer.length() - 20000);

			String snapshot = buffer.toString();
			activity.runOnUiThread(() -> {
				log.setText(snapshot);
				scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
			});
		}
	}

	/** Stops the background reader. Safe to call even if nothing is running. */
	static void stop() {
		running = false;
	}
}
