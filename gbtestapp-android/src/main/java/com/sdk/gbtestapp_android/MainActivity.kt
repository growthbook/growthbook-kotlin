package com.sdk.gbtestapp_android

import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.sdk.growthbook.GBSDKBuilder

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBContext


/*
We use custom dimensions to pull experiment results. The value of the dimension must be in the format: [experiment][delimiter][variation]. For example, button-colors:blue.
 */

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.sdk.gbtestapp_android.R.layout.activity_main)

        GBSDKBuilder(apiKey = "key_5d5f97321c8d5e59",//key_486336ff87c125f4 // key_5d5f97321c8d5e59
            hostURL = "https://cdn.growthbook.io/",
            attributes = HashMap(),
            appInstance = this.applicationContext as ContextWrapper,
            trackingCallback = { gbExperiment, gbExperimentResult ->

        }).initialize()

    }
}