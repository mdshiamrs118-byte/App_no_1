package com.openbrows.app

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.PopupWindow
import android.widget.RadioGroup
import android.widget.SeekBar

class SettingsPanel(private val context: Context) {

    var onSettingsChanged: (() -> Unit)? = null
    private val settings = SettingsManager(context)

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.panel_settings, null)
        val width = (context.resources.displayMetrics.widthPixels * 0.92f).toInt()
        val popup = PopupWindow(
            view,
            width,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.elevation = 20f
        popup.animationStyle = R.style.PopupAnimation

        // Home page
        val homeInput = view.findViewById<EditText>(R.id.settingsHomePage)
        homeInput.setText(settings.homePage)

        // Text size
        val textSeek = view.findViewById<SeekBar>(R.id.settingsTextSize)
        textSeek.progress = settings.textSize
        textSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                settings.textSize = p.coerceAtLeast(50)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { onSettingsChanged?.invoke() }
        })

        // URL position
        val urlPosGroup = view.findViewById<RadioGroup>(R.id.urlPositionGroup)
        urlPosGroup.check(
            if (settings.urlPosition == SettingsManager.POS_BOTTOM) R.id.urlPosBottom
            else R.id.urlPosTop
        )
        urlPosGroup.setOnCheckedChangeListener { _, id ->
            settings.urlPosition = if (id == R.id.urlPosBottom)
                SettingsManager.POS_BOTTOM else SettingsManager.POS_TOP
            onSettingsChanged?.invoke()
        }

        // Tabs position
        val tabsPosGroup = view.findViewById<RadioGroup>(R.id.tabsPositionGroup)
        val checkedTab = when (settings.tabsPosition) {
            SettingsManager.TABS_OFF -> R.id.tabsOff
            SettingsManager.TABS_BOTTOM -> R.id.tabsBottom
            SettingsManager.TABS_LEFT -> R.id.tabsLeft
            else -> R.id.tabsRight
        }
        tabsPosGroup.check(checkedTab)
        tabsPosGroup.setOnCheckedChangeListener { _, id ->
            settings.tabsPosition = when (id) {
                R.id.tabsOff -> SettingsManager.TABS_OFF
                R.id.tabsBottom -> SettingsManager.TABS_BOTTOM
                R.id.tabsLeft -> SettingsManager.TABS_LEFT
                else -> SettingsManager.TABS_RIGHT
            }
            onSettingsChanged?.invoke()
        }

        // Bottom tabs count
        val bottomCountSeek = view.findViewById<SeekBar>(R.id.settingsBottomTabCount)
        bottomCountSeek.progress = settings.bottomTabCount
        bottomCountSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                settings.bottomTabCount = p.coerceIn(1, 10)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Always-show side tabs
        val alwaysShow = view.findViewById<CheckBox>(R.id.settingsAlwaysShowSide)
        alwaysShow.isChecked = settings.alwaysShowSideTabs
        alwaysShow.setOnCheckedChangeListener { _, checked ->
            settings.alwaysShowSideTabs = checked
        }

        // Hide toolbar mode
        val hideGroup = view.findViewById<RadioGroup>(R.id.hideToolbarGroup)
        hideGroup.check(when (settings.hideToolbarMode) {
            SettingsManager.HIDE_OFF -> R.id.hideOff
            SettingsManager.HIDE_ON_SCROLL -> R.id.hideOnScroll
            else -> R.id.hideOnLock
        })
        hideGroup.setOnCheckedChangeListener { _, id ->
            settings.hideToolbarMode = when (id) {
                R.id.hideOff -> SettingsManager.HIDE_OFF
                R.id.hideOnScroll -> SettingsManager.HIDE_ON_SCROLL
                else -> SettingsManager.HIDE_ON_LOCK
            }
            onSettingsChanged?.invoke()
        }

        // Apply button
        view.findViewById<Button>(R.id.settingsApply).setOnClickListener {
            settings.homePage = homeInput.text.toString().ifBlank { settings.homePage }
            onSettingsChanged?.invoke()
            popup.dismiss()
        }

        popup.showAtLocation(
            (context as Activity).window.decorView,
            Gravity.BOTTOM, 0, 0
        )
    }
}
