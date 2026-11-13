package com.example.data.repository

import com.example.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

interface AiRepository {
    val isCloudConnected: Boolean
    val statusMessage: String

    fun generateResponseStream(
        prompt: String,
        conversationHistory: List<MessageEntity>,
        persona: String = "Balanced",
        imageUri: String? = null
    ): Flow<String>
}
