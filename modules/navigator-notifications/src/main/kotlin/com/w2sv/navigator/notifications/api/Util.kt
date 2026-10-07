package com.w2sv.navigator.notifications.api

import android.app.NotificationManager

/**
 * Shouldn't be used in production.
 */
fun NotificationManager.isIdActive(id: Int): Boolean =
    activeNotifications.any { it.id == id }
