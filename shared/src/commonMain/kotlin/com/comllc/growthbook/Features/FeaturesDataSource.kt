package com.comllc.growthbook.Features

import com.comllc.growthbook.ApplicationDispatcher
import com.comllc.growthbook.Configurations.Constants
import com.comllc.growthbook.Network.CoreNetworkClient
import io.ktor.client.request.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class FeaturesDataSource {

    private val apiUrl = Constants.serverURL + Constants.featurePath + Constants.apiKey

    // 4
    fun <T> fetchConfig(
        success: (List<FeaturesDataModel<T>>) -> Unit, failure: (Throwable?) -> Unit) {
        // 5
        GlobalScope.launch(ApplicationDispatcher) {
            try {

                // 6
                val json = CoreNetworkClient().client.get<List<FeaturesDataModel<T>>>(apiUrl)

                print(json)
                json.also(success)
            } catch (ex: Exception) {
                failure(ex)
            }
        }
    }

}