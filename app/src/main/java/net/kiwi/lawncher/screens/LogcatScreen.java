package net.kiwi.lawncher.screens;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import net.kiwi.lawncher.ui.Screen;
import net.kiwi.lawncher.ui.Theme;
import net.kiwi.lawncher.util.LogcatReader;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logcat: live stream of this app's own log output with priority filters,
 * pause / resume, clear and copy-to-clipboard.
 */
public class LogcatScreen implements Screen {

	private static final Pattern PRIO = Pattern.compile("\\s\\d+\\s+\\d+\\s+([VDIWEF])\\s");
	private static final int[] PRIO_COLORS = {
			Theme.TEXT_FAINT, // V
			Theme.TEXT_FAINT, // D
			Theme.TEXT_DIM,   // I
			Theme.WARN,       // W
			Theme.DANGER,     // E
			Theme.DANGER      // F
	};
	private static final int MAX_LINES = 500;

	private LinearLayout root;
	private TextView logView;
	private ScrollView logScroll;
	private TextView statusLabel;
	private LinearLayout chipsRow;
	private int minPriority = 0;
	private boolean autoScroll = true;
	private boolean running = false;
	private final List<String> buffer = new ArrayList<>(800);
	private Process reader;
	private boolean dirty = false;
	private final Handler uiHandler = new Handler(Looper.getMainLooper());

	@Override
	public View build() {
		if (root == null) {
			root = new LinearLayout(Theme.activity);
			root.setOrientation(LinearLayout.VERTICAL);
		}
		root.removeAllViews();

		LinearLayout section = new LinearLayout(Theme.activity);
		section.setOrientation(LinearLayout.VERTICAL);
		section.setPadding(Theme.dp(20), Theme.dp(6), Theme.dp(20), Theme.dp(8));
		root.addView(section, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		// Priority filter chips
		HorizontalScrollView chipsScroll = new HorizontalScrollView(Theme.activity);
		chipsScroll.setHorizontalScrollBarEnabled(false);
		chipsRow = new LinearLayout(Theme.activity);
		chipsRow.setOrientation(LinearLayout.HORIZONTAL);
		chipsScroll.addView(chipsRow);
		section.addView(chipsScroll, matchWrap(0, 0, 0, Theme.dp(10)));
		buildChips();

		// Controls
		LinearLayout controls = new LinearLayout(Theme.activity);
		controls.setOrientation(LinearLayout.HORIZONTAL);
		controls.setGravity(Gravity.CENTER_VERTICAL);
		TextView play = controlPill("PAUSE");
		play.setOnClickListener(v -> toggleReading(play));
		controls.addView(play, new LinearLayout.LayoutParams(
				Theme.dp(66), Theme.dp(32)));
		TextView clear = controlPill("CLEAR");
		clear.setOnClickListener(v -> {
			synchronized (buffer) {
				buffer.clear();
			}
			logView.setText("");
		});
		LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Theme.dp(60), Theme.dp(32));
		cp.leftMargin = Theme.dp(8);
		controls.addView(clear, cp);
		TextView copy = controlPill("COPY");
		copy.setOnClickListener(v -> copyBuffer());
		LinearLayout.LayoutParams cp2 = new LinearLayout.LayoutParams(Theme.dp(58), Theme.dp(32));
		cp2.leftMargin = Theme.dp(8);
		controls.addView(copy, cp2);
		TextView auto = controlPill(autoScroll ? "AUTO \u25B2" : "MANUAL");
		auto.setOnClickListener(v -> {
			autoScroll = !autoScroll;
			auto.setText(autoScroll ? "AUTO \u25B2" : "MANUAL");
		});
		LinearLayout.LayoutParams cp3 = new LinearLayout.LayoutParams(Theme.dp(72), Theme.dp(32));
		cp3.leftMargin = Theme.dp(8);
		controls.addView(auto, cp3);
		statusLabel = Theme.caption(10, Theme.TEXT_FAINT);
		statusLabel.setGravity(Gravity.END);
		statusLabel.setText("own pid " + android.os.Process.myPid());
		controls.addView(statusLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		section.addView(controls, matchWrap(0, 0, 0, Theme.dp(10)));

		// Log area
		logScroll = new ScrollView(Theme.activity);
		logScroll.setFillViewport(true);
		logView = new TextView(Theme.activity);
		logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
		logView.setTypeface(android.graphics.Typeface.MONOSPACE);
		logView.setTextColor(Theme.TEXT_DIM);
		logView.setLineSpacing(Theme.dp(1), 1f);
		logView.setText("Waiting for log output\u2026");
		logScroll.addView(logView, new ScrollView.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		section.addView(logScroll, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		TextView note = Theme.caption(9, Theme.TEXT_FAINT);
		note.setText("Shows this app's own logcat stream (other apps are restricted by Android).");
		note.setGravity(Gravity.CENTER);
		section.addView(note, matchWrap(0, Theme.dp(6), 0, 0));

		return root;
	}

	private void buildChips() {
		chipsRow.removeAllViews();
		String[] labels = {"ALL", "V", "D", "I", "W", "E"};
		for (int i = 0; i < labels.length; i++) {
			final int idx = i;
			TextView chip = Theme.text(11, idx == minPriority ? 0xFF0A0E1A : Theme.TEXT_DIM,
					idx == minPriority);
			chip.setText(labels[i]);
			chip.setGravity(Gravity.CENTER);
			chip.setPadding(Theme.dp(12), Theme.dp(6), Theme.dp(12), Theme.dp(6));
			chip.setBackground(Theme.rounded(idx == minPriority ? Theme.accentStart() : Theme.SURFACE_ALT,
					Theme.dp(9), 0, 0));
			chip.setOnClickListener(v -> {
				minPriority = idx;
				buildChips();
				flush();
			});
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.rightMargin = Theme.dp(6);
			chipsRow.addView(chip, lp);
		}
	}

	private TextView controlPill(String label) {
		TextView t = Theme.caption(10, Theme.TEXT);
		t.setText(label);
		t.setGravity(Gravity.CENTER);
		t.setBackground(Theme.rounded(0x1F5B8CFF, Theme.dp(8), 0, 0));
		t.setClickable(true);
		return t;
	}

	@Override
	public void onShown() {
		startReader();
	}

	@Override
	public void onHidden() {
		stopReader();
	}

	@Override
	public boolean onBack() {
		return false;
	}

	// ---- reader ----

	private void startReader() {
		if (running) return;
		Process p = LogcatReader.startLive(android.os.Process.myPid(), line -> {
			synchronized (buffer) {
				buffer.add(line);
				if (buffer.size() > 800) buffer.remove(0);
			}
			dirty = true;
		});
		if (p == null) {
			statusLabel.setText("logcat unavailable on this device");
			return;
		}
		running = true;
		reader = p;
		// Throttled flusher (~3.3 flushes/sec max)
		uiHandler.postDelayed(new Runnable() {
			@Override public void run() {
				if (dirty) {
					dirty = false;
					flush();
				}
				if (running) uiHandler.postDelayed(this, 300);
			}
		}, 300);
	}

	private void stopReader() {
		running = false;
		LogcatReader.stop(reader);
		reader = null;
	}

	private void toggleReading(TextView play) {
		if (running) {
			stopReader();
			play.setText("PLAY");
			statusLabel.setText("paused \u00B7 " + buffer.size() + " lines");
		} else {
			startReader();
			play.setText("PAUSE");
			statusLabel.setText("live \u00B7 own pid " + android.os.Process.myPid());
		}
	}

	private void flush() {
		if (logView == null) return;
		List<String> lines;
		synchronized (buffer) {
			lines = new ArrayList<>(buffer);
		}
		SpannableStringBuilder sb = new SpannableStringBuilder();
		int shown = 0;
		for (int i = Math.max(0, lines.size() - MAX_LINES); i < lines.size(); i++) {
			String line = lines.get(i);
			int color = colorFor(line);
			if (color < 0) continue;
			shown++;
			int start = sb.length();
			sb.append(line).append('\n');
			sb.setSpan(new ForegroundColorSpan(color), start, start + line.length(),
					SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		logView.setText(sb);
		statusLabel.setText((running ? "live" : "paused") + " \u00B7 " + shown + " lines shown");
		if (autoScroll) {
			logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
		}
	}

	private int colorFor(String line) {
		Matcher m = PRIO.matcher(line);
		if (!m.find()) return Theme.TEXT_FAINT;
		String p = m.group(1);
		int idx = "VDIWEF".indexOf(p);
		if (idx < 0) return Theme.TEXT_FAINT;
		if (idx < minPriority) return -1;
		return PRIO_COLORS[idx];
	}

	private void copyBuffer() {
		StringBuilder sb = new StringBuilder();
		synchronized (buffer) {
			for (String line : buffer) sb.append(line).append('\n');
		}
		ClipboardManager cm = (ClipboardManager) Theme.activity.getSystemService(
				android.content.Context.CLIPBOARD_SERVICE);
		if (cm != null) {
			cm.setPrimaryClip(ClipData.newPlainText("logcat", sb.toString()));
			Toast.makeText(Theme.activity, "Log copied to clipboard", Toast.LENGTH_SHORT).show();
		}
	}

	private static LinearLayout.LayoutParams matchWrap(int l, int t, int r, int b) {
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		p.setMargins(l, t, r, b);
		return p;
	}
}
