package com.sdk.growthbook.features

import com.sdk.growthbook.model.GBContext
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

  fun buildUrl(gBContext: GBContext): String {
	return URLBuilder(url = Url(gBContext.hostURL))
	  .pathComponents(featurePath, gBContext.apiKey)
	  .buildString()
  }
}