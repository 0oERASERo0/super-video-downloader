package com.myAllVideoBrowser.util.nas

import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import com.myAllVideoBrowser.util.AppLogger
import com.myAllVideoBrowser.util.NotificationsHelper
import com.myAllVideoBrowser.util.downloaders.generic_downloader.models.VideoTaskItem
import com.myAllVideoBrowser.util.downloaders.generic_downloader.models.VideoTaskState
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NasFinalizer @Inject constructor(
    private val nasUploadManager: NasUploadManager,
    private val notificationsHelper: NotificationsHelper,
) {

    data class Result(
        val uploaded: Boolean,
        val remoteUri: String? = null,
        val error: String? = null,
        val deletedLocal: Boolean = false,
    )

    fun isEnabled(): Boolean = nasUploadManager.isRemoteEnabled()

    /**
     * Uploads a finalized local file to the configured NAS destination.
     * Returns Result with details; safe no-op if NAS is disabled.
     *
     * Best-effort: failures here never break the existing local download
     * outcome — the local file remains in place.
     */
    fun finalize(
        context: Context,
        localUri: Uri,
        item: VideoTaskItem?,
    ): Result {
        if (!nasUploadManager.isRemoteEnabled()) {
            return Result(uploaded = false)
        }
        val local = try {
            localUri.toFile()
        } catch (e: Throwable) {
            AppLogger.e("NAS finalize: cannot convert uri to file: $localUri ${e.message}")
            return Result(uploaded = false, error = "Local URI not a file")
        }
        if (!local.exists() || local.length() == 0L) {
            return Result(uploaded = false, error = "Local file missing")
        }

        notifyStart(context, item, local.name)

        val result = nasUploadManager.uploadAndRecord(
            sourceFile = local,
            displayName = local.name,
        ) { sent, total ->
            notifyProgress(context, item, local.name, sent, total)
        }

        return if (result.success) {
            notifyDone(context, item, local.name, ok = true)
            val deleted = if (nasUploadManager.shouldDeleteLocalAfterUpload()) {
                try {
                    local.delete()
                } catch (e: Throwable) {
                    AppLogger.e("NAS finalize: local delete failed ${e.message}")
                    false
                }
            } else {
                false
            }
            Result(uploaded = true, remoteUri = result.remoteUri, deletedLocal = deleted)
        } else {
            notifyDone(context, item, local.name, ok = false)
            Result(uploaded = false, error = result.errorMessage)
        }
    }

    /**
     * Best-effort: swallow any errors so the calling downloader is not
     * affected by NAS failures.
     */
    fun finalizeQuiet(context: Context, localFile: File, item: VideoTaskItem?): Result {
        return try {
            finalize(context, Uri.fromFile(localFile), item)
        } catch (e: Throwable) {
            AppLogger.e("NAS finalize failed silently: ${e.message}")
            Result(uploaded = false, error = e.message)
        }
    }

    private fun notifyStart(context: Context, item: VideoTaskItem?, name: String) {
        if (item == null) return
        try {
            val task = item.clone().also {
                it.lineInfo = "Uploading to NAS: $name"
                it.taskState = VideoTaskState.PREPARE
            }
            val (id, builder) = notificationsHelper.createNotificationBuilder(task)
            notificationsHelper.showNotification(Pair(id, builder))
        } catch (_: Throwable) {
        }
    }

    private fun notifyProgress(
        context: Context,
        item: VideoTaskItem?,
        name: String,
        sent: Long,
        total: Long,
    ) {
        if (item == null) return
        try {
            val percent = if (total > 0) (sent * 100f / total) else 0f
            val task = item.clone().also {
                it.taskState = VideoTaskState.PREPARE
                it.totalSize = total
                it.downloadSize = sent
                it.percent = percent
                it.lineInfo = "Uploading to NAS: ${percent.toInt()}% $name"
            }
            val (id, builder) = notificationsHelper.createNotificationBuilder(task)
            notificationsHelper.showNotification(Pair(id, builder))
        } catch (_: Throwable) {
        }
    }

    private fun notifyDone(context: Context, item: VideoTaskItem?, name: String, ok: Boolean) {
        if (item == null) return
        try {
            val task = item.clone().also {
                it.taskState = if (ok) VideoTaskState.SUCCESS else VideoTaskState.ERROR
                it.lineInfo = if (ok) "Uploaded to NAS: $name" else "NAS upload failed: $name"
            }
            val (id, builder) = notificationsHelper.createNotificationBuilder(task)
            notificationsHelper.showNotification(Pair(id, builder))
        } catch (_: Throwable) {
        }
    }
}
