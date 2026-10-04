package com.geminiwrapper.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity {

    private static final String GEMINI_URL = "https://gemini.google.com/app";
    private static final String CHAT_URL = "file:///android_asset/chat.html";
    private static final int REQ_FILE = 1001;

    // Pages on these domains stay inside the app (needed for Google sign-in too).
    private static final String[] INTERNAL_DOMAINS = {
            "google.com", "gstatic.com", "googleusercontent.com", "googleapis.com"
    };

    private WebView geminiView;
    private WebView chatView;
    private View pillGemini;
    private View pillChat;
    private ImageView iconGemini;
    private ImageView iconChat;
    private ValueCallback<Uri[]> filePathCallback;
    private String relayScript = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        geminiView = findViewById(R.id.geminiView);
        chatView = findViewById(R.id.chatView);
        pillGemini = findViewById(R.id.pillGemini);
        pillChat = findViewById(R.id.pillChat);
        iconGemini = findViewById(R.id.iconGemini);
        iconChat = findViewById(R.id.iconChat);

        relayScript = readAsset("gemini_relay.js");

        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            // Lets you inspect the Gemini DOM from chrome://inspect if Google changes its markup.
            WebView.setWebContentsDebuggingEnabled(true);
        }

        setupGeminiView();
        setupChatView();

        findViewById(R.id.tabGemini).setOnClickListener(v -> {
            spin(iconGemini);
            selectTab(false);
        });
        findViewById(R.id.tabChat).setOnClickListener(v -> {
            spin(iconChat);
            selectTab(true);
        });

        selectTab(false);
        geminiView.loadUrl(GEMINI_URL);
        chatView.loadUrl(CHAT_URL);
    }

    // ---------------------------------------------------------------- tabs

    private void selectTab(boolean chat) {
        chatView.setVisibility(chat ? View.VISIBLE : View.GONE);
        pillGemini.setSelected(!chat);
        pillChat.setSelected(chat);
        iconGemini.setAlpha(chat ? 0.55f : 1f);
        iconChat.setAlpha(chat ? 1f : 0.55f);
        (chat ? chatView : geminiView).requestFocus();
    }

    private void spin(final View icon) {
        icon.animate().cancel();
        icon.setRotation(0f);
        icon.animate()
                .rotationBy(360f)
                .setDuration(600)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> icon.setRotation(0f))
                .start();
    }

    // ------------------------------------------------------ Gemini WebView

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupGeminiView() {
        WebSettings s = geminiView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportMultipleWindows(false);
        // Google refuses sign-in inside WebViews, so present as regular Chrome.
        s.setUserAgentString(s.getUserAgentString()
                .replace("; wv", "")
                .replace("Version/4.0 ", ""));

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(geminiView, true);

        geminiView.addJavascriptInterface(new GeminiBridge(), "GeminiBridge");

        geminiView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isInternal(uri)) {
                    return false;
                }
                openExternal(uri);
                return true;
            }
        });

        geminiView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });
    }

    private boolean isInternal(Uri uri) {
        String scheme = uri.getScheme();
        if ("about".equals(scheme)) {
            return true;
        }
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        for (String d : INTERNAL_DOMAINS) {
            if (host.equals(d) || host.endsWith("." + d)) {
                return true;
            }
        }
        return false;
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) {
            // nothing can open it
        }
    }

    // ---------------------------------------------------- custom chat WebView

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupChatView() {
        WebSettings s = chatView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        chatView.setBackgroundColor(0xFF131314);
        chatView.addJavascriptInterface(new ChatBridge(), "Android");
        chatView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri.toString().startsWith("file:///android_asset/")) {
                    return false;
                }
                openExternal(uri);
                return true;
            }
        });
    }

    /** Called from chat.html. */
    private class ChatBridge {
        @JavascriptInterface
        public void send(final String text) {
            runOnUiThread(() -> geminiView.evaluateJavascript(
                    relayScript + "\n;window.__geminiRelay.send(" + JSONObject.quote(text) + ");",
                    null));
        }

        @JavascriptInterface
        public void cancel() {
            runOnUiThread(() -> geminiView.evaluateJavascript(
                    "if (window.__geminiRelay) { window.__geminiRelay.cancel(); }", null));
        }

        @JavascriptInterface
        public void newChat() {
            runOnUiThread(() -> {
                geminiView.evaluateJavascript(
                        "if (window.__geminiRelay) { window.__geminiRelay.cancel(); }", null);
                geminiView.loadUrl(GEMINI_URL);
            });
        }
    }

    /** Called from the script injected into the Gemini page. */
    private class GeminiBridge {
        @JavascriptInterface
        public void onUpdate(String text, boolean done) {
            pushToChat("window.onAiReply(" + JSONObject.quote(text) + "," + done + ");");
        }

        @JavascriptInterface
        public void onError(String message) {
            pushToChat("window.onAiError(" + JSONObject.quote(message) + ");");
        }
    }

    private void pushToChat(final String js) {
        runOnUiThread(() -> chatView.evaluateJavascript(js, null));
    }

    // -------------------------------------------------------------- plumbing

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (chatView.getVisibility() == View.VISIBLE) {
            selectTab(false);
        } else if (geminiView.canGoBack()) {
            geminiView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        geminiView.destroy();
        chatView.destroy();
        super.onDestroy();
    }
}
