package com.mahmoud.rasd;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.provider.Settings;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collections;

/**
 * Rasd — the whole app (assets/www/index.html) runs inside a WebView.
 * The page is served from https://appassets.androidplatform.net so the camera works,
 * and window.RasdNative gives it: Google Drive sign-in, saving / sharing files,
 * copy, vibration. The hardware back button is passed to window.__back().
 */
public class MainActivity extends Activity {
    static final int RC_AUTH = 11, RC_FILE = 12, RC_CAM_PERM = 13, RC_NOTIF = 14;
    static final String HOST = "appassets.androidplatform.net";
    static final String SCOPE = "https://www.googleapis.com/auth/drive.appdata";
    /* in-app updates: bump versionCode in app/build.gradle + "code" in this file on GitHub */
    static final String UPD_URL = "https://mahmoudbassiuoni.github.io/rasd/version.json";
    long lastCheck = 0;
    boolean updShowing = false;
    File pendingApk;

    WebView wv;
    FrameLayout root;
    ValueCallback<Uri[]> fileCb;
    Uri camUri;
    PermissionRequest pendingPerm;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        wv = new WebView(this);
        root.addView(wv, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        setupBars();

        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setTextZoom(100);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { /* no app */ }
                return true;
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            /* no grey "play" picture before the camera starts */
            @Override
            public Bitmap getDefaultVideoPoster() {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            }

            @Override
            public void onPermissionRequest(final PermissionRequest req) {
                boolean cam = false;
                for (String r : req.getResources())
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) cam = true;
                if (!cam) { req.deny(); return; }
                if (hasCam()) {
                    req.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                } else {
                    if (pendingPerm != null) pendingPerm.deny();
                    pendingPerm = req;
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, RC_CAM_PERM);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                camUri = null;
                try {
                    Intent it;
                    if (p.isCaptureEnabled() && hasCam()) {
                        File dir = new File(getCacheDir(), "cam");
                        dir.mkdirs();
                        File f = new File(dir, "scan_" + System.currentTimeMillis() + ".jpg");
                        camUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                        it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        it.putExtra(MediaStore.EXTRA_OUTPUT, camUri);
                        it.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } else {
                        it = p.createIntent();
                    }
                    startActivityForResult(it, RC_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    camUri = null;
                    cb.onReceiveValue(null);
                }
                return true;
            }
        });

        wv.addJavascriptInterface(new Bridge(), "RasdNative");
        wv.loadUrl("https://" + HOST + "/assets/www/index.html");

        UpdJob.schedule(this);
        subscribe();
        askNotif();
        if (getIntent() != null && getIntent().getBooleanExtra("upd", false)) lastCheck = 0;

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        }
    }

    /* white bars with dark icons; on Android 11+ we draw edge-to-edge and pad ourselves */
    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        if (i != null && i.getBooleanExtra("upd", false) && !updShowing) { lastCheck = 0; checkUpdate(); }
    }

    /* instant update pushes (Firebase topic) — only when google-services.json was built in */
    static void subscribe() {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("rasd-updates");
        } catch (Throwable t) { /* Firebase not set up */ }
    }

    /* Android 13+: ask once for notifications (update alerts) */
    void askNotif() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        android.content.SharedPreferences sp = getSharedPreferences("rasd", MODE_PRIVATE);
        if (sp.getBoolean("askedNotif", false)) return;
        sp.edit().putBoolean("askedNotif", true).apply();
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, RC_NOTIF);
    }

    /* the app's data is also kept in the app's own storage, so it survives a WebView reset */
    void persist(String json) {
        try {
            File tmp = new File(getFilesDir(), "rasd-data.tmp");
            try (FileOutputStream os = new FileOutputStream(tmp)) { os.write(json.getBytes("UTF-8")); }
            File f = new File(getFilesDir(), "rasd-data.json");
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f); }
        } catch (Exception e) { /* ignore */ }
    }

    String restore() {
        try {
            File f = new File(getFilesDir(), "rasd-data.json");
            if (!f.exists()) return "";
            return readAll(new java.io.FileInputStream(f));
        } catch (Exception e) { return ""; }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingApk != null && canInstall()) { install(pendingApk); return; }
        if (!updShowing && System.currentTimeMillis() - lastCheck > 20 * 60 * 1000) checkUpdate();
    }

    /* ---------- in-app update (like Daftari) ---------- */
    boolean ar() { return "ar".equals(Locale.getDefault().getLanguage()); }

    @SuppressWarnings("deprecation")
    long myCode() {
        try {
            if (Build.VERSION.SDK_INT >= 28) return getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) { return 0; }
    }

    String myName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    static String readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    /* the new APK must really be on GitHub (same size as in version.json) before we offer it */
    static boolean apkReady(String apk, long size) {
        if (size <= 0) return true;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(apk + "?t=" + System.currentTimeMillis()).openConnection();
            c.setRequestMethod("HEAD");
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setUseCaches(false);
            return c.getResponseCode() == 200 && c.getContentLengthLong() == size;
        } catch (Exception e) { return false; }
    }

    String pendingApkUrl = "";

    void checkUpdate() {
        lastCheck = System.currentTimeMillis();
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(UPD_URL + "?t=" + System.currentTimeMillis()).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setUseCaches(false);
                if (c.getResponseCode() != 200) return;
                JSONObject j = new JSONObject(readAll(c.getInputStream()));
                final long code = j.optLong("code", 0);
                final String name = j.optString("name", "");
                final String apk = j.optString("apk", "");
                final String notes = j.optString(ar() ? "notes" : "notes_en", j.optString("notes", ""));
                if (code > myCode() && apk.startsWith("https://") && apkReady(apk, j.optLong("size", 0)))
                    runOnUiThread(() -> showUpdate(name, apk, notes));
            } catch (Exception e) { /* offline: try next time */ }
        }).start();
    }

    void showUpdate(final String name, final String apk, final String notes) {
        if (updShowing || isFinishing()) return;
        updShowing = true;
        pendingApkUrl = apk;
        try {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("cur", myName());
            o.put("notes", notes);
            wv.evaluateJavascript("(window.__upd&&window.__upd(" + o.toString() + "))?'1':'0'", v -> {
                if (!"\"1\"".equals(v)) showUpdateDialog(name, apk, notes);
            });
        } catch (Exception e) {
            showUpdateDialog(name, apk, notes);
        }
    }

    void showUpdateDialog(String name, final String apk, String notes) {
        String msg = (ar() ? "الإصدار " + name + " نزل (عندك " + myName() + ")." : "Version " + name + " is out (you have " + myName() + ").")
                + (notes.isEmpty() ? "" : "\n\n" + notes);
        new AlertDialog.Builder(this)
                .setTitle(ar() ? "تحديث جديد لرصد" : "Rasd update")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton(ar() ? "حدّث دلوقتي" : "Update now", (d, w) -> download(apk))
                .show();
    }

    /* called from the styled update sheet: progress is shown there, no native dialog */
    boolean webProgress = false;

    void download(final String apk) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final TextView tv = new TextView(this);
        tv.setText(ar() ? "بينزّل التحديث… 0%" : "Downloading… 0%");
        final ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        box.addView(tv);
        box.addView(pb);
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(ar() ? "تحديث رصد" : "Updating Rasd")
                .setView(box)
                .setCancelable(false)
                .create();
        if (!webProgress) dlg.show();
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(apk + "?t=" + System.currentTimeMillis()).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setUseCaches(false);
                if (c.getResponseCode() != 200) throw new java.io.IOException("http " + c.getResponseCode());
                final long len = c.getContentLengthLong();
                File dir = new File(getCacheDir(), "upd");
                dir.mkdirs();
                final File f = new File(dir, "rasd.apk");
                try (InputStream in = c.getInputStream(); FileOutputStream os = new FileOutputStream(f)) {
                    byte[] buf = new byte[16384];
                    long got = 0;
                    int n, last = -1;
                    while ((n = in.read(buf)) > 0) {
                        os.write(buf, 0, n);
                        got += n;
                        if (len > 0) {
                            final int pc = (int) (got * 100 / len);
                            if (pc != last) {
                                last = pc;
                                runOnUiThread(() -> { pb.setProgress(pc); tv.setText((ar() ? "بينزّل التحديث… " : "Downloading… ") + pc + "%"); });
                                js("window.__updProg&&window.__updProg(" + pc + ")");
                            }
                        }
                    }
                }
                js("window.__updProg&&window.__updProg(100)");
                runOnUiThread(() -> { if (dlg.isShowing()) dlg.dismiss(); updShowing = false; pendingApk = f; install(f); });
            } catch (Exception e) {
                js("window.__updFail&&window.__updFail()");
                runOnUiThread(() -> {
                    if (dlg.isShowing()) dlg.dismiss();
                    Toast.makeText(this, ar() ? "التحميل فشل، اتأكد من النت وجرّب تاني" : "Download failed — check the internet", Toast.LENGTH_LONG).show();
                    lastCheck = 0;
                    if (!webProgress) updShowing = false;
                });
            }
        }).start();
    }

    boolean canInstall() {
        return Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
    }

    void install(File f) {
        if (!canInstall()) {
            Toast.makeText(this, ar() ? "اسمح لرصد بتثبيت التحديثات وارجع للتطبيق" : "Allow Rasd to install updates, then come back", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) { /* old phone */ }
            return;
        }
        pendingApk = null;
        try {
            Uri u = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(u, "application/vnd.android.package-archive");
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
        } catch (Exception e) {
            Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    void setupBars() {
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                int a = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(a, a);
            }
            root.setOnApplyWindowInsetsListener((v, in) -> {
                // the keyboard does NOT resize the page: dialogs with typing sit at the top, so
                // nothing has to move (resizing on every keyboard frame caused stutter/flicker)
                android.graphics.Insets i = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return WindowInsets.CONSUMED;
            });
        } else {
            int f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(f);
        }
    }

    /* status / navigation bar colour follows the app's look (light or dark theme) */
    @SuppressWarnings("deprecation")
    void applyBars(String hex, boolean light) {
        int c;
        try { c = Color.parseColor(hex); } catch (Exception e) { return; }
        root.setBackgroundColor(c);
        getWindow().setStatusBarColor(c);
        getWindow().setNavigationBarColor(c);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController ic = getWindow().getInsetsController();
            if (ic != null) {
                int a = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                ic.setSystemBarsAppearance(light ? a : 0, a);
            }
        } else {
            int f = 0;
            if (light) {
                f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(f);
        }
    }

    boolean hasCam() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    void goBack() {
        wv.evaluateJavascript("(window.__back&&window.__back())?'1':'0'", v -> {
            if (!"\"1\"".equals(v)) moveTaskToBack(true);
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        goBack();
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] perms, int[] res) {
        super.onRequestPermissionsResult(rc, perms, res);
        if (rc == RC_CAM_PERM && pendingPerm != null) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED)
                pendingPerm.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            else
                pendingPerm.deny();
            pendingPerm = null;
        }
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        if (rc == RC_FILE) {
            Uri[] r = null;
            if (res == RESULT_OK) {
                if (camUri != null) r = new Uri[]{camUri};
                else r = WebChromeClient.FileChooserParams.parseResult(res, data);
            }
            if (fileCb != null) fileCb.onReceiveValue(r);
            fileCb = null;
            camUri = null;
        } else if (rc == RC_AUTH) {
            if (data == null || res != RESULT_OK) { sendErr("cancel"); return; }
            try {
                AuthorizationResult ar = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data);
                String at = ar.getAccessToken();
                if (at == null) sendErr("cancel"); else sendTok(at);
            } catch (ApiException e) {
                sendErr(e.getMessage());
            }
        }
    }

    /* ---------- Google Drive (drive.appdata) ---------- */
    void authorize(final boolean interactive) {
        AuthorizationRequest req = AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(SCOPE)))
                .build();
        Identity.getAuthorizationClient(this).authorize(req)
                .addOnSuccessListener(this, r -> {
                    if (r.hasResolution()) {
                        if (!interactive) { sendErr("silent"); return; }
                        PendingIntent pi = r.getPendingIntent();
                        if (pi == null) { sendErr("Google sign-in unavailable"); return; }
                        try {
                            startIntentSenderForResult(pi.getIntentSender(), RC_AUTH, null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            sendErr(e.getMessage());
                        }
                    } else {
                        String at = r.getAccessToken();
                        if (at == null) sendErr(interactive ? "Google sign-in failed" : "silent"); else sendTok(at);
                    }
                })
                .addOnFailureListener(this, e -> sendErr(interactive ? String.valueOf(e.getMessage()) : "silent"));
    }

    void js(final String code) {
        runOnUiThread(() -> wv.evaluateJavascript(code, null));
    }

    void sendTok(String at) {
        js("window.__nativeTok&&window.__nativeTok(" + JSONObject.quote(at) + ",3500)");
    }

    void sendErr(String m) {
        js("window.__nativeTokErr&&window.__nativeTokErr(" + JSONObject.quote(m == null ? "error" : m) + ")");
    }

    /* ---------- files ---------- */
    static String safe(String n) {
        String s = n == null ? "file" : n.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return s.isEmpty() ? "file" : s;
    }

    String saveFile(String name, String b64, String mime) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            name = safe(name);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Rasd");
                Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (u == null) return "";
                try (OutputStream os = getContentResolver().openOutputStream(u)) {
                    if (os == null) return "";
                    os.write(data);
                }
                return "Download/Rasd/" + name;
            } else {
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) return "";
                dir.mkdirs();
                File f = new File(dir, name);
                try (FileOutputStream os = new FileOutputStream(f)) { os.write(data); }
                return f.getAbsolutePath();
            }
        } catch (Exception e) {
            return "";
        }
    }

    /* e-mail apps only (Gmail, Outlook…) with the file attached */
    void emailFile(String name, String b64, String mime) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            File dir = new File(getCacheDir(), "share");
            dir.mkdirs();
            File f = new File(dir, safe(name));
            try (FileOutputStream os = new FileOutputStream(f)) { os.write(data); }
            Uri u = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType(mime);
            it.putExtra(Intent.EXTRA_STREAM, u);
            it.putExtra(Intent.EXTRA_SUBJECT, f.getName());
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            it.setSelector(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")));
            final Intent ch = Intent.createChooser(it, f.getName());
            ch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            runOnUiThread(() -> {
                try { startActivity(ch); } catch (Exception e) { shareFile(name, b64, mime); }
            });
        } catch (Exception e) {
            shareFile(name, b64, mime);
        }
    }

    void shareFile(String name, String b64, String mime) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            File dir = new File(getCacheDir(), "share");
            dir.mkdirs();
            File f = new File(dir, safe(name));
            try (FileOutputStream os = new FileOutputStream(f)) { os.write(data); }
            Uri u = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType(mime);
            it.putExtra(Intent.EXTRA_STREAM, u);
            it.putExtra(Intent.EXTRA_SUBJECT, f.getName());
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            final Intent ch = Intent.createChooser(it, f.getName());
            ch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            runOnUiThread(() -> startActivity(ch));
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Share failed", Toast.LENGTH_SHORT).show());
        }
    }

    class Bridge {
        @JavascriptInterface
        public void authorize(final boolean interactive) {
            runOnUiThread(() -> MainActivity.this.authorize(interactive));
        }

        @JavascriptInterface
        public String saveFile(String name, String b64, String mime) {
            return MainActivity.this.saveFile(name, b64, mime);
        }

        @JavascriptInterface
        public void shareFile(String name, String b64, String mime) {
            MainActivity.this.shareFile(name, b64, mime);
        }

        @JavascriptInterface
        public void emailFile(String name, String b64, String mime) {
            MainActivity.this.emailFile(name, b64, mime);
        }

        @JavascriptInterface
        public void setBars(final String hex, final boolean light) {
            runOnUiThread(() -> applyBars(hex, light));
        }

        @JavascriptInterface
        public void persist(String json) {
            MainActivity.this.persist(json);
        }

        @JavascriptInterface
        public String restore() {
            return MainActivity.this.restore();
        }

        @JavascriptInterface
        public void shareText(final String text) {
            runOnUiThread(() -> {
                Intent it = new Intent(Intent.ACTION_SEND);
                it.setType("text/plain");
                it.putExtra(Intent.EXTRA_TEXT, text);
                try { startActivity(Intent.createChooser(it, getString(R.string.app_name))); } catch (Exception e) { /* none */ }
            });
        }

        @JavascriptInterface
        public void startUpdate() {
            runOnUiThread(() -> {
                if (pendingApkUrl.isEmpty()) return;
                webProgress = true;
                download(pendingApkUrl);
            });
        }

        @JavascriptInterface
        public String version() {
            return myName();
        }

        @JavascriptInterface
        public void checkUpdate() {
            runOnUiThread(() -> { lastCheck = 0; MainActivity.this.checkUpdate(); });
        }

        @JavascriptInterface
        public void copy(final String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("codes", text));
            });
        }

        @JavascriptInterface
        @SuppressWarnings("deprecation")
        public void vibrate(String pattern) {
            try {
                String[] a = pattern.split(",");
                long[] t = new long[a.length * 2];
                for (int i = 0; i < a.length; i++) {
                    t[i * 2] = i == 0 ? 0 : 90;
                    t[i * 2 + 1] = Long.parseLong(a[i].trim());
                }
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v == null) return;
                if (Build.VERSION.SDK_INT >= 26) {
                    int[] amp = new int[t.length];
                    for (int i = 0; i < amp.length; i++) amp[i] = (i % 2 == 1) ? 255 : 0;
                    if (v.hasAmplitudeControl()) v.vibrate(VibrationEffect.createWaveform(t, amp, -1));
                    else v.vibrate(VibrationEffect.createWaveform(t, -1));
                }
                else v.vibrate(t, -1);
            } catch (Exception e) { /* ignore */ }
        }
    }
}
