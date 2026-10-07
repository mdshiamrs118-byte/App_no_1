package com.w2sv.navigator.domain.moving

import android.content.ContentResolver
import android.os.Build
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import com.w2sv.androidutils.database.getBooleanOrThrow
import com.w2sv.androidutils.database.getLongOrThrow
import com.w2sv.androidutils.database.getStringOrThrow
import com.w2sv.androidutils.database.query
import com.w2sv.common.uri.MediaUri
import com.w2sv.domain.model.filetype.SourceType
import java.io.File
import kotlinx.parcelize.Parcelize
import slimber.log.e

/**
 * @property rowId queried via [MediaStore.MediaColumns._ID]
 * @property absPath queried via [MediaStore.MediaColumns.DATA]
 * @property relativePath queried via [MediaStore.MediaColumns.RELATIVE_PATH]. Path relative to the storage volume,
 * e.g. "Documents/", "DCIM/Camera/".
 * @property size queried via [MediaStore.MediaColumns.SIZE]
 * @property isPending queried via [MediaStore.MediaColumns.IS_PENDING]
 * @property isTrashed queried via [MediaStore.MediaColumns.IS_TRASHED]
 */
@Parcelize
data class MediaStoreEntry(
    val rowId: String,
    val absPath: String,
    val relativePath: String,
    val size: Long,
    val isPending: Boolean,
    val isTrashed: Boolean
) : Parcelable {

    val fileName: String
        get() = absPath.substringAfterLast(File.separator)

    /**
     * Equals empty string if no extension present (=> directory).
     */
    val fileExtension: String
        get() = fileName.substringAfterLast(".", "")

    val parentDirName: String
        get() = relativePath
            .removeSuffix(File.separator)
            .substringAfterLast(File.separator)

    fun file(): File =
        File(absPath)

    fun fileExists(): Boolean =
        file().exists()

    fun sourceType(): SourceType =
        when {
            relativePath.contains(directoryRecordingsCompat) -> SourceType.Recording
            // NOTE: Don't change the order of the Screenshot and Camera branches, as Environment.DIRECTORY_SCREENSHOTS
            // may be a child dir of Environment.DIRECTORY_DCIM
            relativePath.contains(Environment.DIRECTORY_SCREENSHOTS) -> SourceType.Screenshot
            relativePath.contains(Environment.DIRECTORY_DCIM) -> SourceType.Camera
            relativePath.contains(Environment.DIRECTORY_DOWNLOADS) -> SourceType.Download
            else -> SourceType.OtherApp
        }

    companion object {
        /**
         * [MediaStore.MediaColumns.IS_TRASHED] was only introduced in API 30; querying it on API 29 throws.
         */
        private val isTrashedColumnAvailable: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        private val queryColumns: Array<String> by lazy {
            buildList {
                add(MediaStore.MediaColumns._ID)
                add(MediaStore.MediaColumns.DATA)
                add(MediaStore.MediaColumns.RELATIVE_PATH)
                add(MediaStore.MediaColumns.SIZE)
                add(MediaStore.MediaColumns.IS_PENDING)
                if (isTrashedColumnAvailable) {
                    add(MediaStore.MediaColumns.IS_TRASHED)
                }
            }
                .toTypedArray()
        }

        fun queryFor(mediaUri: MediaUri, contentResolver: ContentResolver): MediaStoreEntry? =
            try {
                contentResolver.query(uri = mediaUri.uri, columns = queryColumns) {
                    MediaStoreEntry(
                        rowId = it.getStringOrThrow(MediaStore.MediaColumns._ID),
                        absPath = it.getStringOrThrow(MediaStore.MediaColumns.DATA),
                        relativePath = it.getStringOrThrow(MediaStore.MediaColumns.RELATIVE_PATH),
                        size = it.getLongOrThrow(MediaStore.MediaColumns.SIZE),
                        isPending = it.getBooleanOrThrow(MediaStore.MediaColumns.IS_PENDING),
                        isTrashed = if (isTrashedColumnAvailable) it.getBooleanOrThrow(MediaStore.MediaColumns.IS_TRASHED) else false
                    )
                }
            } catch (e: Exception) {
                e(e)
                null
            }
    }
}

private val directoryRecordingsCompat: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Environment.DIRECTORY_RECORDINGS else "Recordings"
