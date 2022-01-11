package com.comllc.growthbook.sandbox

import com.comllc.cachinglibrary_kmm.sandbox.SandboxFileManager
import com.comllc.cachinglibrary_kmm.sandbox.deleteSandbox
import com.comllc.cachinglibrary_kmm.sandbox.getSandbox
import com.comllc.cachinglibrary_kmm.sandbox.saveSandbox
import com.comllc.cachinglibrary_kmm.sharedPreferences.SharedPreferencesKMM
import com.comllc.cachinglibrary_kmm.sharedPreferences.deletePref
import com.comllc.cachinglibrary_kmm.sharedPreferences.getPrefValue
import com.comllc.cachinglibrary_kmm.sharedPreferences.setPrefValue
import kotlinx.serialization.Serializable

class CachingManager(val context : SandboxFileManager) {

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