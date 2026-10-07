package com.w2sv.navigator.domain.moving

import com.w2sv.navigator.domain.notifications.NotificationEvent

sealed interface MoveOperationFeedback {
    data class Toast(val text: CharSequence, val duration: Int) : MoveOperationFeedback

    @JvmInline
    value class Notification(val event: NotificationEvent) : MoveOperationFeedback
}
