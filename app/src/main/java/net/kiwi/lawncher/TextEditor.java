package net.kiwi.lawncher;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.text.style.BackgroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Multi-file FileRift / text editor with:
 *  - syntax highlighting + whitespace glyphs (· for space, → for tab)
 *  - FileRift outline / jump-to-object
 *  - autocomplete from FileRiftLang
 *  - tools overlay (Find, Outline, …)
 *  - bottom action bar: TAB, {}, $d (duplicate)
 */
public class TextEditor {

	private static final int C_COMMENT = Color.parseColor("#6C7086");
	private static final int C_TAG = Color.parseColor("#89B4FA");
	private static final int C_KEY = Color.parseColor("#CBA6F7");
	private static final int C_STRING = Color.parseColor("#A6E3A1");
	private static final int C_NUMBER = Color.parseColor("#FAB387");
	private static final int C_MACRO = Color.parseColor("#F5C2E7");
	private static final int C_CHUNK_CHIP = Color.parseColor("#89B4FA");
	private static final int C_CHUNK_CHIP_BG = Color.parseColor("#2A2B3D");
	private static final int C_CHUNK = Color.parseColor("#F9E2AF");
	private static final int C_BRACE = Color.parseColor("#CDD6F4");
	private static final int C_WS = Color.parseColor("#45475A");
	private static final int C_ERROR = Color.parseColor("#F38BA8");
	private static final int C_ERROR_BG = Color.parseColor("#33F38BA8");

	private static final Pattern RE_COMMENT = Pattern.compile("#.*$");
	private static final Pattern RE_TAG = Pattern.compile("^(\\s*)([A-Za-z_][A-Za-z0-9_]*)(\\s*\\{)");
	private static final Pattern RE_KEY_COLON = Pattern.compile("^(\\s*)([A-Za-z_][A-Za-z0-9_]*)(\\s*:)");
	private static final Pattern RE_STRING = Pattern.compile("(['\"])(?:\\\\.|[^\\\\])*?\\1");
	private static final Pattern RE_NUMBER = Pattern.compile("\\b-?\\d+(?:\\.\\d+)?\\b");
	private static final Pattern RE_MACRO = Pattern.compile("\\$\\w+\\[[^\\]]*\\]");
	private static final Pattern RE_CHUNK_MARK = Pattern.compile("\\$end|\\$");
	private static final Pattern RE_LUA_KEYWORD = Pattern.compile(
			"\\b(and|break|do|else|elseif|end|false|for|function|goto|if|in|local|nil|not|or|repeat|return|then|true|until|while)\\b");
	private static final Pattern RE_LUA_COMMENT = Pattern.compile("--.*$");
	private static final int C_LUA_KW = Color.parseColor("#CBA6F7");
	private static final int C_LUA_FN = Color.parseColor("#89B4FA");

	private static class Tab {
		File file;
		boolean isFileRift;
		String fileType = "";
		boolean dirty;
		boolean loaded;
		EditHistory history;
		EditText editor;
		ScrollView scroll;
		FileRiftLang.Analysis analysis;
		boolean highlighting;
		volatile int highlightGen;
		volatile int analyzeGen;

		/** Virtual Lua tab backed by a $...$end chunk in parent. */
		boolean isChunk;
		Tab parentTab;
		int chunkOpenLine = -1;
		String chunkKey = "";
		int chunkIndex = -1;
		String fullText;
		boolean collapsedView;
	}

	public static final int REQ_EDITOR_OPEN = 5102;
	private static java.lang.ref.WeakReference<Session> liveSession;

	@SuppressLint("ClickableViewAccessibility")
	public static void open(Activity activity, File file, boolean isFileRift) {
		Session s = new Session(activity);
		liveSession = new java.lang.ref.WeakReference<>(s);
		s.show(file, isFileRift);
	}

	public static void handleActivityResult(int requestCode, int resultCode, android.content.Intent data) {
		// Editor file open uses in-app FilesScreen.pickFile — no system URI handling.
	}

	private static class Session {
		final Activity activity;
		final Dialog dialog;
		final List<Tab> tabs = new ArrayList<>();
		int active = -1;

		FrameLayout rootFrame;
		LinearLayout mainCol;
		LinearLayout tabStrip;
		FrameLayout editorHost;
		LinearLayout findStrip;
		EditText findInput;
		TextView statusBar;
		LinearLayout bottomBar;
		View overlayScrim;
		LinearLayout toolsPanel;
		LinearLayout outlineList;
		PopupWindow completionPopup;
		LinearLayout completionList;
		boolean toolsOpen;
		boolean findVisible;
		boolean showOutline;
		TextView headerUndo;
		TextView headerRedo;
		TextView headerFindBtn;
		TextView headerOutlineBtn;
		TextView headerCmdBtn;

		final ExecutorService executor = Executors.newSingleThreadExecutor();
		final AtomicBoolean cancelled = new AtomicBoolean(false);
		final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
		Runnable pendingHighlight;
		Runnable pendingAnalyze;
		Runnable completionRunnable;

		Session(Activity activity) {
			this.activity = activity;
			dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar);
			if (dialog.getWindow() != null) {
				dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
			}
		}

		void show(File file, boolean isFileRift) {
			buildChrome();
			dialog.setContentView(rootFrame);
			dialog.setOnDismissListener(d -> {
				cancelled.set(true);
				executor.shutdownNow();
				if (completionPopup != null && completionPopup.isShowing()) completionPopup.dismiss();
			});
			dialog.show();
			openTab(file, isFileRift);
		}

		void buildChrome() {
			rootFrame = new FrameLayout(activity);
			rootFrame.setBackgroundColor(Color.parseColor(Theme.BG));
			rootFrame.setOnApplyWindowInsetsListener((v, insets) -> {
				int left, top, right, bottom;
				if (android.os.Build.VERSION.SDK_INT >= 30) {
					android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
					android.graphics.Insets ime = insets.getInsets(android.view.WindowInsets.Type.ime());
					left = bars.left; top = bars.top; right = bars.right;
					bottom = Math.max(bars.bottom, ime.bottom);
				} else {
					left = insets.getSystemWindowInsetLeft();
					top = insets.getSystemWindowInsetTop();
					right = insets.getSystemWindowInsetRight();
					bottom = insets.getSystemWindowInsetBottom();
				}
				v.setPadding(left, top, right, bottom);
				return insets;
			});
			rootFrame.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
				@Override public void onViewAttachedToWindow(View v) { v.requestApplyInsets(); }
				@Override public void onViewDetachedFromWindow(View v) {}
			});

			mainCol = new LinearLayout(activity);
			mainCol.setOrientation(LinearLayout.VERTICAL);
			rootFrame.addView(mainCol, new FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

			// Header
			LinearLayout header = new LinearLayout(activity);
			header.setGravity(Gravity.CENTER_VERTICAL);
			header.setPadding(Theme.dp(activity, 8), Theme.dp(activity, 10),
					Theme.dp(activity, 8), Theme.dp(activity, 10));
			header.setBackgroundColor(Color.parseColor(Theme.CARD));

			TextView title = new TextView(activity);
			title.setText("Editor");
			title.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
			title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
			title.setTypeface(null, Typeface.BOLD);
			header.addView(title);

			headerUndo = headerChip("↶", Theme.TEXT_DIM, v -> {
				Tab tab = activeTab();
				if (tab != null && tab.history != null && tab.history.canUndo()) {
					tab.history.undo(tab.editor);
					refreshUndoRedo();
				}
			});
			header.addView(headerUndo);

			headerRedo = headerChip("↷", Theme.TEXT_DIM, v -> {
				Tab tab = activeTab();
				if (tab != null && tab.history != null && tab.history.canRedo()) {
					tab.history.redo(tab.editor);
					refreshUndoRedo();
				}
			});
			header.addView(headerRedo);

			headerFindBtn = headerChip("Find", Theme.TEXT_DIM, v -> {
				if (findVisible) hideFind(); else showFind();
			});
			header.addView(headerFindBtn);

			headerOutlineBtn = headerChip("Out", Theme.TEXT_DIM, v -> openTools());
			header.addView(headerOutlineBtn);

			headerCmdBtn = headerChip("⌘", Theme.ACCENT_BLUE, v -> showCommandPalette());
			header.addView(headerCmdBtn);

			View spacer = new View(activity);
			spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
			header.addView(spacer);

			header.addView(iconBtn(R.drawable.ic_save, Theme.ACCENT_BLUE, v -> saveActive()));
			header.addView(iconBtn(R.drawable.ic_close, Theme.TEXT_DIM, v -> dialog.dismiss()));
			mainCol.addView(header);

			// Find strip
			findStrip = new LinearLayout(activity);
			findStrip.setOrientation(LinearLayout.HORIZONTAL);
			findStrip.setGravity(Gravity.CENTER_VERTICAL);
			findStrip.setPadding(Theme.dp(activity, 12), Theme.dp(activity, 8),
					Theme.dp(activity, 12), Theme.dp(activity, 8));
			findStrip.setBackgroundColor(Color.parseColor(Theme.CARD));
			findStrip.setVisibility(View.GONE);

			GradientDrawable searchBg = new GradientDrawable();
			searchBg.setCornerRadius(Theme.dp(activity, 8));
			searchBg.setColor(Color.parseColor(Theme.BG));
			searchBg.setStroke(Theme.dp(activity, 1), Color.parseColor(Theme.BORDER));

			findInput = new EditText(activity);
			findInput.setHint("Find…");
			findInput.setHintTextColor(Color.parseColor(Theme.TEXT_DIM));
			findInput.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
			findInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
			findInput.setBackground(searchBg);
			findInput.setPadding(Theme.dp(activity, 10), Theme.dp(activity, 6),
					Theme.dp(activity, 10), Theme.dp(activity, 6));
			findInput.setSingleLine(true);
			findInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
			findStrip.addView(findInput);

			Button next = smallBtn("Next", Theme.ACCENT_BLUE);
			next.setOnClickListener(v -> findNext());
			LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			np.leftMargin = Theme.dp(activity, 6);
			findStrip.addView(next, np);

			TextView dismiss = new TextView(activity);
			dismiss.setText("×");
			dismiss.setTextColor(Color.parseColor(Theme.TEXT_DIM));
			dismiss.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
			dismiss.setPadding(Theme.dp(activity, 12), 0, Theme.dp(activity, 4), 0);
			dismiss.setOnClickListener(v -> hideFind());
			findStrip.addView(dismiss);
			mainCol.addView(findStrip);

			// Tabs
			HorizontalScrollView tabScroll = new HorizontalScrollView(activity);
			tabScroll.setHorizontalScrollBarEnabled(false);
			tabStrip = new LinearLayout(activity);
			tabStrip.setOrientation(LinearLayout.HORIZONTAL);
			tabStrip.setPadding(Theme.dp(activity, 6), Theme.dp(activity, 6), Theme.dp(activity, 6), 0);
			tabScroll.addView(tabStrip);
			mainCol.addView(tabScroll, new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

			editorHost = new FrameLayout(activity);
			mainCol.addView(editorHost, new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

			// Bottom action bar (above soft keyboard conceptually)
			bottomBar = buildBottomBar();
			mainCol.addView(bottomBar);

			statusBar = new TextView(activity);
			statusBar.setTextColor(Color.parseColor(Theme.TEXT_DIM));
			statusBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
			statusBar.setPadding(Theme.dp(activity, 12), Theme.dp(activity, 4),
					Theme.dp(activity, 12), Theme.dp(activity, 6));
			statusBar.setBackgroundColor(Color.parseColor(Theme.CARD));
			statusBar.setText("Ready");
			mainCol.addView(statusBar);

			// Overlay tools
			overlayScrim = new View(activity);
			overlayScrim.setBackgroundColor(Color.parseColor("#66000000"));
			overlayScrim.setVisibility(View.GONE);
			overlayScrim.setOnClickListener(v -> closeTools());
			rootFrame.addView(overlayScrim, new FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

			toolsPanel = buildToolsPanel();
			FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
					Theme.dp(activity, 260), ViewGroup.LayoutParams.MATCH_PARENT);
			tp.gravity = Gravity.END;
			toolsPanel.setTranslationX(Theme.dp(activity, 260));
			rootFrame.addView(toolsPanel, tp);

			// Completion popup host list (shown as PopupWindow)
			completionList = new LinearLayout(activity);
			completionList.setOrientation(LinearLayout.VERTICAL);
			completionList.setBackgroundColor(Color.parseColor(Theme.CARD));
			int pad = Theme.dp(activity, 4);
			completionList.setPadding(pad, pad, pad, pad);
		}

		LinearLayout buildBottomBar() {
			LinearLayout bar = new LinearLayout(activity);
			bar.setOrientation(LinearLayout.HORIZONTAL);
			bar.setGravity(Gravity.CENTER_VERTICAL);
			bar.setPadding(Theme.dp(activity, 8), Theme.dp(activity, 6),
					Theme.dp(activity, 8), Theme.dp(activity, 6));
			bar.setBackgroundColor(Color.parseColor(Theme.CARD));

			bar.addView(chipBtn("Tab", v -> insertText("\t")));
			bar.addView(chipBtn("{ }", v -> insertBraces()));
			bar.addView(chipBtn("Dup", v -> duplicateLineOrSelection()));
			bar.addView(chipBtn("Prev", v -> jumpObject(-1)));
			bar.addView(chipBtn("Next", v -> jumpObject(1)));
			bar.addView(chipBtn("Chunk", v -> openChunkUnderCursor()));
			bar.addView(chipBtn("Tpl", v -> goToTemplateUnderCursor()));
			bar.addView(chipBtn("Del$", v -> deleteSelectedChunks()));
			return bar;
		}

		TextView headerChip(String label, String color, View.OnClickListener click) {
			TextView t = new TextView(activity);
			t.setText(label);
			t.setTextColor(Color.parseColor(color));
			t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
			t.setPadding(Theme.dp(activity, 8), Theme.dp(activity, 6),
					Theme.dp(activity, 8), Theme.dp(activity, 6));
			t.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.BG));
			LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			p.leftMargin = Theme.dp(activity, 4);
			t.setLayoutParams(p);
			t.setOnClickListener(click);
			return t;
		}

		void refreshUndoRedo() {
			Tab tab = activeTab();
			boolean canU = tab != null && tab.history != null && tab.history.canUndo();
			boolean canR = tab != null && tab.history != null && tab.history.canRedo();
			if (headerUndo != null) {
				headerUndo.setTextColor(Color.parseColor(canU ? Theme.ACCENT_BLUE : Theme.TEXT_DIM));
				headerUndo.setAlpha(canU ? 1f : 0.4f);
			}
			if (headerRedo != null) {
				headerRedo.setTextColor(Color.parseColor(canR ? Theme.ACCENT_BLUE : Theme.TEXT_DIM));
				headerRedo.setAlpha(canR ? 1f : 0.4f);
			}
		}

		TextView chipBtn(String label, View.OnClickListener click) {
			TextView t = new TextView(activity);
			t.setText(label);
			t.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
			t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
			t.setTypeface(Typeface.MONOSPACE);
			t.setPadding(Theme.dp(activity, 12), Theme.dp(activity, 8),
					Theme.dp(activity, 12), Theme.dp(activity, 8));
			t.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.BG));
			LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			p.rightMargin = Theme.dp(activity, 6);
			t.setLayoutParams(p);
			t.setOnClickListener(click);
			return t;
		}

		LinearLayout buildToolsPanel() {
			LinearLayout panel = new LinearLayout(activity);
			panel.setOrientation(LinearLayout.VERTICAL);
			panel.setBackgroundColor(Color.parseColor(Theme.CARD));
			panel.setPadding(Theme.dp(activity, 14), Theme.dp(activity, 48),
					Theme.dp(activity, 14), Theme.dp(activity, 16));
			panel.setElevation(Theme.dp(activity, 12));

			TextView label = new TextView(activity);
			label.setText("Tools");
			label.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
			label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
			label.setTypeface(null, Typeface.BOLD);
			label.setPadding(0, 0, 0, Theme.dp(activity, 12));
			panel.addView(label);

			panel.addView(toolBtn("Find", v -> { closeTools(); showFind(); }));
			panel.addView(toolBtn("Go to template", v -> {
				closeTools();
				goToTemplateUnderCursor();
			}));
			panel.addView(toolBtn("Outline", v -> {
				showOutline = true;
				rebuildOutline();
			}));
			panel.addView(toolBtn("Save", v -> { saveActive(); closeTools(); }));
			panel.addView(toolBtn("Undo", v -> {
				Tab t = activeTab();
				if (t != null && t.history != null) t.history.undo(t.editor);
			}));
			panel.addView(toolBtn("Redo", v -> {
				Tab t = activeTab();
				if (t != null && t.history != null) t.history.redo(t.editor);
			}));
			panel.addView(toolBtn("Close tab", v -> { closeActiveTab(); closeTools(); }));

			TextView ol = new TextView(activity);
			ol.setText("OUTLINE");
			ol.setTextColor(Color.parseColor(Theme.TEXT_DIM));
			ol.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
			ol.setTypeface(null, Typeface.BOLD);
			ol.setPadding(0, Theme.dp(activity, 16), 0, Theme.dp(activity, 8));
			panel.addView(ol);

			ScrollView outlineScroll = new ScrollView(activity);
			outlineList = new LinearLayout(activity);
			outlineList.setOrientation(LinearLayout.VERTICAL);
			outlineScroll.addView(outlineList);
			panel.addView(outlineScroll, new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

			TextView close = new TextView(activity);
			close.setText("Close panel");
			close.setTextColor(Color.parseColor(Theme.TEXT_DIM));
			close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
			close.setGravity(Gravity.CENTER);
			close.setPadding(0, Theme.dp(activity, 12), 0, 0);
			close.setOnClickListener(v -> closeTools());
			panel.addView(close);
			return panel;
		}

		View toolBtn(String text, View.OnClickListener click) {
			TextView b = new TextView(activity);
			b.setText(text);
			b.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
			b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
			b.setPadding(Theme.dp(activity, 14), Theme.dp(activity, 12),
					Theme.dp(activity, 14), Theme.dp(activity, 12));
			b.setBackground(Theme.rippleBackground(Theme.dp(activity, 10), Theme.BG));
			LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			p.bottomMargin = Theme.dp(activity, 6);
			b.setLayoutParams(p);
			b.setOnClickListener(click);
			return b;
		}

		void openTools() {
			toolsOpen = true;
			overlayScrim.setVisibility(View.VISIBLE);
			overlayScrim.setAlpha(0f);
			overlayScrim.animate().alpha(1f).setDuration(180).start();
			toolsPanel.animate().translationX(0).setDuration(200).start();
			rebuildOutline();
		}

		void closeTools() {
			toolsOpen = false;
			showOutline = false;
			toolsPanel.animate().translationX(Theme.dp(activity, 260)).setDuration(180).start();
			overlayScrim.animate().alpha(0f).setDuration(180)
					.withEndAction(() -> {
						if (!toolsOpen) overlayScrim.setVisibility(View.GONE);
					}).start();
		}

		void showFind() {
			findVisible = true;
			findStrip.setVisibility(View.VISIBLE);
			findInput.requestFocus();
		}

		void hideFind() {
			findVisible = false;
			findStrip.setVisibility(View.GONE);
		}

		void rebuildOutline() {
			outlineList.removeAllViews();
			Tab tab = activeTab();
			if (tab != null && tab.isChunk) {
				TextView info = new TextView(activity);
				info.setText("Lua chunk · " + tab.chunkKey + "\nSave writes back into parent.");
				info.setTextColor(Color.parseColor(Theme.TEXT_DIM));
				info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
				outlineList.addView(info);
				return;
			}
			if (tab == null || tab.analysis == null) {
				TextView empty = new TextView(activity);
				empty.setText(tab != null && tab.isFileRift ? "No objects yet" : "Outline is FileRift-only");
				empty.setTextColor(Color.parseColor(Theme.TEXT_DIM));
				empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
				outlineList.addView(empty);
				return;
			}
			if (!tab.analysis.chunks.isEmpty()) {
				TextView hdr = new TextView(activity);
				hdr.setText("CHUNKS");
				hdr.setTextColor(Color.parseColor(Theme.TEXT_DIM));
				hdr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
				hdr.setTypeface(null, Typeface.BOLD);
				hdr.setPadding(0, 0, 0, Theme.dp(activity, 6));
				outlineList.addView(hdr);
				for (FileRiftLang.Chunk c : tab.analysis.chunks) {
					addChunkRow(tab, c);
				}
				TextView gap = new TextView(activity);
				gap.setPadding(0, Theme.dp(activity, 10), 0, 0);
				outlineList.addView(gap);
			}
			for (FileRiftLang.Node node : tab.analysis.outline) {
				addOutlineRow(node, 0);
				for (FileRiftLang.Node kid : node.kids) addOutlineRow(kid, 1);
			}
			if (outlineList.getChildCount() == 0) {
				TextView empty = new TextView(activity);
				empty.setText("No Object / Template blocks");
				empty.setTextColor(Color.parseColor(Theme.TEXT_DIM));
				empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
				outlineList.addView(empty);
			}
		}

		void addChunkRow(Tab parent, FileRiftLang.Chunk chunk) {
			TextView row = new TextView(activity);
			row.setText("$ " + chunk.label());
			row.setTextColor(Color.parseColor(Theme.ACCENT_BLUE));
			row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
			row.setTypeface(Typeface.MONOSPACE);
			row.setPadding(Theme.dp(activity, 8), Theme.dp(activity, 8),
					Theme.dp(activity, 8), Theme.dp(activity, 8));
			row.setBackground(Theme.rippleBackground(Theme.dp(activity, 6), Theme.BG));
			LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			p.bottomMargin = Theme.dp(activity, 2);
			row.setLayoutParams(p);
			row.setOnClickListener(v -> {
				closeTools();
				openChunkTab(parent, chunk);
			});
			outlineList.addView(row);
		}

		void addOutlineRow(FileRiftLang.Node node, int depth) {
			TextView row = new TextView(activity);
			String prefix = depth > 0 ? "  · " : "▸ ";
			row.setText(prefix + node.label());
			row.setTextColor(Color.parseColor(
					"Component".equals(node.type) ? Theme.TEXT_DIM : Theme.TEXT_MAIN));
			row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
			row.setPadding(Theme.dp(activity, 8 + depth * 10), Theme.dp(activity, 8),
					Theme.dp(activity, 8), Theme.dp(activity, 8));
			row.setBackground(Theme.rippleBackground(Theme.dp(activity, 6), Theme.BG));
			LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			p.bottomMargin = Theme.dp(activity, 2);
			row.setLayoutParams(p);
			final int jumpLine = node.idLine >= 0 ? node.idLine : node.line;
			row.setOnClickListener(v -> {
				closeTools();
				jumpToLine(jumpLine);
			});
			outlineList.addView(row);
		}

		void jumpToLine(int line0) {
			Tab tab = activeTab();
			if (tab == null || tab.editor == null) return;
			String text = tab.editor.getText().toString();
			int pos = 0;
			int line = 0;
			while (line < line0 && pos < text.length()) {
				int nl = text.indexOf('\n', pos);
				if (nl < 0) { pos = text.length(); break; }
				pos = nl + 1;
				line++;
			}
			tab.editor.requestFocus();
			int end = pos;
			while (end < text.length() && text.charAt(end) != '\n') end++;
			Selection.setSelection(tab.editor.getText(), pos, end);
			final int scrollY = pos;
			tab.editor.post(() -> {
				if (tab.editor.getLayout() != null) {
					int l = tab.editor.getLayout().getLineForOffset(scrollY);
					int y = tab.editor.getLayout().getLineTop(l);
					tab.scroll.smoothScrollTo(0, Math.max(0, y - 80));
				}
			});
		}

		void jumpObject(int dir) {
			Tab tab = activeTab();
			if (tab == null || tab.analysis == null || tab.analysis.outline.isEmpty()) return;
			List<FileRiftLang.Node> objs = tab.analysis.outline;
			int cursor = tab.editor.getSelectionStart();
			String text = tab.editor.getText().toString();
			int curLine = 0;
			for (int i = 0; i < cursor && i < text.length(); i++) {
				if (text.charAt(i) == '\n') curLine++;
			}
			int idx = -1;
			if (dir > 0) {
				for (int i = 0; i < objs.size(); i++) {
					if (objs.get(i).line > curLine) { idx = i; break; }
				}
				if (idx < 0) idx = 0;
			} else {
				for (int i = objs.size() - 1; i >= 0; i--) {
					if (objs.get(i).line < curLine) { idx = i; break; }
				}
				if (idx < 0) idx = objs.size() - 1;
			}
			FileRiftLang.Node n = objs.get(idx);
			jumpToLine(n.idLine >= 0 ? n.idLine : n.line);
		}

		void applyCollapsedChunks(Tab tab) {
			if (tab == null || tab.editor == null || tab.isChunk) return;
			String current = tab.editor.getText().toString();
			boolean hasPh = false;
			for (String line : current.split("\n")) {
				if (FileRiftLang.isChunkPlaceholder(line)) { hasPh = true; break; }
			}
			if (!hasPh) tab.fullText = current;
			if (tab.fullText == null) tab.fullText = current;
			FileRiftLang.Analysis full = FileRiftLang.analyze(tab.fullText, tab.fileType);
			if (full.chunks.isEmpty()) {
				tab.analysis = full;
				return;
			}
			String collapsed = FileRiftLang.collapseChunks(tab.fullText, full);
			// Keep FULL chunk list (bodies + keys). Map each to its placeholder line
			// in the collapsed buffer — analyzing collapsed text finds zero $ chunks.
			String[] clines = collapsed.split("\n", -1);
			for (FileRiftLang.Chunk c : full.chunks) {
				c.openLine = -1;
				c.endLine = -1;
			}
			for (int i = 0; i < clines.length; i++) {
				if (!FileRiftLang.isChunkPlaceholder(clines[i])) continue;
				String pk = FileRiftLang.placeholderKey(clines[i]);
				if (pk == null) continue;
				for (FileRiftLang.Chunk c : full.chunks) {
					if (pk.equals(c.key) && c.openLine < 0) {
						c.openLine = i;
						c.endLine = i;
						break;
					}
				}
			}
			tab.highlighting = true;
			tab.editor.setText(collapsed);
			tab.highlighting = false;
			tab.collapsedView = true;
			tab.analysis = full;
			scheduleHighlight(tab);
			if (toolsOpen) rebuildOutline();
		}

		void openChunkUnderCursor() {
			Tab tab = activeTab();
			if (tab == null || tab.isChunk) return;

			// Always resolve against full analysis with real bodies
			String src = tab.fullText != null ? tab.fullText : tab.editor.getText().toString();
			FileRiftLang.Analysis full = FileRiftLang.analyze(src, tab.fileType);
			if (tab.analysis == null || tab.analysis.chunks.isEmpty()) {
				tab.analysis = full;
			} else {
				// Refresh bodies from full text if missing
				for (FileRiftLang.Chunk c : tab.analysis.chunks) {
					if (c.body == null || c.body.isEmpty()) {
						for (FileRiftLang.Chunk f : full.chunks) {
							if (c.key.equals(f.key)) { c.body = f.body; break; }
						}
					}
				}
			}

			int cursor = Math.max(0, tab.editor.getSelectionStart());
			String text = tab.editor.getText().toString();
			int curLine = 0;
			for (int i = 0; i < cursor && i < text.length(); i++) {
				if (text.charAt(i) == '\n') curLine++;
			}
			String[] lines = text.split("\n", -1);
			String line = curLine < lines.length ? lines[curLine] : "";
			FileRiftLang.Chunk hit = null;

			if (FileRiftLang.isChunkPlaceholder(line)) {
				String pk = FileRiftLang.placeholderKey(line);
				if (pk != null) {
					for (FileRiftLang.Chunk c : tab.analysis.chunks) {
						if (pk.equals(c.key)) { hit = c; break; }
					}
					if (hit == null) {
						for (FileRiftLang.Chunk c : full.chunks) {
							if (pk.equals(c.key)) { hit = c; break; }
						}
					}
				}
			}
			if (hit == null) {
				for (FileRiftLang.Chunk c : tab.analysis.chunks) {
					if (c.openLine >= 0 && curLine >= c.openLine && curLine <= c.endLine) {
						hit = c;
						break;
					}
				}
			}
			if (hit == null) {
				// Last resort: any chunk whose placeholder key appears on this line
				for (FileRiftLang.Chunk c : full.chunks) {
					if (!c.key.isEmpty() && line.contains(c.key) && line.contains("{")) {
						hit = c;
						break;
					}
				}
			}
			if (hit == null) {
				Toast.makeText(activity, "Tap a  Name { … }  block to open the chunk", Toast.LENGTH_SHORT).show();
				return;
			}
			// Ensure body is present
			if (hit.body == null || hit.body.isEmpty()) {
				for (FileRiftLang.Chunk f : full.chunks) {
					if (hit.key.equals(f.key)) { hit.body = f.body; break; }
				}
			}
			openChunkTab(tab, hit);
		}

		void goToTemplateUnderCursor() {
			Tab tab = activeTab();
			if (tab == null || tab.analysis == null || tab.editor == null) {
				Toast.makeText(activity, "No template data", Toast.LENGTH_SHORT).show();
				return;
			}
			int cursor = tab.editor.getSelectionStart();
			String text = tab.editor.getText().toString();
			int lineStart = cursor;
			while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
			int lineEnd = cursor;
			while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') lineEnd++;
			String line = text.substring(lineStart, lineEnd);
			String name = FileRiftLang.templateRefAtLine(line);
			if (name == null || name.isEmpty()) {
				Toast.makeText(activity, "No template reference on this line", Toast.LENGTH_SHORT).show();
				return;
			}
			int dest = FileRiftLang.findTemplateLine(tab.analysis, name);
			if (dest < 0) {
				Toast.makeText(activity, "Template \"" + name + "\" not in this file", Toast.LENGTH_SHORT).show();
				return;
			}
			jumpToLine(dest);
			statusBar.setText("→ " + name);
		}


		void openChunkTab(Tab parent, FileRiftLang.Chunk chunk) {
			if (parent == null || chunk == null) return;
			for (int i = 0; i < tabs.size(); i++) {
				Tab existing = tabs.get(i);
				if (existing.isChunk && existing.parentTab == parent
						&& existing.chunkOpenLine == chunk.openLine) {
					switchTo(i);
					return;
				}
			}
			Tab tab = new Tab();
			tab.isChunk = true;
			tab.parentTab = parent;
			tab.chunkOpenLine = chunk.openLine;
			tab.chunkKey = chunk.key != null ? chunk.key : "";
			tab.chunkIndex = chunk.index;
			tab.file = parent.file;
			tab.isFileRift = false;
			tab.fileType = "lua";
			tab.editor = makeEditor(tab);
			tab.scroll = new ScrollView(activity);
			tab.scroll.setFillViewport(true);
			tab.scroll.addView(tab.editor);
			tab.history = new EditHistory();
			tab.loaded = true;
			tab.highlighting = true;
			tab.editor.setText(chunk.body != null ? chunk.body : "");
			tab.highlighting = false;
			tab.history.attach(tab.editor);
			wireEditor(tab);
			tabs.add(tab);
			rebuildTabs();
			switchTo(tabs.size() - 1);
			statusBar.setText(chunk.tabTitle() + " · virtual chunk · save merges into parent");
		}

		void openTab(File file, boolean isFileRift) {
			for (int i = 0; i < tabs.size(); i++) {
				if (tabs.get(i).file.getAbsolutePath().equals(file.getAbsolutePath())) {
					switchTo(i);
					return;
				}
			}
			Tab tab = new Tab();
			tab.file = file;
			tab.isFileRift = isFileRift;
			if (isFileRift) {
				tab.fileType = FileRiftLang.rootExtFromFilename(file.getName());
			}
			tab.editor = makeEditor(tab);
			tab.scroll = new ScrollView(activity);
			tab.scroll.setFillViewport(true);
			tab.scroll.addView(tab.editor);
			tab.history = new EditHistory();
			tabs.add(tab);
			rebuildTabs();
			switchTo(tabs.size() - 1);
			loadTab(tab);
		}

		EditText makeEditor(Tab tab) {
			EditText editor = new EditText(activity);
			editor.setLayoutParams(new FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			editor.setGravity(Gravity.TOP | Gravity.START);
			editor.setBackgroundColor(Color.TRANSPARENT);
			editor.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
			editor.setHintTextColor(Color.parseColor(Theme.TEXT_DIM));
			editor.setTypeface(Typeface.MONOSPACE);
			editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
			editor.setPadding(Theme.dp(activity, 14), Theme.dp(activity, 12),
					Theme.dp(activity, 14), Theme.dp(activity, 24));
			editor.setLineSpacing(Theme.dp(activity, 2), 1.15f);
			editor.setInputType(InputType.TYPE_CLASS_TEXT
					| InputType.TYPE_TEXT_FLAG_MULTI_LINE
					| InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
			editor.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
			editor.setHorizontallyScrolling(false);
			editor.setTextIsSelectable(true);
			editor.setLongClickable(true);
			editor.setSelectAllOnFocus(false);
			return editor;
		}

		void loadTab(Tab tab) {
			statusBar.setText("Loading " + tab.file.getName() + "…");
			ProgressBar pb = new ProgressBar(activity);
			FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
					Theme.dp(activity, 40), Theme.dp(activity, 40));
			lp.gravity = Gravity.CENTER;
			editorHost.addView(pb, lp);

			executor.submit(() -> {
				String result;
				try {
					byte[] bytes = new byte[(int) tab.file.length()];
					try (FileInputStream fis = new FileInputStream(tab.file)) {
						fis.read(bytes);
					}
					if (cancelled.get()) return;
					if (tab.isFileRift) {
						String decoded = net.kiwi.lawncher.filerift.Filerift.decode(bytes, tab.fileType);
						result = decoded != null ? decoded : "// Error: Native decode returned null";
					} else {
						result = new String(bytes);
					}
				} catch (Exception e) {
					result = "// Error reading file: " + e.getMessage();
				}
				final String text = result;
				activity.runOnUiThread(() -> {
					if (cancelled.get()) return;
					editorHost.removeView(pb);
					// Plain text first so the UI is interactive; color/outline run async.
					tab.highlighting = true;
					tab.editor.setText(text);
					tab.highlighting = false;
					tab.loaded = true;
					tab.history.attach(tab.editor);
					wireEditor(tab);
					if (tabs.indexOf(tab) == active) {
						editorHost.removeAllViews();
						editorHost.addView(tab.scroll);
						statusBar.setText(tab.file.getName()
								+ (tab.isFileRift ? " · FileRift" : ""));
						tab.editor.post(() -> {
							scheduleAnalyze(tab);
							scheduleHighlight(tab);
						});
					}
				});
			});
		}

		void wireEditor(Tab tab) {
			tab.editor.addTextChangedListener(new TextWatcher() {
				@Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
				@Override public void onTextChanged(CharSequence s, int a, int b, int c) {
					if (tab.highlighting) return;
					tab.dirty = true;
					rebuildTabs();
					scheduleHighlight(tab);
					if (!tab.isChunk) {
						scheduleAnalyze(tab);
						if (tab.isFileRift) scheduleCompletion(tab);
					}
				}
				@Override public void afterTextChanged(Editable s) {}
			});
			// Rehighlight when scrolling settles (cheap debounce via scheduleHighlight)
			tab.scroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
				if (pendingHighlight != null) uiHandler.removeCallbacks(pendingHighlight);
				final int gen = ++tab.highlightGen;
				pendingHighlight = () -> highlightAsync(tab, gen);
				uiHandler.postDelayed(pendingHighlight, 500);
			});
			// Enter auto-indent for FileRift-ish structure
			tab.editor.setOnKeyListener((v, keyCode, event) -> {
				if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
					return handleEnterIndent(tab);
				}
				return false;
			});
			final long[] downAt = {0};
			final float[] downXY = {0, 0};
			tab.editor.setOnTouchListener((v, event) -> {
				if (tab.isChunk || !tab.isFileRift) return false;
				if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
					downAt[0] = System.currentTimeMillis();
					downXY[0] = event.getX();
					downXY[1] = event.getY();
					return false; // never steal — selection must work
				}
				if (event.getAction() != android.view.MotionEvent.ACTION_UP) return false;
				// Ignore if finger moved (selection drag) or held long without release path
				if (Math.abs(event.getX() - downXY[0]) > Theme.dp(activity, 12)
						|| Math.abs(event.getY() - downXY[1]) > Theme.dp(activity, 12))
					return false;
				try {
					int off = tab.editor.getOffsetForPosition(event.getX(), event.getY());
					if (off < 0) return false;
					String text = tab.editor.getText().toString();
					int line = 0;
					for (int i = 0; i < off && i < text.length(); i++) {
						if (text.charAt(i) == '\n') line++;
					}
					String[] lines = text.split("\n", -1);
					String ln = line < lines.length ? lines[line] : "";
					if (!FileRiftLang.isChunkPlaceholder(ln)) return false;
					long held = System.currentTimeMillis() - downAt[0];
					if (held >= 400) {
						// Long-press: expand placeholder to Key: $ … $end in place
						expandChunkPlaceholderAtLine(tab, line);
					} else {
						openChunkUnderCursor();
					}
					return true;
				} catch (Exception ignored) {}
				return false;
			});
			// After any text change refresh undo chrome
			tab.editor.addTextChangedListener(new TextWatcher() {
				@Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
				@Override public void onTextChanged(CharSequence s, int a, int b, int c) {
					if (!tab.highlighting) refreshUndoRedo();
				}
				@Override public void afterTextChanged(Editable s) {}
			});
		}

		boolean handleEnterIndent(Tab tab) {
			Editable ed = tab.editor.getText();
			int cursor = tab.editor.getSelectionStart();
			if (cursor < 0) return false;
			String text = ed.toString();
			int lineStart = cursor;
			while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
			String line = text.substring(lineStart, cursor);
			StringBuilder indent = new StringBuilder();
			for (int i = 0; i < line.length(); i++) {
				char c = line.charAt(i);
				if (c == ' ' || c == '\t') indent.append(c);
				else break;
			}
			String trimmed = line.trim();
			if (trimmed.endsWith("{")) indent.append('\t');
			String insert = "\n" + indent;
			ed.replace(cursor, Math.max(cursor, tab.editor.getSelectionEnd()), insert);
			Selection.setSelection(ed, cursor + insert.length());
			return true;
		}

		void scheduleHighlight(Tab tab) {
			if (pendingHighlight != null) uiHandler.removeCallbacks(pendingHighlight);
			final int gen = ++tab.highlightGen;
			pendingHighlight = () -> highlightAsync(tab, gen);
			uiHandler.postDelayed(pendingHighlight, 400);
		}

		void scheduleAnalyze(Tab tab) {
			if (!tab.isFileRift) return;
			if (pendingAnalyze != null) uiHandler.removeCallbacks(pendingAnalyze);
			final int gen = ++tab.analyzeGen;
			pendingAnalyze = () -> analyzeAsync(tab, gen);
			uiHandler.postDelayed(pendingAnalyze, 300);
		}

		void scheduleCompletion(Tab tab) {
			if (completionRunnable != null) uiHandler.removeCallbacks(completionRunnable);
			completionRunnable = () -> showCompletions(tab);
			uiHandler.postDelayed(completionRunnable, 200);
		}

		/** Regex/span work off the UI thread; only span application touches the Editable. */
		void highlightAsync(Tab tab, int gen) {
			if (tab.editor == null || !tab.loaded) return;
			final String snapshot = tab.editor.getText().toString();
			final int scrollY = tab.scroll != null ? tab.scroll.getScrollY() : 0;
			final int viewH = tab.scroll != null ? tab.scroll.getHeight() : 0;
			final int lineH;
			if (tab.editor.getLayout() != null && tab.editor.getLineCount() > 0) {
				lineH = Math.max(1, tab.editor.getLayout().getLineBottom(0) - tab.editor.getLayout().getLineTop(0));
			} else {
				lineH = Theme.dp(activity, 18);
			}
			int first = Math.max(0, (scrollY / lineH) - 20);
			int last = first + Math.max(50, (viewH / lineH) + 40);
			final int fromLine = first;
			final int toLine = last;
			final FileRiftLang.Analysis analysis = tab.analysis;
			executor.execute(() -> {
				if (gen != tab.highlightGen || cancelled.get()) return;
				List<int[]> spans = computeSpans(snapshot, fromLine, toLine);
				if (gen != tab.highlightGen || cancelled.get()) return;
				activity.runOnUiThread(() -> {
					if (gen != tab.highlightGen || cancelled.get() || tab.editor == null) return;
					applySpanList(tab, spans, snapshot.length());
				});
			});
		}

		void analyzeAsync(Tab tab, int gen) {
			if (!tab.isFileRift || tab.editor == null) return;
			final String snapshot = tab.editor.getText().toString();
			final String type = tab.fileType;
			executor.execute(() -> {
				if (gen != tab.analyzeGen || cancelled.get()) return;
				FileRiftLang.Analysis a = FileRiftLang.analyze(snapshot, type);
				if (gen != tab.analyzeGen || cancelled.get()) return;
				activity.runOnUiThread(() -> {
					if (gen != tab.analyzeGen || cancelled.get()) return;
					tab.analysis = a;
					statusBar.setText((tab.file != null ? tab.file.getName() : "")
							+ " · " + a.outline.size() + " objects · " + a.templates.size()
							+ " templates · " + a.chunks.size() + " chunks"
							+ (tab.dirty ? " •" : ""));
					if (toolsOpen) rebuildOutline();
					if (tab.isFileRift && !tab.isChunk && !a.chunks.isEmpty() && !tab.collapsedView) {
						applyCollapsedChunks(tab);
					}
});
			});
		}

		List<int[]> computeSpans(String text, int fromLine, int toLine) {
			List<int[]> out = new ArrayList<>();
			String[] lines = text.split("\n", -1);
			int offset = 0;
			boolean inChunk = false;
			// Track chunk state from start of file so mid-file views stay correct
			for (int li = 0; li < lines.length; li++) {
				String line = lines[li];
				int lineStart = offset;
				int lineEnd = offset + line.length();
				String trimmed = line.trim();
				boolean visible = li >= fromLine && li <= toLine;

				if (inChunk) {
					if (visible) out.add(new int[]{lineStart, lineEnd, C_CHUNK, 0});
					if (trimmed.equals("$end")) inChunk = false;
					offset = lineEnd + 1;
					continue;
				}
				if (trimmed.matches("^\\w+\\s*:?\\s*\\$\\s*$")) {
					if (visible) out.add(new int[]{lineStart, lineEnd, C_CHUNK, 0});
					inChunk = true;
					offset = lineEnd + 1;
					continue;
				}

				if (visible) {
					if (FileRiftLang.isChunkPlaceholder(line)) {
						out.add(new int[]{lineStart, lineEnd, C_CHUNK_CHIP, 2});
						offset = lineEnd + 1;
						continue;
					}
					// Lua-ish lines inside $ chunks get keyword coloring
					if (inChunk || trimmed.startsWith("--")) {
						Matcher lc = RE_LUA_COMMENT.matcher(line);
						if (lc.find())
							out.add(new int[]{lineStart + lc.start(), lineStart + lc.end(), C_COMMENT, 0});
						Matcher kw = RE_LUA_KEYWORD.matcher(line);
						while (kw.find())
							out.add(new int[]{lineStart + kw.start(), lineStart + kw.end(), C_LUA_KW, 0});
					}
					Matcher cm = RE_COMMENT.matcher(line);
					if (cm.find())
						out.add(new int[]{lineStart + cm.start(), lineStart + cm.end(), C_COMMENT, 0});

					Matcher tag = RE_TAG.matcher(line);
					if (tag.find()) {
						out.add(new int[]{lineStart + tag.start(2), lineStart + tag.end(2), C_TAG, 0});
						out.add(new int[]{lineStart + tag.start(3), lineStart + tag.end(3), C_BRACE, 0});
					} else {
						Matcher key = RE_KEY_COLON.matcher(line);
						if (key.find())
							out.add(new int[]{lineStart + key.start(2), lineStart + key.end(2), C_KEY, 0});
					}

					Matcher str = RE_STRING.matcher(line);
					while (str.find())
						out.add(new int[]{lineStart + str.start(), lineStart + str.end(), C_STRING, 0});


					Matcher mac = RE_MACRO.matcher(line);
					while (mac.find())
						out.add(new int[]{lineStart + mac.start(), lineStart + mac.end(), C_MACRO, 0});

					if (trimmed.equals("}"))
						out.add(new int[]{lineStart, lineEnd, C_BRACE, 0});
				}
				offset = lineEnd + 1;
			}
			return out;
		}

		void applySpanList(Tab tab, List<int[]> spans, int expectedLen) {
			Editable ed = tab.editor.getText();
			if (ed == null || ed.length() != expectedLen) return;
			int selStart = tab.editor.getSelectionStart();
			int selEnd = tab.editor.getSelectionEnd();
			tab.highlighting = true;
			ForegroundColorSpan[] old = ed.getSpans(0, ed.length(), ForegroundColorSpan.class);
			for (ForegroundColorSpan span : old) ed.removeSpan(span);
			BackgroundColorSpan[] oldBg = ed.getSpans(0, ed.length(), BackgroundColorSpan.class);
			for (BackgroundColorSpan span : oldBg) ed.removeSpan(span);
			int len = ed.length();
			for (int[] s : spans) {
				int a = s[0], b = s[1];
				if (a < 0 || b > len || a >= b) continue;
				ed.setSpan(new ForegroundColorSpan(s[2]), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				if (s.length > 3 && s[3] == 2) {
					ed.setSpan(new BackgroundColorSpan(C_CHUNK_CHIP_BG), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
			}
			tab.highlighting = false;
			if (selStart >= 0 && selEnd >= 0 && selStart <= len && selEnd <= len) {
				try { Selection.setSelection(ed, selStart, selEnd); } catch (Exception ignored) {}
			}
		}

		void showCompletions(Tab tab) {
			if (!tab.isFileRift || tab.analysis == null) return;
			String text = tab.editor.getText().toString();
			int cursor = tab.editor.getSelectionStart();
			List<String> items = FileRiftLang.completions(tab.analysis, text, cursor);
			if (items.isEmpty()) {
				if (completionPopup != null && completionPopup.isShowing()) completionPopup.dismiss();
				return;
			}
			completionList.removeAllViews();
			int max = Math.min(items.size(), 8);
			for (int i = 0; i < max; i++) {
				final String item = items.get(i);
				TextView row = new TextView(activity);
				row.setText(item);
				row.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
				row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
				row.setTypeface(Typeface.MONOSPACE);
				row.setPadding(Theme.dp(activity, 12), Theme.dp(activity, 10),
						Theme.dp(activity, 12), Theme.dp(activity, 10));
				row.setBackground(Theme.rippleBackground(0, Theme.CARD));
				row.setOnClickListener(v -> {
					insertCompletion(tab, item);
					if (completionPopup != null) completionPopup.dismiss();
				});
				completionList.addView(row);
			}
			if (completionPopup == null) {
				completionPopup = new PopupWindow(completionList,
						Theme.dp(activity, 200), ViewGroup.LayoutParams.WRAP_CONTENT, false);
				completionPopup.setBackgroundDrawable(new GradientDrawable());
				completionPopup.setOutsideTouchable(true);
			} else {
				completionPopup.setContentView(completionList);
			}
			try {
				if (!completionPopup.isShowing()) {
					completionPopup.showAsDropDown(bottomBar, Theme.dp(activity, 12), -Theme.dp(activity, 8));
				}
			} catch (Exception ignored) {}
		}

		void insertCompletion(Tab tab, String item) {
			Editable ed = tab.editor.getText();
			int cursor = tab.editor.getSelectionStart();
			String text = ed.toString();
			int lineStart = cursor;
			while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
			String before = text.substring(lineStart, cursor);
			// replace trailing word
			int wordStart = cursor;
			while (wordStart > lineStart) {
				char c = text.charAt(wordStart - 1);
				if (Character.isLetterOrDigit(c) || c == '_') wordStart--;
				else break;
			}
			// if inserting a structural tag, add " {\n\t\n}"
			if (item.equals("Object") || item.equals("Component") || item.equals("Template")
					|| item.equals("ObjectLibrary") || item.equals("Bounds")) {
				ed.replace(wordStart, cursor, item + " {\n\t\n}");
				Selection.setSelection(ed, wordStart + item.length() + 4);
			} else if (item.startsWith("'")) {
				ed.replace(wordStart, cursor, item);
				Selection.setSelection(ed, wordStart + item.length());
			} else {
				ed.replace(wordStart, cursor, item + ": ");
				Selection.setSelection(ed, wordStart + item.length() + 2);
			}
		}

		void insertText(String s) {
			Tab tab = activeTab();
			if (tab == null || tab.editor == null) return;
			int start = Math.max(0, tab.editor.getSelectionStart());
			int end = Math.max(start, tab.editor.getSelectionEnd());
			tab.editor.getText().replace(start, end, s);
			Selection.setSelection(tab.editor.getText(), start + s.length());
		}

		void insertBraces() {
			Tab tab = activeTab();
			if (tab == null || tab.editor == null) return;
			int start = Math.max(0, tab.editor.getSelectionStart());
			int end = Math.max(start, tab.editor.getSelectionEnd());
			Editable ed = tab.editor.getText();
			if (start != end) {
				ed.replace(start, end, "{" + ed.subSequence(start, end) + "}");
				Selection.setSelection(ed, end + 2);
			} else {
				ed.replace(start, end, "{\n\t\n}");
				Selection.setSelection(ed, start + 3);
			}
		}

		void duplicateLineOrSelection() {
			Tab tab = activeTab();
			if (tab == null || tab.editor == null) return;
			Editable ed = tab.editor.getText();
			int start = tab.editor.getSelectionStart();
			int end = tab.editor.getSelectionEnd();
			if (start < 0) return;
			if (start != end) {
				CharSequence sel = ed.subSequence(start, end);
				ed.insert(end, sel);
				Selection.setSelection(ed, end, end + sel.length());
			} else {
				String text = ed.toString();
				int lineStart = start;
				while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
				int lineEnd = start;
				while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') lineEnd++;
				String line = text.substring(lineStart, lineEnd);
				String insert = (lineEnd < text.length() ? "\n" : "\n") + line;
				ed.insert(lineEnd, insert);
				Selection.setSelection(ed, lineEnd + insert.length());
			}
		}

		void switchTo(int index) {
			if (index < 0 || index >= tabs.size()) return;
			active = index;
			Tab tab = tabs.get(index);
			refreshUndoRedo();
			editorHost.removeAllViews();
			if (tab.loaded) {
				editorHost.addView(tab.scroll);
				if (tab.isChunk) {
					statusBar.setText("$" + tab.chunkKey + " · virtual Lua chunk"
							+ (tab.dirty ? " •" : ""));
				} else {
					statusBar.setText((tab.file != null ? tab.file.getName() : "")
							+ (tab.dirty ? " •" : "") + (tab.isFileRift ? " · FileRift" : ""));
					scheduleAnalyze(tab);
				}
			} else {
				statusBar.setText("Loading…");
			}
			rebuildTabs();
		}

		Tab activeTab() {
			return (active >= 0 && active < tabs.size()) ? tabs.get(active) : null;
		}

		void closeActiveTab() {
			closeTab(active);
		}

		void rebuildTabs() {
			tabStrip.removeAllViews();
			for (int i = 0; i < tabs.size(); i++) {
				final int idx = i;
				Tab tab = tabs.get(i);
				LinearLayout chip = new LinearLayout(activity);
				chip.setOrientation(LinearLayout.HORIZONTAL);
				chip.setGravity(Gravity.CENTER_VERTICAL);
				boolean on = i == active;
				chip.setBackground(Theme.rippleBackground(Theme.dp(activity, 8),
						on ? Theme.ACCENT_BLUE : Theme.BG));
				chip.setPadding(Theme.dp(activity, 8), Theme.dp(activity, 4),
						Theme.dp(activity, 4), Theme.dp(activity, 4));

				String name;
				if (tab.isChunk) {
					name = "$" + (tab.chunkKey.isEmpty() ? "chunk" : tab.chunkKey);
				} else {
					name = tab.file != null ? tab.file.getName() : "untitled";
				}
				if (name.length() > 16) name = name.substring(0, 14) + "…";

				TextView label = new TextView(activity);
				label.setText(name + (tab.dirty ? " •" : ""));
				label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
				label.setTextColor(Color.parseColor(on ? Theme.ACCENT_DARK_TEXT : Theme.TEXT_MAIN));
				label.setPadding(Theme.dp(activity, 4), Theme.dp(activity, 2),
						Theme.dp(activity, 4), Theme.dp(activity, 2));
				label.setOnClickListener(v -> switchTo(idx));
				chip.addView(label);

				TextView close = new TextView(activity);
				close.setText("×");
				close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
				close.setTextColor(Color.parseColor(on ? Theme.ACCENT_DARK_TEXT : Theme.TEXT_DIM));
				close.setPadding(Theme.dp(activity, 6), Theme.dp(activity, 2),
						Theme.dp(activity, 6), Theme.dp(activity, 2));
				close.setOnClickListener(v -> closeTab(idx));
				chip.addView(close);

				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				lp.rightMargin = Theme.dp(activity, 4);
				chip.setLayoutParams(lp);
				tabStrip.addView(chip);
			}
			TextView add = new TextView(activity);
			add.setText("+");
			add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
			add.setTypeface(null, Typeface.BOLD);
			add.setTextColor(Color.parseColor(Theme.ACCENT_GREEN));
			add.setPadding(Theme.dp(activity, 12), Theme.dp(activity, 6),
					Theme.dp(activity, 12), Theme.dp(activity, 6));
			add.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.BG));
			add.setOnClickListener(v -> pickFileToOpen());
			tabStrip.addView(add);
		}

		void closeTab(int idx) {
			if (idx < 0 || idx >= tabs.size()) return;
			tabs.remove(idx);
			if (tabs.isEmpty()) {
				dialog.dismiss();
				return;
			}
			if (active > idx) active--;
			if (active >= tabs.size()) active = tabs.size() - 1;
			if (active < 0) active = 0;
			rebuildTabs();
			switchTo(active);
		}

		void pickFileToOpen() {
			FilesScreen.pickFile(activity, file -> {
				if (file == null || !file.isFile()) return;
				String lower = file.getName().toLowerCase();
				boolean rift = lower.endsWith(".scene") || lower.endsWith(".scl")
						|| lower.endsWith(".gopt") || lower.endsWith(".gplayer")
						|| lower.endsWith(".gdata") || lower.endsWith(".gstate");
				openTab(file, rift);
			});
		}

		void saveActive() {
			Tab tab = activeTab();
			if (tab == null || !tab.loaded) {
				Toast.makeText(activity, "Nothing to save", Toast.LENGTH_SHORT).show();
				return;
			}
			if (tab.isChunk) {
				saveChunkBack(tab);
				return;
			}
			try {
				FileOutputStream fos = new FileOutputStream(tab.file);
				if (tab.isFileRift) {
					String toWrite = tab.editor.getText().toString();
					if (tab.collapsedView && tab.analysis != null) {
						toWrite = FileRiftLang.expandChunks(toWrite, tab.analysis);
					} else if (tab.fullText != null && tab.collapsedView) {
						toWrite = tab.fullText;
					}
					byte[] recoded = net.kiwi.lawncher.filerift.Filerift.recode(toWrite, tab.fileType);
					if (recoded == null) {
						Toast.makeText(activity, "Invalid markup, recode failed", Toast.LENGTH_LONG).show();
						fos.close();
						return;
					}
					fos.write(recoded);
					Toast.makeText(activity, "Binary saved", Toast.LENGTH_SHORT).show();
				} else {
					fos.write(tab.editor.getText().toString().getBytes());
					Toast.makeText(activity, "Saved", Toast.LENGTH_SHORT).show();
				}
				fos.close();
				tab.dirty = false;
				rebuildTabs();
				statusBar.setText(tab.file.getName() + " · saved");
			} catch (Exception e) {
				Toast.makeText(activity, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
			}
		}

		/** Merge virtual chunk buffer into parent FileRift text (does not write disk). */
		void saveChunkBack(Tab chunkTab) {
			Tab parent = chunkTab.parentTab;
			if (parent == null || parent.editor == null) {
				Toast.makeText(activity, "Parent tab is gone", Toast.LENGTH_SHORT).show();
				return;
			}
			String newBody = chunkTab.editor.getText().toString()
					.replace("\r\n", "\n").replace("\r", "\n");
			if (newBody.endsWith("\n")) newBody = newBody.substring(0, newBody.length() - 1);

			String base = parent.fullText != null ? parent.fullText
					: parent.editor.getText().toString();
			FileRiftLang.Analysis full = FileRiftLang.analyze(base, parent.fileType);
			FileRiftLang.Chunk target = null;
			for (FileRiftLang.Chunk c : full.chunks) {
				if (c.key.equals(chunkTab.chunkKey) && c.index == chunkTab.chunkIndex) {
					target = c;
					break;
				}
			}
			if (target == null) {
				for (FileRiftLang.Chunk c : full.chunks) {
					if (c.key.equals(chunkTab.chunkKey)) { target = c; break; }
				}
			}
			if (target == null) {
				Toast.makeText(activity, "Could not find chunk in parent", Toast.LENGTH_LONG).show();
				return;
			}
			target.body = newBody;
			String merged = FileRiftLang.spliceChunk(base, target, newBody);
			parent.fullText = merged;
			parent.dirty = true;
			parent.collapsedView = false;
			parent.analysis = full;
			// Keep bodies updated
			for (FileRiftLang.Chunk c : full.chunks) {
				if (c.key.equals(chunkTab.chunkKey) && c.index == chunkTab.chunkIndex)
					c.body = newBody;
			}
			applyCollapsedChunks(parent);
			for (FileRiftLang.Chunk c : parent.analysis.chunks) {
				if (c.key.equals(chunkTab.chunkKey)) {
					chunkTab.chunkOpenLine = c.openLine;
					chunkTab.chunkIndex = c.index;
					break;
				}
			}
			chunkTab.dirty = false;
			rebuildTabs();
			statusBar.setText("$" + chunkTab.chunkKey + " · merged into parent");
			Toast.makeText(activity, "Chunk merged — save parent to write file", Toast.LENGTH_SHORT).show();
		}


		void expandChunkPlaceholderAtLine(Tab tab, int lineIdx) {
			if (tab == null || tab.editor == null || tab.analysis == null) return;
			String text = tab.editor.getText().toString();
			String[] lines = text.split("\n", -1);
			if (lineIdx < 0 || lineIdx >= lines.length) return;
			if (!FileRiftLang.isChunkPlaceholder(lines[lineIdx])) return;
			String key = FileRiftLang.placeholderKey(lines[lineIdx]);
			FileRiftLang.Chunk c = null;
			if (key != null) {
				for (FileRiftLang.Chunk cand : tab.analysis.chunks) {
					if (key.equals(cand.key)) { c = cand; break; }
				}
			}
			String indent = "";
			String ln = lines[lineIdx];
			for (int j = 0; j < ln.length(); j++) {
				char ch = ln.charAt(j);
				if (ch == ' ' || ch == '\t') indent += ch;
				else break;
			}
			String k = (c != null && c.key != null && !c.key.isEmpty()) ? c.key
					: (key != null ? key : "chunk");
			StringBuilder block = new StringBuilder();
			block.append(indent).append(k).append(": $\n");
			if (c != null && c.body != null && !c.body.isEmpty()) {
				block.append(c.body);
				if (!c.body.endsWith("\n")) block.append('\n');
			}
			block.append(indent).append("$end");
			// replace single placeholder line
			int pos = 0;
			for (int i = 0; i < lineIdx; i++) pos += lines[i].length() + 1;
			int end = pos + lines[lineIdx].length();
			tab.editor.getText().replace(pos, end, block.toString());
			tab.collapsedView = false;
			Toast.makeText(activity, "Expanded $" + k, Toast.LENGTH_SHORT).show();
		}

		void deleteSelectedChunks() {
			Tab tab = activeTab();
			if (tab == null || tab.editor == null || !tab.isFileRift) {
				Toast.makeText(activity, "Select a chunk placeholder first", Toast.LENGTH_SHORT).show();
				return;
			}
			int a = tab.editor.getSelectionStart();
			int b = tab.editor.getSelectionEnd();
			if (a < 0) return;
			if (a > b) { int t = a; a = b; b = t; }
			String text = tab.editor.getText().toString();
			// If selection empty, try current line
			if (a == b) {
				int ls = a;
				while (ls > 0 && text.charAt(ls - 1) != '\n') ls--;
				int le = a;
				while (le < text.length() && text.charAt(le) != '\n') le++;
				a = ls; b = le;
			}
			String sel = text.substring(a, Math.min(b, text.length()));
			String[] selLines = sel.split("\n", -1);
			boolean any = false;
			for (String ln : selLines) {
				if (FileRiftLang.isChunkPlaceholder(ln)) { any = true; break; }
			}
			if (!any) {
				Toast.makeText(activity, "No chunk placeholders in selection", Toast.LENGTH_SHORT).show();
				return;
			}
			StringBuilder kept = new StringBuilder();
			boolean first = true;
			for (String ln : selLines) {
				if (FileRiftLang.isChunkPlaceholder(ln)) continue;
				if (!first) kept.append('\n');
				kept.append(ln);
				first = false;
			}
			tab.editor.getText().replace(a, Math.min(b, text.length()), kept.toString());
			Toast.makeText(activity, "Removed chunk placeholder(s)", Toast.LENGTH_SHORT).show();
		}

		void showCommandPalette() {
			Dialog d = new Dialog(activity);
			d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
			if (d.getWindow() != null)
				d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

			LinearLayout card = new LinearLayout(activity);
			card.setOrientation(LinearLayout.VERTICAL);
			card.setBackground(Theme.rippleBackground(Theme.dp(activity, 14), Theme.CARD));
			card.setPadding(Theme.dp(activity, 12), Theme.dp(activity, 12),
					Theme.dp(activity, 12), Theme.dp(activity, 12));

			EditText input = new EditText(activity);
			input.setHint("Command palette…");
			input.setHintTextColor(Color.parseColor(Theme.TEXT_DIM));
			input.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
			input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
			input.setSingleLine(true);
			input.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.BG));
			input.setPadding(Theme.dp(activity, 10), Theme.dp(activity, 10),
					Theme.dp(activity, 10), Theme.dp(activity, 10));
			card.addView(input);

			ScrollView scroll = new ScrollView(activity);
			LinearLayout list = new LinearLayout(activity);
			list.setOrientation(LinearLayout.VERTICAL);
			scroll.addView(list);
			card.addView(scroll, new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(activity, 280)));

			java.util.List<String[]> actions = new java.util.ArrayList<>();
			actions.add(new String[]{"Find in file", "find"});
			actions.add(new String[]{"Open outline", "outline"});
			actions.add(new String[]{"Save", "save"});
			actions.add(new String[]{"Open file…", "open"});
			actions.add(new String[]{"Prev object", "prev"});
			actions.add(new String[]{"Next object", "next"});
			actions.add(new String[]{"Expand chunk under cursor", "expand"});
			actions.add(new String[]{"Delete selected chunks", "delchunks"});

			Tab tab = activeTab();
			if (tab != null && tab.analysis != null) {
				for (FileRiftLang.Node n : tab.analysis.outline) {
					actions.add(new String[]{"→ " + n.label(), "goto:" + (n.idLine >= 0 ? n.idLine : n.line)});
				}
				for (String name : tab.analysis.templates.keySet()) {
					Integer line = tab.analysis.templates.get(name);
					actions.add(new String[]{"Tpl " + name, "goto:" + (line != null ? line : 0)});
				}
				for (FileRiftLang.Chunk c : tab.analysis.chunks) {
					actions.add(new String[]{"$ " + c.label(), "chunk:" + c.index});
				}
			}

			Runnable[] render = new Runnable[1];
			render[0] = () -> {
				list.removeAllViews();
				String q = input.getText().toString().trim().toLowerCase();
				int shown = 0;
				for (String[] a : actions) {
					if (!q.isEmpty() && !a[0].toLowerCase().contains(q)) continue;
					TextView row = new TextView(activity);
					row.setText(a[0]);
					row.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
					row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
					row.setPadding(Theme.dp(activity, 10), Theme.dp(activity, 12),
							Theme.dp(activity, 10), Theme.dp(activity, 12));
					row.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.BG));
					final String cmd = a[1];
					row.setOnClickListener(v -> {
						d.dismiss();
						runPaletteCommand(cmd);
					});
					LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
							ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
					rp.bottomMargin = Theme.dp(activity, 4);
					row.setLayoutParams(rp);
					list.addView(row);
					if (++shown >= 40) break;
				}
			};
			input.addTextChangedListener(new TextWatcher() {
				@Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
				@Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
				@Override public void afterTextChanged(Editable s) { render[0].run(); }
			});
			render[0].run();

			d.setContentView(card);
			d.show();
			if (d.getWindow() != null) {
				d.getWindow().setLayout(
						(int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
						ViewGroup.LayoutParams.WRAP_CONTENT);
			}
			input.requestFocus();
		}

		void runPaletteCommand(String cmd) {
			if ("find".equals(cmd)) showFind();
			else if ("outline".equals(cmd)) openTools();
			else if ("save".equals(cmd)) saveActive();
			else if ("open".equals(cmd)) pickFileToOpen();
			else if ("prev".equals(cmd)) jumpObject(-1);
			else if ("next".equals(cmd)) jumpObject(1);
			else if ("expand".equals(cmd)) {
				Tab tab = activeTab();
				if (tab == null || tab.editor == null) return;
				int off = tab.editor.getSelectionStart();
				String text = tab.editor.getText().toString();
				int line = 0;
				for (int i = 0; i < off && i < text.length(); i++) if (text.charAt(i) == '\n') line++;
				expandChunkPlaceholderAtLine(tab, line);
			} else if ("delchunks".equals(cmd)) deleteSelectedChunks();
			else if (cmd.startsWith("goto:")) {
				try { jumpToLine(Integer.parseInt(cmd.substring(5))); } catch (Exception ignored) {}
			} else if (cmd.startsWith("chunk:")) {
				Tab tab = activeTab();
				if (tab == null || tab.analysis == null) return;
				try {
					int idx = Integer.parseInt(cmd.substring(6));
					for (FileRiftLang.Chunk c : tab.analysis.chunks) {
						if (c.index == idx) { openChunkTab(tab, c); break; }
					}
				} catch (Exception ignored) {}
			}
		}

		void findNext() {
			Tab tab = activeTab();
			if (tab == null || tab.editor == null) return;
			String query = findInput.getText().toString().toLowerCase();
			if (query.isEmpty()) return;
			String text = tab.editor.getText().toString().toLowerCase();
			int start = tab.editor.getSelectionEnd();
			int index = text.indexOf(query, start);
			if (index < 0) index = text.indexOf(query, 0);
			if (index >= 0) {
				tab.editor.requestFocus();
				animateCursor(tab.editor, tab.scroll, index, index + query.length());
			} else {
				Toast.makeText(activity, "Not found", Toast.LENGTH_SHORT).show();
			}
		}

		ImageView iconBtn(int res, String color, View.OnClickListener click) {
			ImageView iv = new ImageView(activity);
			iv.setImageResource(res);
			iv.setColorFilter(Color.parseColor(color));
			int pad = Theme.dp(activity, 8);
			iv.setPadding(pad, pad, pad, pad);
			iv.setBackground(Theme.rippleBackground(Theme.dp(activity, 18), Theme.CARD));
			iv.setOnClickListener(click);
			return iv;
		}

		Button smallBtn(String text, String bg) {
			Button b = new Button(activity);
			b.setText(text);
			b.setAllCaps(false);
			b.setTextColor(Color.parseColor(Theme.ACCENT_DARK_TEXT));
			b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
			b.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), bg));
			return b;
		}
	}

	private static void animateCursor(EditText editor, ScrollView scrollView, int targetStart, int targetEnd) {
		int currentStart = editor.getSelectionStart();
		int currentEnd = editor.getSelectionEnd();
		ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
		animator.setDuration(200);
		animator.setInterpolator(new AccelerateDecelerateInterpolator());
		animator.addUpdateListener(a -> {
			float f = a.getAnimatedFraction();
			int s = Math.round(currentStart + (targetStart - currentStart) * f);
			int e = Math.round(currentEnd + (targetEnd - currentEnd) * f);
			if (editor.getText() != null && s <= editor.getText().length() && e <= editor.getText().length()) {
				Selection.setSelection(editor.getText(), Math.max(0, s), Math.max(0, e));
			}
		});
		animator.start();
		editor.post(() -> {
			if (editor.getLayout() != null) {
				int line = editor.getLayout().getLineForOffset(targetStart);
				int y = editor.getLayout().getLineTop(line);
				scrollView.smoothScrollTo(0, Math.max(0, y - 100));
			}
		});
	}

	private static class EditHistory {
		private static final int MAX = 150;
		private final LinkedList<Item> undo = new LinkedList<>();
		private final LinkedList<Item> redo = new LinkedList<>();
		private boolean working;

		static class Item {
			final int start;
			final CharSequence before, after;
			final long ts;
			Item(int s, CharSequence b, CharSequence a) {
				start = s; before = b; after = a; ts = System.currentTimeMillis();
			}
		}

		void attach(EditText editor) {
			editor.addTextChangedListener(new TextWatcher() {
				CharSequence beforeText;
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
					if (!working) beforeText = s.subSequence(start, start + count).toString();
				}
				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
					if (!working && beforeText != null)
						add(start, beforeText, s.subSequence(start, start + count).toString());
				}
				@Override public void afterTextChanged(Editable s) {}
			});
		}

		void add(int start, CharSequence before, CharSequence after) {
			if (before.length() == 0 && after.length() == 0) return;
			if (!undo.isEmpty()) {
				Item last = undo.peek();
				if (System.currentTimeMillis() - last.ts < 800) {
					if (last.before.length() == 0 && before.length() == 0
							&& start == last.start + last.after.length()
							&& !after.toString().contains("\n")) {
						undo.pop();
						undo.push(new Item(last.start, "", last.after.toString() + after));
						redo.clear();
						return;
					}
					if (last.after.length() == 0 && after.length() == 0
							&& start + before.length() == last.start) {
						undo.pop();
						undo.push(new Item(start, before.toString() + last.before, ""));
						redo.clear();
						return;
					}
				}
			}
			undo.push(new Item(start, before, after));
			if (undo.size() > MAX) undo.removeLast();
			redo.clear();
		}

		boolean canUndo() { return !undo.isEmpty(); }
		boolean canRedo() { return !redo.isEmpty(); }

		void undo(EditText editor) {
			if (undo.isEmpty()) return;
			working = true;
			Item item = undo.pop();
			redo.push(item);
			Editable text = editor.getText();
			text.replace(item.start, item.start + item.after.length(), item.before);
			Selection.setSelection(text, item.start + item.before.length());
			working = false;
		}

		void redo(EditText editor) {
			if (redo.isEmpty()) return;
			working = true;
			Item item = redo.pop();
			undo.push(item);
			Editable text = editor.getText();
			text.replace(item.start, item.start + item.before.length(), item.after);
			Selection.setSelection(text, item.start + item.after.length());
			working = false;
		}
	}
}
