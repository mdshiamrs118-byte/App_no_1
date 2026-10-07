package com.w2sv.navigator.observing

import android.content.ContentResolver
import junit.framework.TestCase.assertEquals
import org.junit.Test

class FileChangeEventTest {

    @Test
    fun `parseFrom parses relevant FileChangeEvent from notify flags`() {
        fun assert(expected: FileChangeEvent?, notifyFlags: Int) {
            assertEquals(
                expected,
                FileChangeEvent.parseFrom(notifyFlags)
            )
        }

        assert(FileChangeEvent.Insert, ContentResolver.NOTIFY_INSERT)
        assert(FileChangeEvent.Update, ContentResolver.NOTIFY_UPDATE)
        assert(FileChangeEvent.Delete, ContentResolver.NOTIFY_DELETE)
        assert(
            FileChangeEvent.Update,
            ContentResolver.NOTIFY_UPDATE or ContentResolver.NOTIFY_SYNC_TO_NETWORK
        )
        assert(null, ContentResolver.NOTIFY_SYNC_TO_NETWORK)
        assert(null, 0)
    }

    @Test
    fun `describeNotifyFlags returns readable representation of notify flags`() {
        assertEquals(
            "UPDATE|SYNC_TO_NETWORK",
            FileChangeEvent.describeNotifyFlags(ContentResolver.NOTIFY_UPDATE or ContentResolver.NOTIFY_SYNC_TO_NETWORK)
        )
    }
}
