package com.w2sv.navigator.notifications.api.controller

import android.app.Notification
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import com.w2sv.navigator.notifications.api.env.NotificationEnvironment
import com.w2sv.navigator.notifications.api.isIdActive
import slimber.log.i

internal class NotificationSummaryController(
    environment: NotificationEnvironment,
    private val notificationId: Int,
    private val builder: (Int) -> NotificationCompat.Builder
) : NotificationEnvironment by environment {

    fun update(notificationCount: Int, onPost: Boolean) {
        if (showSummary(notificationCount, onPost)) {
            i { "Posting summary notification; notificationCount=$notificationCount" }
            notificationManager.notify(notificationId, notification(notificationCount))
        } else {
            i { "Cancelling summary notification".takeIf { notificationManager.isIdActive(notificationId) }.orEmpty() }
            notificationManager.cancel(notificationId)
        }
    }

    private fun notification(activeNotifications: Int): Notification =
        builder(activeNotifications)
            .setGroupSummary(true)
            .build()
}

@VisibleForTesting
internal fun showSummary(notificationCount: Int, onPost: Boolean): Boolean =
    (!onPost && notificationCount != 0) || notificationCount >= 2
