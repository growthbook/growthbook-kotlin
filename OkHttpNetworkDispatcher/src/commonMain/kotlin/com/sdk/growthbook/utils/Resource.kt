package com.sdk.growthbook.utils

sealed class Resource<out R> {

    /**
     * Success child of wrapper
     */
    data class Success<out T>(val data: T) : Resource<T>()

    /**
     * Failed child of wrapper
     */
    data class Error(val exception: Throwable) : Resource<Nothing>()
}
