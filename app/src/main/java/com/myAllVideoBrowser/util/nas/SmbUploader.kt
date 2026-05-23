package com.myAllVideoBrowser.util.nas

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.util.concurrent.TimeUnit
import com.myAllVideoBrowser.data.nas.NasConfig
import com.myAllVideoBrowser.data.nas.NasDestinationType
import com.myAllVideoBrowser.util.AppLogger
import java.io.File
import java.io.FileInputStream
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmbUploader @Inject constructor() : NasUploader {

    companion object {
        private const val PROGRESS_TICK = 256 * 1024L
        private const val BUF_SIZE = 64 * 1024
    }

    override fun supports(config: NasConfig): Boolean =
        config.type == NasDestinationType.SMB

    override fun upload(
        config: NasConfig,
        localFile: File,
        remoteFileName: String,
        onProgress: (sent: Long, total: Long) -> Unit
    ): NasUploader.UploadResult {
        if (!supports(config)) {
            return NasUploader.UploadResult(false, errorMessage = "Not an SMB config")
        }
        if (!localFile.exists() || localFile.length() == 0L) {
            return NasUploader.UploadResult(false, errorMessage = "Local file missing or empty")
        }

        return try {
            withShare(config) { share ->
                val baseDir = normalizeDir(config.remotePath)
                if (baseDir.isNotEmpty()) ensureDirChain(share, baseDir)

                val targetName = uniqueRemoteName(share, baseDir, remoteFileName)
                val targetPath = joinPath(baseDir, targetName)

                val file = share.openFile(
                    targetPath,
                    EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.SYNCHRONIZE),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
                )

                val total = localFile.length()
                var sent = 0L
                var lastReport = 0L
                try {
                    file.outputStream.use { out ->
                        FileInputStream(localFile).use { input ->
                            val buf = ByteArray(BUF_SIZE)
                            while (true) {
                                val n = input.read(buf)
                                if (n == -1) break
                                out.write(buf, 0, n)
                                sent += n
                                if (sent - lastReport >= PROGRESS_TICK) {
                                    lastReport = sent
                                    try {
                                        onProgress(sent, total)
                                    } catch (_: Throwable) {
                                    }
                                }
                            }
                            out.flush()
                        }
                    }
                } finally {
                    try {
                        file.close()
                    } catch (_: Throwable) {
                    }
                }

                onProgress(total, total)

                val cleanPath = if (targetPath.startsWith("/")) targetPath else "/$targetPath"
                val uri = "smb://${config.host}/${config.shareName}$cleanPath".replace('\\', '/')
                NasUploader.UploadResult(success = true, remoteUri = uri, sizeBytes = total)
            }
        } catch (e: Throwable) {
            AppLogger.e("SMB upload failed: ${e.message}")
            e.printStackTrace()
            NasUploader.UploadResult(false, errorMessage = e.message ?: "SMB error")
        }
    }

    override fun testConnection(config: NasConfig): Result<Unit> {
        if (!supports(config)) return Result.failure(IllegalArgumentException("Not an SMB config"))
        return try {
            withShare(config) {
                Result.success(Unit)
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun <T> withShare(config: NasConfig, block: (DiskShare) -> T): T {
        val cfg = SmbConfig.builder()
            .withMultiProtocolNegotiate(true)
            .withDfsEnabled(false)
            .withTimeout(30, TimeUnit.SECONDS)
            .withSoTimeout(30, TimeUnit.SECONDS)
            .build()
        val client = SMBClient(cfg)
        val port = if (config.port > 0) config.port else SMBClient.DEFAULT_PORT
        val connection = client.connect(config.host, port)
        try {
            val auth = when {
                config.username.isBlank() -> AuthenticationContext.anonymous()
                else -> AuthenticationContext(
                    config.username,
                    config.password.toCharArray(),
                    null
                )
            }
            val session = connection.authenticate(auth)
            val shareName = config.shareName.trim().trim('/').trim('\\')
            (session.connectShare(shareName) as DiskShare).use { share ->
                return block(share)
            }
        } finally {
            try {
                connection.close()
            } catch (_: Throwable) {
            }
            try {
                client.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun normalizeDir(input: String): String {
        return input.trim().replace('\\', '/').trim('/')
    }

    private fun joinPath(dir: String, name: String): String {
        return if (dir.isEmpty()) name else "$dir/$name"
    }

    private fun ensureDirChain(share: DiskShare, dirPath: String) {
        val parts = dirPath.split('/').filter { it.isNotEmpty() }
        val current = StringBuilder()
        for (p in parts) {
            if (current.isNotEmpty()) current.append('/')
            current.append(p)
            val path = current.toString()
            try {
                if (!share.folderExists(path)) {
                    share.mkdir(path)
                }
            } catch (e: Throwable) {
                AppLogger.d("SMB mkdir probe failed for $path: ${e.message}")
            }
        }
    }

    private fun uniqueRemoteName(share: DiskShare, dir: String, fileName: String): String {
        val initial = joinPath(dir, fileName)
        if (!share.fileExists(initial)) return fileName

        val baseName = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "")
        for (i in 1..50) {
            val candidate = if (ext.isEmpty()) "$baseName ($i)" else "$baseName ($i).$ext"
            if (!share.fileExists(joinPath(dir, candidate))) return candidate
        }
        return fileName
    }
}
