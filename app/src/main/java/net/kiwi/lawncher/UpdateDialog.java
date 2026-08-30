package net.kiwi.lawncher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class UpdateDialog {

    private UpdateDialog() {}

    public static final class UpdateInfo {
        public int versionCode;
        public String versionName = "";
        public int minSupportedVersionCode;
        public boolean forceUpdate;
        public String title = "Update Available";
        public final List<String> changelog = new ArrayList<>();
        public String downloadUrl = "";
        public String releaseUrl = "";
    }

    public static void show(Activity activity, UpdateInfo info) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.86f), WindowManager.LayoutParams.WRAP_CONTENT);
        }
        dialog.setCancelable(!info.forceUpdate);
        dialog.setCanceledOnTouchOutside(!info.forceUpdate);
        if (info.forceUpdate) dialog.setOnKeyListener((d, keyCode, event) -> keyCode == KeyEvent.KEYCODE_BACK);
        dialog.setContentView(buildContent(activity, dialog, info));
        dialog.show();
    }

    private static View buildContent(Activity activity, Dialog dialog, UpdateInfo info) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(Theme.dp(activity, 16));
        cardBg.setColor(Color.parseColor(Theme.CARD));
        root.setBackground(cardBg);
        root.setPadding(Theme.dp(activity, 20), Theme.dp(activity, 20), Theme.dp(activity, 20), Theme.dp(activity, 16));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(activity);
        title.setText(info.forceUpdate ? "Update Required" : info.title);
        title.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        if (!info.versionName.isEmpty()) {
            TextView chip = new TextView(activity);
            chip.setText("v" + info.versionName);
            chip.setTextColor(Color.parseColor(Theme.ACCENT_DARK_TEXT));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setBackground(Theme.circleBackground(Theme.ACCENT_BLUE));
            chip.setPadding(Theme.dp(activity, 10), Theme.dp(activity, 4), Theme.dp(activity, 10), Theme.dp(activity, 4));
            header.addView(chip);
        }
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(activity);
        subtitle.setText(info.forceUpdate ? "This version of Lawncher is no longer supported. Please update to continue." : "A new version of Lawncher is available.");
        subtitle.setTextColor(Color.parseColor(Theme.TEXT_DIM));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = Theme.dp(activity, 6);
        subtitleParams.bottomMargin = Theme.dp(activity, 14);
        root.addView(subtitle, subtitleParams);

        if (!info.changelog.isEmpty()) {
            LinearLayout notesList = new LinearLayout(activity);
            notesList.setOrientation(LinearLayout.VERTICAL);
            for (String line : info.changelog) notesList.addView(bulletRow(activity, line));

            ScrollView notesScroll = new ScrollView(activity);
            notesScroll.addView(notesList, new ScrollView.LayoutParams(-1, -2));
            GradientDrawable notesBg = new GradientDrawable();
            notesBg.setCornerRadius(Theme.dp(activity, 10));
            notesBg.setColor(Color.parseColor(Theme.BG));
            notesScroll.setBackground(notesBg);
            notesScroll.setPadding(Theme.dp(activity, 12), Theme.dp(activity, 10), Theme.dp(activity, 12), Theme.dp(activity, 10));

            LinearLayout.LayoutParams notesParams = new LinearLayout.LayoutParams(-1, Theme.dp(activity, 160));
            notesParams.bottomMargin = Theme.dp(activity, 16);
            root.addView(notesScroll, notesParams);
        }

        TextView status = new TextView(activity);
        status.setTextColor(Color.parseColor(Theme.TEXT_DIM));
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.bottomMargin = Theme.dp(activity, 6);
        root.addView(status, statusParams);

        ProgressBar pb = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setVisibility(View.GONE);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(-1, Theme.dp(activity, 6));
        pbParams.bottomMargin = Theme.dp(activity, 14);
        root.addView(pb, pbParams);

        LinearLayout buttonRow = new LinearLayout(activity);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        if (!info.forceUpdate) {
            Button later = new Button(activity);
            later.setText("Later");
            later.setAllCaps(false);
            later.setTextColor(Color.parseColor(Theme.TEXT_DIM));
            later.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.BG));
            later.setOnClickListener(v -> dialog.dismiss());
            LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(-2, -2);
            laterParams.rightMargin = Theme.dp(activity, 10);
            buttonRow.addView(later, laterParams);
        }

        Button update = new Button(activity);
        update.setText("Update Now");
        update.setAllCaps(false);
        update.setTextColor(Color.parseColor(Theme.ACCENT_DARK_TEXT));
        update.setBackground(Theme.rippleBackground(Theme.dp(activity, 8), Theme.ACCENT_BLUE));
        update.setOnClickListener(v -> beginUpdate(activity, info, update, pb, status));
        buttonRow.addView(update, new LinearLayout.LayoutParams(-2, -2));

        root.addView(buttonRow, new LinearLayout.LayoutParams(-1, -2));
        return root;
    }

    private static View bulletRow(Activity activity, String text) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Theme.dp(activity, 3), 0, Theme.dp(activity, 3));

        TextView dot = new TextView(activity);
        dot.setText("\u2022");
        dot.setTextColor(Color.parseColor(Theme.ACCENT_BLUE));
        dot.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(-2, -2);
        dotParams.rightMargin = Theme.dp(activity, 8);
        row.addView(dot, dotParams);

        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextColor(Color.parseColor(Theme.TEXT_MAIN));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private static void beginUpdate(Activity activity, UpdateInfo info, Button updateBtn, ProgressBar pb, TextView status) {
        if (info.downloadUrl.isEmpty()) {
            openReleasePage(activity, info);
            return;
        }
        updateBtn.setEnabled(false);
        updateBtn.setText("Downloading...");
        pb.setVisibility(View.VISIBLE);
        pb.setProgress(0);
        status.setVisibility(View.VISIBLE);
        status.setText("Downloading update...");

        new Thread(() -> {
            File file = new File(activity.getCacheDir(), "lawncher_update_" + info.versionCode + ".apk");
            try {
                HttpURLConnection conn = openFollowingRedirects(info.downloadUrl);
                int length = conn.getContentLength();
                try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    long total = 0;
                    while ((read = in.read(buffer)) != -1) {
                        total += read;
                        if (length > 0) {
                            int progress = (int) (total * 100 / length);
                            activity.runOnUiThread(() -> pb.setProgress(progress));
                        }
                        out.write(buffer, 0, read);
                    }
                }
                activity.runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    status.setText("Opening installer...");
                    promptInstall(activity, file, info);
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    updateBtn.setEnabled(true);
                    updateBtn.setText("Retry");
                    status.setText("Download failed! Opening release page instead.");
                    openReleasePage(activity, info);
                });
                file.delete();
            }
        }).start();
    }

    private static void promptInstall(Activity activity, File apkFile, UpdateInfo info) {
        try {
            Uri apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Couldn't launch installer opening release page!!", Toast.LENGTH_LONG).show();
            openReleasePage(activity, info);
        }
    }

    private static void openReleasePage(Activity activity, UpdateInfo info) {
        String url = !info.releaseUrl.isEmpty() ? info.releaseUrl : info.downloadUrl;
        if (url.isEmpty()) return;
        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private static HttpURLConnection openFollowingRedirects(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.connect();
        int code = conn.getResponseCode();
        if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_SEE_OTHER) {
            String location = conn.getHeaderField("Location");
            conn = (HttpURLConnection) new URL(location).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.connect();
        }
        return conn;
    }
}