package dev.opencode.android.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object Permissions {

    /** API 30+ con All Files Access permite usar File API en todo el almacenamiento. */
    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()

    /** API 29 y menores: necesita permiso de lectura de almacenamiento. */
    fun hasReadStorage(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return hasAllFilesAccess()
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun allFilesSettingsIntent(activity: Activity): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.parse("package:${activity.packageName}"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${activity.packageName}"))
        }
    }

    fun openAllFilesSettings(activity: Activity) {
        activity.startActivity(allFilesSettingsIntent(activity))
    }

    val storagePermissionContract = ActivityResultContracts.RequestPermission()

    /** URI persistente elegida con SAF (Sistema de Archivos de Android). */
    fun takePersistable(activity: Activity, uri: Uri) {
        try {
            activity.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
    }
}