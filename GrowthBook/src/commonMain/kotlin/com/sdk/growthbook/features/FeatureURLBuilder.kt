package com.sdk.growthbook.features

import com.sdk.growthbook.model.GBOptions
import com.sdk.growthbook.utils.FeatureRefreshStrategy

internal class FeatureURLBuilder(private val gbOptions: GBOptions) {

    /**
     * Supportive method for build URL dynamically depending on what's strategy user has chosen
     */
    fun buildUrl(
        apiKey: String,
        featureRefreshStrategy: FeatureRefreshStrategy = FeatureRefreshStrategy.STALE_WHILE_REVALIDATE
    ): String {
        val baseUrl: String = if (featureRefreshStrategy == FeatureRefreshStrategy.SERVER_SENT_EVENTS) {
            gbOptions.streamingHost ?: DEFAULT_STREAMING_HOST
        } else {
            gbOptions.apiHost
        }

        val endpointPath = when (featureRefreshStrategy) {
            FeatureRefreshStrategy.STALE_WHILE_REVALIDATE -> FEATURE_PATH
            FeatureRefreshStrategy.SERVER_SENT_EVENTS -> EVENTS_PATH
            FeatureRefreshStrategy.SERVER_SENT_REMOTE_FEATURE_EVAL -> REMOTE_FEATURE_PATH
        }

        /**
         * Validate url for setting "/" to result
         */
        val baseUrlWithFeaturePath = if (baseUrl.endsWith('/'))
            "$baseUrl$endpointPath"
        else
            "$baseUrl/$endpointPath"

        return "$baseUrlWithFeaturePath/$apiKey"
    }

    companion object {
        /**
         * Context Path for Fetching Feature Details - Web Service
         */
        private const val FEATURE_PATH = "api/features"
        private const val EVENTS_PATH = "sub"
        private const val REMOTE_FEATURE_PATH = "api/eval"

        private const val DEFAULT_STREAMING_HOST = "https://cdn.growthbook.io"
    }
}
