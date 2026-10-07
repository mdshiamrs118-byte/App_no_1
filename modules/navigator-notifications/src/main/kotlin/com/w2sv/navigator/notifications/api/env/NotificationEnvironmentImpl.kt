package com.w2sv.navigator.notifications.api.env

import android.app.NotificationManager
import android.content.Context

internal data class NotificationEnvironmentImpl(override val context: Context, override val notificationManager: NotificationManager) :
    NotificationEnvironment
