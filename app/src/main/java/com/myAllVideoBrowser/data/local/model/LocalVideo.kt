package com.myAllVideoBrowser.data.local.model

import android.net.Uri

data class LocalVideo(
    var id: Long,
    var uri: Uri,
    var name: String,
    var isRemote: Boolean = false,
    var remoteLabel: String = "",
    var remoteRecordId: String? = null,
) {

    var size: String = ""

    val thumbnailPath: Uri
        get() = uri

    val isRemoteVisible: Boolean
        get() = isRemote

    val remoteLabelOrEmpty: String
        get() = if (isRemote) remoteLabel else ""
}