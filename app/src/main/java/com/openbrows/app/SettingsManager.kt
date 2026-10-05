package com.openbrows.app

import android.content.Context

class SettingsManager(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    companion object {
        const val TABS_OFF = 0
        const val TABS_BOTTOM = 1
        const val TABS_LEFT = 2
        const val TABS_RIGHT = 3

        const val HIDE_OFF = 0
        const val HIDE_ON_SCROLL = 1
        const val HIDE_ON_LOCK = 2

        const val POS_BOTTOM = 0
        const val POS_TOP = 1
    }

    var homePage: String
        get() = prefs.getString("home_page", "https://www.google.com") ?: "https://www.google.com"
        set(v) = prefs.edit().putString("home_page", v).apply()

    var textSize: Int
        get() = prefs.getInt("text_size", 100)
        set(v) = prefs.edit().putInt("text_size", v).apply()

    var urlPosition: Int
        get() = prefs.getInt("url_position", POS_BOTTOM)
        set(v) = prefs.edit().putInt("url_position", v).apply()

    var tabsPosition: Int
        get() = prefs.getInt("tabs_position", TABS_OFF)
        set(v) = prefs.edit().putInt("tabs_position", v).apply()

    var showBottomTabs: Boolean
        get() = prefs.getBoolean("show_bottom_tabs", true)
        set(v) = prefs.edit().putBoolean("show_bottom_tabs", v).apply()

    var bottomTabCount: Int
        get() = prefs.getInt("bottom_tab_count", 4)
        set(v) = prefs.edit().putInt("bottom_tab_count", v).apply()

    var alwaysShowSideTabs: Boolean
        get() = prefs.getBoolean("always_show_side_tabs", false)
        set(v) = prefs.edit().putBoolean("always_show_side_tabs", v).apply()

    var hideToolbarMode: Int
        get() = prefs.getInt("hide_toolbar_mode", HIDE_OFF)
        set(v) = prefs.edit().putInt("hide_toolbar_mode", v).apply()
}
