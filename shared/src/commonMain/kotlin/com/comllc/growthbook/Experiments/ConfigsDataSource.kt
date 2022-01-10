package com.comllc.growthbook.Experiments

import com.comllc.growthbook.ApplicationDispatcher
import com.comllc.growthbook.Configurations.Constants
import com.comllc.growthbook.Network.CoreNetworkClient
import io.ktor.client.request.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ConfigsDataSource {

    private val apiUrl = Constants.serverURL + Constants.configPath + Constants.apiKey

    // 4
    fun fetchConfig(
        success: (List<ConfigsDataModel>) -> Unit, failure: (Throwable?) -> Unit) {
        // 5
        GlobalScope.launch(ApplicationDispatcher) {
            try {

                // 6
                val json = CoreNetworkClient().client.get<List<ConfigsDataModel>>(apiUrl)

                print(json)
                json.also(success)
            } catch (ex: Exception) {
                failure(ex)
            }
        }
    }

}