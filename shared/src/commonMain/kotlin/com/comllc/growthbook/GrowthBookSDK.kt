package com.comllc.growthbook

class GrowthSDKBuilder {

    private lateinit var url: String
    private lateinit var key: String



    fun build() {

    }
}

private class GrowthBookSDK {

    companion object {
        val sharedInstance : GrowthBookSDK = GrowthBookSDK()

        fun initialize(url : String, key: String) {
            sharedInstance.baseURL = url
            sharedInstance.apiKey = key
        }

        fun baseURL() : String {
            return sharedInstance.baseURL
        }

        fun apiKey() : String {
            return sharedInstance.apiKey
        }
    }

    private lateinit var baseURL : String
    private lateinit var apiKey : String
}