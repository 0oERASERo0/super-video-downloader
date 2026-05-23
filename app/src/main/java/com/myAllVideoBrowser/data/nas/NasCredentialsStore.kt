package com.myAllVideoBrowser.data.nas

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.myAllVideoBrowser.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NasCredentialsStore @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val PREF_NAME = "nas_secure_prefs"
        private const val KEY_TYPE = "nas_type"
        private const val KEY_HOST = "nas_host"
        private const val KEY_PORT = "nas_port"
        private const val KEY_USERNAME = "nas_username"
        private const val KEY_PASSWORD = "nas_password"
        private const val KEY_REMOTE_PATH = "nas_remote_path"
        private const val KEY_SHARE = "nas_share"
        private const val KEY_TLS = "nas_tls"
        private const val KEY_DELETE_LOCAL = "nas_delete_local_after_upload"
    }

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            AppLogger.e("EncryptedSharedPreferences init failed, falling back to plain prefs: ${e.message}")
            context.getSharedPreferences(PREF_NAME + "_fallback", Context.MODE_PRIVATE)
        }
    }

    fun load(): NasConfig {
        val typeStr = prefs.getString(KEY_TYPE, NasDestinationType.LOCAL.name)
            ?: NasDestinationType.LOCAL.name
        val type = try {
            NasDestinationType.valueOf(typeStr)
        } catch (e: IllegalArgumentException) {
            NasDestinationType.LOCAL
        }
        return NasConfig(
            type = type,
            host = prefs.getString(KEY_HOST, "") ?: "",
            port = prefs.getInt(KEY_PORT, 0),
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            password = prefs.getString(KEY_PASSWORD, "") ?: "",
            remotePath = prefs.getString(KEY_REMOTE_PATH, "") ?: "",
            shareName = prefs.getString(KEY_SHARE, "") ?: "",
            useTls = prefs.getBoolean(KEY_TLS, false),
            deleteLocalAfterUpload = prefs.getBoolean(KEY_DELETE_LOCAL, false),
        )
    }

    fun save(config: NasConfig) {
        prefs.edit()
            .putString(KEY_TYPE, config.type.name)
            .putString(KEY_HOST, config.host.trim())
            .putInt(KEY_PORT, config.port)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .putString(KEY_REMOTE_PATH, config.remotePath.trim())
            .putString(KEY_SHARE, config.shareName.trim())
            .putBoolean(KEY_TLS, config.useTls)
            .putBoolean(KEY_DELETE_LOCAL, config.deleteLocalAfterUpload)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
