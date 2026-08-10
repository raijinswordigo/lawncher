package net.kiwi.lawncher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Minimal animated hamburger that morphs into an X while the drawer is open. */
public class HamburgerView extends View {

	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final float density;
	private float progress; // 0 = closed, 1 = open (X)
	private ValueAnimator animator;

	public HamburgerView(Context context) {
		super(context);
		density = context.getResources().getDisplayMetrics().density;
		paint.setColor(Theme.TEXT);
		paint.setStyle(Paint.Style.FILL);
	}

	public void setOpen(boolean open) {
		if (animator != null) animator.cancel();
		animator = ValueAnimator.ofFloat(progress, open ? 1f : 0f);
		animator.setDuration(240);
		animator.setInterpolator(new DecelerateInterpolator(1.4f));
		animator.addUpdateListener(a -> {
			progress = (float) a.getAnimatedValue();
			invalidate();
		});
		animator.start();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		float w = getWidth(), h = getHeight();
		float cx = w / 2f, cy = h / 2f;
		float barW = Math.min(w * 0.5f, 22f * density);
		float halfW = barW / 2f;
		float thickness = 2.4f * density;
		float gap = Math.max(4f, h * 0.09f);
		float radius = thickness / 2f;

		canvas.save();
		canvas.translate(cx, cy);
		for (int i = -1; i <= 1; i++) {
			canvas.save();
			float y = i * (gap + thickness) - thickness / 2f;
			if (progress > 0f) {
				if (i == 0) {
					paint.setAlpha((int) (255f * (1f - progress)));
				} else {
					canvas.rotate(45f * progress * (i < 0 ? -1f : 1f));
					y = -thickness / 2f;
				}
			}
			canvas.drawRoundRect(-halfW, y, halfW, y + thickness, radius, radius, paint);
			canvas.restore();
			paint.setAlpha(255);
		}
		canvas.restore();
	}
}
