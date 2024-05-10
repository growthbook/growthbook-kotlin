package com.sdk.growthbook.features

import com.sdk.growthbook.utils.FeatureRefreshStrategy

internal class FeatureURLBuilder {

    companion object {
        /**
         * Context Path for Fetching Feature Details - Web Service
         */
        private const val FEATURE_PATH = "api/features"
        private const val EVENTS_PATH = "sub/"
        private const val REMOTE_FEATURE_PATH = "api/eval"
    }

    /**
     * Supportive method for build URL dynamically depending on what's strategy user has chosen
     */
    fun buildUrl(
        baseUrl: String,
        apiKey: String,
        featureRefreshStrategy: FeatureRefreshStrategy = FeatureRefreshStrategy.STALE_WHILE_REVALIDATE
    ): String {
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
}
