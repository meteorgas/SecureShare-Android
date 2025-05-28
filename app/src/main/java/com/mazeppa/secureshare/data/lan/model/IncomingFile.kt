package com.mazeppa.secureshare.data.lan.model

data class IncomingFile(
    val name: String,
    val size: String,
    val mimeType: String,
    var progress: Int = 0
)