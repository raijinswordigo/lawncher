package net.kiwi.lawncher.screens;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import net.kiwi.lawncher.InstanceManager;
import net.kiwi.lawncher.Launcher;
import net.kiwi.lawncher.ModManager;
import net.kiwi.lawncher.files.FileManager;
import net.kiwi.lawncher.ui.LauncherShell;
import net.kiwi.lawncher.ui.Screen;
import net.kiwi.lawncher.ui.Theme;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Installed mods: sleek searchable grid, detail overlay, install / launch / delete. */
public class ModsScreen implements Screen {

	private LinearLayout root;
	private GridLayout grid;
	private TextView countLabel;
	private EditText search;
	private FrameLayout overlay;
	private ModManager.ModInfo openMod;
	private List<ModManager.ModInfo> all = new ArrayList<>();
	private InstanceManager.InstanceInfo activeInstance;
	private LinearLayout instanceRowContent;
	private LinearLayout playCardWrap;
	private android.net.Uri pendingApkUri;

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

		section.addView(buildSearchField(), matchWrap(0, 0, 0, Theme.dp(14)));
		section.addView(buildInstanceRow(), matchWrap(0, 0, 0, Theme.dp(16)));

		playCardWrap = new LinearLayout(Theme.activity);
		playCardWrap.setOrientation(LinearLayout.VERTICAL);
		section.addView(playCardWrap, matchWrap(0, 0, 0, Theme.dp(16)));

		LinearLayout labelRow = new LinearLayout(Theme.activity);
		labelRow.setOrientation(LinearLayout.HORIZONTAL);
		labelRow.setGravity(Gravity.CENTER_VERTICAL);
		TextView label = Theme.caption(12, Theme.TEXT_DIM);
		label.setText("INSTALLED MODS");
		labelRow.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		countLabel = Theme.caption(12, Theme.TEXT_FAINT);
		labelRow.addView(countLabel);
		section.addView(labelRow, matchWrap(0, 0, 0, Theme.dp(10)));

		ScrollView scroll = new ScrollView(Theme.activity);
		scroll.setFillViewport(true);
		grid = new GridLayout(Theme.activity);
		grid.setColumnCount(2);
		scroll.addView(grid, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		section.addView(scroll, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		Button install = primaryButton("Install Mod  \u25B8", Theme.accentStart(), Theme.accentEnd());
		install.setOnClickListener(v -> openFilePicker());
		LinearLayout.LayoutParams ip = matchWrap(0, Theme.dp(12), 0, 0);
		section.addView(install, ip);

		overlay = new FrameLayout(Theme.activity);
		overlay.setVisibility(View.GONE);
		root.addView(overlay, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		refreshPlayCard();
		refreshGrid();
		return root;
	}

	@Override
	public void onShown() {
		refreshInstances();
	}

	@Override
	public void onHidden() {
	}

	@Override
	public boolean onBack() {
		if (overlay != null && overlay.getVisibility() == View.VISIBLE) {
			closeDetail();
			return true;
		}
		return false;
	}

	// ---- grid ----

	private View buildSearchField() {
		LinearLayout field = new LinearLayout(Theme.activity);
		field.setOrientation(LinearLayout.HORIZONTAL);
		field.setGravity(Gravity.CENTER_VERTICAL);
		field.setBackground(Theme.rounded(Theme.SURFACE_ALT, Theme.dp(14), Theme.BORDER, 1));
		field.setPadding(Theme.dp(14), 0, Theme.dp(8), 0);

		search = new EditText(Theme.activity);
		search.setBackground(null);
		search.setHint("Search mods\u2026");
		search.setHintTextColor(Theme.TEXT_FAINT);
		search.setTextColor(Theme.TEXT);
		search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		search.setSingleLine(true);
		search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
		search.addTextChangedListener(new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
			@Override public void onTextChanged(CharSequence s, int a, int b, int c) { refreshGrid(); }
			@Override public void afterTextChanged(Editable s) {}
		});
		field.addView(search, new LinearLayout.LayoutParams(0, Theme.dp(48), 1f));

		TextView icon = Theme.text(14, Theme.TEXT_FAINT, false);
		icon.setText("\uD83D\uDD0D");
		icon.setGravity(Gravity.CENTER);
		field.addView(icon, new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36)));
		return field;
	}

	private void refreshGrid() {
		if (grid == null) return;
		activeInstance = InstanceManager.resolveActiveInstance(Theme.activity);
		all = ModManager.listInstalledMods(Theme.activity, activeInstance);
		applyFilter();
	}

	private void applyFilter() {
		if (grid == null) return;
		String query = search != null && search.getText() != null
				? search.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
		List<ModManager.ModInfo> shown = new ArrayList<>();
		for (ModManager.ModInfo mod : all) {
			if (query.isEmpty() || (mod.name != null && mod.name.toLowerCase(Locale.ROOT).contains(query))
					|| (mod.id != null && mod.id.toLowerCase(Locale.ROOT).contains(query))) {
				shown.add(mod);
			}
		}
		grid.removeAllViews();
		if (all.isEmpty()) {
			countLabel.setText(activeInstance != null ? "in \u201C" + activeInstance.name + "\u201D" : "");
		} else {
			countLabel.setText(activeInstance != null
					? "in \u201C" + activeInstance.name + "\u201D \u00B7 " + all.size()
					: all.size() + " installed");
		}

		if (shown.isEmpty()) {
			grid.addView(buildEmptyState(all.isEmpty()));
			return;
		}
		for (ModManager.ModInfo mod : shown) {
			grid.addView(buildCell(mod));
		}
	}

	private TextView buildEmptyState(boolean nothingInstalled) {
		TextView empty = new TextView(Theme.activity);
		if (InstanceManager.listInstances(Theme.activity).isEmpty()) {
			empty.setText("No game yet.\n\nTap \u201C+ Add Instance\u201D to snapshot the installed\nSwordigo, or import any APK \u2014 vanilla, modded or custom.");
		} else {
			empty.setText(nothingInstalled
					? "No mods in this instance yet.\n\nTap \u201CPLAY\u201D above to run it plain vanilla,\nor \u201CInstall Mod\u201D to run it modded."
					: "No mods match \u201C" + (search != null ? search.getText() : "") + "\u201D.");
		}
		empty.setTextColor(Theme.TEXT_DIM);
		empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		empty.setGravity(Gravity.CENTER);
		empty.setLineSpacing(Theme.dp(4), 1f);
		empty.setPadding(0, Theme.dp(48), 0, Theme.dp(48));
		GridLayout.LayoutParams params = new GridLayout.LayoutParams();
		params.width = 0;
		params.columnSpec = GridLayout.spec(0, 2, 1f);
		empty.setLayoutParams(params);
		return empty;
	}

	private View buildCell(final ModManager.ModInfo mod) {
		LinearLayout cell = new LinearLayout(Theme.activity);
		cell.setOrientation(LinearLayout.VERTICAL);
		cell.setGravity(Gravity.CENTER_HORIZONTAL);
		cell.setPadding(Theme.dp(10), Theme.dp(14), Theme.dp(10), Theme.dp(14));
		cell.setClickable(true);
		cell.setFocusable(true);
		cell.setBackground(Theme.ripple(Theme.dp(18)));
		cell.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
		cell.setElevation(Theme.dp(1));
		cell.setClipToOutline(true);

		FrameLayout iconWrap = new FrameLayout(Theme.activity);
		iconWrap.setBackground(Theme.iconTile(mod.id == null ? mod.name.hashCode() : mod.id.hashCode(), Theme.dp(18)));
		iconWrap.addView(buildIconContent(mod, Theme.dp(26)), new FrameLayout.LayoutParams(Theme.dp(62), Theme.dp(62)));
		cell.addView(iconWrap, new LinearLayout.LayoutParams(Theme.dp(62), Theme.dp(62)));

		TextView name = Theme.text(13, Theme.TEXT, true);
		name.setText(mod.name);
		name.setMaxLines(1);
		name.setEllipsize(TextUtils.TruncateAt.END);
		LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(Theme.dp(104), ViewGroup.LayoutParams.WRAP_CONTENT);
		np.topMargin = Theme.dp(9);
		cell.addView(name, np);

		LinearLayout meta = new LinearLayout(Theme.activity);
		meta.setOrientation(LinearLayout.HORIZONTAL);
		meta.setGravity(Gravity.CENTER);
		TextView version = Theme.text(10, Theme.TEXT_DIM, false);
		version.setText("v" + mod.version);
		meta.addView(version);
		if (mod.category != null && !mod.category.isEmpty() && !"General".equals(mod.category)) {
			TextView cat = chip("  " + mod.category + "  ", Theme.SURFACE_ALT, Theme.TEXT_FAINT, 8);
			LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			cp.leftMargin = Theme.dp(6);
			meta.addView(cat, cp);
		}
		cell.addView(meta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		cell.setOnClickListener(v -> openDetail(mod));
		cell.setOnLongClickListener(v -> {
			confirmDelete(mod);
			return true;
		});

		GridLayout.LayoutParams params = new GridLayout.LayoutParams();
		params.width = 0;
		params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
		params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
		params.setMargins(Theme.dp(4), Theme.dp(4), Theme.dp(4), Theme.dp(4));
		cell.setLayoutParams(params);
		return cell;
	}

	// ---- detail overlay ----

	private void openDetail(ModManager.ModInfo mod) {
		openMod = mod;
		LauncherShell.setLaunchMod(mod.id);
		overlay.removeAllViews();
		overlay.setVisibility(View.VISIBLE);

		ScrollView scroll = new ScrollView(Theme.activity);
		LinearLayout col = new LinearLayout(Theme.activity);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(Theme.dp(20), Theme.dp(6), Theme.dp(20), Theme.dp(24));

		// Back / delete row
		LinearLayout topRow = new LinearLayout(Theme.activity);
		topRow.setOrientation(LinearLayout.HORIZONTAL);
		topRow.setGravity(Gravity.CENTER_VERTICAL);
		TextView back = circleGlyph("\u2190", Theme.SURFACE_ALT, Theme.TEXT);
		back.setContentDescription("Back to mod list");
		back.setOnClickListener(v -> closeDetail());
		topRow.addView(back, new LinearLayout.LayoutParams(Theme.dp(40), Theme.dp(40)));
		TextView title = Theme.text(17, Theme.TEXT, true);
		title.setText(mod.name);
		title.setMaxLines(1);
		title.setEllipsize(TextUtils.TruncateAt.END);
		topRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		TextView trash = circleGlyph("\uD83D\uDDD1", Theme.SURFACE_ALT, Theme.DANGER);
		trash.setContentDescription("Uninstall " + mod.name);
		trash.setOnClickListener(v -> confirmDelete(mod));
		topRow.addView(trash, new LinearLayout.LayoutParams(Theme.dp(40), Theme.dp(40)));
		col.addView(topRow, matchWrap(0, 0, 0, Theme.dp(16)));

		// Hero
		FrameLayout hero = new FrameLayout(Theme.activity);
		hero.setBackground(Theme.iconTile(mod.id == null ? mod.name.hashCode() : mod.id.hashCode(), Theme.dp(24)));
		TextView bigLetter = Theme.text(44, 0xFF0A0E1A, true);
		String nm = mod.name == null ? "" : mod.name.trim();
		bigLetter.setText(nm.isEmpty() ? "?" : nm.substring(0, 1).toUpperCase(Locale.ROOT));
		bigLetter.setGravity(Gravity.CENTER);
		hero.addView(bigLetter, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
		TextView heroName = Theme.text(20, 0xFF0A0E1A, true);
		heroName.setText(mod.name);
		heroName.setPadding(Theme.dp(16), 0, Theme.dp(16), Theme.dp(14));
		hero.addView(heroName, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));
		col.addView(hero, matchWrap(0, 0, 0, Theme.dp(16)));

		// Chips
		LinearLayout chips = new LinearLayout(Theme.activity);
		chips.setOrientation(LinearLayout.HORIZONTAL);
		chips.addView(chip("v" + mod.version, Theme.SURFACE_ALT, Theme.TEXT_DIM, 9));
		if (mod.category != null && !mod.category.isEmpty()) {
			LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			cp.leftMargin = Theme.dp(8);
			chips.addView(chip("  " + mod.category + "  ", 0x14FFFFFF, Theme.TEXT, 9), cp);
		}
		LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		col.addView(chips, catLp);

		if (mod.description != null && !mod.description.isEmpty()) {
			col.addView(sectionLabel("ABOUT"), matchWrap(0, Theme.dp(20), 0, Theme.dp(8)));
			TextView desc = Theme.text(14, Theme.TEXT, false);
			desc.setText(mod.description);
			desc.setLineSpacing(Theme.dp(4), 1f);
			col.addView(desc, matchWrap(0, 0, 0, Theme.dp(4)));
		}

		List<File> screenshots = mod.screenshotFiles();
		if (!screenshots.isEmpty()) {
			col.addView(sectionLabel("SCREENSHOTS"), matchWrap(0, Theme.dp(20), 0, Theme.dp(10)));
			col.addView(buildScreenshotGallery(screenshots), matchWrap(0, 0, 0, Theme.dp(4)));
		}

		col.addView(sectionLabel("FILES"), matchWrap(0, Theme.dp(20), 0, Theme.dp(8)));
		TextView path = Theme.text(11, Theme.TEXT_FAINT, false);
		path.setText(mod.dir == null ? "" : mod.dir.getAbsolutePath());
		path.setTypeface(android.graphics.Typeface.MONOSPACE);
		col.addView(path, matchWrap(0, 0, 0, Theme.dp(4)));
		TextView size = Theme.caption(11, Theme.TEXT_FAINT);
		size.setText(FileManager.humanSize(mod.dirSize()));
		col.addView(size, matchWrap(0, 0, 0, Theme.dp(20)));

		Button launch = primaryButton("Launch Game  \u25B8", Theme.accentStart(), Theme.accentEnd());
		launch.setOnClickListener(v -> net.kiwi.lawncher.MainActivity.launch());
		col.addView(launch, matchWrap(0, 0, 0, Theme.dp(10)));

		Button uninstall = outlineButton("Uninstall", Theme.DANGER);
		uninstall.setOnClickListener(v -> confirmDelete(mod));
		col.addView(uninstall, matchWrap(0, 0, 0, 0));

		scroll.addView(col, new ScrollView.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		overlay.addView(scroll, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
	}

	private void closeDetail() {
		overlay.setVisibility(View.GONE);
		overlay.removeAllViews();
		openMod = null;
		LauncherShell.setLaunchMod("");
	}

	private void confirmDelete(final ModManager.ModInfo mod) {
		new AlertDialog.Builder(Theme.activity)
				.setTitle("Uninstall " + mod.name + "?")
				.setMessage("This removes the mod's files from this device.")
				.setPositiveButton("Uninstall", (dialog, which) -> {
					boolean removed = ModManager.deleteMod(mod);
					InstanceManager.removeModRef(Theme.activity, activeInstance, mod.id);
					Toast.makeText(Theme.activity, removed ? "Uninstalled " + mod.name : "Couldn't remove all files",
							Toast.LENGTH_SHORT).show();
					if (openMod != null && openMod.id != null && openMod.id.equals(mod.id)) {
						closeDetail();
					}
					refreshGrid();
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	// ---- picker handoff ----

	private void openFilePicker() {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("application/zip");
		Theme.activity.startActivityForResult(intent, Launcher.FILE_PICKER_REQUEST_CODE);
	}

	/** Called by the shell with the file picked by the user. */
	public void installUri(android.content.Context context, android.net.Uri uri) {
		final InstanceManager.InstanceInfo inst = InstanceManager.resolveActiveInstance(Theme.activity);
		if (inst == null) {
			// No instance yet: auto-create a vanilla one first, then install.
			Toast.makeText(Theme.activity, "No instance yet \u2014 creating vanilla instance\u2026", Toast.LENGTH_SHORT).show();
			new Thread(() -> {
				final InstanceManager.InstanceInfo created = InstanceManager.ensureVanillaInstance(Theme.activity);
				Theme.activity.runOnUiThread(() -> {
					if (created == null) {
						Toast.makeText(Theme.activity, "No game found \u2014 add an instance first (\u201C+ Add Instance\u201D)",
								Toast.LENGTH_LONG).show();
						return;
					}									InstanceManager.setActiveInstance(Theme.activity, created.id);
									LauncherShell.setActiveInstance(created.id);
									refreshInstances();
									doInstallInto(created, uri);
				});
			}).start();
			return;
		}
		doInstallInto(inst, uri);
	}

	private void doInstallInto(InstanceManager.InstanceInfo inst, android.net.Uri uri) {
		Toast.makeText(Theme.activity, "Installing mod into \u201C" + inst.name + "\u201D\u2026", Toast.LENGTH_SHORT).show();
		ModManager.installModInto(Theme.activity, uri, inst, new ModManager.InstallCallback() {
			@Override public void onSuccess(ModManager.ModInfo mod) {
				InstanceManager.addModRef(Theme.activity, inst, mod.id);
				Theme.activity.runOnUiThread(() -> {
					Toast.makeText(Theme.activity, "Installed " + mod.name + " v" + mod.version
							+ " into \u201C" + inst.name + "\u201D", Toast.LENGTH_LONG).show();
					refreshInstances();
				});
			}

			@Override public void onFailure(String reason) {
				Theme.activity.runOnUiThread(() ->
						Toast.makeText(Theme.activity, "Failed to install: " + reason, Toast.LENGTH_LONG).show());
			}
		});
	}

	// ---- instances ----

	private View buildInstanceRow() {
		LinearLayout wrap = new LinearLayout(Theme.activity);
		wrap.setOrientation(LinearLayout.VERTICAL);
		TextView cap = Theme.caption(12, Theme.TEXT_DIM);
		cap.setText("INSTANCES");
		wrap.addView(cap, matchWrap(0, 0, 0, Theme.dp(8)));

		HorizontalScrollView scroll = new HorizontalScrollView(Theme.activity);
		scroll.setHorizontalScrollBarEnabled(false);
		instanceRowContent = new LinearLayout(Theme.activity);
		instanceRowContent.setOrientation(LinearLayout.HORIZONTAL);
		scroll.addView(instanceRowContent, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		wrap.addView(scroll, matchWrap(0, 0, 0, 0));
		refreshInstanceChips();
		return wrap;
	}

	private void refreshInstanceChips() {
		if (instanceRowContent == null) return;
		activeInstance = InstanceManager.resolveActiveInstance(Theme.activity);
		instanceRowContent.removeAllViews();
		for (InstanceManager.InstanceInfo inst : InstanceManager.listInstances(Theme.activity)) {
			instanceRowContent.addView(buildInstanceChip(inst));
		}
		instanceRowContent.addView(buildAddInstanceChip());
	}

	private void refreshInstances() {
		refreshInstanceChips();
		refreshPlayCard();
		refreshGrid();
	}

	/**
	 * The hero PLAY card: launches the active instance with no mod selected
	 * (plain vanilla). Hidden until an instance exists.
	 */
	private void refreshPlayCard() {
		if (playCardWrap == null) return;
		playCardWrap.removeAllViews();
		if (activeInstance == null) return;

		LinearLayout card = new LinearLayout(Theme.activity);
		card.setOrientation(LinearLayout.HORIZONTAL);
		card.setGravity(Gravity.CENTER_VERTICAL);
		card.setPadding(Theme.dp(16), Theme.dp(14), Theme.dp(16), Theme.dp(14));
		card.setBackground(Theme.gradient(Theme.accentStart(), Theme.accentEnd(), Theme.dp(16)));
		card.setClickable(true);
		card.setFocusable(true);
		card.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
		card.setElevation(Theme.dp(2));
		card.setClipToOutline(true);
		card.setOnClickListener(v -> {
			// Reset any mod selection: this launch is plain vanilla.
			LauncherShell.setLaunchMod("");
			net.kiwi.lawncher.MainActivity.launch();
		});

		TextView playIcon = Theme.text(20, 0xFF0A0E1A, true);
		playIcon.setText("\u25B6");
		playIcon.setGravity(Gravity.CENTER);
		playIcon.setBackground(Theme.rounded(0x2A0A0E1A, Theme.dp(22), 0, 0));
		card.addView(playIcon, new LinearLayout.LayoutParams(Theme.dp(44), Theme.dp(44)));

		LinearLayout texts = new LinearLayout(Theme.activity);
		texts.setOrientation(LinearLayout.VERTICAL);
		texts.setGravity(Gravity.CENTER_VERTICAL);
		texts.setPadding(Theme.dp(12), 0, 0, 0);
		TextView play = Theme.text(18, 0xFF0A0E1A, true);
		play.setText("PLAY");
		texts.addView(play, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		TextView sub = Theme.caption(11, 0xCC0A0E1A);
		sub.setText(activeInstance.name + " \u00B7 " + activeInstance.kindLabel()
				+ (activeInstance.versionName.isEmpty() ? "" : " \u00B7 v" + activeInstance.versionName)
				+ " \u00B7 no mod");
		texts.addView(sub, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		card.addView(texts, new LinearLayout.LayoutParams(
				0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		TextView hint = Theme.caption(10, 0xAA0A0E1A);
		hint.setText("tap to run\nplain vanilla");
		card.addView(hint, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		playCardWrap.addView(card, matchWrap(0, 0, 0, 0));
	}

	private TextView buildInstanceChip(final InstanceManager.InstanceInfo inst) {
		boolean active = activeInstance != null && inst.id.equals(activeInstance.id);
		TextView chip = Theme.text(12, active ? 0xFF0A0E1A : Theme.TEXT, true);
		chip.setText(inst.name + " \u00B7 " + inst.kindLabel());
		chip.setGravity(Gravity.CENTER);
		chip.setPadding(Theme.dp(12), Theme.dp(7), Theme.dp(12), Theme.dp(7));
		chip.setBackground(active
				? Theme.gradient(Theme.accentStart(), Theme.accentEnd(), Theme.dp(14))
				: Theme.rounded(Theme.SURFACE_ALT, Theme.dp(14), Theme.BORDER, 1));
		chip.setClickable(true);
		chip.setOnClickListener(v -> {
			InstanceManager.setActiveInstance(Theme.activity, inst.id);
			LauncherShell.setActiveInstance(inst.id);
			refreshInstances();
		});
		chip.setOnLongClickListener(v -> {
			confirmDeleteInstance(inst);
			return true;
		});
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		p.rightMargin = Theme.dp(8);
		chip.setLayoutParams(p);
		return chip;
	}

	private TextView buildAddInstanceChip() {
		TextView chip = Theme.text(12, Theme.accentEnd(), true);
		chip.setText("  + Add Instance  ");
		chip.setGravity(Gravity.CENTER);
		chip.setPadding(Theme.dp(12), Theme.dp(7), Theme.dp(12), Theme.dp(7));
		chip.setBackground(Theme.rounded(0x14FFFFFF, Theme.dp(14), Theme.BORDER, 1));
		chip.setClickable(true);
		chip.setOnClickListener(v -> promptAddInstance());
		return chip;
	}

	private void promptAddInstance() {
		final boolean hasInstalled = InstanceManager.scanInstalledGame(Theme.activity) != null;
		final List<String> options = new ArrayList<>();
		if (hasInstalled) options.add("Use installed game (" + InstanceManager.GAME_PACKAGE + ")");
		options.add("Choose APK file\u2026");
		new AlertDialog.Builder(Theme.activity)
				.setTitle("Add instance")
				.setItems(options.toArray(new String[0]), (d, which) -> {
					if (hasInstalled && which == 0) {
						promptInstanceKind(true);
					} else {
						pickInstanceApk();
					}
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void promptInstanceKind(final boolean fromInstalled) {
		final String[] kinds = {"Vanilla \u2014 clean game", "Modded \u2014 already patched build", "Custom \u2014 custom engine / build"};
		new AlertDialog.Builder(Theme.activity)
				.setTitle("What is this game?")
				.setItems(kinds, (d, which) -> {
					String kind = which == 0 ? InstanceManager.KIND_VANILLA
							: which == 1 ? InstanceManager.KIND_MODDED : InstanceManager.KIND_CUSTOM;
					promptInstanceName(fromInstalled, kind);
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void promptInstanceName(final boolean fromInstalled, final String kind) {
		final EditText input = new EditText(Theme.activity);
		input.setHint(fromInstalled ? "Vanilla" : "My Swordigo");
		input.setSingleLine(true);
		new AlertDialog.Builder(Theme.activity)
				.setTitle("Instance name")
				.setView(input)
				.setPositiveButton("Create", (d, w) -> createInstance(fromInstalled, kind, input.getText().toString().trim()))
				.setNegativeButton("Cancel", null)
				.show();
	}

	private void createInstance(final boolean fromInstalled, final String kind, final String name) {
		Toast.makeText(Theme.activity, "Creating instance\u2026", Toast.LENGTH_SHORT).show();
		new Thread(() -> {
			final InstanceManager.InstanceInfo created = fromInstalled
					? InstanceManager.createFromInstalled(Theme.activity, name, kind)
					: InstanceManager.importApk(Theme.activity, pendingApkUri, kind, name);
			Theme.activity.runOnUiThread(() -> {
				if (created == null) {
					Toast.makeText(Theme.activity, fromInstalled
								? "Game not installed \u2014 pick an APK instead"
								: "Couldn't import that APK", Toast.LENGTH_LONG).show();
					return;
				}
				InstanceManager.setActiveInstance(Theme.activity, created.id);
				LauncherShell.setActiveInstance(created.id);
				Toast.makeText(Theme.activity, "Instance \u201C" + created.name + "\u201D created", Toast.LENGTH_LONG).show();
				refreshInstances();
			});
		}).start();
	}

	private void pickInstanceApk() {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("*/*");
		intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
				"application/vnd.android.package-archive", "application/zip", "application/octet-stream"});
		Theme.activity.startActivityForResult(intent, Launcher.INSTANCE_PICKER_REQUEST_CODE);
	}

	/** Called by the shell when the user picked an APK for a new instance. */
	public void createInstanceFromApk(android.content.Context context, android.net.Uri uri) {
		pendingApkUri = uri;
		promptInstanceKind(false);
	}

	private void confirmDeleteInstance(final InstanceManager.InstanceInfo inst) {
		new AlertDialog.Builder(Theme.activity)
				.setTitle("Delete instance \u201C" + inst.name + "\u201D?")
				.setMessage("Removes this instance, its mods and its copied game files.")
				.setPositiveButton("Delete", (dialog, which) -> {
					boolean ok = InstanceManager.deleteInstance(Theme.activity, inst);
					Toast.makeText(Theme.activity, ok ? "Instance deleted" : "Couldn't remove all files",
							Toast.LENGTH_SHORT).show();
					refreshInstances();
				})
				.setNegativeButton("Cancel", null)
				.show();
	}

	// ---- small helpers ----

	private static View buildIconContent(ModManager.ModInfo mod, int letterSizePx) {
		File iconFile = mod.iconFile();
		Bitmap bitmap = iconFile != null ? BitmapFactory.decodeFile(iconFile.getAbsolutePath()) : null;
		if (bitmap != null) {
			ImageView icon = new ImageView(Theme.activity);
			icon.setImageBitmap(bitmap);
			icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
			return icon;
		}
		TextView letter = Theme.text(20, 0xFF0A0E1A, true);
		String name = mod.name != null ? mod.name.trim() : "";
		letter.setText(!name.isEmpty() ? name.substring(0, 1).toUpperCase(Locale.ROOT) : "?");
		letter.setGravity(Gravity.CENTER);
		return letter;
	}

	private static HorizontalScrollView buildScreenshotGallery(List<File> screenshots) {
		HorizontalScrollView scroll = new HorizontalScrollView(Theme.activity);
		scroll.setHorizontalScrollBarEnabled(false);
		LinearLayout row = new LinearLayout(Theme.activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		for (File file : screenshots) {
			Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
			if (bitmap == null) continue;
			ImageView shot = new ImageView(Theme.activity);
			shot.setImageBitmap(bitmap);
			shot.setScaleType(ImageView.ScaleType.CENTER_CROP);
			shot.setBackground(Theme.rounded(0x00000000, Theme.dp(12), Theme.BORDER, 1));
			shot.setClipToOutline(true);
			LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Theme.dp(200), Theme.dp(120));
			sp.rightMargin = Theme.dp(10);
			row.addView(shot, sp);
		}
		scroll.addView(row);
		return scroll;
	}

	private static TextView sectionLabel(String text) {
		TextView label = Theme.caption(11, Theme.TEXT_FAINT);
		label.setText(text);
		return label;
	}

	private static TextView chip(String text, int bg, int color, int sizeSp) {
		TextView chip = Theme.text(sizeSp, color, false);
		chip.setText(text);
		chip.setGravity(Gravity.CENTER);
		chip.setPadding(Theme.dp(10), Theme.dp(5), Theme.dp(10), Theme.dp(5));
		chip.setBackground(Theme.rounded(bg, Theme.dp(9), 0, 0));
		return chip;
	}

	private static TextView circleGlyph(String glyph, int bg, int color) {
		TextView t = Theme.text(16, color, true);
		t.setText(glyph);
		t.setGravity(Gravity.CENTER);
		t.setBackground(Theme.rounded(bg, Theme.dp(20), Theme.BORDER, 1));
		t.setClickable(true);
		return t;
	}

	private static Button primaryButton(String text, int start, int end) {
		Button btn = new Button(Theme.activity);
		btn.setText(text);
		btn.setTextColor(0xFF0A0E1A);
		btn.setAllCaps(false);
		btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		btn.setTypeface(null, android.graphics.Typeface.BOLD);
		btn.setBackground(Theme.gradient(start, end, Theme.dp(14)));
		btn.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
		btn.setElevation(Theme.dp(2));
		btn.setClipToOutline(true);
		return btn;
	}

	private static Button outlineButton(String text, int color) {
		Button btn = new Button(Theme.activity);
		btn.setText(text);
		btn.setTextColor(color);
		btn.setAllCaps(false);
		btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		btn.setTypeface(null, android.graphics.Typeface.BOLD);
		btn.setBackground(Theme.rounded(0x00000000, Theme.dp(14), color, 1));
		return btn;
	}

	private static LinearLayout.LayoutParams matchWrap(int l, int t, int r, int b) {
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		p.setMargins(l, t, r, b);
		return p;
	}
}
