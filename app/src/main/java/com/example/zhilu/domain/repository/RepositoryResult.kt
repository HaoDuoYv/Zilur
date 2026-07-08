package com.example.zhilu.common

sealed class RepositoryResult<out T> {
    data class Success<out T>(val data: T) : RepositoryResult<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : RepositoryResult<Nothing>()
}
