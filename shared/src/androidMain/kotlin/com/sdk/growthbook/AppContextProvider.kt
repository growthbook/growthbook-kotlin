package com.sdk.growthbook

import android.content.Context
import androidx.startup.Initializer
import com.sdk.growthbook.sandbox.CachingAndroid
import java.util.*

// TODO Initializes Context.
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