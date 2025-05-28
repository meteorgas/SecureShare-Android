package com.mazeppa.secureshare.data

import android.net.Uri

data class OutgoingFile(
    val name: String,
    val size: String,
    val uri: Uri,
    var progress: Int = 0
)