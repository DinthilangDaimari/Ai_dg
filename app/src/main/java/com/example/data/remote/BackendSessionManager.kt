package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

/**
 * Ephemeral in-memory backend session token manager.
 * Tokens are issued at runtime by POST /api/auth/session and kept strictly in RAM;
 * zero static secrets or client tokens are stored in BuildConfig, XML, or SharedPreferences.
 */
object BackendSessionManager {

    private const val TAG = "BackendSessionManager"
    private val mutex = Mutex()
    private val processInstallId: String = UUID.randomUUID().toString()

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var expiresAtMillis: Long = 0L

    @Volatile
    private var cachedServerUrl: String = ""

    suspend fun getSessionToken(client: OkHttpClient, serverUrl: String): String? {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val now = System.currentTimeMillis()
        val existing = cachedToken
        if (existing != null && cachedServerUrl == cleanUrl && now < expiresAtMillis - 30_000L) {
            return existing
        }

        return mutex.withLock {
            val recheck = cachedToken
            val checkNow = System.currentTimeMillis()
            if (recheck != null && cachedServerUrl == cleanUrl && checkNow < expiresAtMillis - 30_000L) {
                return@withLock recheck
            }

            requestNewToken(client, cleanUrl)
        }
    }

    suspend fun refreshSessionToken(client: OkHttpClient, serverUrl: String): String? {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        return mutex.withLock {
            cachedToken = null
            expiresAtMillis = 0L
            requestNewToken(client, cleanUrl)
        }
    }

    fun clearSession() {
        cachedToken = null
        expiresAtMillis = 0L
    }

    private fun requestNewToken(client: OkHttpClient, cleanServerUrl: String): String? {
        if (cleanServerUrl.isBlank()) return null
        return try {
            val body = JSONObject().apply {
                put("appId", BuildConfig.APPLICATION_ID)
                put("installId", processInstallId)
            }
            val request = Request.Builder()
                .url("$cleanServerUrl/api/auth/session")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to obtain ephemeral backend token: HTTP ${response.code}")
                    return null
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val token = json.optString("token").takeIf { it.isNotBlank() } ?: return null
                val ttlSeconds = json.optLong("expiresInSeconds", 3600L)
                cachedToken = token
                cachedServerUrl = cleanServerUrl
                expiresAtMillis = System.currentTimeMillis() + (ttlSeconds * 1000L)
                token
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting ephemeral session token: ${e.message}")
            null
        }
    }
}
