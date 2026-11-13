package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LocalConversationRepository(
    private val database: AppDatabase
) : ConversationRepository {

    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()

    override fun getConversations(): Flow<List<ConversationEntity>> {
        return conversationDao.getAllConversations()
    }

    override suspend fun getConversation(id: String): ConversationEntity? = withContext(Dispatchers.IO) {
        conversationDao.getConversationById(id)
    }

    override suspend fun createOrUpdateConversation(conversation: ConversationEntity) = withContext(Dispatchers.IO) {
        conversationDao.insertOrUpdate(conversation)
    }

    override suspend fun renameConversation(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        conversationDao.renameConversation(id, newTitle)
    }

    override suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        conversationDao.deleteConversationById(id)
    }

    override suspend fun clearAllConversations() = withContext(Dispatchers.IO) {
        conversationDao.deleteAllConversations()
    }

    override fun getMessages(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForConversation(conversationId)
    }

    override suspend fun getMessagesList(conversationId: String): List<MessageEntity> = withContext(Dispatchers.IO) {
        messageDao.getMessagesList(conversationId)
    }

    override suspend fun saveMessage(message: MessageEntity) = withContext(Dispatchers.IO) {
        messageDao.insertMessage(message)
    }

    override suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        messageDao.deleteMessageById(messageId)
    }

    override suspend fun deleteMessagesFrom(conversationId: String, fromTimestamp: Long) = withContext(Dispatchers.IO) {
        messageDao.deleteMessagesFromTimestamp(conversationId, fromTimestamp)
    }
}
