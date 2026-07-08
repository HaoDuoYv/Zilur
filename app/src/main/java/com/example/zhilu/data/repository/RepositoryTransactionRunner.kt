package com.example.zhilu.data.repository

import androidx.room.withTransaction
import com.example.zhilu.data.local.database.AppDatabase

interface RepositoryTransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}

object NoOpRepositoryTransactionRunner : RepositoryTransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
}

class RoomRepositoryTransactionRunner(
    private val database: AppDatabase
) : RepositoryTransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T =
        database.withTransaction { block() }
}
