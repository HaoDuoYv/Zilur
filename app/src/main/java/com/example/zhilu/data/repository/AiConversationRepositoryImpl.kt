package com.example.zhilu.data.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.local.dao.AiConversationDao
import com.example.zhilu.data.local.dao.AiMessageDao
import com.example.zhilu.data.local.entity.AiConversationEntity
import com.example.zhilu.data.local.entity.AiMessageEntity
import com.example.zhilu.data.local.mapper.AiMapper
import com.example.zhilu.domain.model.AiConversation
import com.example.zhilu.domain.model.AiMessage
import com.example.zhilu.domain.repository.AiConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class AiConversationRepositoryImpl(
    private val conversationDao: AiConversationDao,
    private val messageDao: AiMessageDao
) : AiConversationRepository {

    override fun getConversations(): Flow<RepositoryResult<List<AiConversation>>> =
        conversationDao.getAll()
            .map<List<AiConversationEntity>, RepositoryResult<List<AiConversation>>> { list ->
                RepositoryResult.Success(list.map(AiMapper::toDomain))
            }
            .catch { e -> emit(RepositoryResult.Error("Failed to load conversations", e)) }

    override fun getMessages(conversationId: Long): Flow<RepositoryResult<List<AiMessage>>> =
        messageDao.getByConversation(conversationId)
            .map<List<AiMessageEntity>, RepositoryResult<List<AiMessage>>> { list ->
                RepositoryResult.Success(list.map(AiMapper::toDomain))
            }
            .catch { e -> emit(RepositoryResult.Error("Failed to load messages", e)) }

    override suspend fun createConversation(title: String): RepositoryResult<AiConversation> =
        runCatching {
            val now = System.currentTimeMillis()
            val id = conversationDao.insert(
                AiConversationEntity(title = title, createdAt = now, updatedAt = now)
            )
            AiMapper.toDomain(conversationDao.getById(id)!!)
        }.toRepositoryResult("Failed to create conversation")

    override suspend fun updateConversationTitle(conversationId: Long, title: String): RepositoryResult<Unit> =
        runCatching {
            conversationDao.updateTitle(conversationId, title, System.currentTimeMillis())
        }.toRepositoryResult("Failed to update conversation title")

    override suspend fun deleteConversation(conversationId: Long): RepositoryResult<Unit> =
        runCatching {
            conversationDao.deleteById(conversationId)
        }.toRepositoryResult("Failed to delete conversation")

    override suspend fun addMessage(message: AiMessage): RepositoryResult<AiMessage> =
        runCatching {
            val id = messageDao.insert(AiMapper.toEntity(message))
            conversationDao.touch(message.conversationId, System.currentTimeMillis())
            message.copy(id = id)
        }.toRepositoryResult("Failed to save message")
}
