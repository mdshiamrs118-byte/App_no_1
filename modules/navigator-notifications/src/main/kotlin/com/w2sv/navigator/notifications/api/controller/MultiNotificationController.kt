package com.w2sv.navigator.notifications.api.controller

import android.app.Notification
import androidx.core.app.NotificationCompat
import com.w2sv.navigator.notifications.AppNotification
import com.w2sv.navigator.notifications.api.MultiNotificationIds
import com.w2sv.navigator.notifications.api.SummaryNotification
import com.w2sv.navigator.notifications.api.env.NotificationEnvironment

internal abstract class MultiNotificationController<Args>(
    environment: NotificationEnvironment,
    appNotification: AppNotification,
    summaryNotification: SummaryNotification? = null
) : NotificationController<Args>(environment, appNotification.channel) {

    private val ids = MultiNotificationIds(appNotification.multiInstanceIdBase)
    private val summaryController: NotificationSummaryController? = summaryNotification?.let { summary ->
        NotificationSummaryController(
            environment = environment,
            notificationId = appNotification.summaryId,
            builder = { count -> summary.run { builder().configure(context, count) } }
        )
    }

    val notificationCount: Int by ids::count

    fun post(args: Args): Int {
        val id = ids.next()
        notificationManager.notify(id, build(args, id))
        summaryController?.update(notificationCount, true)
        return id
    }

    open fun cancel(id: Int) {
        notificationManager.cancel(id)
        ids.cancel(id)
        summaryController?.update(notificationCount, false)
    }

    fun build(args: Args, id: Int): Notification =
        builder()
            .apply { configure(args, id) }
            .build()

    final override fun builder(): NotificationCompat.Builder =
        super.builder().setGroup(appNotificationChannel.name)
}
