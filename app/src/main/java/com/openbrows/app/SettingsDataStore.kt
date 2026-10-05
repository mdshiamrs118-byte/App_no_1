package com.openbrows.app

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "openbrows_settings")

enum class UrlPosition { TOP, BOTTOM }
enum class TabsPosition { OFF, BOTTOM, LEFT, RIGHT }
enum class HideToolbarMode { OFF, ON, ON_AND_LOCK }

data class BrowserSettings(
    val defaultHomePage: String = "https://www.google.com",
    val textSize: Float = 100f,
    val urlPosition: UrlPosition = UrlPosition.BOTTOM,
    val tabsPosition: TabsPosition = TabsPosition.OFF,
    val tabsVisibleCountAtOnce: Int = 3,
    val verticalTabsAlwaysShowIcons: Boolean = false,
    val hideToolbarMode: HideToolbarMode = HideToolbarMode.OFF
)

class SettingsRepository(private val context: Context) {
    companion object {
        val HOME_PAGE = stringPreferencesKey("home_page")
        val TEXT_SIZE = floatPreferencesKey("text_size")
        val URL_POS = stringPreferencesKey("url_pos")
        val TABS_POS = stringPreferencesKey("tabs_pos")
        val TABS_COUNT = intPreferencesKey("tabs_count")
        val ALWAYS_SHOW_ICONS = booleanPreferencesKey("always_show_icons")
        val HIDE_TOOLBAR = stringPreferencesKey("hide_toolbar")
    }

    val settingsFlow: Flow<BrowserSettings> = context.dataStore.data.map { pref ->
        BrowserSettings(
            defaultHomePage = pref[HOME_PAGE] ?: "https://www.google.com",
            textSize = pref[TEXT_SIZE] ?: 100f,
            urlPosition = UrlPosition.valueOf(pref[URL_POS] ?: UrlPosition.BOTTOM.name),
            tabsPosition = TabsPosition.valueOf(pref[TABS_POS] ?: TabsPosition.OFF.name),
            tabsVisibleCountAtOnce = pref[TABS_COUNT] ?: 3,
            verticalTabsAlwaysShowIcons = pref[ALWAYS_SHOW_ICONS] ?: false,
            hideToolbarMode = HideToolbarMode.valueOf(pref[HIDE_TOOLBAR] ?: HideToolbarMode.OFF.name)
        )
    }

    suspend fun updateSettings(settings: BrowserSettings) {
        context.dataStore.edit { pref ->
            pref[HOME_PAGE] = settings.defaultHomePage
            pref[TEXT_SIZE] = settings.textSize
            pref[URL_POS] = settings.urlPosition.name
            pref[TABS_POS] = settings.tabsPosition.name
            pref[TABS_COUNT] = settings.tabsVisibleCountAtOnce
            pref[ALWAYS_SHOW_ICONS] = settings.verticalTabsAlwaysShowIcons
            pref[HIDE_TOOLBAR] = settings.hideToolbarMode.name
        }
    }
}
