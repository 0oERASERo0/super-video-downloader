package com.myAllVideoBrowser.util.nas

import com.myAllVideoBrowser.data.nas.NasConfig
import java.io.File

interface NasUploader {

    data class UploadResult(
        val success: Boolean,
        val remoteUri: String? = null,
        val errorMessage: String? = null,
        val sizeBytes: Long = 0L
    )

    fun supports(config: NasConfig): Boolean

    fun upload(
        config: NasConfig,
        localFile: File,
        remoteFileName: String,
        onProgress: (sent: Long, total: Long) -> Unit
    ): UploadResult

    fun testConnection(config: NasConfig): Result<Unit>
}
