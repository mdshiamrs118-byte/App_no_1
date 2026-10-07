package com.w2sv.navigator.notifications.api.controller

import junit.framework.TestCase.assertEquals
import org.junit.Test

class NotificationSummaryControllerKtTest {

    @Test
    fun showSummary() {
        fun test(expected: Boolean, activeNotifications: Int, onPost: Boolean) {
            assertEquals(expected, showSummary(activeNotifications, onPost))
        }

        test(false, 0, false)
        test(false, 0, true)
        test(true, 1, false)
        test(false, 1, true)
        test(true, 2, true)
        test(true, 2, false)
    }
}
