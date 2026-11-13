package com.example.data.repository

import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun getConversations(): Flow<List<ConversationEntity>>
    suspend fun getConversation(id: String): ConversationEntity?
    suspend fun createOrUpdateConversation(conversation: ConversationEntity)
    suspend fun renameConversation(id: String, newTitle: String)
    suspend fun deleteConversation(id: String)
    suspend fun clearAllConversations()

    fun getMessages(conversationId: String): Flow<List<MessageEntity>>
    suspend fun getMessagesList(conversationId: String): List<MessageEntity>
    suspend fun saveMessage(message: MessageEntity)
    suspend fun deleteMessage(messageId: String)
    suspend fun deleteMessagesFrom(conversationId: String, fromTimestamp: Long)
}
