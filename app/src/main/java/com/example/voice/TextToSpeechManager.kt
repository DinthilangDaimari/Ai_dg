package com.example.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TextToSpeechManager(context: Context) {

    private val tag = "TextToSpeechManager"
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    var speechRate: Float = 1.0f
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    var speechPitch: Float = 1.0f
        set(value) {
            field = value
            tts?.setPitch(value)
        }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(tag, "Default language not supported, falling back to US English")
                    tts?.setLanguage(Locale.US)
                }
                tts?.setSpeechRate(speechRate)
                tts?.setPitch(speechPitch)
                setupProgressListener()
                isInitialized = true
                _isReady.value = true
                Log.d(tag, "TTS initialized successfully")
            } else {
                Log.e(tag, "TTS initialization failed with code $status")
            }
        }
    }

    private val utteranceCallbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speakingMessageId.value = utteranceId
            }

            override fun onDone(utteranceId: String?) {
                if (_speakingMessageId.value == utteranceId) {
                    _speakingMessageId.value = null
                }
                if (utteranceId != null) {
                    utteranceCallbacks.remove(utteranceId)?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (_speakingMessageId.value == utteranceId) {
                    _speakingMessageId.value = null
                }
                if (utteranceId != null) {
                    utteranceCallbacks.remove(utteranceId)
                }
                Log.e(tag, "TTS error speaking utterance $utteranceId")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (_speakingMessageId.value == utteranceId) {
                    _speakingMessageId.value = null
                }
                if (utteranceId != null) {
                    utteranceCallbacks.remove(utteranceId)
                }
                Log.e(tag, "TTS error code $errorCode for utterance $utteranceId")
            }
        })
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val id = java.util.UUID.randomUUID().toString()
        if (onDone != null) {
            utteranceCallbacks[id] = onDone
        }
        speak(text, id)
    }

    fun speak(text: String, messageId: String) {
        if (!isInitialized || tts == null) {
            Log.w(tag, "TTS is not initialized yet")
            return
        }

        // Clean markdown symbols so TTS reads clean speech
        val cleanText = cleanMarkdownForSpeech(text)

        if (_speakingMessageId.value == messageId) {
            // Already speaking this message -> toggle to stop
            stop()
            return
        }

        stop()
        _speakingMessageId.value = messageId
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, messageId)
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping TTS", e)
        }
        _speakingMessageId.value = null
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _isReady.value = false
    }

    private fun cleanMarkdownForSpeech(raw: String): String {
        return raw
            // Remove code blocks
            .replace(Regex("```[a-zA-Z]*\\n[\\s\\S]*?```"), "Code snippet omitted.")
            // Remove headers
            .replace(Regex("#{1,6}\\s*"), "")
            // Remove bold/italics
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("\\*(.*?)\\*"), "$1")
            // Remove inline code
            .replace(Regex("`(.*?)`"), "$1")
            // Remove link markdown
            .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1")
            // Clean emojis / special symbols nicely
            .trim()
    }
}
