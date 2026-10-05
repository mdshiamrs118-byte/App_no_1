package com.openbrows.app

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView
import android.webkit.WebView

class MoreMenuPopup(
    private val context: Context,
    private val webView: WebView,
    private val tabManager: TabManager
) {

    var onSettingsClicked: (() -> Unit)? = null
    var onNewTab: (() -> Unit)? = null

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.popup_more_menu, null)
        val popup = PopupWindow(
            view,
            560,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.elevation = 16f
        popup.animationStyle = R.style.PopupAnimation

        view.findViewById<TextView>(R.id.menuNewTab).setOnClickListener {
            onNewTab?.invoke()
            popup.dismiss()
        }
        view.findViewById<TextView>(R.id.menuRefresh).setOnClickListener {
            webView.reload()
            popup.dismiss()
        }
        view.findViewById<TextView>(R.id.menuShare).setOnClickListener {
            val url = webView.url ?: return@setOnClickListener
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, url)
            }
            context.startActivity(android.content.Intent.createChooser(send, "Share link"))
            popup.dismiss()
        }
        view.findViewById<TextView>(R.id.menuSettings).setOnClickListener {
            onSettingsClicked?.invoke()
            popup.dismiss()
        }
        view.findViewById<TextView>(R.id.menuCloseTab).setOnClickListener {
            tabManager.closeTab(tabManager.currentIndex)
            popup.dismiss()
        }
        view.findViewById<TextView>(R.id.menuExit).setOnClickListener {
            (context as? android.app.Activity)?.finish()
        }

        popup.showAtLocation(webView, Gravity.BOTTOM or Gravity.END, 24, 220)
    }
}
