
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

class LogcatScreen {

	private static final int MAX_LOG_SIZE = 20000;
	private static final long UPDATE_INTERVAL = 50;

	private static java.lang.Process process;
	private static volatile boolean running;

	static View build(Activity activity) {
		stop();

		LinearLayout screen = new LinearLayout(activity);
		Theme.attachToRoot(screen);
		screen.setOrientation(LinearLayout.VERTICAL);
		screen.setBackgroundColor(Color.parseColor(Theme.BG));

		screen.addView(
		TopBar.build(activity, "Logcat"),
		new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT,
		ViewGroup.LayoutParams.WRAP_CONTENT
		)
		);

		TextView log = new TextView(activity);
		log.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		log.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
		log.setTypeface(Typeface.MONOSPACE);
		log.setTextIsSelectable(true);
		log.setGravity(View.TEXT_ALIGNMENT_TEXT_START);
		log.setPadding(
		Theme.dp(activity, 16),
		Theme.dp(activity, 4),
		Theme.dp(activity, 16),
		Theme.dp(activity, 24)
		);

		ScrollView scroll = new ScrollView(activity);
		scroll.setFillViewport(false);
		scroll.setSmoothScrollingEnabled(true);

		scroll.addView(log, new ScrollView.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT,
		ViewGroup.LayoutParams.WRAP_CONTENT
		));

		screen.addView(scroll, new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT,
		0,
		1f
		));

		start(activity, log, scroll);

		return screen;
	}

	private static void start(
	Activity activity,
	TextView log,
	ScrollView scroll
	) {
		running = true;

		new Thread(() -> {
			java.lang.Process proc = null;

			try {
				proc = new ProcessBuilder(
				"logcat",
				"--pid=" + Process.myPid(),
				"-v",
				"brief"
				).redirectErrorStream(true).start();

				process = proc;
				readLoop(activity, log, scroll, proc);

			} catch (IOException e) {
				if (running) {
					activity.runOnUiThread(() ->
					log.setText("Failed to start logcat:\n" + e.getMessage())
					);
				}
			} finally {
				if (proc != null) {
					proc.destroy();

					if (process == proc)
						process = null;
				}
			}
		}, "Lawncher-Logcat").start();
	}

	private static void readLoop(
	Activity activity,
	TextView log,
	ScrollView scroll,
	java.lang.Process proc
	) throws IOException {

		StringBuilder buffer = new StringBuilder(MAX_LOG_SIZE);
		StringBuilder pending = new StringBuilder();
		long lastUpdate = 0;

		try (BufferedReader reader = new BufferedReader(
		new InputStreamReader(proc.getInputStream())
		)) {
			String line;

			while (running && (line = reader.readLine()) != null) {
				if (line.contains("MotionEvent")
				|| line.contains("OverScroller")
				|| line.contains("D/Editor"))
					continue;

				pending.append(line).append('\n');

				long now = System.currentTimeMillis();

				if (now - lastUpdate < UPDATE_INTERVAL)
					continue;

				buffer.append(pending);
				pending.setLength(0);

				if (buffer.length() > MAX_LOG_SIZE) {
					int remove = buffer.length() - MAX_LOG_SIZE;
					int newline = buffer.indexOf("\n", remove);

					buffer.delete(
					0,
					newline >= 0 ? newline + 1 : remove
					);
				}

				String text = buffer.toString();

				activity.runOnUiThread(() -> {
					if (!running || activity.isFinishing())
						return;

					int x = scroll.getScrollX();
					int y = scroll.getScrollY();

					log.setText(text);

					scroll.post(() -> {
						if (running)
							scroll.scrollTo(x, y);
					});
				});

				lastUpdate = now;
			}
		}
	}

	static void stop() {
		running = false;

		java.lang.Process proc = process;
		process = null;

		if (proc != null)
			proc.destroy();
	}
}
