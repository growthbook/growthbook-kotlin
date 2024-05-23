package com.sdk.growthbook

import android.content.Context
import androidx.startup.Initializer
import com.sdk.growthbook.sandbox.CachingAndroid

/**
 * Android App Context Provider
 * KotlinX Implementation to simplify SDK Initialization
 */
internal class AppContextProvider : Initializer<Context> {
    override fun create(context: Context) : Context {
        CachingAndroid.consumeContext(context)
        return context
    }
    override fun dependencies(): List<Class<out Initializer<*>>> {
        // No dependencies on other libraries.
        return emptyList()
    }
}
