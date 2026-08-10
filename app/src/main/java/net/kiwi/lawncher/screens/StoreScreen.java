package net.kiwi.lawncher.screens;

import android.app.AlertDialog;
import android.content.Intent;
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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import net.kiwi.lawncher.Launcher;
import net.kiwi.lawncher.ModManager;
import net.kiwi.lawncher.billing.BillingManager;
import net.kiwi.lawncher.store.StoreManager;
import net.kiwi.lawncher.store.StoreMod;
import net.kiwi.lawncher.ui.Screen;
import net.kiwi.lawncher.ui.Theme;
import net.kiwi.lawncher.util.RemoteImage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The Mod Store: browse, search, buy and install community mods. */
public class StoreScreen implements Screen {

	private LinearLayout root;
	private FrameLayout overlay;
	private LinearLayout chipsRow;
	private LinearLayout featuredColumn;
	private LinearLayout rowsColumn;
	private TextView countLabel;
	private EditText search;
	private ProgressBar downloadBar;
	private TextView downloadLabel;
	private String category = "All";
	private List<StoreMod> catalog = new ArrayList<>();
	private List<StoreMod> filtered = new ArrayList<>();
	private boolean loaded = false;
	private String installingId = null;
	private StoreMod openMod = null;

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

		section.addView(buildSearchField(), matchWrap(0, 0, 0, Theme.dp(12)));

		HorizontalScrollView chipsScroll = new HorizontalScrollView(Theme.activity);
		chipsScroll.setHorizontalScrollBarEnabled(false);
		chipsRow = new LinearLayout(Theme.activity);
		chipsRow.setOrientation(LinearLayout.HORIZONTAL);
		chipsScroll.addView(chipsRow, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		section.addView(chipsScroll, matchWrap(0, 0, 0, Theme.dp(12)));

		ScrollView scroll = new ScrollView(Theme.activity);
		scroll.setFillViewport(true);
		LinearLayout col = new LinearLayout(Theme.activity);
		col.setOrientation(LinearLayout.VERTICAL);

		featuredColumn = new LinearLayout(Theme.activity);
		featuredColumn.setOrientation(LinearLayout.VERTICAL);
		col.addView(featuredColumn, matchWrap(0, 0, 0, Theme.dp(4)));

		LinearLayout allRow = new LinearLayout(Theme.activity);
		allRow.setOrientation(LinearLayout.HORIZONTAL);
		allRow.setGravity(Gravity.CENTER_VERTICAL);
		TextView all = Theme.caption(12, Theme.TEXT_DIM);
		all.setText("ALL MODS");
		allRow.addView(all, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		countLabel = Theme.caption(12, Theme.TEXT_FAINT);
		allRow.addView(countLabel);
		col.addView(allRow, matchWrap(0, Theme.dp(14), 0, Theme.dp(10)));

		rowsColumn = new LinearLayout(Theme.activity);
		rowsColumn.setOrientation(LinearLayout.VERTICAL);
		col.addView(rowsColumn, matchWrap(0, 0, 0, 0));

		scroll.addView(col, new ScrollView.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		section.addView(scroll, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		overlay = new FrameLayout(Theme.activity);
		overlay.setVisibility(View.GONE);
		root.addView(overlay, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		if (!loaded) {
			loaded = true;
			StoreManager.loadCatalog(Theme.activity, (ok, mods, err) -> {
				catalog = mods == null ? new ArrayList<>() : mods;
				buildChips();
				applyFilter();
			});
		} else {
			buildChips();
			applyFilter();
		}
		return root;
	}

	@Override
	public void onShown() {
		if (loaded) applyFilter();
	}

	@Override
	public void onHidden() {
	}

	@Override
	public boolean onBack() {
		if (overlay != null && overlay.getVisibility() == View.VISIBLE) {
			overlay.setVisibility(View.GONE);
			overlay.removeAllViews();
			openMod = null;
			return true;
		}
		return false;
	}

	// ---- search & chips ----

	private View buildSearchField() {
		LinearLayout field = new LinearLayout(Theme.activity);
		field.setOrientation(LinearLayout.HORIZONTAL);
		field.setGravity(Gravity.CENTER_VERTICAL);
		field.setBackground(Theme.rounded(Theme.SURFACE_ALT, Theme.dp(14), Theme.BORDER, 1));
		field.setPadding(Theme.dp(14), 0, Theme.dp(8), 0);

		search = new EditText(Theme.activity);
		search.setBackground(null);
		search.setHint("Search the store\u2026");
		search.setHintTextColor(Theme.TEXT_FAINT);
		search.setTextColor(Theme.TEXT);
		search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		search.setSingleLine(true);
		search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
		search.addTextChangedListener(new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
			@Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyFilter(); }
			@Override public void afterTextChanged(Editable s) {}
		});
		field.addView(search, new LinearLayout.LayoutParams(0, Theme.dp(48), 1f));

		TextView icon = Theme.text(14, Theme.TEXT_FAINT, false);
		icon.setText("\uD83D\uDD0D");
		icon.setGravity(Gravity.CENTER);
		field.addView(icon, new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36)));
		return field;
	}

	private void buildChips() {
		chipsRow.removeAllViews();
		Set<String> cats = new LinkedHashSet<>();
		for (StoreMod m : catalog) cats.add(m.category);
		addChip("All", "All", 0);
		int i = 1;
		for (String c : cats) addChip(c, c, i++);
	}

	private void addChip(String label, final String value, int index) {
		TextView chip = Theme.text(12, "All".equals(value) ? 0xFF0A0E1A : Theme.TEXT_DIM, "All".equals(value));
		chip.setText(label);
		chip.setGravity(Gravity.CENTER);
		chip.setPadding(Theme.dp(14), Theme.dp(7), Theme.dp(14), Theme.dp(7));
		boolean sel = category.equals(value);
		chip.setBackground(Theme.rounded(sel ? Theme.accentStart() : Theme.SURFACE_ALT,
				Theme.dp(10), sel ? 0 : Theme.BORDER, sel ? 0 : 1));
		if (sel) chip.setTextColor(0xFF0A0E1A);
		chip.setOnClickListener(v -> {
			category = value;
			buildChips();
			applyFilter();
		});
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.rightMargin = Theme.dp(8);
		chipsRow.addView(chip, lp);
	}

	// ---- listing ----

	private void applyFilter() {
		String query = search != null && search.getText() != null
				? search.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
		filtered = new ArrayList<>();
		for (StoreMod m : catalog) {
			boolean catOk = "All".equals(category) || category.equals(m.category);
			boolean qOk = query.isEmpty() || (m.name != null && m.name.toLowerCase(Locale.ROOT).contains(query))
					|| (m.author != null && m.author.toLowerCase(Locale.ROOT).contains(query))
					|| (m.id != null && m.id.toLowerCase(Locale.ROOT).contains(query));
			if (catOk && qOk) filtered.add(m);
		}
		renderLists();
	}

	private void renderLists() {
		featuredColumn.removeAllViews();
		rowsColumn.removeAllViews();
		countLabel.setText(filtered.size() + " mods");

		List<StoreMod> featured = new ArrayList<>();
		for (StoreMod m : filtered) if (m.featured) featured.add(m);
		if (!featured.isEmpty()) {
			TextView label = Theme.caption(12, Theme.TEXT_DIM);
			label.setText("FEATURED");
			featuredColumn.addView(label, matchWrap(0, 0, 0, Theme.dp(10)));

			HorizontalScrollView h = new HorizontalScrollView(Theme.activity);
			h.setHorizontalScrollBarEnabled(false);
			LinearLayout row = new LinearLayout(Theme.activity);
			row.setOrientation(LinearLayout.HORIZONTAL);
			for (final StoreMod m : featured) {
				row.addView(buildFeaturedCard(m), new LinearLayout.LayoutParams(
						Theme.dp(220), Theme.dp(118)));
			}
			h.addView(row);
			featuredColumn.addView(h, matchWrap(0, 0, 0, Theme.dp(16)));
		}

		if (filtered.isEmpty()) {
			TextView empty = Theme.text(13, Theme.TEXT_DIM, false);
			empty.setText("Nothing in the store matches yet.\n\nThe bundled catalog is a demo \u2014 point StoreManager.CATALOG_URL\nat a hosted catalog to go live.");
			empty.setGravity(Gravity.CENTER);
			empty.setLineSpacing(Theme.dp(4), 1f);
			empty.setPadding(0, Theme.dp(40), 0, Theme.dp(40));
			rowsColumn.addView(empty, matchWrap(0, 0, 0, 0));
			return;
		}
		for (StoreMod m : filtered) {
			rowsColumn.addView(buildRow(m), matchWrap(0, 0, 0, Theme.dp(10)));
		}
	}

	private View buildFeaturedCard(final StoreMod m) {
		LinearLayout card = new LinearLayout(Theme.activity);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setGravity(Gravity.BOTTOM);
		card.setClickable(true);
		card.setFocusable(true);
		card.setBackground(Theme.iconTile(m.id.hashCode(), Theme.dp(16)));
		card.setPadding(Theme.dp(14), Theme.dp(12), Theme.dp(14), Theme.dp(12));
		card.setOnClickListener(v -> openDetail(m));

		LinearLayout.LayoutParams inner = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		TextView name = Theme.text(15, 0xFF0A0E1A, true);
		name.setText(m.name);
		name.setMaxLines(1);
		name.setEllipsize(TextUtils.TruncateAt.END);
		card.addView(name, inner);
		TextView meta = Theme.caption(10, 0x990A0E1A);
		meta.setText(m.author + "  \u00B7  " + m.installsLabel() + " installs");
		card.addView(meta, inner);
		TextView price = Theme.text(12, 0xFF0A0E1A, true);
		price.setText(m.isPaid() ? m.priceLabel() : "Free");
		price.setGravity(Gravity.END);
		card.addView(price, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return card;
	}

	private View buildRow(final StoreMod m) {
		LinearLayout row = new LinearLayout(Theme.activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setClickable(true);
		row.setFocusable(true);
		row.setPadding(Theme.dp(12), Theme.dp(12), Theme.dp(10), Theme.dp(12));
		row.setBackground(Theme.ripple(Theme.dp(18), Theme.SURFACE));
		row.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
		row.setElevation(Theme.dp(1));
		row.setClipToOutline(true);
		row.setOnClickListener(v -> openDetail(m));

		FrameLayout tile = new FrameLayout(Theme.activity);
		tile.setBackground(Theme.iconTile(m.id.hashCode(), Theme.dp(14)));
		if (m.screenshots != null && !m.screenshots.isEmpty() && !m.screenshots.get(0).isEmpty()) {
			ImageView img = new ImageView(Theme.activity);
			RemoteImage.load(Theme.activity, m.screenshots.get(0), img);
			img.setScaleType(ImageView.ScaleType.CENTER_CROP);
			tile.addView(img, new FrameLayout.LayoutParams(Theme.dp(52), Theme.dp(52)));
		} else {
			TextView letter = Theme.text(18, 0xFF0A0E1A, true);
			letter.setText(initial(m.name));
			letter.setGravity(Gravity.CENTER);
			tile.addView(letter, new FrameLayout.LayoutParams(Theme.dp(52), Theme.dp(52)));
		}
		row.addView(tile, new LinearLayout.LayoutParams(Theme.dp(52), Theme.dp(52)));

		LinearLayout text = new LinearLayout(Theme.activity);
		text.setOrientation(LinearLayout.VERTICAL);
		text.setPadding(Theme.dp(12), 0, Theme.dp(8), 0);
		TextView name = Theme.text(14, Theme.TEXT, true);
		name.setText(m.name);
		name.setMaxLines(1);
		name.setEllipsize(TextUtils.TruncateAt.END);
		text.addView(name);
		TextView meta = Theme.caption(11, Theme.TEXT_DIM);
		meta.setText(m.author + "  \u00B7  v" + m.version + "  \u00B7  " + m.installsLabel() + " installs");
		text.addView(meta);
		TextView rating = Theme.caption(10, Theme.GOLD);
		rating.setText("\u2605 " + m.ratingLabel() + "  \u00B7  " + m.category);
		text.addView(rating);
		row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		boolean installed = StoreManager.isInstalled(Theme.activity, m.id);
		TextView state = Theme.text(12, installed ? Theme.SUCCESS : Theme.TEXT, installed);
		state.setText(installed ? "Installed \u2713" : (m.isPaid() ? m.priceLabel() : "Get"));
		state.setGravity(Gravity.CENTER);
		state.setPadding(Theme.dp(12), Theme.dp(8), Theme.dp(12), Theme.dp(8));
		state.setBackground(Theme.rounded(installed ? 0x143DDC97 : (m.isPaid() ? 0x14FFD166 : 0x1F5B8CFF),
				Theme.dp(10), 0, 0));
		if (installed) state.setTextColor(Theme.SUCCESS);
		else if (m.isPaid()) state.setTextColor(Theme.GOLD);
		row.addView(state, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		return row;
	}

	// ---- detail overlay ----

	private void openDetail(final StoreMod m) {
		openMod = m;
		overlay.removeAllViews();
		overlay.setVisibility(View.VISIBLE);

		ScrollView scroll = new ScrollView(Theme.activity);
		LinearLayout col = new LinearLayout(Theme.activity);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(Theme.dp(20), Theme.dp(6), Theme.dp(20), Theme.dp(24));

		LinearLayout topRow = new LinearLayout(Theme.activity);
		topRow.setOrientation(LinearLayout.HORIZONTAL);
		topRow.setGravity(Gravity.CENTER_VERTICAL);
		TextView back = Theme.text(16, Theme.TEXT, true);
		back.setText("\u2190");
		back.setGravity(Gravity.CENTER);
		back.setBackground(Theme.rounded(Theme.SURFACE_ALT, Theme.dp(20), Theme.BORDER, 1));
		back.setClickable(true);
		back.setOnClickListener(v -> overlay.setVisibility(View.GONE));
		topRow.addView(back, new LinearLayout.LayoutParams(Theme.dp(40), Theme.dp(40)));
		TextView title = Theme.text(17, Theme.TEXT, true);
		title.setText(m.name);
		title.setMaxLines(1);
		title.setEllipsize(TextUtils.TruncateAt.END);
		topRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		col.addView(topRow, matchWrap(0, 0, 0, Theme.dp(16)));

		// Hero
		FrameLayout hero = new FrameLayout(Theme.activity);
		hero.setBackground(Theme.iconTile(m.id.hashCode(), Theme.dp(24)));
		if (m.screenshots != null && !m.screenshots.isEmpty() && !m.screenshots.get(0).isEmpty()) {
			ImageView img = new ImageView(Theme.activity);
			RemoteImage.load(Theme.activity, m.screenshots.get(0), img);
			img.setScaleType(ImageView.ScaleType.CENTER_CROP);
			hero.addView(img, new FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		} else {
			TextView big = Theme.text(46, 0xFF0A0E1A, true);
			big.setText(initial(m.name));
			big.setGravity(Gravity.CENTER);
			hero.addView(big, new FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
		}
		TextView heroName = Theme.text(20, 0xFF0A0E1A, true);
		heroName.setText(m.name);
		heroName.setPadding(Theme.dp(16), 0, Theme.dp(16), Theme.dp(14));
		hero.addView(heroName, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));
		col.addView(hero, matchWrap(0, 0, 0, Theme.dp(16)));

		LinearLayout chips = new LinearLayout(Theme.activity);
		chips.setOrientation(LinearLayout.HORIZONTAL);
		chips.addView(chip("v" + m.version, Theme.SURFACE_ALT, Theme.TEXT_DIM, 9));
		chips.addView(chip("  " + m.category + "  ", 0x14FFFFFF, Theme.TEXT, 9), chipGap());
		chips.addView(chip("\u2605 " + m.ratingLabel() + " (" + m.installsLabel() + ")", 0x14FFD166, Theme.GOLD, 9), chipGap());
		col.addView(chips, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		col.addView(sectionLabel("ABOUT"), matchWrap(0, Theme.dp(20), 0, Theme.dp(8)));
		TextView desc = Theme.text(14, Theme.TEXT, false);
		desc.setText(m.longDescription != null && !m.longDescription.isEmpty() ? m.longDescription : m.description);
		desc.setLineSpacing(Theme.dp(4), 1f);
		col.addView(desc, matchWrap(0, 0, 0, Theme.dp(4)));

		if (!m.tags.isEmpty()) {
			col.addView(sectionLabel("TAGS"), matchWrap(0, Theme.dp(18), 0, Theme.dp(8)));
			LinearLayout tagsRow = new LinearLayout(Theme.activity);
			tagsRow.setOrientation(LinearLayout.HORIZONTAL);
			for (String tag : m.tags) {
				tagsRow.addView(chip("  " + tag + "  ", Theme.SURFACE_ALT, Theme.TEXT_DIM, 9), chipGap());
			}
			col.addView(tagsRow, matchWrap(0, 0, 0, Theme.dp(4)));
		}

		col.addView(sectionLabel("BY " + m.author.toUpperCase(Locale.ROOT)), matchWrap(0, Theme.dp(18), 0, Theme.dp(6)));
		TextView meta = Theme.caption(11, Theme.TEXT_FAINT);
		meta.setText("id: " + m.id + "  \u00B7  size: "
				+ (m.sizeBytes > 0 ? net.kiwi.lawncher.files.FileManager.humanSize(m.sizeBytes) : "unknown"));
		col.addView(meta, matchWrap(0, 0, 0, Theme.dp(18)));

		// Download progress area
		downloadBar = new ProgressBar(Theme.activity, null, android.R.attr.progressBarStyleHorizontal);
		downloadBar.setMax(m.sizeBytes > 0 ? (int) m.sizeBytes : 100);
		downloadBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Theme.accentStart()));
		downloadBar.setVisibility(View.GONE);
		col.addView(downloadBar, matchWrap(0, 0, 0, Theme.dp(4)));
		downloadLabel = Theme.caption(11, Theme.TEXT_FAINT);
		downloadLabel.setVisibility(View.GONE);
		col.addView(downloadLabel, matchWrap(0, 0, 0, Theme.dp(12)));

		// CTA
		boolean installed = StoreManager.isInstalled(Theme.activity, m.id);
		Button cta = new Button(Theme.activity);
		cta.setAllCaps(false);
		cta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		cta.setTypeface(null, android.graphics.Typeface.BOLD);
		cta.setText(installed
				? "Installed \u2713"
				: (m.isPaid() ? "Buy for " + m.priceLabel() : "Install"));
		cta.setTextColor(installed ? Theme.SUCCESS : 0xFF0A0E1A);
		cta.setBackground(installed
				? Theme.rounded(0x143DDC97, Theme.dp(14), Theme.SUCCESS, 1)
				: Theme.gradient(Theme.accentStart(), Theme.accentEnd(), Theme.dp(14)));
		cta.setEnabled(!installed);
		cta.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
		cta.setElevation(Theme.dp(2));
		cta.setClipToOutline(true);
		cta.setOnClickListener(v -> startInstall(m));
		col.addView(cta, matchWrap(0, Theme.dp(8), 0, 0));

		if (m.downloadUrl == null || m.downloadUrl.isEmpty()) {
			TextView demo = Theme.caption(10, Theme.TEXT_FAINT);
			demo.setText("Demo catalog: no hosted package for this entry. You can still install a matching .zip manually.");
			demo.setGravity(Gravity.CENTER);
			LinearLayout.LayoutParams dp2 = matchWrap(0, Theme.dp(8), 0, 0);
			col.addView(demo, dp2);
		}

		scroll.addView(col, new ScrollView.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		overlay.addView(scroll, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
	}

	private void startInstall(final StoreMod m) {
		boolean installed = StoreManager.isInstalled(Theme.activity, m.id);
		if (installed || (installingId != null && installingId.equals(m.id))) return;

		if (m.isPaid() && !BillingManager.get().isOwned("store." + m.id)) {
			BillingManager.get().purchase(Theme.activity, "store." + m.id, m.name,
					m.priceCents, m.currency, false, (ok, data, err) -> {
						if (ok) {
							Toast.makeText(Theme.activity, "Purchase complete \u2014 starting install", Toast.LENGTH_SHORT).show();
							beginDownload(m);
						} else {
							Toast.makeText(Theme.activity, "Purchase failed: " + err, Toast.LENGTH_LONG).show();
						}
					});
			return;
		}
		beginDownload(m);
	}

	private void beginDownload(final StoreMod m) {
		if (m.downloadUrl == null || m.downloadUrl.isEmpty()) {
			new AlertDialog.Builder(Theme.activity)
					.setTitle("Offline catalog")
					.setMessage("This demo build has no hosted package for \u201C" + m.name
							+ "\u201D.\n\nYou can still install a matching mod .zip from your device.")
					.setPositiveButton("Pick .zip", (dialog, which) -> {
						Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
						intent.setType("application/zip");
						Theme.activity.startActivityForResult(intent, Launcher.FILE_PICKER_REQUEST_CODE);
					})
					.setNegativeButton("Cancel", null)
					.show();
			return;
		}
		installingId = m.id;
		downloadBar.setProgress(0);
		downloadBar.setVisibility(View.VISIBLE);
		downloadLabel.setVisibility(View.VISIBLE);
		downloadLabel.setText("Downloading " + m.name + "\u2026");
		StoreManager.installFromStore(Theme.activity, m, (done, total) -> {
			if (total > 0) downloadBar.setMax((int) total);
			downloadBar.setProgress((int) done);
			downloadLabel.setText("Downloading " + m.name + " \u00B7 "
					+ net.kiwi.lawncher.files.FileManager.humanSize(done)
					+ (total > 0 ? " / " + net.kiwi.lawncher.files.FileManager.humanSize(total) : ""));
		}, new ModManager.InstallCallback() {
			@Override public void onSuccess(ModManager.ModInfo info) {
				installingId = null;
				Theme.activity.runOnUiThread(() -> {
					downloadBar.setVisibility(View.GONE);
					downloadLabel.setVisibility(View.GONE);
					Toast.makeText(Theme.activity, "Installed " + m.name + " \u2014 find it in Mods", Toast.LENGTH_LONG).show();
					applyFilter();
					// Refresh the open detail so its CTA shows the installed state.
					if (openMod != null && openMod.id != null && openMod.id.equals(m.id)) {
						openDetail(openMod);
					}
				});
			}

			@Override public void onFailure(String reason) {
				installingId = null;
				Theme.activity.runOnUiThread(() -> {
					downloadBar.setVisibility(View.GONE);
					downloadLabel.setVisibility(View.GONE);
					Toast.makeText(Theme.activity, "Install failed: " + reason, Toast.LENGTH_LONG).show();
				});
			}
		});
	}

	// ---- helpers ----

	private static String initial(String name) {
		String n = name == null ? "" : name.trim();
		return n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase(Locale.ROOT);
	}

	private static TextView chip(String text, int bg, int color, int sizeSp) {
		TextView chip = Theme.text(sizeSp, color, false);
		chip.setText(text);
		chip.setGravity(Gravity.CENTER);
		chip.setPadding(Theme.dp(10), Theme.dp(5), Theme.dp(10), Theme.dp(5));
		chip.setBackground(Theme.rounded(bg, Theme.dp(9), 0, 0));
		return chip;
	}

	private static LinearLayout.LayoutParams chipGap() {
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		p.leftMargin = Theme.dp(8);
		return p;
	}

	private static TextView sectionLabel(String text) {
		TextView label = Theme.caption(11, Theme.TEXT_FAINT);
		label.setText(text);
		return label;
	}

	private static LinearLayout.LayoutParams matchWrap(int l, int t, int r, int b) {
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		p.setMargins(l, t, r, b);
		return p;
	}
}
