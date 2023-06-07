package com.sdk.growthbook.features

internal class FeatureURLBuilder {

    companion object {
        /**
         * Context Path for Fetching Feature Details - Web Service
         */
        private const val featurePath = "api/features"
    }

    fun buildUrl(baseUrl: String, apiKey: String): String {
        val baseUrlWithFeaturePath = if (baseUrl.endsWith('/'))
            "$baseUrl$featurePath"
        else
            "$baseUrl/$featurePath"

        return "$baseUrlWithFeaturePath/$apiKey"
    }
}
