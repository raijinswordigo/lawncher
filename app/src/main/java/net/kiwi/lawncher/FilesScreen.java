package net.kiwi.lawncher;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * "Files" screen: browse and delete Lawncher's own external
 * (Android/data/.../files, where ModManager keeps mods/) and internal
 * storage. All filesystem work lives in {@link FileManager}.
 */
class FilesScreen {

	/** API 23-safe stand-in for java.util.function.Consumer (that interface needs API 24+). */
	interface FileCallback {
		void accept(File file);
	}

	static Activity activity;
	private static FileManager.Root currentRoot;
	private static File currentDir;
	private static String filterQuery = "";
	private static LinearLayout searchBar;
	private static android.widget.EditText searchInput;

	private static LinearLayout rootToggle;
	private static TextView pathLabel;
	private static LinearLayout listContainer;
	private static LinearLayout bookmarkList;
	private static FileCallback pickCallback;
	private static Dialog pickDialog;
	private static boolean sideOpen;
	private static LinearLayout sidePanel;
	private static LinearLayout pathBarHost;
	private static FrameLayout bodyFrame;
	private static View sideScrim;

	public static final int REQ_IMPORT_FILE = 5001;

	static View build(Activity act) {
		return buildInternal(act, null, null);
	}

	/**
	 * @param pickOnFile if non-null, file taps call this and dismiss pickDialog (picker mode).
	 * @param hostDialog dialog to dismiss on pick (nullable in normal mode).
	 */
	private static View buildInternal(Activity act, FileCallback pickOnFile, Dialog hostDialog) {
		activity = act;
		pickCallback = pickOnFile;
		pickDialog = hostDialog;
		sideOpen = false;
		act.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
		if (currentDir == null) {
			currentRoot = FileManager.Root.EXTERNAL;
			currentDir = FileManager.rootDir(act, currentRoot);
		}

		LinearLayout screen = new LinearLayout(act);
		Theme.attachToRoot(screen);
		screen.setOrientation(LinearLayout.VERTICAL);
		screen.setBackgroundColor(Color.parseColor(Theme.BG));

		LinearLayout topBar = new LinearLayout(act);
		topBar.setOrientation(LinearLayout.HORIZONTAL);
		String title = pickOnFile != null ? "Open file" : "Files";
		topBar.addView(TopBar.build(act, title), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		if (pickOnFile == null) {
			ImageView newBtn = new ImageView(act);
			newBtn.setImageResource(R.drawable.ic_add);
			newBtn.setColorFilter(Color.parseColor(Theme.ACCENT_GREEN));
			newBtn.setPadding(10, 20, 20, 20);
			newBtn.setOnClickListener(v -> showCreateDialog());
			topBar.addView(newBtn);

			ImageView importBtn = new ImageView(act);
			importBtn.setImageResource(R.drawable.ic_upload);
			importBtn.setColorFilter(Color.parseColor(Theme.ACCENT_BLUE));
			importBtn.setPadding(10, 20, 20, 20);
			importBtn.setOnClickListener(v -> launchSystemPicker());
			topBar.addView(importBtn);

			ImageView pasteBtn = new ImageView(act);
			pasteBtn.setImageResource(R.drawable.ic_paste);
			pasteBtn.setColorFilter(Color.parseColor(Theme.TEXT_DIM));
			pasteBtn.setPadding(10, 20, 40, 20);
			pasteBtn.setOnClickListener(v -> executePaste());
			topBar.addView(pasteBtn);
		} else {
			TextView cancel = new TextView(act);
			cancel.setText("Cancel");
			cancel.setTextColor(Color.parseColor(Theme.TEXT_DIM));
			cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
			cancel.setPadding(Theme.dp(act, 12), Theme.dp(act, 16), Theme.dp(act, 16), Theme.dp(act, 16));
			cancel.setOnClickListener(v -> {
				if (pickDialog != null) pickDialog.dismiss();
				pickCallback = null;
				pickDialog = null;
			});
			topBar.addView(cancel);
		}

		screen.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		// Body: main browser + right sidebar (edge-swipe open)
		bodyFrame = new FrameLayout(act) {
			float downX, downY;
			boolean trackingEdge;

			@Override
			public boolean onInterceptTouchEvent(MotionEvent ev) {
				int edge = Theme.dp(activity, 28);
				int w = getWidth();
				int sideW = Theme.dp(activity, 168);
				switch (ev.getActionMasked()) {
					case MotionEvent.ACTION_DOWN:
						downX = ev.getX();
						downY = ev.getY();
						trackingEdge = false;
						if (!sideOpen && downX >= w - edge) trackingEdge = true;
						// When open, only track swipe-to-close from the dimmed main area
						if (sideOpen && downX < w - sideW) trackingEdge = true;
						break;
					case MotionEvent.ACTION_MOVE:
						if (!trackingEdge) break;
						float dx = ev.getX() - downX;
						float dy = Math.abs(ev.getY() - downY);
						if (dy > Math.abs(dx) + Theme.dp(activity, 8)) {
							trackingEdge = false;
							break;
						}
						if (!sideOpen && dx < -Theme.dp(activity, 16)) return true;
						if (sideOpen && dx > Theme.dp(activity, 16)) return true;
						break;
				}
				return super.onInterceptTouchEvent(ev);
			}

			@Override
			public boolean onTouchEvent(MotionEvent ev) {
				if (!trackingEdge) return super.onTouchEvent(ev);
				if (ev.getActionMasked() == MotionEvent.ACTION_UP
				|| ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
					float dx = ev.getX() - downX;
					if (!sideOpen && dx < -Theme.dp(activity, 24)) setSideOpen(true);
					else if (sideOpen && dx > Theme.dp(activity, 24)) setSideOpen(false);
					trackingEdge = false;
					return true;
				}
				return true;
			}
		};
		bodyFrame.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		LinearLayout main = new LinearLayout(act);
		main.setOrientation(LinearLayout.VERTICAL);
		main.setLayoutParams(new FrameLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		pathBarHost = new LinearLayout(act);
		pathBarHost.setOrientation(LinearLayout.HORIZONTAL);
		pathBarHost.setGravity(Gravity.CENTER_VERTICAL);
		rebuildPathBar();
		main.addView(pathBarHost, marginParams(8, 4, 8, 4));

		searchBar = buildSearchBar();
		main.addView(searchBar, marginParams(12, 4, 12, 8));

		ScrollView scroll = new ScrollView(act);
		listContainer = new LinearLayout(act);
		listContainer.setOrientation(LinearLayout.VERTICAL);
		scroll.addView(listContainer, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		main.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		bodyFrame.addView(main);

		// Dimmed scrim — tap to close the places/bookmarks drawer
		sideScrim = new View(act);
		sideScrim.setBackgroundColor(0x66000000);
		sideScrim.setVisibility(View.GONE);
		sideScrim.setOnClickListener(v -> setSideOpen(false));
		bodyFrame.addView(sideScrim, new FrameLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		sidePanel = buildFilesSidebar(act);
		FrameLayout.LayoutParams sideLp = new FrameLayout.LayoutParams(
		Theme.dp(act, 168), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
		sidePanel.setLayoutParams(sideLp);
		sidePanel.setVisibility(View.GONE);
		sidePanel.setClickable(true);
		bodyFrame.addView(sidePanel);

		screen.addView(bodyFrame);

		refresh();
		return screen;
	}

	// ==========================================
	// System File Picker Integration
	// ==========================================

	private static void launchSystemPicker() {
		if (activity == null) return;
		try {
			Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			intent.setType("*/*");
			activity.startActivityForResult(intent, REQ_IMPORT_FILE);
		} catch (Exception e) {
			Toast.makeText(activity, "Could not open system file picker", Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Call this from your host Activity's onActivityResult to handle imported files.
	 */
	public static void handleActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQ_IMPORT_FILE && resultCode == Activity.RESULT_OK && data != null) {
			Uri uri = data.getData();
			if (uri != null && currentDir != null && activity != null) {
				try {
					String filename = "imported_file";
					Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null);
					if (cursor != null) {
						if (cursor.moveToFirst()) {
							int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
							if (nameIndex != -1) {
								filename = cursor.getString(nameIndex);
							}
						}
						cursor.close();
					}

					File destFile = new File(currentDir, filename);
					InputStream inputStream = activity.getContentResolver().openInputStream(uri);
					FileOutputStream fos = new FileOutputStream(destFile);
					byte[] buffer = new byte[4096];
					int length;
					while (inputStream != null && (length = inputStream.read(buffer)) > 0) {
						fos.write(buffer, 0, length);
					}
					if (inputStream != null) inputStream.close();
					fos.close();

					Toast.makeText(activity, "Imported: " + filename, Toast.LENGTH_SHORT).show();
					refresh();
				} catch (Exception e) {
					Toast.makeText(activity, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
				}
			}
		}
	}

	// ==========================================
	// Root switch + path bar
	// ==========================================


	private static LinearLayout buildSearchBar() {
		LinearLayout bar = new LinearLayout(activity);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
		bar.setPadding(Theme.dp(activity, 12), Theme.dp(activity, 4), Theme.dp(activity, 12), Theme.dp(activity, 4));
		android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
		bg.setCornerRadius(Theme.dp(activity, 12));
		bg.setColor(android.graphics.Color.parseColor(Theme.CARD));
		bg.setStroke(Theme.dp(activity, 1), android.graphics.Color.parseColor(Theme.BORDER));
		bar.setBackground(bg);

		ImageView icon = new ImageView(activity);
		icon.setImageResource(R.drawable.ic_files);
		icon.setColorFilter(android.graphics.Color.parseColor(Theme.TEXT_DIM));
		int is = Theme.dp(activity, 18);
		LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(is, is);
		ip.rightMargin = Theme.dp(activity, 8);
		bar.addView(icon, ip);

		searchInput = new android.widget.EditText(activity);
		searchInput.setHint("Filter files…");
		searchInput.setHintTextColor(android.graphics.Color.parseColor(Theme.TEXT_DIM));
		searchInput.setTextColor(android.graphics.Color.parseColor(Theme.TEXT_MAIN));
		searchInput.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
		searchInput.setBackground(null);
		searchInput.setSingleLine(true);
		searchInput.setPadding(0, Theme.dp(activity, 8), 0, Theme.dp(activity, 8));
		searchInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		searchInput.addTextChangedListener(new android.text.TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
			@Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
			@Override public void afterTextChanged(android.text.Editable s) {
				filterQuery = s.toString().trim().toLowerCase(java.util.Locale.ROOT);
				refresh();
			}
		});
		bar.addView(searchInput);

		TextView clear = new TextView(activity);
		clear.setText("×");
		clear.setTextColor(android.graphics.Color.parseColor(Theme.TEXT_DIM));
		clear.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
		clear.setPadding(Theme.dp(activity, 8), 0, 0, 0);
		clear.setOnClickListener(v -> {
			filterQuery = "";
			if (searchInput != null) searchInput.setText("");
			refresh();
		});
		bar.addView(clear);
		return bar;
	}

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

	private static void rebuildRootToggle() {
		if (rootToggle == null) return;
		rootToggle.removeAllViews();
		rootToggle.addView(toggleButton(FileManager.Root.EXTERNAL, "External"));
		rootToggle.addView(toggleButton(FileManager.Root.INTERNAL, "Internal"));
	}

	private static void rebuildPathBar() {
		if (pathBarHost == null || activity == null) return;
		pathBarHost.removeAllViews();

		HorizontalScrollView scroll = new HorizontalScrollView(activity);
		scroll.setHorizontalScrollBarEnabled(false);
		LinearLayout crumbs = new LinearLayout(activity);
		crumbs.setOrientation(LinearLayout.HORIZONTAL);
		crumbs.setGravity(Gravity.CENTER_VERTICAL);
		scroll.addView(crumbs);
		pathBarHost.addView(scroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		File root = FileManager.rootDir(activity, currentRoot);
		String rootPath = root.getAbsolutePath();
		String cur = currentDir.getAbsolutePath();
		if (!cur.startsWith(rootPath)) {
			TextView abs = crumbChip("/", root, true);
			crumbs.addView(abs);
			return;
		}
		String rel = cur.equals(rootPath) ? "" : cur.substring(rootPath.length());
		if (rel.startsWith("/")) rel = rel.substring(1);

		crumbs.addView(crumbChip(currentRoot == FileManager.Root.EXTERNAL ? "external" : "internal", root, rel.isEmpty()));

		if (!rel.isEmpty()) {
			String[] parts = rel.split("/");
			StringBuilder acc = new StringBuilder(rootPath);
			for (int i = 0; i < parts.length; i++) {
				if (parts[i].isEmpty()) continue;
				acc.append('/').append(parts[i]);
				final File target = new File(acc.toString());
				boolean last = i == parts.length - 1;
				TextView sep = new TextView(activity);
				sep.setText(" / ");
				sep.setTextColor(Color.parseColor(Theme.TEXT_DIM));
				sep.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
				crumbs.addView(sep);
				crumbs.addView(crumbChip(parts[i], target, last));
			}
		}
		scroll.post(() -> scroll.fullScroll(View.FOCUS_RIGHT));

		// Toggle on the RIGHT — matches the right-hand places/bookmarks drawer
		TextView sideBtn = new TextView(activity);
		sideBtn.setText(sideOpen ? "»" : "«");
		sideBtn.setTextColor(Color.parseColor(Theme.ACCENT_BLUE));
		sideBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		sideBtn.setPadding(Theme.dp(activity, 12), Theme.dp(activity, 8), Theme.dp(activity, 12), Theme.dp(activity, 8));
		sideBtn.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.CARD));
		sideBtn.setOnClickListener(v -> toggleSide());
		pathBarHost.addView(sideBtn);
	}

	private static TextView crumbChip(String label, File target, boolean current) {
		TextView t = new TextView(activity);
		t.setText(label);
		t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		t.setPadding(Theme.dp(activity, 4), Theme.dp(activity, 6), Theme.dp(activity, 4), Theme.dp(activity, 6));
		if (current) {
			t.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
			t.setTypeface(null, Typeface.BOLD);
		} else {
			t.setTextColor(Color.parseColor(Theme.ACCENT_BLUE));
			t.setOnClickListener(v -> {
				if (target != null && target.isDirectory()) {
					currentDir = target;
					refresh();
				}
			});
		}
		return t;
	}

	private static void toggleSide() {
		setSideOpen(!sideOpen);
	}

	private static void setSideOpen(boolean open) {
		sideOpen = open;
		if (sidePanel != null) {
			sidePanel.setVisibility(sideOpen ? View.VISIBLE : View.GONE);
		}
		if (sideScrim != null) {
			sideScrim.setVisibility(sideOpen ? View.VISIBLE : View.GONE);
		}
		rebuildPathBar();
	}



	/** Subsequence fuzzy match: query chars appear in order inside name. */
	private static boolean fuzzyMatch(String name, String query) {
		if (name.contains(query)) return true;
		int qi = 0;
		for (int i = 0; i < name.length() && qi < query.length(); i++) {
			if (name.charAt(i) == query.charAt(qi)) qi++;
		}
		return qi == query.length();
	}

	private static void goUp() {
		File root = FileManager.rootDir(activity, currentRoot);
		if (!currentDir.equals(root) && currentDir.getParentFile() != null) {
			currentDir = currentDir.getParentFile();
			refresh();
		} else {
			Toast.makeText(activity, "Already at root directory", Toast.LENGTH_SHORT).show();
		}
	}

	// ==========================================
	// Listing
	// ==========================================

	private static void refresh() {
		if (listContainer == null || activity == null) return;
		rebuildPathBar();

		listContainer.removeAllViews();
		listContainer.addView(buildUpRow());

		List<FileManager.Entry> entries = FileManager.list(currentDir);
		if (filterQuery != null && !filterQuery.isEmpty()) {
			List<FileManager.Entry> filtered = new java.util.ArrayList<>();
			for (FileManager.Entry e : entries) {
				if (fuzzyMatch(e.name().toLowerCase(java.util.Locale.ROOT), filterQuery))
					filtered.add(e);
			}
			entries = filtered;
		}
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
		empty.setText(filterQuery != null && !filterQuery.isEmpty() ? "No matches for \"" + filterQuery + "\"" : "Empty folder.");
		empty.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		empty.setGravity(Gravity.CENTER);
		empty.setPadding(0, Theme.dp(activity, 40), 0, Theme.dp(activity, 40));
		return empty;
	}

	private static View buildUpRow() {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(Theme.dp(activity, 4), Theme.dp(activity, 14), Theme.dp(activity, 4), Theme.dp(activity, 14));
		row.setClickable(true);
		row.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.BG));

		ImageView icon = new ImageView(activity);
		icon.setImageResource(R.drawable.ic_up);
		icon.setColorFilter(Color.parseColor(Theme.TEXT_DIM));
		LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
		Theme.dp(activity, 24), Theme.dp(activity, 24));
		iconParams.rightMargin = Theme.dp(activity, 12);
		iconParams.leftMargin = Theme.dp(activity, 8);
		row.addView(icon, iconParams);

		TextView name = new TextView(activity);
		name.setText("..");
		name.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		name.setTypeface(null, Typeface.BOLD);
		name.setMaxLines(1);
		row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		row.setOnClickListener(v -> goUp());
		return row;
	}

	private static View buildRow(FileManager.Entry entry) {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(Theme.dp(activity, 4), Theme.dp(activity, 12), Theme.dp(activity, 4), Theme.dp(activity, 12));
		row.setClickable(true);
		row.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.BG));

		ImageView icon = new ImageView(activity);
		icon.setImageResource(entry.isDirectory ? R.drawable.ic_folder : R.drawable.ic_file);
		icon.setColorFilter(Color.parseColor(Theme.TEXT_DIM));

		LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(Theme.dp(activity, 24), Theme.dp(activity, 24));
		iconParams.rightMargin = Theme.dp(activity, 12);
		iconParams.leftMargin = Theme.dp(activity, 8);
		row.addView(icon, iconParams);

		LinearLayout labels = new LinearLayout(activity);
		labels.setOrientation(LinearLayout.VERTICAL);
		TextView name = new TextView(activity);
		name.setText(entry.name());
		name.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
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

		row.setOnClickListener(v -> {
			if (entry.isDirectory) {
				currentDir = entry.file;
				refresh();
			} else {
				handleFileClick(entry.file);
			}
		});

		row.setOnLongClickListener(v -> {
			showLongPressMenu(entry);
			return true;
		});

		return row;
	}

	private static void showLongPressMenu(FileManager.Entry entry) {
		Dialog dialog = new Dialog(activity);
		dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
		if (dialog.getWindow() != null) {
			dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
		}

		LinearLayout card = new LinearLayout(activity);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setBackground(Theme.rippleBackground(Theme.dp(activity, 16), Theme.CARD));
		card.setPadding(Theme.dp(activity, 8), Theme.dp(activity, 12),
		Theme.dp(activity, 8), Theme.dp(activity, 12));

		// Header with file/folder icon
		LinearLayout header = new LinearLayout(activity);
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setPadding(Theme.dp(activity, 12), Theme.dp(activity, 4),
		Theme.dp(activity, 12), Theme.dp(activity, 12));

		ImageView typeIcon = new ImageView(activity);
		typeIcon.setImageResource(entry.isDirectory ? R.drawable.ic_folder : R.drawable.ic_file);
		typeIcon.setColorFilter(Color.parseColor(Theme.ACCENT_BLUE));
		LinearLayout.LayoutParams tip = new LinearLayout.LayoutParams(
		Theme.dp(activity, 22), Theme.dp(activity, 22));
		tip.rightMargin = Theme.dp(activity, 10);
		header.addView(typeIcon, tip);

		TextView title = new TextView(activity);
		title.setText(entry.name());
		title.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		title.setTypeface(null, Typeface.BOLD);
		title.setMaxLines(2);
		header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		card.addView(header);

		View divider = new View(activity);
		divider.setBackgroundColor(Color.parseColor(Theme.BG));
		card.addView(divider, new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(activity, 1)));

		card.addView(menuRow(R.drawable.ic_bookmark, "Bookmark", Theme.ACCENT_GREEN, () -> {
			dialog.dismiss();
			addBookmark(activity, entry.file.getAbsolutePath());
			rebuildBookmarks(activity);
			Toast.makeText(activity, "Bookmarked", Toast.LENGTH_SHORT).show();
		}));
		card.addView(menuRow(R.drawable.ic_copy, "Copy", Theme.TEXT_MAIN, () -> {
			dialog.dismiss();
			FileManager.clipboardFile = entry.file;
			FileManager.isCutOperation = false;
			Toast.makeText(activity, "Copied to clipboard", Toast.LENGTH_SHORT).show();
		}));
		card.addView(menuRow(R.drawable.ic_cut, "Cut", Theme.TEXT_MAIN, () -> {
			dialog.dismiss();
			FileManager.clipboardFile = entry.file;
			FileManager.isCutOperation = true;
			Toast.makeText(activity, "Ready to move", Toast.LENGTH_SHORT).show();
		}));
		card.addView(menuRow(R.drawable.ic_upload, "Export", Theme.ACCENT_BLUE, () -> {
			dialog.dismiss();
			exportFile(entry.file);
		}));
		card.addView(menuRow(R.drawable.ic_delete, "Delete", Theme.ACCENT_RED, () -> {
			dialog.dismiss();
			confirmDelete(entry);
		}));

		dialog.setContentView(card);
		dialog.show();
		if (dialog.getWindow() != null) {
			dialog.getWindow().setLayout(
			Theme.dp(activity, 280),
			ViewGroup.LayoutParams.WRAP_CONTENT);
		}
	}

	private static View menuRow(int iconRes, String label, String color, Runnable action) {
		LinearLayout row = new LinearLayout(activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(Theme.dp(activity, 14), Theme.dp(activity, 12),
		Theme.dp(activity, 14), Theme.dp(activity, 12));
		row.setClickable(true);
		row.setFocusable(true);
		row.setBackground(Theme.rippleBackground(Theme.dp(activity, 10), Theme.CARD));

		ImageView icon = new ImageView(activity);
		icon.setImageResource(iconRes);
		icon.setColorFilter(Color.parseColor(color));
		LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
		Theme.dp(activity, 22), Theme.dp(activity, 22));
		ip.rightMargin = Theme.dp(activity, 14);
		row.addView(icon, ip);

		TextView text = new TextView(activity);
		text.setText(label);
		text.setTextColor(Color.parseColor(color));
		text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		row.addView(text);

		row.setOnClickListener(v -> action.run());
		return row;
	}

	private static void executePaste() {
		if (FileManager.clipboardFile == null || !FileManager.clipboardFile.exists()) {
			Toast.makeText(activity, "Clipboard is empty", Toast.LENGTH_SHORT).show();
			return;
		}

		File targetFile = new File(currentDir, FileManager.clipboardFile.getName());
		if (targetFile.exists()) {
			Toast.makeText(activity, "File already exists here", Toast.LENGTH_SHORT).show();
			return;
		}

		boolean success = FileManager.copyFileOrDirectory(FileManager.clipboardFile, targetFile);

		if (success && FileManager.isCutOperation) {
			FileManager.delete(FileManager.clipboardFile);
			FileManager.clipboardFile = null;
			FileManager.isCutOperation = false;
		}

		Toast.makeText(activity, success ? "Pasted successfully" : "Paste failed", Toast.LENGTH_SHORT).show();
		refresh();
	}

	private static void exportFile(File file) {
		if (file.isDirectory()) {
			Toast.makeText(activity, "Cannot export directories directly", Toast.LENGTH_SHORT).show();
			return;
		}
		try {
			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType("*/*");
			Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", file);
			intent.putExtra(Intent.EXTRA_STREAM, uri);
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			activity.startActivity(Intent.createChooser(intent, "Export " + file.getName()));
		} catch (Exception e) {
			Toast.makeText(activity, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}

	private static void showCreateDialog() {
		Dialog dialog = new Dialog(activity);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		if (dialog.getWindow() != null) {
			dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
			dialog.getWindow().setLayout(
			(int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
			ViewGroup.LayoutParams.WRAP_CONTENT);
		}

		LinearLayout root = new LinearLayout(activity);
		root.setOrientation(LinearLayout.VERTICAL);
		GradientDrawable cardBg = new GradientDrawable();
		cardBg.setCornerRadius(Theme.dp(activity, 16));
		cardBg.setColor(Color.parseColor(Theme.CARD));
		root.setBackground(cardBg);
		root.setPadding(Theme.dp(activity, 24), Theme.dp(activity, 24), Theme.dp(activity, 24), Theme.dp(activity, 20));

		TextView title = new TextView(activity);
		String dirName = currentDir.getName();
		title.setText(dirName.isEmpty() ? "Create new" : "New in " + dirName);
		title.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
		title.setTypeface(null, Typeface.BOLD);
		root.addView(title);

		android.widget.EditText input = new android.widget.EditText(activity);
		input.setHint("Name");
		input.setHintTextColor(Color.parseColor(Theme.TEXT_DIM));
		input.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		input.setSingleLine(true);
		GradientDrawable inputBg = new GradientDrawable();
		inputBg.setCornerRadius(Theme.dp(activity, 10));
		inputBg.setColor(Color.parseColor(Theme.BG));
		input.setBackground(inputBg);
		int inputPad = Theme.dp(activity, 16);
		input.setPadding(inputPad, Theme.dp(activity, 14), inputPad, Theme.dp(activity, 14));
		LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		inputParams.topMargin = Theme.dp(activity, 18);
		inputParams.bottomMargin = Theme.dp(activity, 22);
		root.addView(input, inputParams);

		LinearLayout buttonRow = new LinearLayout(activity);
		buttonRow.setOrientation(LinearLayout.HORIZONTAL);
		buttonRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

		TextView cancel = new TextView(activity);
		cancel.setText("Cancel");
		cancel.setAllCaps(false);
		cancel.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		cancel.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.CARD));
		cancel.setPadding(Theme.dp(activity, 14), Theme.dp(activity, 10), Theme.dp(activity, 14), Theme.dp(activity, 10));
		cancel.setOnClickListener(v -> dialog.dismiss());
		LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		cancelParams.rightMargin = Theme.dp(activity, 8);
		buttonRow.addView(cancel, cancelParams);

		TextView folder = new TextView(activity);
		folder.setText("Folder");
		folder.setAllCaps(false);
		folder.setTextColor(Color.parseColor(Theme.ACCENT_DARK_TEXT));
		folder.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		folder.setTypeface(null, Typeface.BOLD);
		folder.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.ACCENT_GREEN));
		folder.setPadding(Theme.dp(activity, 16), Theme.dp(activity, 10), Theme.dp(activity, 16), Theme.dp(activity, 10));
		folder.setOnClickListener(v -> {
			createEntry(input.getText().toString(), true);
			dialog.dismiss();
		});
		LinearLayout.LayoutParams folderParams = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		folderParams.rightMargin = Theme.dp(activity, 8);
		buttonRow.addView(folder, folderParams);

		TextView file = new TextView(activity);
		file.setText("File");
		file.setAllCaps(false);
		file.setTextColor(Color.parseColor(Theme.ACCENT_DARK_TEXT));
		file.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		file.setTypeface(null, Typeface.BOLD);
		file.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.ACCENT_GREEN));
		file.setPadding(Theme.dp(activity, 16), Theme.dp(activity, 10), Theme.dp(activity, 16), Theme.dp(activity, 10));
		file.setOnClickListener(v -> {
			createEntry(input.getText().toString(), false);
			dialog.dismiss();
		});
		buttonRow.addView(file);

		root.addView(buttonRow, new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		dialog.setContentView(root);
		dialog.show();
	}

	private static void createEntry(String rawName, boolean isDirectory) {
		String name = rawName == null ? "" : rawName.trim();
		if (name.isEmpty()) {
			Toast.makeText(activity, "Enter a name", Toast.LENGTH_SHORT).show();
			return;
		}
		File target = new File(currentDir, name);
		if (target.exists()) {
			Toast.makeText(activity, target.getName() + " already exists", Toast.LENGTH_SHORT).show();
			return;
		}
		boolean ok;
		try {
			ok = isDirectory ? target.mkdirs() : target.createNewFile();
		} catch (Exception e) {
			ok = false;
		}
		Toast.makeText(activity, ok ? "Created " + target.getName() : "Couldn't create " + target.getName(),
		Toast.LENGTH_SHORT).show();
		if (ok) refresh();
	}

	private static void confirmDelete(FileManager.Entry entry) {
		Runnable doDelete = () -> {
			boolean ok = FileManager.delete(entry.file);
			Toast.makeText(activity, ok ? "Deleted" : "Couldn't delete everything", Toast.LENGTH_SHORT).show();
			refresh();
		};
		if (!SettingsScreen.pref(activity, SettingsScreen.KEY_CONFIRM_DELETE, true)) {
			doDelete.run();
			return;
		}
		new AlertDialog.Builder(activity)
		.setTitle("Delete " + entry.name() + "?")
		.setMessage(entry.isDirectory ? "This deletes the folder and everything inside it." : "This can't be undone.")
		.setPositiveButton("Delete", (dialog, which) -> doDelete.run())
		.setNegativeButton("Cancel", null)
		.show();
	}

	private static LinearLayout.LayoutParams marginParams(int l, int t, int r, int b) {
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		p.setMargins(Theme.dp(activity, l), Theme.dp(activity, t), Theme.dp(activity, r), Theme.dp(activity, b));
		return p;
	}

	// ==========================================
	// File Handlers (Text Editor & Image Viewer)
	// ==========================================

	private static void handleFileClick(File file) {
		if (pickCallback != null) {
			FileCallback cb = pickCallback;
			Dialog d = pickDialog;
			pickCallback = null;
			pickDialog = null;
			if (d != null) d.dismiss();
			cb.accept(file);
			return;
		}
		String name = file.getName().toLowerCase();
		if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
		|| name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp")) {
			openImageViewer(file);
		} else if (name.endsWith(".scene") || name.endsWith(".scl") || name.endsWith(".gopt")
		|| name.endsWith(".gplayer") || name.endsWith(".gdata") || name.endsWith(".gstate")) {
			TextEditor.open(activity, file, true);
		} else if (name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".wav")
		|| name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".flac")) {
			openMusicPlayer(file);
		} else if (name.endsWith(".pod")) {
			PODViewer.show(file);
		} else if (name.endsWith(".pvr") || name.endsWith(".tex")) {
			Toast.makeText(activity, "Decoding " + file.getName() + "…", Toast.LENGTH_SHORT).show();
			new Thread(() -> {
				final Bitmap bmp = net.kiwi.lawncher.filerift.Filerift.decodeTextureBitmap(file);
				activity.runOnUiThread(() -> {
					if (bmp == null) {
						Toast.makeText(activity, "Couldn't decode " + file.getName(), Toast.LENGTH_SHORT).show();
					} else {
						openTextureViewer(file, bmp);
					}
				});
			}).start();
		} else {
			// Text / toml / lua / json / etc.
			TextEditor.open(activity, file, false);
		}
	}

	private static void openTextureViewer(File file, Bitmap bmp) {
		Dialog dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_Light_NoActionBar);
		LinearLayout root = new LinearLayout(activity);
		Theme.attachToRoot(root);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(Color.parseColor(Theme.BG));

		LinearLayout header = new LinearLayout(activity);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setPadding(Theme.dp(activity, 18), Theme.dp(activity, 14),
		Theme.dp(activity, 12), Theme.dp(activity, 14));

		LinearLayout titleCol = new LinearLayout(activity);
		titleCol.setOrientation(LinearLayout.VERTICAL);
		titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		TextView title = new TextView(activity);
		title.setText(file.getName());
		title.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		title.setTypeface(null, Typeface.BOLD);
		title.setMaxLines(1);
		titleCol.addView(title);

		TextView sub = new TextView(activity);
		sub.setText(bmp.getWidth() + " x " + bmp.getHeight() + " px\u00b7 decoded from compressed texture");
		sub.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
		sub.setMaxLines(1);
		titleCol.addView(sub);

		header.addView(titleCol);

		TextView btnSave = new TextView(activity);
		btnSave.setText("Export PNG");
		btnSave.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		btnSave.setPadding(Theme.dp(activity, 10), Theme.dp(activity, 6),
		Theme.dp(activity, 10), Theme.dp(activity, 6));
		btnSave.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.ACCENT_BLUE));
		btnSave.setTextColor(Color.parseColor("#0B0E14"));
		header.addView(btnSave);

		ImageView btnClose = new ImageView(activity);
		btnClose.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
		btnClose.setColorFilter(Color.parseColor(Theme.TEXT_DIM));
		btnClose.setPadding(Theme.dp(activity, 12), 0, 0, 0);
		header.addView(btnClose);

		root.addView(header);

		ImageView imageView = new ImageView(activity);
		imageView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
		imageView.setAdjustViewBounds(true);
		imageView.setImageBitmap(bmp);
		root.addView(imageView);

		btnSave.setOnClickListener(v -> {
			// Cache + system share/export sheet (not save-into-folder).
			try {
				String name0 = file.getName();
				String lower0 = name0.toLowerCase();
				String base = lower0.endsWith(".pvr") || lower0.endsWith(".tex")
				? name0.substring(0, name0.lastIndexOf('.')) : name0;
				File out = new File(activity.getCacheDir(), base + ".png");
				try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
					bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
				}
				exportFile(out);
			} catch (Exception e) {
				Toast.makeText(activity, "Couldn't export PNG: " + e.getMessage(), Toast.LENGTH_SHORT).show();
			}
		});
		btnClose.setOnClickListener(v -> dialog.dismiss());

		dialog.setContentView(root);
		dialog.show();
	}

	private static void openImageViewer(File file) {
		Dialog dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_Light_NoActionBar);
		LinearLayout root = new LinearLayout(activity);
		Theme.attachToRoot(root);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(Color.parseColor(Theme.BG));

		LinearLayout header = new LinearLayout(activity);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setPadding(30, 40, 30, 40);

		TextView title = new TextView(activity);
		title.setText(file.getName());
		title.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		title.setTypeface(null, Typeface.BOLD);
		title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		ImageView btnClose = new ImageView(activity);
		btnClose.setImageResource(R.drawable.ic_close);
		btnClose.setColorFilter(Color.parseColor(Theme.TEXT_DIM));
		btnClose.setPadding(20, 0, 0, 0);

		header.addView(title);
		header.addView(btnClose);
		root.addView(header);

		ImageView imageView = new ImageView(activity);
		imageView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
		Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
		imageView.setImageBitmap(bmp);

		root.addView(imageView);
		dialog.setContentView(root);

		btnClose.setOnClickListener(v -> dialog.dismiss());
		dialog.show();
	}

	private static MediaPlayer musicPlayer;

	private static void releaseMusicPlayer() {
		if (musicPlayer != null) {
			try { musicPlayer.release(); } catch (Exception ignored) {}
			musicPlayer = null;
		}
	}

	private static void openMusicPlayer(File file) {
		Dialog dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_Light_NoActionBar);
		LinearLayout root = new LinearLayout(activity);
		Theme.attachToRoot(root);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(Color.parseColor(Theme.BG));

		LinearLayout header = new LinearLayout(activity);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setPadding(30, 40, 30, 40);

		TextView title = new TextView(activity);
		title.setText(file.getName());
		title.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		title.setTypeface(null, Typeface.BOLD);
		title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		ImageView btnClose = new ImageView(activity);
		btnClose.setImageResource(R.drawable.ic_close);
		btnClose.setColorFilter(Color.parseColor(Theme.TEXT_DIM));
		btnClose.setPadding(20, 0, 0, 0);

		header.addView(title);
		header.addView(btnClose);
		root.addView(header);

		LinearLayout controls = new LinearLayout(activity);
		controls.setOrientation(LinearLayout.VERTICAL);
		controls.setGravity(Gravity.CENTER_HORIZONTAL);
		controls.setPadding(Theme.dp(activity, 30), Theme.dp(activity, 50), Theme.dp(activity, 30), Theme.dp(activity, 50));

		TextView btnPlayPause = new TextView(activity);
		btnPlayPause.setText("\u25B6 Play");
		btnPlayPause.setTextColor(Color.parseColor(Theme.ACCENT_BLUE));
		btnPlayPause.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
		btnPlayPause.setTypeface(null, Typeface.BOLD);
		btnPlayPause.setPadding(Theme.dp(activity, 24), Theme.dp(activity, 12), Theme.dp(activity, 24), Theme.dp(activity, 12));

		SeekBar seekBar = new SeekBar(activity);
		LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		seekParams.topMargin = Theme.dp(activity, 24);
		seekBar.setLayoutParams(seekParams);

		controls.addView(btnPlayPause);
		controls.addView(seekBar);
		root.addView(controls);

		dialog.setContentView(root);

		releaseMusicPlayer();
		musicPlayer = new MediaPlayer();
		try {
			musicPlayer.setDataSource(file.getAbsolutePath());
			musicPlayer.prepare();
			seekBar.setMax(musicPlayer.getDuration());
		} catch (Exception e) {
			Toast.makeText(activity, "Couldn't play " + file.getName(), Toast.LENGTH_SHORT).show();
			releaseMusicPlayer();
			dialog.dismiss();
			return;
		}

		Handler handler = new Handler(Looper.getMainLooper());
		Runnable updateSeek = new Runnable() {
			@Override
			public void run() {
				if (musicPlayer != null && musicPlayer.isPlaying()) {
					seekBar.setProgress(musicPlayer.getCurrentPosition());
					handler.postDelayed(this, 500);
				}
			}
		};

		btnPlayPause.setOnClickListener(v -> {
			if (musicPlayer == null) return;
			if (musicPlayer.isPlaying()) {
				musicPlayer.pause();
				btnPlayPause.setText("\u25B6 Play");
			} else {
				musicPlayer.start();
				btnPlayPause.setText("\u23F8 Pause");
				handler.post(updateSeek);
			}
		});

		seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
				if (fromUser && musicPlayer != null) musicPlayer.seekTo(progress);
			}
			@Override
			public void onStartTrackingTouch(SeekBar sb) {}
			@Override
			public void onStopTrackingTouch(SeekBar sb) {}
		});

		musicPlayer.setOnCompletionListener(mp -> {
			btnPlayPause.setText("\u25B6 Play");
			seekBar.setProgress(0);
		});

		btnClose.setOnClickListener(v -> {
			handler.removeCallbacksAndMessages(null);
			releaseMusicPlayer();
			dialog.dismiss();
		});
		dialog.setOnDismissListener(d -> {
			handler.removeCallbacksAndMessages(null);
			releaseMusicPlayer();
		});

		dialog.show();
	}

	// ==========================================
	// Sidebar + bookmarks
	// ==========================================

	private static final String BOOKMARKS_PREFS = "lawncher_prefs";
	private static final String KEY_BOOKMARKS = "file_bookmarks";

	private static LinearLayout buildFilesSidebar(Activity act) {
		LinearLayout side = new LinearLayout(act);
		side.setOrientation(LinearLayout.VERTICAL);
		side.setBackgroundColor(Color.parseColor(Theme.CARD));
		side.setPadding(Theme.dp(act, 10), Theme.dp(act, 12), Theme.dp(act, 10), Theme.dp(act, 12));
		LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
		Theme.dp(act, 168), ViewGroup.LayoutParams.MATCH_PARENT);
		side.setLayoutParams(sp);

		LinearLayout head = new LinearLayout(act);
		head.setOrientation(LinearLayout.HORIZONTAL);
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.setPadding(0, 0, 0, Theme.dp(act, 10));
		TextView headTitle = new TextView(act);
		headTitle.setText("Places");
		headTitle.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		headTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		headTitle.setTypeface(null, Typeface.BOLD);
		headTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		head.addView(headTitle);
		TextView closeSide = new TextView(act);
		closeSide.setText("×");
		closeSide.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		closeSide.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
		closeSide.setPadding(Theme.dp(act, 10), Theme.dp(act, 4), Theme.dp(act, 4), Theme.dp(act, 4));
		closeSide.setBackground(Theme.rippleBackground(Theme.dp(act, 8), Theme.BG));
		closeSide.setOnClickListener(v -> setSideOpen(false));
		head.addView(closeSide);
		side.addView(head);

		TextView rootsLabel = new TextView(act);
		rootsLabel.setText("PLACES");
		rootsLabel.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		rootsLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
		rootsLabel.setTypeface(null, Typeface.BOLD);
		rootsLabel.setPadding(0, 0, 0, Theme.dp(act, 8));
		side.addView(rootsLabel);

		side.addView(sidePlaceBtn(act, "External", () -> {
			currentRoot = FileManager.Root.EXTERNAL;
			currentDir = FileManager.rootDir(act, currentRoot);
			refresh();
		}));
		side.addView(sidePlaceBtn(act, "Internal", () -> {
			currentRoot = FileManager.Root.INTERNAL;
			currentDir = FileManager.rootDir(act, currentRoot);
			refresh();
		}));

		TextView bmLabel = new TextView(act);
		bmLabel.setText("BOOKMARKS");
		bmLabel.setTextColor(Color.parseColor(Theme.TEXT_DIM));
		bmLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
		bmLabel.setTypeface(null, Typeface.BOLD);
		bmLabel.setPadding(0, Theme.dp(act, 16), 0, Theme.dp(act, 8));
		side.addView(bmLabel);

		ScrollView bmScroll = new ScrollView(act);
		bookmarkList = new LinearLayout(act);
		bookmarkList.setOrientation(LinearLayout.VERTICAL);
		bmScroll.addView(bookmarkList);
		side.addView(bmScroll, new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		TextView addBm = new TextView(act);
		addBm.setText("+ Bookmark here");
		addBm.setTextColor(Color.parseColor(Theme.ACCENT_GREEN));
		addBm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		addBm.setPadding(Theme.dp(act, 8), Theme.dp(act, 10), Theme.dp(act, 8), Theme.dp(act, 10));
		addBm.setBackground(Theme.rippleBackground(Theme.dp(act, 8), Theme.BG));
		addBm.setOnClickListener(v -> {
			if (currentDir != null) {
				addBookmark(act, currentDir.getAbsolutePath());
				rebuildBookmarks(act);
				Toast.makeText(act, "Bookmarked", Toast.LENGTH_SHORT).show();
			}
		});
		side.addView(addBm);

		rebuildBookmarks(act);
		return side;
	}

	private static TextView sidePlaceBtn(Activity act, String label, Runnable action) {
		TextView b = new TextView(act);
		b.setText(label);
		b.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
		b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		b.setPadding(Theme.dp(act, 10), Theme.dp(act, 10), Theme.dp(act, 10), Theme.dp(act, 10));
		b.setClickable(true);
		b.setFocusable(true);
		b.setBackground(Theme.rippleBackground(Theme.dp(act, 8), Theme.BG));
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		p.bottomMargin = Theme.dp(act, 4);
		b.setLayoutParams(p);
		b.setOnClickListener(v -> action.run());
		return b;
	}

	private static void rebuildBookmarks(Activity act) {
		if (bookmarkList == null) return;
		bookmarkList.removeAllViews();
		List<String> bms = loadBookmarks(act);
		if (bms.isEmpty()) {
			TextView empty = new TextView(act);
			empty.setText("None yet");
			empty.setTextColor(Color.parseColor(Theme.TEXT_DIM));
			empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
			empty.setPadding(Theme.dp(act, 10), Theme.dp(act, 8), Theme.dp(act, 10), Theme.dp(act, 8));
			bookmarkList.addView(empty);
			return;
		}
		for (String path : bms) {
			final String bmPath = path;
			File f = new File(path);

			// Same chrome as External / Internal place buttons
			LinearLayout row = new LinearLayout(act);
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setClickable(true);
			row.setFocusable(true);
			row.setPadding(Theme.dp(act, 10), Theme.dp(act, 10), Theme.dp(act, 6), Theme.dp(act, 10));
			row.setBackground(Theme.rippleBackground(Theme.dp(act, 8), Theme.BG));

			TextView label = new TextView(act);
			label.setText(f.getName().isEmpty() ? path : f.getName());
			label.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
			label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
			label.setMaxLines(1);
			label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
			row.addView(label);

			TextView rm = new TextView(act);
			rm.setText("×");
			rm.setTextColor(Color.parseColor(Theme.TEXT_DIM));
			rm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
			rm.setPadding(Theme.dp(act, 8), Theme.dp(act, 2), Theme.dp(act, 4), Theme.dp(act, 2));
			rm.setOnClickListener(v -> {
				removeBookmark(act, bmPath);
				rebuildBookmarks(act);
			});
			row.addView(rm);

			row.setOnClickListener(v -> {
				File target = new File(bmPath);
				File inn = FileManager.rootDir(act, FileManager.Root.INTERNAL);
				File jump = target.isDirectory() ? target : target.getParentFile();
				if (jump == null || !jump.exists()) {
					Toast.makeText(act, "Bookmark missing", Toast.LENGTH_SHORT).show();
					return;
				}
				if (jump.getAbsolutePath().startsWith(inn.getAbsolutePath()))
					currentRoot = FileManager.Root.INTERNAL;
				else
					currentRoot = FileManager.Root.EXTERNAL;
				currentDir = jump;
				refresh();
				if (target.isFile() && target.exists()) handleFileClick(target);
			});

			LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			rp.bottomMargin = Theme.dp(act, 4);
			row.setLayoutParams(rp);
			bookmarkList.addView(row);
		}
	}

	private static List<String> loadBookmarks(Activity act) {
		SharedPreferences prefs = act.getSharedPreferences(BOOKMARKS_PREFS, Activity.MODE_PRIVATE);
		String raw = prefs.getString(KEY_BOOKMARKS, "");
		List<String> out = new java.util.ArrayList<>();
		if (raw == null || raw.isEmpty()) return out;
		for (String line : raw.split("\n")) {
			String s = line.trim();
			if (!s.isEmpty()) out.add(s);
		}
		return out;
	}

	private static void saveBookmarks(Activity act, List<String> list) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) sb.append('\n');
			sb.append(list.get(i));
		}
		act.getSharedPreferences(BOOKMARKS_PREFS, Activity.MODE_PRIVATE)
		.edit().putString(KEY_BOOKMARKS, sb.toString()).apply();
	}

	private static void addBookmark(Activity act, String path) {
		List<String> list = loadBookmarks(act);
		if (!list.contains(path)) list.add(path);
		saveBookmarks(act, list);
	}

	private static void removeBookmark(Activity act, String path) {
		List<String> list = loadBookmarks(act);
		list.remove(path);
		saveBookmarks(act, list);
	}

	/** Navigate Files to a directory (used from mod detail "Open folder"). */
	static void openDirectory(Activity act, File dir) {
		if (act == null || dir == null) return;
		File target = dir.isDirectory() ? dir : dir.getParentFile();
		if (target == null || !target.exists()) {
			Toast.makeText(act, "Folder not found", Toast.LENGTH_SHORT).show();
			return;
		}
		File inn = FileManager.rootDir(act, FileManager.Root.INTERNAL);
		String path = target.getAbsolutePath();
		if (path.startsWith(inn.getAbsolutePath()))
			currentRoot = FileManager.Root.INTERNAL;
		else
			currentRoot = FileManager.Root.EXTERNAL;
		currentDir = target;
		sideOpen = false;
		Sidebar.showScreen(build(act), null);
	}

	/**
	 * In-app file picker (same roots/bookmarks as Files). Calls onPicked with a file.
	 */
	static void pickFile(Activity act, FileCallback onPicked) {
		Dialog dialog = new Dialog(act, android.R.style.Theme_DeviceDefault_NoActionBar);
		dialog.setContentView(buildInternal(act, onPicked, dialog));
		dialog.setOnDismissListener(d -> {
			pickCallback = null;
			pickDialog = null;
		});
		dialog.show();
	}


}