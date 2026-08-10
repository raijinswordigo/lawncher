package net.kiwi.lawncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/**
 * "Files" screen: browse and delete Lawncher's own external
 * (Android/data/.../files, where ModManager keeps mods/) and internal
 * storage. All filesystem work lives in {@link FileManager}.
 */
class FilesScreen {

	private static Activity activity;
	private static FileManager.Root currentRoot;
	private static File currentDir;

	private static LinearLayout rootToggle;
	private static TextView pathLabel;
	private static LinearLayout listContainer;

	static View build(Activity act) {
		activity = act;
		currentRoot = FileManager.Root.EXTERNAL;
		currentDir = FileManager.rootDir(act, currentRoot);

		LinearLayout screen = new LinearLayout(act);
		screen.setOrientation(LinearLayout.VERTICAL);
		screen.setBackgroundColor(Color.parseColor(Theme.BG));

		screen.addView(TopBar.build(act, "Files"), new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		rootToggle = buildRootToggle();
		screen.addView(rootToggle, marginParams(20, 8, 20, 4));
		screen.addView(buildPathBar(), marginParams(20, 14, 20, 4));

		ScrollView scroll = new ScrollView(act);
		listContainer = new LinearLayout(act);
		listContainer.setOrientation(LinearLayout.VERTICAL);
		scroll.addView(listContainer, new ScrollView.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		screen.addView(scroll, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		refresh();
		return screen;
	}

	// ==========================================
	// Root switch + path bar
	// ==========================================

	private static LinearLayout buildRootToggle() {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.addView(toggleButton(FileManager.Root.EXTERNAL, "External"));
		row.addView(toggleButton(FileManager.Root.INTERNAL, "Internal"));
		return row;
	}

	private static TextView toggleButton(FileManager.Root forRoot, String label) {
		boolean active = currentRoot == forRoot;

		TextView btn = new TextView(activity);
		btn.setText(label);
		btn.setGravity(Gravity.CENTER);
		btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		btn.setPadding(0, Theme.dp(activity, 10), 0, Theme.dp(activity, 10));
		btn.setTextColor(Color.parseColor(active ? Theme.ACCENT_DARK_TEXT : Theme.TEXT_DIM));
		btn.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);

		GradientDrawable bg = new GradientDrawable();
		bg.setCornerRadius(Theme.dp(activity, 10));
		bg.setColor(Color.parseColor(active ? Theme.ACCENT_BLUE : Theme.CARD));
		btn.setBackground(bg);

		btn.setOnClickListener(v -> {
			currentRoot = forRoot;
			currentDir = FileManager.rootDir(activity, currentRoot);
			rebuildRootToggle();
			refresh();
		});

		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		if (forRoot == FileManager.Root.EXTERNAL) p.rightMargin = Theme.dp(activity, 8);
		btn.setLayoutParams(p);
		return btn;
	}

	// The two buttons swap colors on switch - simplest is rebuilding both rather than tracking refs.
	private static void rebuildRootToggle() {
		rootToggle.removeAllViews();
		rootToggle.addView(toggleButton(FileManager.Root.EXTERNAL, "External"));
		rootToggle.addView(toggleButton(FileManager.Root.INTERNAL, "Internal"));
	}

	private static LinearLayout buildPathBar() {
		LinearLayout bar = new LinearLayout(activity);
		bar.setGravity(Gravity.CENTER_VERTICAL);

		TextView up = new TextView(activity);
		up.setText("\u2191");
		up.setTextColor(Color.parseColor(Theme.ACCENT_BLUE));
		up.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		up.setPadding(0, 0, Theme.dp(activity, 14), 0);
		up.setOnClickListener(v -> goUp());
		bar.addView(up);

		pathLabel = new TextView(activity);
		pathLabel.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		pathLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		pathLabel.setMaxLines(1);
		bar.addView(pathLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		return bar;
	}

	private static void goUp() {
		File root = FileManager.rootDir(activity, currentRoot);
		if (!currentDir.equals(root) && currentDir.getParentFile() != null) {
			currentDir = currentDir.getParentFile();
			refresh();
		}
	}

	// ==========================================
	// Listing
	// ==========================================

	private static void refresh() {
		File root = FileManager.rootDir(activity, currentRoot);
		String relative = currentDir.equals(root) ? "/" : currentDir.getAbsolutePath().substring(root.getAbsolutePath().length());
		pathLabel.setText(relative);

		listContainer.removeAllViews();
		List<FileManager.Entry> entries = FileManager.list(currentDir);
		if (entries.isEmpty()) {
			listContainer.addView(emptyState());
			return;
		}
		for (FileManager.Entry entry : entries) {
			listContainer.addView(buildRow(entry));
		}
	}

	private static TextView emptyState() {
		TextView empty = new TextView(activity);
		empty.setText("Empty folder.");
		empty.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		empty.setGravity(Gravity.CENTER);
		empty.setPadding(0, Theme.dp(activity, 40), 0, Theme.dp(activity, 40));
		return empty;
	}

	private static View buildRow(FileManager.Entry entry) {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(Theme.dp(activity, 4), Theme.dp(activity, 10), Theme.dp(activity, 4), Theme.dp(activity, 10));
		row.setClickable(true);
		row.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.BG));

		TextView icon = new TextView(activity);
		icon.setText(entry.isDirectory ? "\uD83D\uDCC1" : "\uD83D\uDCC4");
		icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
		LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		iconParams.rightMargin = Theme.dp(activity, 12);
		row.addView(icon, iconParams);

		LinearLayout labels = new LinearLayout(activity);
		labels.setOrientation(LinearLayout.VERTICAL);
		TextView name = new TextView(activity);
		name.setText(entry.name());
		name.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		name.setMaxLines(1);
		labels.addView(name);
		if (!entry.isDirectory) {
			TextView size = new TextView(activity);
			size.setText(FileManager.humanSize(entry.size));
			size.setTextColor(Color.parseColor(Theme.TEXT_DIM));
			size.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
			labels.addView(size);
		}
		row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		TextView delete = new TextView(activity);
		delete.setText("\uD83D\uDDD1");
		delete.setTextColor(Color.parseColor(Theme.ACCENT_RED));
		delete.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		delete.setPadding(Theme.dp(activity, 14), 0, Theme.dp(activity, 4), 0);
		delete.setContentDescription("Delete " + entry.name());
		delete.setOnClickListener(v -> confirmDelete(entry));
		row.addView(delete);

		row.setOnClickListener(v -> {
			if (entry.isDirectory) {
				currentDir = entry.file;
				refresh();
			} else {
				Toast.makeText(activity, entry.name() + " \u2022 " + FileManager.humanSize(entry.size), Toast.LENGTH_SHORT).show();
			}
		});

		return row;
	}

	private static void confirmDelete(FileManager.Entry entry) {
		new AlertDialog.Builder(activity)
				.setTitle("Delete " + entry.name() + "?")
				.setMessage(entry.isDirectory ? "This deletes the folder and everything inside it." : "This can't be undone.")
				.setPositiveButton("Delete", (dialog, which) -> {
					boolean ok = FileManager.delete(entry.file);
					Toast.makeText(activity, ok ? "Deleted" : "Couldn't delete everything", Toast.LENGTH_SHORT).show();
					refresh();
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private static LinearLayout.LayoutParams marginParams(int l, int t, int r, int b) {
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		p.setMargins(Theme.dp(activity, l), Theme.dp(activity, t), Theme.dp(activity, r), Theme.dp(activity, b));
		return p;
	}
}
