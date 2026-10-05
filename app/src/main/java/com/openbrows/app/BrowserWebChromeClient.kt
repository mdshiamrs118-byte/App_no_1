package com.openbrows.app

import android.webkit.WebChromeClient
import android.webkit.WebView

class BrowserWebChromeClient(private val urlBar: BottomUrlBar) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        urlBar.setProgress(newProgress)
    }
}
