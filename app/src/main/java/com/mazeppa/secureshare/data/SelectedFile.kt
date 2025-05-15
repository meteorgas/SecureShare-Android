package com.mazeppa.secureshare.data

import android.net.Uri

data class SelectedFile(
    val uri: Uri,
    val name: String,
    val size: String
)