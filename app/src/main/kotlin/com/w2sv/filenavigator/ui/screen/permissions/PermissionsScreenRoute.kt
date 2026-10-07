package com.w2sv.filenavigator.ui.screen.permissions

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.PermissionState
import com.w2sv.androidutils.content.openAppSettings
import com.w2sv.common.util.goToManageExternalStorageSettings
import com.w2sv.composed.core.OnChange
import com.w2sv.composed.permissions.extensions.launchPermissionRequest
import com.w2sv.filenavigator.ui.AppViewModel
import com.w2sv.filenavigator.ui.sharedstate.AppPermissionsState
import com.w2sv.filenavigator.ui.sharedstate.RequiredPermission
import com.w2sv.filenavigator.ui.util.activityViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList

@Composable
fun PermissionsScreenRoute(onAllPermissionsGranted: () -> Unit, appVM: AppViewModel = activityViewModel()) {
    val permissionsState = appVM.permissionsState
    val postNotificationsPermissionState = rememberPostNotificationsPermissionState()

    SyncPostNotificationsPermissionState(
        permissionState = postNotificationsPermissionState,
        appPermissionsState = permissionsState
    )

    val legacyStoragePermissionRequest = rememberLegacyStoragePermissionRequest(permissionsState)

    val missingPermissions by permissionsState.missingPermissions.collectAsStateWithLifecycle()

    OnChange(missingPermissions) { if (it.isEmpty()) onAllPermissionsGranted() }

    val permissionCards = remember(missingPermissions) {
        buildPermissionCards(
            missingPermissions = missingPermissions.toPersistentList(),
            permissionsState = permissionsState,
            postNotificationsPermissionState = postNotificationsPermissionState,
            launchLegacyStoragePermissionRequest = legacyStoragePermissionRequest
        )
    }

    PermissionsScreen(cards = permissionCards)
}

@Composable
private fun SyncPostNotificationsPermissionState(permissionState: PermissionState, appPermissionsState: AppPermissionsState) {
    OnChange(permissionState.status) { appPermissionsState.refresh() }
}

/**
 * Android 10 (API 29) has no "All files access" permission. There, storage access is obtained through the regular runtime
 * permissions READ_EXTERNAL_STORAGE & WRITE_EXTERNAL_STORAGE.
 *
 * @return a function launching the request. If the request has already been denied once, opens the app settings instead, as the
 * system may suppress the dialog from then on.
 */
@Composable
private fun rememberLegacyStoragePermissionRequest(permissionsState: AppPermissionsState): (Context) -> Unit {
    var deniedBefore by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        deniedBefore = !results.values.all { it }
        permissionsState.refresh()
    }

    return remember(launcher) {
        { context ->
            if (deniedBefore) {
                context.openAppSettings()
            } else {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }
}

private fun buildPermissionCards(
    missingPermissions: ImmutableList<RequiredPermission>,
    permissionsState: AppPermissionsState,
    postNotificationsPermissionState: PermissionState,
    launchLegacyStoragePermissionRequest: (Context) -> Unit
): ImmutableList<PermissionCard> =
    missingPermissions.map {
        when (it) {
            RequiredPermission.PostNotifications -> PermissionCard.postNotifications(
                onGrantButtonClick = { context ->
                    permissionsState.onPostNotificationsPermissionRequested()
                    postNotificationsPermissionState.launchPermissionRequest(
                        launchedBefore = permissionsState.postNotificationsPermissionRequested(),
                        onSuppressed = { context.openAppSettings() }
                    )
                }
            )

            RequiredPermission.ManageAllFiles -> PermissionCard.manageAllFiles(
                onGrantButtonClick = { context ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        goToManageExternalStorageSettings(context)
                    } else {
                        launchLegacyStoragePermissionRequest(context)
                    }
                }
            )
        }
    }
        .toPersistentList()
