package com.openbrows.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Tab(val id: Int, var title: String, var url: String)

class TabManager(private val context: Context) {

    val tabs = mutableListOf<Tab>()
    var currentIndex = 0
    private var nextId = 0

    var onTabSelected: ((Tab) -> Unit)? = null
    var onTabClosed: (() -> Unit)? = null

    fun addTab(url: String) {
        val tab = Tab(nextId++, "New Tab", url)
        tabs.add(tab)
        currentIndex = tabs.size - 1
        saveState()
    }

    fun closeTab(index: Int) {
        if (index !in tabs.indices || tabs.size <= 1) return
        tabs.removeAt(index)
        if (currentIndex >= tabs.size) currentIndex = tabs.size - 1
        saveState()
        onTabClosed?.invoke()
    }

    fun selectTab(index: Int) {
        if (index !in tabs.indices) return
        currentIndex = index
        saveState()
        onTabSelected?.invoke(tabs[index])
    }

    fun updateCurrentTitle(title: String) {
        tabs.getOrNull(currentIndex)?.title = title
        saveState()
    }

    fun updateCurrentUrl(url: String) {
        tabs.getOrNull(currentIndex)?.url = url
        saveState()
    }

    private fun saveState() {
        val json = JSONArray()
        tabs.forEach { tab ->
            json.put(JSONObject().apply {
                put("id", tab.id)
                put("title", tab.title)
                put("url", tab.url)
            })
        }
        context.getSharedPreferences("tabs", Context.MODE_PRIVATE)
            .edit()
            .putString("tabs", json.toString())
            .putInt("current", currentIndex)
            .putInt("nextId", nextId)
            .apply()
    }

    fun restoreState() {
        val prefs = context.getSharedPreferences("tabs", Context.MODE_PRIVATE)
        val saved = prefs.getString("tabs", null) ?: return
        try {
            val json = JSONArray(saved)
            tabs.clear()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                tabs.add(Tab(obj.getInt("id"), obj.getString("title"), obj.getString("url")))
            }
            currentIndex = prefs.getInt("current", 0).coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
            nextId = prefs.getInt("nextId", tabs.size)
        } catch (_: Exception) { }
    }
}
