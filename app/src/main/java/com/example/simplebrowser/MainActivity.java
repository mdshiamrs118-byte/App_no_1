package com.example.simplebrowser;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView WebView;
    private android.widget.ProgressBar ProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WebView = findViewById(R.id.webView);
        ProgressBar = findViewById(R.id.progressBar);

        WebSettings webSettings = WebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        WebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                ProgressBar.setVisibility(android.view.View.GONE);
            }
        });

        WebView.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                ProgressBar.setProgress(newProgress);
            }
        });

        // Load default homepage
        WebView.loadUrl("https://www.google.com");
    }

    @Override
    public boolean onCreateMenu(Menu menu) {
        // Inflate menu with custom items if needed
        return true;
    }

    @Override
    public boolean onMenuItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                WebView.reload();
                return true;
            case R.id.action_home:
                WebView.loadUrl("https://www.google.com");
                return true;
            default:
                return super.onMenuItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        if (WebView.canGoBack()) {
            WebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}