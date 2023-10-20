package com.sdk.growthbook.features

import com.sdk.growthbook.Utils.FeatureRefreshStrategy

internal class FeatureURLBuilder {

    companion object {
        /**
         * Context Path for Fetching Feature Details - Web Service
         */
        private const val featurePath = "api/features"
        private const val eventsPath = "sub/"
    }

    fun buildUrl(
        baseUrl: String,
        apiKey: String,
        featureRefreshStrategy: FeatureRefreshStrategy = FeatureRefreshStrategy.STALE_WHILE_REVALIDATE
    ): String {
        val endpointPath =
            if (featureRefreshStrategy == FeatureRefreshStrategy.SERVER_SENT_EVENTS) eventsPath else featurePath

        val baseUrlWithFeaturePath = if (baseUrl.endsWith('/'))
            "$baseUrl$endpointPath"
        else
            "$baseUrl/$endpointPath"

        return "$baseUrlWithFeaturePath/$apiKey"
    }
}
