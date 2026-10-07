package com.w2sv.navigator.notifications.api.env

import android.app.NotificationManager
import android.content.Context

interface NotificationEnvironment {
    val context: Context
    val notificationManager: NotificationManager
}
