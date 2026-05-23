package com.myAllVideoBrowser.data.nas

enum class NasDestinationType {
    LOCAL, WEBDAV, SMB
}

data class NasConfig(
    val type: NasDestinationType = NasDestinationType.LOCAL,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
    val remotePath: String = "",
    val shareName: String = "",
    val useTls: Boolean = false,
    val deleteLocalAfterUpload: Boolean = false,
) {
    fun isComplete(): Boolean {
        return when (type) {
            NasDestinationType.LOCAL -> true
            NasDestinationType.WEBDAV -> host.isNotBlank()
            NasDestinationType.SMB -> host.isNotBlank() && shareName.isNotBlank()
        }
    }

    fun describe(): String {
        return when (type) {
            NasDestinationType.LOCAL -> "Local"
            NasDestinationType.WEBDAV -> {
                val scheme = if (useTls) "https" else "http"
                val portPart = if (port > 0) ":$port" else ""
                val path = if (remotePath.startsWith("/")) remotePath else "/$remotePath"
                "$scheme://$host$portPart$path"
            }
            NasDestinationType.SMB -> {
                val portPart = if (port > 0) ":$port" else ""
                val path = if (remotePath.isBlank()) "" else if (remotePath.startsWith("/")) remotePath else "/$remotePath"
                "smb://$host$portPart/$shareName$path"
            }
        }
    }
}
