package com.example.zhilu.data.repository

import com.example.zhilu.common.RepositoryResult

internal fun <T> Result<T>.toRepositoryResult(message: String): RepositoryResult<T> =
    fold(
        onSuccess = { RepositoryResult.Success(it) },
        onFailure = { RepositoryResult.Error(message, it) }
    )
