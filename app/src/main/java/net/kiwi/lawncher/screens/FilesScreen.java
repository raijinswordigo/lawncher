package net.kiwi.lawncher.screens;

import android.app.AlertDialog;
import android.os.Environment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import net.kiwi.lawncher.files.FileManager;
import net.kiwi.lawncher.ui.Screen;
import net.kiwi.lawncher.ui.Theme;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Files: browse the launcher's own storage — external files
 * (/Android/data/net.kiwi.lawncher/files), internal data and cache — with
 * properties / rename / delete / new-folder actions.
 */
public class FilesScreen implements Screen {

	private LinearLayout root;
	private LinearLayout rowsColumn;
	private TextView pathLabel;
	private TextView infoLabel;
	private final Stack<File> history = new Stack<>();
	private File current;
	private boolean bySize = false;

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

		section.addView(buildRootChips(), matchWrap(0, 0, 0, Theme.dp(12)));

		LinearLayout navRow = new LinearLayout(Theme.activity);
		navRow.setOrientation(LinearLayout.HORIZONTAL);
		navRow.setGravity(Gravity.CENTER_VERTICAL);
		TextView up = Theme.text(15, Theme.TEXT, true);
		up.setText("\u2191");
		up.setGravity(Gravity.CENTER);
		up.setBackground(Theme.rounded(Theme.SURFACE_ALT, Theme.dp(20), Theme.BORDER, 1));
		up.setClickable(true);
		up.setOnClickListener(v -> goUp());
		navRow.addView(up, new LinearLayout.LayoutParams(Theme.dp(40), Theme.dp(40)));
		pathLabel = Theme.caption(11, Theme.TEXT_DIM);
		pathLabel.setTypeface(android.graphics.Typeface.MONOSPACE);
		pathLabel.setMaxLines(1);
		pathLabel.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
		navRow.addView(pathLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		TextView sort = Theme.caption(11, Theme.TEXT_DIM);
		sort.setText("SIZE");
		sort.setGravity(Gravity.CENTER);
		sort.setPadding(Theme.dp(10), Theme.dp(6), Theme.dp(10), Theme.dp(6));
		sort.setBackground(Theme.rounded(bySize ? 0x1F5B8CFF : Theme.SURFACE_ALT, Theme.dp(9), 0, 0));
		sort.setClickable(true);
		sort.setOnClickListener(v -> {
			bySize = !bySize;
			sort.setText(bySize ? "NAME" : "SIZE");
			sort.setBackground(Theme.rounded(bySize ? 0x1F5B8CFF : Theme.SURFACE_ALT, Theme.dp(9), 0, 0));
			render();
		});
		navRow.addView(sort, new LinearLayout.LayoutParams(Theme.dp(52), Theme.dp(32)));
		TextView create = Theme.caption(11, Theme.TEXT);
		create.setText("NEW");
		create.setGravity(Gravity.CENTER);
		create.setPadding(Theme.dp(10), Theme.dp(6), Theme.dp(10), Theme.dp(6));
		create.setBackground(Theme.rounded(0x1F5B8CFF, Theme.dp(9), 0, 0));
		create.setClickable(true);
		create.setOnClickListener(v -> promptNewFolder());
		LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Theme.dp(48), Theme.dp(32));
		cp.leftMargin = Theme.dp(8);
		navRow.addView(create, cp);
		section.addView(navRow, matchWrap(0, 0, 0, Theme.dp(12)));

		ScrollView scroll = new ScrollView(Theme.activity);
		scroll.setFillViewport(true);
		rowsColumn = new LinearLayout(Theme.activity);
		rowsColumn.setOrientation(LinearLayout.VERTICAL);
		scroll.addView(rowsColumn, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		section.addView(scroll, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		infoLabel = Theme.caption(10, Theme.TEXT_FAINT);
		infoLabel.setGravity(Gravity.CENTER);
		section.addView(infoLabel, matchWrap(0, Theme.dp(8), 0, 0));

		return root;
	}

	@Override
	public void onShown() {
		if (current == null) enter(getExternalDir());
		else render();
	}

	@Override
	public void onHidden() {
	}

	@Override
	public boolean onBack() {
		if (history.isEmpty()) return false;
		goUp();
		return true;
	}

	// ---- navigation ----

	private File getExternalDir() {
		File f = Theme.activity.getExternalFilesDir(null);
		return f != null ? f : Theme.activity.getFilesDir();
	}

	private View buildRootChips() {
		HorizontalScrollView scroll = new HorizontalScrollView(Theme.activity);
		scroll.setHorizontalScrollBarEnabled(false);
		LinearLayout row = new LinearLayout(Theme.activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		addRootChip(row, "Launcher data", getExternalDir());
		addRootChip(row, "Internal", Theme.activity.getFilesDir());
		addRootChip(row, "Cache", Theme.activity.getCacheDir());
		addRootChip(row, "Android/data",
				new File(Environment.getExternalStorageDirectory(), "Android/data"));
		scroll.addView(row);
		return scroll;
	}

	private void addRootChip(LinearLayout row, final String label, final File dir) {
		TextView chip = Theme.text(12, Theme.TEXT_DIM, false);
		chip.setText(label);
		chip.setGravity(Gravity.CENTER);
		chip.setPadding(Theme.dp(14), Theme.dp(7), Theme.dp(14), Theme.dp(7));
		chip.setBackground(Theme.rounded(Theme.SURFACE_ALT, Theme.dp(10), Theme.BORDER, 1));
		chip.setOnClickListener(v -> {
			if (dir == null) {
				Toast.makeText(Theme.activity, "Location not available on this device", Toast.LENGTH_SHORT).show();
				return;
			}
			if (!dir.canRead()) {
				toastRestricted(label);
				return;
			}
			enter(dir);
		});
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.rightMargin = Theme.dp(8);
		row.addView(chip, lp);
	}

	private void toastRestricted(String label) {
		Toast.makeText(Theme.activity,
				label + " is restricted by Android on this device.\n"
						+ "Launcher data, Internal and Cache are always accessible.",
				Toast.LENGTH_LONG).show();
	}

	private void enter(File dir) {
		if (current != null && current.exists()) history.push(current);
		current = dir;
		render();
	}

	private void goUp() {
		if (history.isEmpty()) return;
		current = history.pop();
		render();
	}

	private void render() {
		pathLabel.setText(current == null ? "" : current.getAbsolutePath());
		rowsColumn.removeAllViews();

		if (current == null) return;
		List<FileManager.Entry> entries = FileManager.list(current, true, bySize);
		if (entries.isEmpty()) {
			TextView empty = Theme.text(13, Theme.TEXT_DIM, false);
			empty.setText("This folder is empty.");
			empty.setGravity(Gravity.CENTER);
			empty.setPadding(0, Theme.dp(40), 0, Theme.dp(40));
			rowsColumn.addView(empty, matchWrap(0, 0, 0, 0));
		} else {
			for (final FileManager.Entry entry : entries) {
				rowsColumn.addView(buildRow(entry), matchWrap(0, 0, 0, Theme.dp(8)));
			}
		}
		int folders = 0, files = 0;
		for (FileManager.Entry e : entries) if (e.directory) folders++; else files++;
		infoLabel.setText(folders + " folders \u00B7 " + files + " files \u00B7 "
				+ FileManager.humanSize(FileManager.dirSize(current)));
	}

	private View buildRow(final FileManager.Entry entry) {
		LinearLayout row = new LinearLayout(Theme.activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setClickable(true);
		row.setFocusable(true);
		row.setPadding(Theme.dp(12), Theme.dp(12), Theme.dp(12), Theme.dp(12));
		row.setBackground(Theme.ripple(Theme.dp(16), Theme.SURFACE));

		TextView icon = Theme.text(20, entry.directory ? Theme.GOLD : iconColor(entry.name), false);
		icon.setText(entry.directory ? "\uD83D\uDCC1" : "\uD83D\uDCC4");
		icon.setGravity(Gravity.CENTER);
		icon.setBackground(Theme.rounded(Theme.SURFACE_ALT, Theme.dp(12), 0, 0));
		row.addView(icon, new LinearLayout.LayoutParams(Theme.dp(46), Theme.dp(46)));

		LinearLayout text = new LinearLayout(Theme.activity);
		text.setOrientation(LinearLayout.VERTICAL);
		text.setPadding(Theme.dp(12), 0, 0, 0);
		TextView name = Theme.text(14, Theme.TEXT, !entry.directory);
		name.setText(entry.name);
		name.setMaxLines(1);
		name.setEllipsize(android.text.TextUtils.TruncateAt.END);
		text.addView(name);
		TextView meta = Theme.caption(10, Theme.TEXT_FAINT);
		meta.setText(entry.directory
				? FileManager.childCount(entry.file) + " items \u00B7 " + FileManager.humanDate(entry.modified)
				: FileManager.humanSize(entry.size) + " \u00B7 " + FileManager.humanDate(entry.modified));
		text.addView(meta);
		row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		if (entry.directory) {
			row.setOnClickListener(v -> {
				if (entry.file.canRead()) enter(entry.file);
				else toastRestricted(entry.name);
			});
		} else {
			row.setOnClickListener(v -> showProperties(entry));
		}
		row.setOnLongClickListener(v -> {
			showActions(entry);
			return true;
		});
		return row;
	}

	private static int iconColor(String name) {
		String n = name == null ? "" : name.toLowerCase();
		if (n.endsWith(".zip")) return Theme.GOLD;
		if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")) return Theme.SUCCESS;
		if (n.endsWith(".toml") || n.endsWith(".json")) return Theme.accentStart();
		if (n.endsWith(".lua")) return 0xFF9C6CFF;
		return Theme.TEXT_DIM;
	}

	// ---- actions ----

	private void showProperties(FileManager.Entry entry) {
		StringBuilder sb = new StringBuilder();
		sb.append(entry.name).append('\n');
		sb.append('\n');
		sb.append("Path: ").append(entry.file.getAbsolutePath()).append('\n');
		sb.append("Size: ").append(FileManager.humanSize(
				entry.directory ? FileManager.dirSize(entry.file) : entry.size)).append('\n');
		sb.append("Modified: ").append(FileManager.humanDate(entry.modified));
		new AlertDialog.Builder(Theme.activity)
				.setTitle(entry.directory ? "Folder properties" : "File properties")
				.setMessage(sb.toString())
				.setPositiveButton("OK", null)
				.show();
	}

	private void showActions(final FileManager.Entry entry) {
		AlertDialog.Builder b = new AlertDialog.Builder(Theme.activity);
		b.setTitle(entry.name);
		final String[] options = {"Properties", "Rename", "Delete"};
		b.setItems(options, (dialog, which) -> {
			if (which == 0) showProperties(entry);
			else if (which == 1) promptRename(entry);
			else confirmDelete(entry);
		});
		b.setNegativeButton("Cancel", null);
		b.show();
	}

	private void promptRename(final FileManager.Entry entry) {
		final EditText input = new EditText(Theme.activity);
		input.setText(entry.name);
		input.setSelectAllOnFocus(true);
		input.setSingleLine(true);
		input.setInputType(InputType.TYPE_CLASS_TEXT);
		input.setHint("New name");
		new AlertDialog.Builder(Theme.activity)
				.setTitle("Rename")
				.setView(input)
				.setPositiveButton("Rename", (dialog, which) -> {
					if (FileManager.rename(entry.file, input.getText().toString())) render();
					else Toast.makeText(Theme.activity, "Couldn't rename", Toast.LENGTH_SHORT).show();
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void confirmDelete(final FileManager.Entry entry) {
		new AlertDialog.Builder(Theme.activity)
				.setTitle("Delete " + entry.name + "?")
				.setMessage("This permanently removes the selected item.")
				.setPositiveButton("Delete", (dialog, which) -> {
					if (FileManager.deleteRecursive(entry.file)) render();
					else Toast.makeText(Theme.activity, "Couldn't delete", Toast.LENGTH_SHORT).show();
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void promptNewFolder() {
		final EditText input = new EditText(Theme.activity);
		input.setSingleLine(true);
		input.setInputType(InputType.TYPE_CLASS_TEXT);
		input.setHint("Folder name");
		input.addTextChangedListener(new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
			@Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
			@Override public void afterTextChanged(Editable s) {}
		});
		new AlertDialog.Builder(Theme.activity)
				.setTitle("New folder")
				.setView(input)
				.setPositiveButton("Create", (dialog, which) -> {
					if (current != null && FileManager.createFolder(current, input.getText().toString())) render();
					else Toast.makeText(Theme.activity, "Couldn't create folder", Toast.LENGTH_SHORT).show();
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private static LinearLayout.LayoutParams matchWrap(int l, int t, int r, int b) {
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		p.setMargins(l, t, r, b);
		return p;
	}
}
