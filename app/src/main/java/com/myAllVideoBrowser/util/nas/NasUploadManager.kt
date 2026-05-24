package com.myAllVideoBrowser.util.nas

import com.myAllVideoBrowser.data.local.room.dao.RemoteVideoDao
import com.myAllVideoBrowser.data.local.room.entity.RemoteVideoInfo
import com.myAllVideoBrowser.data.nas.NasConfig
import com.myAllVideoBrowser.data.nas.NasCredentialsStore
import com.myAllVideoBrowser.data.nas.NasDestinationType
import com.myAllVideoBrowser.util.AppLogger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NasUploadManager @Inject constructor(
    private val credentialsStore: NasCredentialsStore,
    private val webDavUploader: WebDavUploader,
    private val smbUploader: SmbUploader,
    private val remoteVideoDao: RemoteVideoDao,
) {

    fun currentConfig(): NasConfig = credentialsStore.load()

    fun isRemoteEnabled(): Boolean {
        val cfg = currentConfig()
        return cfg.type != NasDestinationType.LOCAL && cfg.isComplete()
    }

    private fun uploaderFor(config: NasConfig): NasUploader? = when (config.type) {
        NasDestinationType.WEBDAV -> webDavUploader
        NasDestinationType.SMB -> smbUploader
        NasDestinationType.LOCAL -> null
    }

    fun testConnection(config: NasConfig): Result<Unit> {
        val uploader = uploaderFor(config)
            ?: return Result.failure(IllegalStateException("Local destination has no remote test"))
        return uploader.testConnection(config)
    }

    fun uploadAndRecord(
        sourceFile: File,
        displayName: String,
        onProgress: (Long, Long) -> Unit
    ): NasUploader.UploadResult {
        val cfg = currentConfig()
        if (cfg.type == NasDestinationType.LOCAL) {
            return NasUploader.UploadResult(false, errorMessage = "NAS disabled")
        }
        if (!cfg.isComplete()) {
            return NasUploader.UploadResult(false, errorMessage = "NAS config incomplete")
        }
        val uploader = uploaderFor(cfg)
            ?: return NasUploader.UploadResult(false, errorMessage = "No uploader for ${cfg.type}")

        AppLogger.d("NAS upload start: ${cfg.type} ${sourceFile.absolutePath} -> $displayName")
        val result = uploader.upload(cfg, sourceFile, displayName, onProgress)
        AppLogger.d("NAS upload done: success=${result.success} uri=${result.remoteUri} err=${result.errorMessage}")

        if (result.success && result.remoteUri != null) {
            try {
                remoteVideoDao.insert(
                    RemoteVideoInfo(
                        name = displayName,
                        remoteUri = result.remoteUri,
                        destinationType = cfg.type.name,
                        destinationLabel = cfg.describe(),
                        sizeBytes = if (result.sizeBytes > 0) result.sizeBytes else sourceFile.length(),
                    )
                )
            } catch (e: Throwable) {
                AppLogger.e("Failed to record remote upload: ${e.message}")
            }
        }
        return result
    }

    fun shouldDeleteLocalAfterUpload(): Boolean = currentConfig().deleteLocalAfterUpload
}
