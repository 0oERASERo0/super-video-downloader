package com.myAllVideoBrowser.util.nas

import com.myAllVideoBrowser.data.nas.NasConfig
import com.myAllVideoBrowser.data.nas.NasDestinationType
import com.myAllVideoBrowser.util.AppLogger
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavUploader @Inject constructor() : NasUploader {

    companion object {
        private const val PROGRESS_TICK = 256 * 1024L
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    override fun supports(config: NasConfig): Boolean =
        config.type == NasDestinationType.WEBDAV

    override fun upload(
        config: NasConfig,
        localFile: File,
        remoteFileName: String,
        onProgress: (sent: Long, total: Long) -> Unit
    ): NasUploader.UploadResult {
        if (!supports(config)) {
            return NasUploader.UploadResult(false, errorMessage = "Not a WebDAV config")
        }
        if (!localFile.exists() || localFile.length() == 0L) {
            return NasUploader.UploadResult(false, errorMessage = "Local file missing or empty")
        }

        return try {
            ensureRemoteDir(config)

            val target = buildFileUrl(config, remoteFileName)
            val finalUrl = uniqueRemoteUrl(config, target, remoteFileName)

            val body = ProgressFileBody(
                localFile,
                contentType(remoteFileName),
                onProgress
            )
            val builder = Request.Builder().url(finalUrl).put(body)
            authHeader(config)?.let { builder.header("Authorization", it) }

            val response = client.newCall(builder.build()).execute()
            response.use { resp ->
                if (resp.isSuccessful || resp.code == 201 || resp.code == 204) {
                    onProgress(localFile.length(), localFile.length())
                    NasUploader.UploadResult(
                        success = true,
                        remoteUri = finalUrl,
                        sizeBytes = localFile.length()
                    )
                } else {
                    NasUploader.UploadResult(
                        success = false,
                        errorMessage = "WebDAV PUT failed HTTP ${resp.code}"
                    )
                }
            }
        } catch (e: Throwable) {
            AppLogger.e("WebDAV upload failed: ${e.message}")
            e.printStackTrace()
            NasUploader.UploadResult(false, errorMessage = e.message ?: "WebDAV error")
        }
    }

    override fun testConnection(config: NasConfig): Result<Unit> {
        if (!supports(config)) return Result.failure(IllegalArgumentException("Not a WebDAV config"))
        return try {
            val url = baseUrl(config)
            val builder = Request.Builder().url(url)
                .method("PROPFIND", RequestBody.create(null, ByteArray(0)))
                .header("Depth", "0")
            authHeader(config)?.let { builder.header("Authorization", it) }

            client.newCall(builder.build()).execute().use { resp ->
                if (resp.isSuccessful || resp.code in 200..299 || resp.code == 207) {
                    Result.success(Unit)
                } else if (resp.code == 401) {
                    Result.failure(Exception("Auth failed (401)"))
                } else if (resp.code == 405) {
                    val getBuilder = Request.Builder().url(url).get()
                    authHeader(config)?.let { getBuilder.header("Authorization", it) }
                    client.newCall(getBuilder.build()).execute().use { r ->
                        if (r.isSuccessful) Result.success(Unit)
                        else Result.failure(Exception("HTTP ${r.code}"))
                    }
                } else {
                    Result.failure(Exception("HTTP ${resp.code}"))
                }
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun authHeader(config: NasConfig): String? {
        if (config.username.isBlank()) return null
        return Credentials.basic(config.username, config.password)
    }

    private fun baseUrl(config: NasConfig): String {
        val scheme = if (config.useTls) "https" else "http"
        val portPart = if (config.port > 0) ":${config.port}" else ""
        val rawPath = config.remotePath.trim().trim('/')
        val pathPart = if (rawPath.isEmpty()) "/" else "/${encodePathSegments(rawPath)}/"
        return "$scheme://${config.host}$portPart$pathPart"
    }

    private fun buildFileUrl(config: NasConfig, fileName: String): String {
        return baseUrl(config) + encodeSegment(fileName)
    }

    private fun encodePathSegments(path: String): String =
        path.split('/').filter { it.isNotEmpty() }.joinToString("/") { encodeSegment(it) }

    private fun encodeSegment(segment: String): String =
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    private fun ensureRemoteDir(config: NasConfig) {
        val rawPath = config.remotePath.trim().trim('/')
        if (rawPath.isEmpty()) return

        val scheme = if (config.useTls) "https" else "http"
        val portPart = if (config.port > 0) ":${config.port}" else ""
        val host = "$scheme://${config.host}$portPart"
        val segments = rawPath.split('/').filter { it.isNotEmpty() }

        val accumulated = StringBuilder()
        for (segment in segments) {
            accumulated.append('/').append(encodeSegment(segment))
            val dirUrl = "$host${accumulated}/"
            val builder = Request.Builder().url(dirUrl)
                .method("MKCOL", RequestBody.create(null, ByteArray(0)))
            authHeader(config)?.let { builder.header("Authorization", it) }
            try {
                client.newCall(builder.build()).execute().close()
            } catch (e: Throwable) {
                AppLogger.d("WebDAV MKCOL probe failed (continuing): ${e.message}")
            }
        }
    }

    private fun uniqueRemoteUrl(config: NasConfig, initial: String, fileName: String): String {
        if (!fileExists(config, initial)) return initial
        val baseName = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "")
        for (i in 1..50) {
            val candidate = if (ext.isEmpty()) "$baseName ($i)" else "$baseName ($i).$ext"
            val url = buildFileUrl(config, candidate)
            if (!fileExists(config, url)) return url
        }
        return initial
    }

    private fun fileExists(config: NasConfig, url: String): Boolean {
        return try {
            val builder = Request.Builder().url(url).head()
            authHeader(config)?.let { builder.header("Authorization", it) }
            client.newCall(builder.build()).execute().use { resp ->
                resp.isSuccessful || resp.code == 200
            }
        } catch (e: Throwable) {
            false
        }
    }

    private fun contentType(fileName: String): okhttp3.MediaType? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mt = when (ext) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            else -> "application/octet-stream"
        }
        return mt.toMediaTypeOrNull()
    }

    private class ProgressFileBody(
        private val file: File,
        private val contentType: okhttp3.MediaType?,
        private val onProgress: (Long, Long) -> Unit,
    ) : RequestBody() {

        override fun contentType(): okhttp3.MediaType? = contentType

        override fun contentLength(): Long = file.length()

        override fun writeTo(sink: BufferedSink) {
            val total = file.length()
            var sent = 0L
            var lastReport = 0L
            file.source().use { source ->
                val buffer = okio.Buffer()
                while (true) {
                    val read = source.read(buffer, 64 * 1024L)
                    if (read == -1L) break
                    sink.write(buffer, read)
                    sent += read
                    if (sent - lastReport >= PROGRESS_TICK) {
                        lastReport = sent
                        try {
                            onProgress(sent, total)
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
        }
    }
}
