package com.w2sv.navigator.notifications.api

import android.content.Context
import androidx.core.app.NotificationCompat

internal fun interface SummaryNotification {
    fun NotificationCompat.Builder.configure(context: Context, count: Int): NotificationCompat.Builder
}
