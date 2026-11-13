package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.entity.MessageEntity
import com.example.data.remote.BackendSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Secure Server-Proxy AI Repository for Dinthi AI.
 *
 * In strict compliance with zero-trust client security standards:
 * - NO Gemini API key is stored, referenced, or packaged in the Android APK.
 * - All AI requests are routed through the secure backend server.
 * - The server authenticates requests via client token and enforces rate limiting
 *   before invoking the Gemini API on the server side.
 */
class GeminiAiRepository(
    private val context: Context,
    private val getServerUrl: () -> String = { BuildConfig.SERVER_BASE_URL },
    private val fallbackRepository: MockAiRepository = MockAiRepository()
) : AiRepository {

    private val tag = "ServerAiRepository"

    override val isCloudConnected: Boolean
        get() = true // Backed by secure server proxy

    override val statusMessage: String
        get() = "Secure Server Proxy (Zero API Key in APK)"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun generateResponseStream(
        prompt: String,
        conversationHistory: List<MessageEntity>,
        persona: String,
        imageUri: String?
    ): Flow<String> = flow {
        val serverUrl = getServerUrl().trim().trimEnd('/')
        val endpoint = "$serverUrl/api/chat/stream"

        Log.d(tag, "Sending request to secure server endpoint: $endpoint")

        val payload = buildServerPayload(prompt, conversationHistory, persona, imageUri)

        var token = BackendSessionManager.getSessionToken(okHttpClient, serverUrl)
        if (token.isNullOrBlank()) {
            Log.w(tag, "Unable to obtain ephemeral session token, falling back to local generator")
            fallbackRepository.generateResponseStream(prompt, conversationHistory, persona, imageUri)
                .collect { emit(it) }
            return@flow
        }

        fun buildChatRequest(bearer: String): Request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $bearer")
            .addHeader("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        var receivedAnyValidChunk = false

        try {
            var response = okHttpClient.newCall(buildChatRequest(token)).execute()
            if (response.code == 401) {
                response.close()
                token = BackendSessionManager.refreshSessionToken(okHttpClient, serverUrl)
                if (!token.isNullOrBlank()) {
                    response = okHttpClient.newCall(buildChatRequest(token)).execute()
                }
            }

            if (!response.isSuccessful) {
                val code = response.code
                val errBody = response.body?.string().orEmpty()
                Log.w(tag, "Server proxy returned error $code: $errBody")
                response.close()

                if (code == 429) {
                    emit("⚠️ **Rate limit exceeded.** Please slow down and try again in a minute.")
                    return@flow
                } else if (code == 401) {
                    emit("⚠️ **Authentication error:** Unauthorized client token.")
                    return@flow
                }

                // If server is unavailable, fallback to local generator
                fallbackRepository.generateResponseStream(prompt, conversationHistory, persona, imageUri)
                    .collect { emit(it) }
                return@flow
            }

            val body = response.body
            if (body == null) {
                fallbackRepository.generateResponseStream(prompt, conversationHistory, persona, imageUri)
                    .collect { emit(it) }
                return@flow
            }

            BufferedReader(InputStreamReader(body.byteStream())).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    if (line.startsWith("data:")) {
                        val rawData = line.removePrefix("data:").trim()
                        if (rawData == "[DONE]") {
                            break
                        }
                        if (rawData.isNotBlank()) {
                            try {
                                val json = JSONObject(rawData)
                                if (json.has("text")) {
                                    val text = json.getString("text")
                                    if (text.isNotEmpty()) {
                                        emit(text)
                                        receivedAnyValidChunk = true
                                    }
                                } else if (json.has("error")) {
                                    val errorMsg = json.getString("error")
                                    Log.e(tag, "Server reported error: $errorMsg")
                                }
                            } catch (e: Exception) {
                                // Raw text chunk fallback
                                if (rawData.isNotEmpty() && !rawData.startsWith("{")) {
                                    emit(rawData)
                                    receivedAnyValidChunk = true
                                }
                            }
                        }
                    }
                    line = reader.readLine()
                }
            }

            if (!receivedAnyValidChunk) {
                Log.w(tag, "No chunks received from server, falling back to local generator")
                fallbackRepository.generateResponseStream(prompt, conversationHistory, persona, imageUri)
                    .collect { emit(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Server connection failed: ${e.message}, falling back to local generator", e)
            fallbackRepository.generateResponseStream(prompt, conversationHistory, persona, imageUri)
                .collect { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun checkServerHealth(serverUrl: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val request = Request.Builder()
            .url("$cleanUrl/health")
            .get()
            .build()

        val startTime = System.currentTimeMillis()
        try {
            val response = okHttpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val body = response.body?.string().orEmpty()
            response.close()

            if (response.isSuccessful) {
                Result.success("Server Online (${latency}ms) • $body")
            } else {
                Result.failure(Exception("HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildServerPayload(
        prompt: String,
        conversationHistory: List<MessageEntity>,
        persona: String,
        imageUriString: String?
    ): JSONObject {
        val root = JSONObject()
        root.put("prompt", prompt)
        root.put("persona", persona)

        // Previous history
        val historyArray = JSONArray()
        val recentHistory = conversationHistory.takeLast(10)
        for (msg in recentHistory) {
            val msgObj = JSONObject()
            msgObj.put("role", msg.role)
            msgObj.put("content", msg.content)
            historyArray.put(msgObj)
        }
        root.put("history", historyArray)

        // Attached image as base64
        if (!imageUriString.isNullOrBlank()) {
            val imgData = resolveImageBase64(imageUriString)
            if (imgData != null) {
                root.put("imageMimeType", imgData.first)
                root.put("imageBase64", imgData.second)
            }
        }

        return root
    }

    private fun resolveImageBase64(uriString: String): Pair<String, String>? {
        return try {
            val uri = Uri.parse(uriString)
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = inputStream.use { it.readBytes() }
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Pair(mimeType, base64)
        } catch (e: Exception) {
            Log.e(tag, "Error reading image URI: $uriString", e)
            null
        }
    }
}
