package com.mazeppa.secureshare.utils

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.net.toUri

object PermissionHandler {

    fun requestPermissions(
        permissionLauncher: ActivityResultLauncher<Array<String>>,
        startActivityForResult: ActivityResultLauncher<Intent>,
        packageName: String
    ) {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )

        requestStoragePermission(
            startActivityForResult = startActivityForResult,
            permissionsToRequest = permissionsToRequest,
            permissionLauncher = permissionLauncher,
            packageName = packageName
        )
    }

    private fun requestStoragePermission(
        permissionLauncher: ActivityResultLauncher<Array<String>>,
        startActivityForResult: ActivityResultLauncher<Intent>,
        packageName: String,
        permissionsToRequest: MutableList<String>
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
            val manageAllFilesIntent =
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    addCategory("android.intent.category.DEFAULT")
                    data = "package:$packageName".toUri()
                }
            if (!Environment.isExternalStorageManager()) {
                startActivityForResult.launch(manageAllFilesIntent)
            }
        } else {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}