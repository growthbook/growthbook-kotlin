package com.sdk.growthbook.features

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.pathComponents

internal class FeatureURLBuilder {

  companion object {
	/**
	 * Context Path for Fetching Feature Details - Web Service
	 */
	private const val featurePath = "api/features/"
  }

  fun buildUrl(baseUrl: String, apiKey: String): String {
	return URLBuilder(url = Url(baseUrl))
	  .pathComponents(featurePath, apiKey)
	  .buildString()
  }
}