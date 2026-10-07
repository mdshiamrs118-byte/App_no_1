package com.w2sv.navigator.observing

import android.os.SystemClock
import com.w2sv.navigator.domain.moving.MediaStoreEntry
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * Suspends until the provided MediaStore data becomes stable, i.e. its file size
 * remains unchanged for at least [stableWindowMs].
 *
 * The data is polled periodically and the function is cancellable. Returns the
 * stable [MediaStoreEntry], or null if [provideEntry] returns null.
 */
internal suspend fun awaitStableMediaStoreEntry(
    provideEntry: () -> MediaStoreEntry?,
    stableWindowMs: Long = 1_200,
    elapsedTime: () -> Long = SystemClock::elapsedRealtime,
    log: (() -> String) -> Unit = {}
): MediaStoreEntry? {
    val pollPeriod = stableWindowMs / 5
    var previousStatus: FileStatus? = null

    while (true) {
        currentCoroutineContext().ensureActive()

        val entry = provideEntry() ?: return null
        val status = FileStatus.compute(
            previousStatus = previousStatus,
            size = entry.size,
            elapsedTime = elapsedTime
        )

        log { "FileStatus=$status" }

        if (status is FileStatus.Stable && status.stableFor >= stableWindowMs) {
            return entry
        }

        previousStatus = status
        delay(pollPeriod)
    }
}

private sealed interface FileStatus {
    val size: Long

    data class Stable(override val size: Long, val stableSince: Long, val stableFor: Long) : FileStatus
    data class Unstable(override val size: Long, val measuredAt: Long) : FileStatus

    companion object {
        fun compute(previousStatus: FileStatus?, size: Long, elapsedTime: () -> Long): FileStatus {
            val now = elapsedTime()

            return if (previousStatus?.size == size) {
                val stableSince = when (previousStatus) {
                    is Stable -> previousStatus.stableSince
                    is Unstable -> previousStatus.measuredAt
                }
                Stable(
                    size = size,
                    stableSince = stableSince,
                    stableFor = now - stableSince
                )
            } else {
                Unstable(size = size, measuredAt = now)
            }
        }
    }
}
