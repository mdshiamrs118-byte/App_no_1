package com.w2sv.navigator.observing

import android.os.Handler
import com.w2sv.domain.model.filetype.FileAndSourceType
import com.w2sv.domain.model.filetype.FileType
import com.w2sv.domain.model.filetype.SourceType
import com.w2sv.navigator.domain.moving.MediaStoreEntry

internal class MediaFileObserver(
    private val fileType: FileType,
    private val enabledSourceTypes: Collection<SourceType>,
    handler: Handler,
    environment: FileObserverEnvironment
) : FileObserver(
    mediaType = fileType.mediaType,
    blacklistSize = 16,
    handler = handler,
    environment = environment
) {
    override val logIdentifier: String
        get() = "${fileType.logIdentifier}FileObserver"

    override fun enabledFileAndSourceTypeOrNull(mediaStoreEntry: MediaStoreEntry): FileAndSourceType? {
        val sourceType = mediaStoreEntry.sourceType()

        return if (enabledSourceTypes.contains(sourceType)) {
            FileAndSourceType(fileType, sourceType)
        } else {
            null
        }
    }
}
