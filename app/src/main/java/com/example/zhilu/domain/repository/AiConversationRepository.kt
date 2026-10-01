package com.example.zhilu.domain.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.AiConversation
import com.example.zhilu.domain.model.AiMessage
import kotlinx.coroutines.flow.Flow

interface AiConversationRepository {
    fun getConversations(): Flow<RepositoryResult<List<AiConversation>>>
    fun getMessages(conversationId: Long): Flow<RepositoryResult<List<AiMessage>>>
    suspend fun createConversation(title: String = "新对话"): RepositoryResult<AiConversation>
    suspend fun updateConversationTitle(conversationId: Long, title: String): RepositoryResult<Unit>
    suspend fun deleteConversation(conversationId: Long): RepositoryResult<Unit>
    suspend fun addMessage(message: AiMessage): RepositoryResult<AiMessage>
}
