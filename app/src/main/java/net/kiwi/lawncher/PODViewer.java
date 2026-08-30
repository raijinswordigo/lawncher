package net.kiwi.lawncher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import net.kiwi.lawncher.filerift.Filerift;

import java.io.File;
import java.nio.file.Files;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Interactive 3D POD model viewer (Raijin's request).
 *
 * A faithful remaster of Ruby's POD viewer: renders with a real OpenGL ES 3.0
 * pipeline (Lambertian shaders, diffuse texture mapping, node transforms,
 * CPU skinning, XZ grid) via a GLSurfaceView. Supports orbit (drag), pinch
 * zoom, wireframe toggle, grid toggle, and animation scrubbing.
 */
class PODViewer {

	private static final Activity activity() { return FilesScreen.activity; }

	@SuppressLint("ClickableViewAccessibility")
	static void show(File file) {
		final Activity act = activity();
		if (act == null) return;

		byte[] data = new byte[0];
		final String baseDir;
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				data = Files.readAllBytes(file.toPath());
			}
			baseDir = file.getParentFile() != null ? file.getParentFile().getAbsolutePath() : "";
		} catch (Exception e) {
			Toast.makeText(act, "Couldn't read " + file.getName(), Toast.LENGTH_SHORT).show();
			return;
		}

		Dialog dialog = new Dialog(act, android.R.style.Theme_DeviceDefault_NoActionBar);
		LinearLayout root = new LinearLayout(act);
		Theme.attachToRoot(root);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(Color.parseColor(Theme.BG));

		// ── Header ──────────────────────────────────────────────────────
		LinearLayout header = new LinearLayout(act);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setPadding(Theme.dp(act, 18), Theme.dp(act, 14),
		Theme.dp(act, 12), Theme.dp(act, 14));

		LinearLayout titleCol = new LinearLayout(act);
		titleCol.setOrientation(LinearLayout.VERTICAL);
		titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		TextView title = new TextView(act);
		title.setText(file.getName());
		title.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		title.setTypeface(null, Typeface.BOLD);
		title.setMaxLines(1);
		titleCol.addView(title);

		final TextView info = new TextView(act);
		info.setText("Loading\u2026");
		info.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
		info.setMaxLines(1);
		titleCol.addView(info);

		header.addView(titleCol);

		// Wireframe toggle
		final boolean[] wireframe = {false};
		TextView btnWire = new TextView(act);
		btnWire.setText("Wireframe");
		btnWire.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		btnWire.setPadding(Theme.dp(act, 10), Theme.dp(act, 6),
		Theme.dp(act, 10), Theme.dp(act, 6));
		btnWire.setBackground(Theme.rippleBackground(Theme.dp(act, 8), Theme.CARD));
		btnWire.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		header.addView(btnWire);

		// Grid toggle
		final boolean[] grid = {true};
		TextView btnGrid = new TextView(act);
		btnGrid.setText("Grid");
		btnGrid.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		btnGrid.setPadding(Theme.dp(act, 10), Theme.dp(act, 6),
		Theme.dp(act, 10), Theme.dp(act, 6));
		btnGrid.setBackground(Theme.rippleBackground(Theme.dp(act, 8), Theme.CARD));
		btnGrid.setTextColor(Color.parseColor(Theme.ACCENT_BLUE));
		header.addView(btnGrid);

		ImageView btnClose = new ImageView(act);
		btnClose.setImageResource(R.drawable.ic_close);
		btnClose.setColorFilter(Color.parseColor(Theme.TEXT_DIM));
		btnClose.setPadding(Theme.dp(act, 12), 0, 0, 0);
		header.addView(btnClose);

		root.addView(header);

		// ── GL surface ──────────────────────────────────────────────────
		final GLSurfaceView glView = new GLSurfaceView(act);
		glView.setEGLContextClientVersion(3);
		glView.setLayoutParams(new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		// Camera state (Ruby orbit convention: yaw/pitch degrees, distance).
		// dist is a ZOOM MULTIPLIER of the model's auto-fit distance (1f =
		// fit the whole model in view), matching podgl::render()'s
		// `cam_dist = radius * 2.6f * zoom` on the native side — it is NOT
		// an absolute world-space distance, so it must stay in a small
		// range around 1, not the old 1..500 absolute-units range.
		final float[] yaw = {45f}, pitch = {30f}, dist = {1f};
		final float[] frame = {0f};
		final boolean[] animate = {false};

		final int[] handle = {-1};
		final int[] frameCount = {0};

		byte[] finalData = data;
		glView.setRenderer(new GLSurfaceView.Renderer() {
			@Override
			public void onSurfaceCreated(GL10 gl, EGLConfig config) {
				Filerift.podGLInit();
				handle[0] = Filerift.podGLLoad(finalData, baseDir);
				if (handle[0] < 0) {
					info.post(() -> info.setText("Couldn't load POD"));
					return;
				}
				frameCount[0] = Filerift.podGLFrameCount(handle[0]);
				String i = Filerift.podGLInfo(handle[0]);
				info.post(() -> info.setText(i == null ? "" : i));
			}

			@Override
			public void onSurfaceChanged(GL10 gl, int width, int height) {
				// viewport handled in render()
			}

			@Override
			public void onDrawFrame(GL10 gl) {
				if (handle[0] < 0) return;
				if (animate[0] && frameCount[0] > 1) {
					frame[0] = (frame[0] + 0.5f) % frameCount[0];
				}
				Filerift.podGLRender(handle[0], glView.getWidth(), glView.getHeight(),
				yaw[0], pitch[0], dist[0], frame[0],
				wireframe[0], grid[0], false);
			}
		});
		glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

		root.addView(glView);

		// ── Controls row: animation scrubber + play + reset ────────────
		LinearLayout controls = new LinearLayout(act);
		controls.setGravity(Gravity.CENTER_VERTICAL);
		controls.setPadding(Theme.dp(act, 18), Theme.dp(act, 4),
		Theme.dp(act, 18), Theme.dp(act, 6));

		TextView btnPlay = new TextView(act);
		btnPlay.setText("Play");
		btnPlay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		btnPlay.setPadding(Theme.dp(act, 12), Theme.dp(act, 6),
		Theme.dp(act, 12), Theme.dp(act, 6));
		btnPlay.setBackground(Theme.rippleBackground(Theme.dp(act, 8), Theme.ACCENT_BLUE));
		btnPlay.setTextColor(Color.parseColor("#0B0E14"));
		controls.addView(btnPlay);

		final SeekBar scrub = new SeekBar(act);
		scrub.setMax(1);
		scrub.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		controls.addView(scrub);

		TextView btnReset = new TextView(act);
		btnReset.setText("Reset");
		btnReset.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		btnReset.setPadding(Theme.dp(act, 12), Theme.dp(act, 6),
		Theme.dp(act, 12), Theme.dp(act, 6));
		btnReset.setBackground(Theme.rippleBackground(Theme.dp(act, 8), Theme.CARD));
		btnReset.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		controls.addView(btnReset);

		root.addView(controls);

		// Hint row
		TextView hint = new TextView(act);
		hint.setText("drag to orbit \u2022 pinch to zoom");
		hint.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
		hint.setGravity(Gravity.CENTER);
		hint.setPadding(0, Theme.dp(act, 2), 0, Theme.dp(act, 14));
		root.addView(hint);

		dialog.setContentView(root);

		// ── Touch: drag = orbit, pinch = zoom ──────────────────────────
		final ScaleGestureDetector scaleDetector = new ScaleGestureDetector(act,
		new ScaleGestureDetector.SimpleOnScaleGestureListener() {
			@Override
			public boolean onScale(ScaleGestureDetector d) {
				float factor = d.getScaleFactor();
				dist[0] /= factor;
				// Clamped as a multiplier of the auto-fit distance (0.05x
				// up to 20x), not absolute world units — matches the
				// native side's `cam_dist = radius * 2.6f * zoom` and its
				// dynamic near/far planes, which are sized for this range.
				// The old 1..500 clamp only allowed zooming OUT (pinch-in
				// was clamped to a floor of 1 = "can't get closer than the
				// initial fit"), and 500x pushed the camera past the far
				// clip plane entirely, making the model vanish.
				dist[0] = Math.max(0.05f, Math.min(dist[0], 20f));
				return true;
			}
		});

		glView.setOnTouchListener(new View.OnTouchListener() {
			private float lastX;
			private float lastY;

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				scaleDetector.onTouchEvent(event);
				float x = event.getX();
				float y = event.getY();

				switch (event.getActionMasked()) {
					case MotionEvent.ACTION_DOWN:
						// Save the initial touch position
						lastX = x;
						lastY = y;
						break;
					case MotionEvent.ACTION_MOVE:
						if (!scaleDetector.isInProgress()) {
							float dx = x - lastX;
							float dy = y - lastY;

							// Negated so the model rotates *with* the finger instead of
							// away from it (we're moving the camera, not the model).
							yaw[0] -= dx * 0.5f;
							pitch[0] = Math.max(-89f, Math.min(89f, pitch[0] - dy * 0.5f));
						}
						lastX = x;
						lastY = y;
						break;
				}
				return true;
			}
		});

		// Wireframe / grid toggles
		btnWire.setOnClickListener(v -> {
			wireframe[0] = !wireframe[0];
			btnWire.setTextColor(Color.parseColor(wireframe[0] ? Theme.ACCENT_BLUE : Theme.TEXT_DIM));
		});

		btnGrid.setOnClickListener(v -> {
			grid[0] = !grid[0];
			btnGrid.setTextColor(Color.parseColor(grid[0] ? Theme.ACCENT_BLUE : Theme.TEXT_DIM));
		});

		// Animation controls
		btnPlay.setOnClickListener(v -> {
			animate[0] = !animate[0];
			btnPlay.setText(animate[0] ? "Pause" : "Play");
			if (frameCount[0] > 1) scrub.setMax(frameCount[0] - 1);
		});

		btnReset.setOnClickListener(v -> {
			yaw[0] = 45f; pitch[0] = 30f; dist[0] = 1f; frame[0] = 0f;
			scrub.setProgress(0);
		});

		scrub.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
				if (fromUser) { animate[0] = false; btnPlay.setText("Play"); frame[0] = progress; }
			}
			@Override public void onStartTrackingTouch(SeekBar sb) {}
			@Override public void onStopTrackingTouch(SeekBar sb) {}
		});

		btnClose.setOnClickListener(v -> {
			// Free GPU resources HERE, while the GL thread is still
			// definitely alive and its context still current — not from
			// dialog.setOnDismissListener below. dismiss() detaches the
			// view, which makes GLSurfaceView tear its render thread down
			// (onDetachedFromWindow -> requestExitAndWait, synchronous);
			// by the time a dismiss listener fires afterward, queueEvent()
			// is posting to a thread that has already exited and will
			// never run the Runnable, so podGLFree() silently never
			// happened. That's harmless correctness-wise now (a fresh GL
			// context on the next open discards the old cache regardless),
			// but it leaked the CPU-side model data and GPU objects for
			// the lifetime of the process. Freeing before onPause()/
			// dismiss() here means the common close path (tapping the X)
			// reliably runs it.
			if (handle[0] >= 0) {
				final int freeHandle = handle[0];
				handle[0] = -1;
				try { glView.queueEvent(() -> Filerift.podGLFree(freeHandle)); } catch (Exception ignored) {}
			}
			glView.onPause();
			dialog.dismiss();
		});

		dialog.setOnDismissListener(d -> {
			// Fallback for dismiss paths that skip btnClose (back button,
			// tap-outside-to-cancel). Best-effort only — may lose the same
			// race described above — but costs nothing to try.
			if (handle[0] >= 0) {
				final int freeHandle = handle[0];
				handle[0] = -1;
				try { glView.queueEvent(() -> Filerift.podGLFree(freeHandle)); } catch (Exception ignored) {}
			}
		});

		dialog.show();
		glView.onResume();
	}
}