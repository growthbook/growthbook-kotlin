package com.comllc.gbtestapp_android

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import com.comllc.growthbook.GrowthBookSDK
import com.comllc.growthbook.model.GBContext

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val gbContext = GBContext()
    }
}