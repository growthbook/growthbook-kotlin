package com.sdk.growthbook.features

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.Network.CoreNetworkClient
import com.sdk.growthbook.Network.NetworkDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * DataSource for Feature API
 */
internal class FeaturesDataSource(val dispatcher: NetworkDispatcher = CoreNetworkClient()) {

    private val JSONParser: Json
        get() = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }

  private val apiUrl =
	FeatureURLBuilder().buildUrl(GrowthBookSDK.gbContext.hostURL, GrowthBookSDK.gbContext.apiKey)

  /**
   * Executes API Call to fetch features
   */
  @DelicateCoroutinesApi
  fun fetchFeatures(
	success: (FeaturesDataModel) -> Unit, failure: (Throwable?) -> Unit
  ) {
	dispatcher.consumeGETRequest(apiUrl,
	  onSuccess = { rawContent ->
		val result: FeaturesDataModel = JSONParser.decodeFromString(rawContent)
		result.also(success)
	  },
	  onError = { apiTimeError ->
		apiTimeError.also(failure)
	  })
  }

}