package com.messengerapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.*;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private PermissionRequest pendingWebPermission;

    private static final int FILE_CHOOSER_REQUEST = 100;
    private static final int PERMISSION_REQUEST    = 101;

    // ── Allowed URL patterns ────────────────────────────────────────────────
    private static final String[] ALLOWED = {
        "messenger.com",
        "facebook.com/login",
        "facebook.com/checkpoint",
        "facebook.com/two_step_verification",
        "facebook.com/two-step-verification",
        "facebook.com/recover",
        "facebook.com/device-based",
        "facebook.com/login/",
        "accounts.facebook.com",
        "edge-chat.facebook.com",
        "z-m-upload.facebook.com",
        "lookaside.fbsbx.com",
        "cdn.fbsbx.com",
        "video.xx.fbcdn.net",
        "graph.facebook.com",
        // Arkose Labs / security check URLs
        "funcaptcha.com",
        "arkoselabs.com",
        "arkose",
        "matchkey",
        // Facebook auth / identity
        "facebook.com/identity",
        "facebook.com/authenticate",
        "facebook.com/ajax",
        "facebook.com/noscript"
    };

    // ── Keywords that indicate login/verification is in progress ─────────────
    private static final String[] LOGIN_KEYWORDS = {
        "login", "checkpoint", "two_step", "two-step",
        "device-based", "recover", "identity", "authenticate",
        "security", "verification", "sms", "otp", "code"
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
        progressBar.setId(View.generateViewId());
        wvParams.addRule(RelativeLayout.BELOW, progressBar.getId());
        webView.setLayoutParams(wvParams);
        root.addView(webView);

        setContentView(root);

        setupWebView();
        webView.loadUrl("https://www.messenger.com/login");
    }

    // ── WebView setup ───────────────────────────────────────────────────────
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

        // Real desktop Chrome UA
        s.setUserAgentString(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36");

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();

                // 1. Always allow URLs in the ALLOWED list
                for (String allowed : ALLOWED) {
                    if (url.contains(allowed)) return false;
                }

                // 2. Allow any facebook.com URL that looks like login/verification
                if (url.contains("facebook.com")) {
                    String urlLower = url.toLowerCase();
                    for (String kw : LOGIN_KEYWORDS) {
                        if (urlLower.contains(kw)) return false; // allow
                    }
                    // Only redirect truly non-login facebook pages
                    view.loadUrl("https://www.messenger.com/");
                    return true;
                }

                return false;
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap fav) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                progressBar.setVisibility(View.GONE);
                injectLoginFix(v, url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }

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

    // ✅ FIX: JavaScript inject – do NOT redirect during login/2FA/checkpoint
    private void injectLoginFix(WebView view, String url) {
        String js = "(function() {" +
            "var href = window.location.href;" +
            // Detect login form
            "var loginForm = document.querySelector('input[name=\"email\"]') || " +
            "               document.querySelector('input[type=\"email\"]') || " +
            "               document.querySelector('#email');" +
            // Detect chat/messages
            "var isChat = href.indexOf('/t/') > -1 || href.indexOf('/messages') > -1;" +
            // Detect that we are on facebook.com (not messenger.com)
            "var isFacebookSite = href.indexOf('facebook.com') > -1 && " +
            "                     href.indexOf('messenger.com') === -1;" +
            // ✅ KEY FIX: Detect ANY login/verification/checkpoint flow
            "var isVerifying = href.indexOf('login') > -1 ||" +
            "                  href.indexOf('checkpoint') > -1 ||" +
            "                  href.indexOf('two_step') > -1 ||" +
            "                  href.indexOf('two-step') > -1 ||" +
            "                  href.indexOf('device-based') > -1 ||" +
            "                  href.indexOf('recover') > -1 ||" +
            "                  href.indexOf('security') > -1 ||" +
            "                  href.indexOf('sms') > -1 ||" +
            "                  href.indexOf('otp') > -1 ||" +
            "                  href.indexOf('code') > -1 ||" +
            "                  href.indexOf('verify') > -1 ||" +
            "                  href.indexOf('authenticate') > -1 ||" +
            "                  href.indexOf('funcaptcha') > -1 ||" +
            "                  href.indexOf('arkose') > -1;" +
            // Only redirect if we're on facebook.com, not in any verification, no login form, not in chat
            "if (!loginForm && !isChat && isFacebookSite && !isVerifying) {" +
            "  window.location.href = 'https://www.messenger.com/';" +
            "}" +
            // Hide Facebook main site links (only when safe to do so)
            "var css = 'a[href*=\"facebook.com/home\"]{display:none!important;}' +" +
            "          'a[aria-label=\"Facebook\"]{display:none!important;}';" +
            "var style = document.getElementById('__fb_block__');" +
            "if (!style) {" +
            "  style = document.createElement('style');" +
            "  style.id = '__fb_block__';" +
            "  document.head && document.head.appendChild(style);" +
            "}" +
            "style.innerHTML = css;" +
            "})()";
        view.evaluateJavascript(js, null);
    }

    // ── File chooser result ─────────────────────────────────────────────────
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

    // ── Back button ─────────────────────────────────────────────────────────
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
