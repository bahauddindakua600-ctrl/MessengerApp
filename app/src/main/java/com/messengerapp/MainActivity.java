package com.messengerapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.*;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private PermissionRequest pendingWebPermission;

    private static final int FILE_CHOOSER_REQUEST = 100;
    private static final int PERMISSION_REQUEST    = 101;

    // ── Allowed URL patterns ──────────────────────────────────────────
    private static final String[] ALLOWED = {
        "messenger.com",
        "facebook.com/login",
        "facebook.com/checkpoint",
        "facebook.com/two_step_verification",
        "accounts.facebook.com",
        "edge-chat.facebook.com",
        "z-m-upload.facebook.com",
        "lookaside.fbsbx.com",
        "cdn.fbsbx.com",
        "video.xx.fbcdn.net",
        "graph.facebook.com"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full-screen layout
        RelativeLayout root = new RelativeLayout(this);
        root.setLayoutParams(new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(0xFF000000);

        // Progress bar at top
        progressBar = new ProgressBar(this, null,
            android.R.attr.progressBarStyleHorizontal);
        RelativeLayout.LayoutParams pbParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, 8);
        pbParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        progressBar.setLayoutParams(pbParams);
        progressBar.setMax(100);
        progressBar.setProgressTintList(
            android.content.res.ColorStateList.valueOf(0xFF0099FF));
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar);

        // WebView
        webView = new WebView(this);
        RelativeLayout.LayoutParams wvParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT);
        wvParams.addRule(RelativeLayout.BELOW, progressBar.getId());
        progressBar.setId(View.generateViewId());
        wvParams.addRule(RelativeLayout.BELOW, progressBar.getId());
        webView.setLayoutParams(wvParams);
        root.addView(webView);

        setContentView(root);

        requestAppPermissions();
        setupWebView();
        webView.loadUrl("https://www.messenger.com");
    }

    // ── Permissions ───────────────────────────────────────────────────
    private void requestAppPermissions() {
        String[] needed = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.READ_EXTERNAL_STORAGE
        };
        boolean required = false;
        for (String p : needed) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) {
                required = true;
                break;
            }
        }
        if (required) {
            ActivityCompat.requestPermissions(this, needed, PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code,
            @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        // Re-grant any pending WebRTC permission after user grants native perms
        if (pendingWebPermission != null) {
            pendingWebPermission.grant(pendingWebPermission.getResources());
            pendingWebPermission = null;
        }
    }

    // ── WebView setup ─────────────────────────────────────────────────
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // Mobile Chrome UA for best Messenger compatibility
        s.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.6099.210 Mobile Safari/537.36");

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                for (String allowed : ALLOWED) {
                    if (url.contains(allowed)) return false; // allow
                }
                // Block everything else on facebook.com (main feed etc.)
                if (url.contains("facebook.com")) return true; // block
                return false;
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap fav) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                progressBar.setVisibility(View.GONE);
                injectCSS(v);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            // ── Grant camera/mic for WebRTC calls ─────────────────────
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                boolean camGranted = ContextCompat.checkSelfPermission(
                    MainActivity.this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED;
                boolean micGranted = ContextCompat.checkSelfPermission(
                    MainActivity.this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;

                if (camGranted && micGranted) {
                    request.grant(request.getResources());
                } else {
                    pendingWebPermission = request;
                    requestAppPermissions();
                }
            }

            // ── File picker for photo/video sending ───────────────────
            @Override
            public boolean onShowFileChooser(WebView wv,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                Intent intent = params.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onProgressChanged(WebView v, int progress) {
                progressBar.setProgress(progress);
                progressBar.setVisibility(
                    progress == 100 ? View.GONE : View.VISIBLE);
            }
        });
    }

    // ── Inject CSS: hide Facebook icon & unwanted links ───────────────
    private void injectCSS(WebView view) {
        String css =
            "a[href='https://www.facebook.com']{display:none!important;}" +
            "a[href='https://facebook.com']{display:none!important;}" +
            "a[href*='facebook.com/home']{display:none!important;}" +
            "[aria-label='Facebook']{display:none!important;}" +
            // Hide the top-right Facebook icon button specifically
            "._1t2u{display:none!important;}" +
            ".__pika_content{display:none!important;}";

        String js = "(function(){" +
            "if(document.getElementById('__fb_block__'))return;" +
            "var s=document.createElement('style');" +
            "s.id='__fb_block__';" +
            "s.innerHTML='" + css + "';" +
            "document.head.appendChild(s);" +
            "})()";

        view.evaluateJavascript(js, null);
    }

    // ── File chooser result ───────────────────────────────────────────
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = null;
            if (res == Activity.RESULT_OK && data != null) {
                results = WebChromeClient.FileChooserParams
                    .parseResult(res, data);
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    // ── Back button ───────────────────────────────────────────────────
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }
}
