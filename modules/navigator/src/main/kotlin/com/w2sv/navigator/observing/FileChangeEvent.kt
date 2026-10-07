package com.w2sv.navigator.observing

import android.content.ContentResolver

/**
 * Event types relevant for the working of [FileObserver], delivered to [android.database.ContentObserver.onChange] via
 * [ContentResolver].NotifyFlags.
 */
internal enum class FileChangeEvent(private val notifyFlag: Int) {
    Update(ContentResolver.NOTIFY_UPDATE),
    Insert(ContentResolver.NOTIFY_INSERT),
    Delete(ContentResolver.NOTIFY_DELETE);

    companion object {
        /**
         * Parses the [FileChangeEvent] from the [notifyFlags] bitmask as delivered to [android.database.ContentObserver.onChange].
         * Although [notifyFlags] is, according to observation, always a combination of multiple [ContentResolver].NotifyFlags,
         * the assumption and observation is, that it includes always only one of the [FileChangeEvent.notifyFlag]s.
         */
        fun parseFrom(notifyFlags: Int): FileChangeEvent? =
            entries.firstOrNull { it.notifyFlag and notifyFlags != 0 }

        /**
         * @return A loggable representation of all flag types encoded in the [flags] bitmask.
         */
        fun describeNotifyFlags(flags: Int): String {
            val flagNames = arrayOf(
                ContentResolver.NOTIFY_INSERT to "INSERT",
                ContentResolver.NOTIFY_UPDATE to "UPDATE",
                ContentResolver.NOTIFY_DELETE to "DELETE",
                ContentResolver.NOTIFY_SYNC_TO_NETWORK to "SYNC_TO_NETWORK",
                ContentResolver.NOTIFY_SKIP_NOTIFY_FOR_DESCENDANTS to "SKIP_DESCENDANTS",
                1 shl 15 to "NO_DELAY" // hidden NOTIFY_NO_DELAY flag
            )

            return buildString {
                flagNames.forEach { (bit, name) ->
                    if (flags and bit != 0) {
                        if (isNotEmpty()) {
                            append("|")
                        }
                        append(name)
                    }
                }
            }
        }
    }
}
