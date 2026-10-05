package com.openbrows.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: ConstraintLayout
    private lateinit var webView: WebView
    private lateinit var urlBar: BottomUrlBar
    private lateinit var tabStrip: RecyclerView
    private lateinit var settingsPanel: SettingsPanel
    private lateinit var moreMenu: MoreMenuPopup
    private lateinit var tabManager: TabManager
    private lateinit var settings: SettingsManager
    private lateinit var lockArrow: FloatingActionButton

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = SettingsManager(this)
        tabManager = TabManager(this)

        rootLayout = findViewById(R.id.rootLayout)
        webView = findViewById(R.id.webView)
        urlBar = findViewById(R.id.urlBar)
        tabStrip = findViewById(R.id.tabStrip)
        lockArrow = findViewById(R.id.lockArrow)

        // ---- WebView setup ----
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            defaultTextEncodingName = "utf-8"
            textZoom = settings.textSize
        }

        webView.webViewClient = BrowserWebViewClient(urlBar)
        webView.webChromeClient = BrowserWebChromeClient(urlBar)

        // ---- URL bar ----
        urlBar.onNavigate = { input ->
            val finalUrl = if (input.startsWith("http://") || input.startsWith("https://")) input
                else if (input.contains(".") && !input.contains(" ")) "https://$input"
                else "https://www.google.com/search?q=${android.net.Uri.encode(input)}"
            webView.loadUrl(finalUrl)
        }
        urlBar.onBack = { if (webView.canGoBack()) webView.goBack() }
        urlBar.onForward = { if (webView.canGoForward()) webView.goForward() }
        urlBar.onMore = { moreMenu.show() }

        // ---- Tab manager ----
        tabManager.restoreState()
        tabManager.onTabSelected = { tab ->
            webView.loadUrl(tab.url)
            urlBar.updateUrl(tab.url)
            updateTabStrip()
        }
        tabManager.onTabClosed = { updateTabStrip() }

        // ---- Popups ----
        settingsPanel = SettingsPanel(this)
        settingsPanel.onSettingsChanged = { applySettings() }

        moreMenu = MoreMenuPopup(this, webView, tabManager)
        moreMenu.onSettingsClicked = { settingsPanel.show() }
        moreMenu.onNewTab = {
            tabManager.addTab(settings.homePage)
            webView.loadUrl(settings.homePage)
            urlBar.updateUrl(settings.homePage)
            updateTabStrip()
        }

        // ---- Scroll listener (hide toolbar) ----
        setupScrollListener()

        // ---- Initial page ----
        if (tabManager.tabs.isEmpty()) {
            tabManager.addTab(settings.homePage)
        }
        val startUrl = tabManager.tabs.getOrNull(tabManager.currentIndex)?.url ?: settings.homePage
        webView.loadUrl(startUrl)
        urlBar.updateUrl(startUrl)

        updateTabStrip()
        applySettings()
    }

    private fun setupScrollListener() {
        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            when (settings.hideToolbarMode) {
                SettingsManager.HIDE_OFF -> urlBar.visible()
                SettingsManager.HIDE_ON_SCROLL -> {
                    if (scrollY > oldScrollY && scrollY > 100) urlBar.hide()
                    else if (scrollY < oldScrollY) urlBar.visible()
                }
                SettingsManager.HIDE_ON_LOCK -> {
                    if (scrollY > oldScrollY && scrollY > 100) {
                        urlBar.hide()
                        showLockArrow()
                    }
                }
            }
        }
    }

    private fun showLockArrow() {
        lockArrow.visibility = View.VISIBLE
        lockArrow.alpha = 0f
        lockArrow.animate().alpha(1f).setDuration(200).start()
        lockArrow.setOnClickListener {
            urlBar.visible()
            lockArrow.animate().alpha(0f).setDuration(200)
                .withEndAction { lockArrow.visibility = View.GONE }.start()
        }
    }

    private fun applySettings() {
        // URL bar position
        val params = urlBar.layoutParams as ConstraintLayout.LayoutParams
        if (settings.urlPosition == SettingsManager.POS_BOTTOM) {
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToTop = -1
            params.verticalBias = 1.0f
        } else {
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom = -1
            params.verticalBias = 0.0f
        }
        urlBar.layoutParams = params

        // Text zoom
        webView.settings.textZoom = settings.textSize

        // Tab strip visibility
        when (settings.tabsPosition) {
            SettingsManager.TABS_OFF -> tabStrip.visibility = View.GONE
            SettingsManager.TABS_BOTTOM -> {
                tabStrip.visibility = if (settings.showBottomTabs) View.VISIBLE else View.GONE
            }
            else -> tabStrip.visibility = View.VISIBLE
        }
        updateTabStrip()
    }

    private fun updateTabStrip() {
        val adapter = TabAdapter(tabManager.tabs, tabManager.currentIndex).apply {
            onTabClick = { idx -> tabManager.selectTab(idx) }
            onTabClose = { idx -> tabManager.closeTab(idx) }
        }
        tabStrip.layoutManager = when (settings.tabsPosition) {
            SettingsManager.TABS_LEFT, SettingsManager.TABS_RIGHT ->
                LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
            else ->
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        }
        tabStrip.adapter = adapter
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else if (tabManager.tabs.size > 1) {
            tabManager.closeTab(tabManager.currentIndex)
            updateTabStrip()
        } else super.onBackPressed()
    }
}
