package com.openbrows.app

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient

class BrowserWebViewClient(private val urlBar: BottomUrlBar) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let { urlBar.updateUrl(it) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        url?.let { urlBar.updateUrl(it) }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        url?.let {
            view?.loadUrl(it)
            return true
        }
        return false
    }
}
