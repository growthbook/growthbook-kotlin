package com.sdk.growthbook.utils

/**
 * Response wrapper for handle success or failed type of result
 */
sealed class Resource<out R> {

    /**
     * Success child of wrapper
     */
    data class Success<out T>(val data: T) : Resource<T>()

    /**
     * Failed child of wrapper
     */
    data class Error(val exception: Exception) : Resource<Nothing>()
}
