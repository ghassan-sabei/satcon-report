package com.satcon.report;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    static final String HOST = "app.satcon.local";
    static final String ORIGIN = "https://" + HOST + "/";
    static final String REMOTE = BuildConfig.REMOTE;
    static final String[] UPDATABLE = {"index.html"};
    WebView web;
    ValueCallback<Uri[]> fileCb;

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 42 && fileCb != null) {
            Uri[] r = null;
            if (res == RESULT_OK && data != null && data.getData() != null) r = new Uri[]{data.getData()};
            fileCb.onReceiveValue(r);
            fileCb = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#2b3a67"));
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try { startActivityForResult(Intent.createChooser(i, "اختار الملف"), 42); }
                catch (Exception e) { fileCb = null; return false; }
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return serve(r.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });
        if (b != null) web.restoreState(b); else web.loadUrl(ORIGIN + "index.html");
    }

    long lastCheck = 0;
    @Override protected void onResume() {
        super.onResume();
        if (System.currentTimeMillis() - lastCheck > 60000) { lastCheck = System.currentTimeMillis(); checkUpdate(); }
    }

    byte[] current(String n) {
        try {
            File f = new File(new File(getFilesDir(), "web"), n);
            InputStream in = f.isFile() ? new FileInputStream(f) : getAssets().open("web/" + n);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int k;
            while ((k = in.read(buf)) > 0) bo.write(buf, 0, k);
            in.close(); return bo.toByteArray();
        } catch (Exception e) { return new byte[0]; }
    }

    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }

    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }

    static final Map<String, String> MIME = new HashMap<>();
    static {
        MIME.put("html", "text/html"); MIME.put("js", "application/javascript");
        MIME.put("json", "application/json"); MIME.put("png", "image/png"); MIME.put("wasm", "application/wasm"); MIME.put("mjs", "application/javascript"); MIME.put("css", "text/css");
    }

    WebResourceResponse serve(Uri u) {
        try {
            String host = u.getHost(), path = u.getPath() == null ? "" : u.getPath();
            if ("cdnjs.cloudflare.com".equals(host)) {
                String name = path.substring(path.lastIndexOf('/') + 1);
                return resp("lib/" + name, null);
            }
            if (!HOST.equals(host)) return null;
            String name = path.replaceFirst("^/+", "");
            if (name.isEmpty()) name = "index.html";
            if (name.equals("sw.js")) return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<>(), null);
            File f = new File(new File(getFilesDir(), "web"), name);
            return resp("web/" + name, f.isFile() ? f : null);
        } catch (Exception e) { return null; }
    }

    WebResourceResponse resp(String asset, File override) throws Exception {
        String ext = asset.substring(asset.lastIndexOf('.') + 1);
        String mime = MIME.containsKey(ext) ? MIME.get(ext) : "application/octet-stream";
        InputStream in = override != null ? new FileInputStream(override) : getAssets().open(asset);
        Map<String, String> h = new HashMap<>();
        h.put("Access-Control-Allow-Origin", "*");
        h.put("Cache-Control", "no-store");
        return new WebResourceResponse(mime, "utf-8", 200, "OK", h, in);
    }

    /* download the latest page in the background; used from the next launch */
    void checkUpdate() {
        new Thread(() -> {
            for (String n : UPDATABLE) {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(REMOTE + n + "?t=" + System.currentTimeMillis()).openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(15000);
                    if (c.getResponseCode() != 200) continue;
                    InputStream in = c.getInputStream();
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192]; int k;
                    while ((k = in.read(buf)) > 0) bo.write(buf, 0, k);
                    in.close();
                    byte[] data = bo.toByteArray();
                    String txt = new String(data, "UTF-8");
                    if (!txt.contains(BuildConfig.MARKER) || !txt.contains("</html>")) continue;
                    if (java.util.Arrays.equals(data, current(n))) continue;
                    File dir = new File(getFilesDir(), "web"); dir.mkdirs();
                    File tmp = new File(dir, n + ".tmp");
                    FileOutputStream fo = new FileOutputStream(tmp); fo.write(data); fo.close();
                    if (tmp.renameTo(new File(dir, n)))
                        runOnUiThread(() -> web.evaluateJavascript("window.__updateReady&&window.__updateReady()", null));
                } catch (Exception ignored) {}
            }
        }).start();
    }

    class Bridge {
        @JavascriptInterface public boolean isNative() { return true; }

        @JavascriptInterface public void print(String title) {
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                PrintDocumentAdapter ad = web.createPrintDocumentAdapter(title);
                PrintAttributes attrs = new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build();
                pm.print(title, ad, attrs);
            });
        }

        @JavascriptInterface public void sharePdf(String b64, String name) {
            try {
                File dir = new File(getCacheDir(), "pdf"); dir.mkdirs();
                File f = new File(dir, name.replaceAll("[^\\w.\\-]", "_"));
                FileOutputStream fo = new FileOutputStream(f);
                fo.write(Base64.decode(b64, Base64.DEFAULT)); fo.close();
                Uri uri = FileProvider.getUriForFile(MainActivity.this, BuildConfig.APPLICATION_ID + ".files", f);
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("application/pdf");
                i.putExtra(Intent.EXTRA_STREAM, uri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(i, "إرسال التقرير")));
            } catch (Exception ignored) {}
        }
    }
}
