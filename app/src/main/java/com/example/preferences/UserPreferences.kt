package com.example.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("dinthi_ai_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getSavedThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _aiPersona = MutableStateFlow(prefs.getString("ai_persona", "Balanced") ?: "Balanced")
    val aiPersona: StateFlow<String> = _aiPersona.asStateFlow()

    private val _speechRate = MutableStateFlow(prefs.getFloat("speech_rate", 1.0f))
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _speechPitch = MutableStateFlow(prefs.getFloat("speech_pitch", 1.0f))
    val speechPitch: StateFlow<Float> = _speechPitch.asStateFlow()

    private val defaultServerUrl = BuildConfig.SERVER_BASE_URL
    private val _serverUrl = MutableStateFlow(prefs.getString("server_url", defaultServerUrl) ?: defaultServerUrl)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _isAssistantEnabled = MutableStateFlow(prefs.getBoolean("is_assistant_enabled", true))
    val isAssistantEnabled: StateFlow<Boolean> = _isAssistantEnabled.asStateFlow()

    private val _screeningRule = MutableStateFlow(prefs.getString("screening_rule", "UNKNOWN_AND_SPAM") ?: "UNKNOWN_AND_SPAM")
    val screeningRule: StateFlow<String> = _screeningRule.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(prefs.getString("selected_language", "English (India)") ?: "English (India)")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _voicePersona = MutableStateFlow(prefs.getString("voice_persona", "Professional") ?: "Professional")
    val voicePersona: StateFlow<String> = _voicePersona.asStateFlow()

    private val _autoHangupSpam = MutableStateFlow(prefs.getBoolean("auto_hangup_spam", true))
    val autoHangupSpam: StateFlow<Boolean> = _autoHangupSpam.asStateFlow()

    private val _readReceiptsEnabled = MutableStateFlow(prefs.getBoolean("read_receipts_enabled", true))
    val readReceiptsEnabled: StateFlow<Boolean> = _readReceiptsEnabled.asStateFlow()

    private val _urgentCallNotificationsEnabled = MutableStateFlow(prefs.getBoolean("urgent_notifications_enabled", true))
    val urgentCallNotificationsEnabled: StateFlow<Boolean> = _urgentCallNotificationsEnabled.asStateFlow()

    private val _silentMode = MutableStateFlow(prefs.getBoolean("silent_mode", false))
    val silentMode: StateFlow<Boolean> = _silentMode.asStateFlow()

    private val _demoMode = MutableStateFlow(prefs.getBoolean("demo_mode", false))
    val demoMode: StateFlow<Boolean> = _demoMode.asStateFlow()

    private val defaultInstructions = "1. Ask unknown callers why they are calling.\n2. Tell delivery couriers I am available after 5 PM.\n3. Do not screen calls from my family.\n4. Ask telemarketers to remove my number."
    private val _userInstructions = MutableStateFlow(prefs.getString("user_instructions", defaultInstructions) ?: defaultInstructions)
    val userInstructions: StateFlow<String> = _userInstructions.asStateFlow()

    private val defaultAddresses = "Home: Flat 402, Green Glen Layout, Bellandur, Bengaluru\nOffice: EcoSpace Tech Park, Outer Ring Road, Bengaluru"
    private val _userAddresses = MutableStateFlow(prefs.getString("user_addresses", defaultAddresses) ?: defaultAddresses)
    val userAddresses: StateFlow<String> = _userAddresses.asStateFlow()

    private val _userDisplayName = MutableStateFlow(prefs.getString("user_display_name", "") ?: "")
    val userDisplayName: StateFlow<String> = _userDisplayName.asStateFlow()

    fun setUserDisplayName(name: String) {
        val clean = name.trim()
        prefs.edit().putString("user_display_name", clean).apply()
        _userDisplayName.value = clean
    }

    fun setSilentMode(enabled: Boolean) {
        prefs.edit().putBoolean("silent_mode", enabled).apply()
        _silentMode.value = enabled
    }

    fun setDemoMode(enabled: Boolean) {
        prefs.edit().putBoolean("demo_mode", enabled).apply()
        _demoMode.value = enabled
    }

    fun setUserInstructions(instructions: String) {
        prefs.edit().putString("user_instructions", instructions).apply()
        _userInstructions.value = instructions
    }

    fun setUserAddresses(addresses: String) {
        prefs.edit().putString("user_addresses", addresses).apply()
        _userAddresses.value = addresses
    }

    fun setAssistantEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_assistant_enabled", enabled).apply()
        _isAssistantEnabled.value = enabled
    }

    fun setScreeningRule(rule: String) {
        prefs.edit().putString("screening_rule", rule).apply()
        _screeningRule.value = rule
    }

    fun setSelectedLanguage(language: String) {
        prefs.edit().putString("selected_language", language).apply()
        _selectedLanguage.value = language
    }

    fun setVoicePersona(persona: String) {
        prefs.edit().putString("voice_persona", persona).apply()
        _voicePersona.value = persona
    }

    fun setAutoHangupSpam(enabled: Boolean) {
        prefs.edit().putBoolean("auto_hangup_spam", enabled).apply()
        _autoHangupSpam.value = enabled
    }

    fun setReadReceiptsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("read_receipts_enabled", enabled).apply()
        _readReceiptsEnabled.value = enabled
    }

    fun setUrgentCallNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("urgent_notifications_enabled", enabled).apply()
        _urgentCallNotificationsEnabled.value = enabled
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeMode.value = mode
    }

    fun setAiPersona(persona: String) {
        prefs.edit().putString("ai_persona", persona).apply()
        _aiPersona.value = persona
    }

    fun setSpeechRate(rate: Float) {
        prefs.edit().putFloat("speech_rate", rate).apply()
        _speechRate.value = rate
    }

    fun setSpeechPitch(pitch: Float) {
        prefs.edit().putFloat("speech_pitch", pitch).apply()
        _speechPitch.value = pitch
    }

    fun setServerUrl(url: String) {
        val cleanUrl = url.trim().trimEnd('/')
        prefs.edit().putString("server_url", cleanUrl).apply()
        _serverUrl.value = cleanUrl
    }

    private fun getSavedThemeMode(): ThemeMode {
        val name = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }
}
