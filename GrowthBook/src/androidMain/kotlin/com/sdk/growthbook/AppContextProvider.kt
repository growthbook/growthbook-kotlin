package com.sdk.growthbook

import android.content.Context
import androidx.startup.Initializer
import com.sdk.growthbook.sandbox.CachingAndroid

/**
 * Android App Context Provider
 * KotlinX Implementation to simplify SDK Initialization
 */
class AppContextProvider : Initializer<Context> {
    override fun create(context: Context) : Context {
        CachingAndroid.context = context
        return context
    }
    override fun dependencies(): List<Class<out Initializer<*>>> {
        // No dependencies on other libraries.
        return emptyList()
    }
}
