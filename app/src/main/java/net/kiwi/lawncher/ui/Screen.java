package net.kiwi.lawncher.ui;

import android.view.View;

/** A top-level screen hosted inside {@link LauncherShell}'s content area. */
public interface Screen {

	/** Rebuild the screen's view and return it. May be called repeatedly. */
	View build();

	/** Called every time the screen becomes visible. */
	void onShown();

	/** Called when the screen is about to be hidden. */
	void onHidden();

	/** Return true if this screen consumed the back press (e.g. closed an overlay). */
	boolean onBack();
}
