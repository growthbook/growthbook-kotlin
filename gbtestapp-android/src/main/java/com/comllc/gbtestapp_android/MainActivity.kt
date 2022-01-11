package com.comllc.gbtestapp_android

import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.comllc.growthbook.GBSDKBuilder

import com.comllc.growthbook.GrowthBookSDK
import com.comllc.growthbook.model.GBContext


/*
We use custom dimensions to pull experiment results. The value of the dimension must be in the format: [experiment][delimiter][variation]. For example, button-colors:blue.
 */

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        GBSDKBuilder(apiKey = "key_5d5f97321c8d5e59",
            hostURL = "https://cdn.growthbook.io/",
            attributes = HashMap(),
            appInstance = this.applicationContext as ContextWrapper,
            trackingCallback = { gbExperiment, gbExperimentResult ->

        }).initialize()

    }
}