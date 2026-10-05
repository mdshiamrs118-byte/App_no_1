package com.openbrows.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TabModel(
    val id: String = java.util.UUID.randomUUID().toString(),
    var url: String = "https://www.google.com",
    var title: String = "New Tab"
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    val settings = repository.settingsFlow

    private val _tabs = MutableStateFlow(listOf(TabModel()))
    val tabs: StateFlow<List<TabModel>> = _tabs.asStateFlow()

    private val _currentTabId = MutableStateFlow(_tabs.value.first().id)
    val currentTabId: StateFlow<String> = _currentTabId.asStateFlow()

    fun addTab(url: String = "https://www.google.com") {
        val newTab = TabModel(url = url)
        _tabs.value = _tabs.value + newTab
        _currentTabId.value = newTab.id
    }

    fun removeTab(id: String) {
        if (_tabs.value.size > 1) {
            val newList = _tabs.value.filter { it.id != id }
            _tabs.value = newList
            if (_currentTabId.value == id) {
                _currentTabId.value = newList.last().id
            }
        }
    }

    fun selectTab(id: String) {
        _currentTabId.value = id
    }

    fun updateSettings(newSettings: BrowserSettings) {
        viewModelScope.launch {
            repository.updateSettings(newSettings)
        }
    }
}
