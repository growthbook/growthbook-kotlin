package com.sdk.growthbook.sandbox

import kotlinx.serialization.Serializable

internal class CachingManager(val context : SandboxFileManager) {

    inline fun <reified T> saveContent(fileName: String, content: @Serializable T){
        context.saveSandbox(fileName, content)
    }

    inline fun <reified T> getContent(fileName: String) : @Serializable T?{
        return context.getSandbox(fileName)
    }

    fun deleteContent(fileName: String){
        context.deleteSandbox(fileName)
    }
}