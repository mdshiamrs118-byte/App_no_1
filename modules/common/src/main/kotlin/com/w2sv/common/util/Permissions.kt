package com.w2sv.common.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.w2sv.androidutils.content.hasPermission
import com.w2sv.androidutils.os.postNotificationsPermissionRequired

/**
 * Opens the "All files access" settings page of the app. Only exists as of API 30.
 */
@RequiresApi(Build.VERSION_CODES.R)
fun goToManageExternalStorageSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.fromParts("package", context.packageName, null)
        )
    )
}

/**
 * Whether the app is allowed to read & move files on shared storage.
 *
 * - API >= 30: [Manifest.permission.MANAGE_EXTERNAL_STORAGE] ("All files access")
 * - API 29 (Android 10): [Manifest.permission.READ_EXTERNAL_STORAGE] and [Manifest.permission.WRITE_EXTERNAL_STORAGE], in conjunction with
 * `requestLegacyExternalStorage` being set in the manifest.
 */
fun Context.hasStorageAccessPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) &&
            hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

/**
 * @return true for API < 33 where [Manifest.permission.POST_NOTIFICATIONS] didn't yet exist, otherwise checks whether the permission has
 * been granted.
 */
fun Context.hasPostNotificationsPermission(): Boolean =
    !postNotificationsPermissionRequired || hasPermission(Manifest.permission.POST_NOTIFICATIONS)
