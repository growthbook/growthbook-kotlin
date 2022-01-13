package com.sdk.gbtestapp_android

import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.startup.Initializer
import com.sdk.growthbook.GBSDKBuilder

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.sandbox.CachingAndroid


/*
We use custom dimensions to pull experiment results. The value of the dimension must be in the format: [experiment][delimiter][variation]. For example, button-colors:blue.
 */

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.sdk.gbtestapp_android.R.layout.activity_main)

        CachingAndroid.context = this.applicationContext

        GBSDKBuilder(apiKey = "key_5d5f97321c8d5e59",//key_486336ff87c125f4 // key_5d5f97321c8d5e59
            hostURL = "https://cdn.growthbook.io/",
            attributes = HashMap(),
            trackingCallback = { gbExperiment, gbExperimentResult ->

        }).initialize()

    }
}


// TODO Initializes Context.
class ContextInitializer : Initializer<Context> {
    override fun create(context: Context): Context {
        return context
    }
    override fun dependencies(): List<Class<out Initializer<*>>> {
        // No dependencies on other libraries.
        return emptyList()
    }
}