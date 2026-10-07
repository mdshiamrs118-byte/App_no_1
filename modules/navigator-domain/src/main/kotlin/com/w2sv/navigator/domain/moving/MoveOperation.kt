package com.w2sv.navigator.domain.moving

import android.content.Intent
import android.os.Parcelable
import com.w2sv.androidutils.content.getParcelableCompat
import kotlinx.parcelize.Parcelize

sealed interface MoveOperation : Parcelable {

    val file: NavigatableFile
    val destination: MoveDestination
    val destinationSelectionManner: DestinationSelectionManner
    val isPartOfBatch: Boolean get() = false

    sealed interface Batchable : MoveOperation {
        override val destination: MoveDestination.Directory
    }

    @Parcelize
    data class FileDestinationPicked(
        override val file: NavigatableFile,
        override val destination: MoveDestination.File,
        override val destinationSelectionManner: DestinationSelectionManner.Picked
    ) : MoveOperation

    @Parcelize
    data class DirectoryDestinationPicked(
        override val file: NavigatableFile,
        override val destination: MoveDestination.Directory,
        override val destinationSelectionManner: DestinationSelectionManner.Picked,
        override val isPartOfBatch: Boolean = true
    ) : Batchable

    @Parcelize
    data class QuickMove(
        override val file: NavigatableFile,
        override val destination: MoveDestination.Directory,
        override val destinationSelectionManner: DestinationSelectionManner.Quick,
        override val isPartOfBatch: Boolean
    ) : Batchable

    @Parcelize
    data class AutoMove(
        override val file: NavigatableFile,
        override val destination: MoveDestination.Directory,
        override val destinationSelectionManner: DestinationSelectionManner.Auto
    ) : MoveOperation

    companion object {
        const val EXTRA = "com.w2sv.navigator.extra.MoveOperation"

        inline operator fun <reified MB : MoveOperation> invoke(intent: Intent): MB =
            checkNotNull(intent.getParcelableCompat<MB>(EXTRA))
    }
}
