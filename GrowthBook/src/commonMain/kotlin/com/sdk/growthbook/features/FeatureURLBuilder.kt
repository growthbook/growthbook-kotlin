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
            // streamingHost > apiHost > GrowthBook Cloud default, mirroring the TypeScript SDK's
            // getApiHosts(). Falling straight through to the Cloud CDN (the pre-8.1.0 behaviour)
            // sent SSE to cdn.growthbook.io for every self-hosted deployment that had not set an
            // explicit streamingHost, while its features were fetched from apiHost.
            gbOptions.streamingHost?.takeIf { it.isNotBlank() }
                ?: gbOptions.apiHost.takeIf { it.isNotBlank() }
                ?: DEFAULT_STREAMING_HOST
        } else {
            gbOptions.apiHost
        }

        val endpointPath = when (featureRefreshStrategy) {
            FeatureRefreshStrategy.STALE_WHILE_REVALIDATE -> FEATURE_PATH
            FeatureRefreshStrategy.SERVER_SENT_EVENTS -> EVENTS_PATH
            FeatureRefreshStrategy.SERVER_SENT_REMOTE_FEATURE_EVAL -> REMOTE_FEATURE_PATH
        }

        /**
         * Normalise the host so exactly one "/" separates it from the endpoint path, whatever
         * number of trailing slashes the configured host carries (as the TypeScript SDK does).
         */
        val baseUrlWithFeaturePath = "${withScheme(baseUrl).trimEnd('/')}/$endpointPath"

        return "$baseUrlWithFeaturePath/$apiKey"
    }

    /**
     * Prepends `https://` to a scheme-less host, as the Java SDK does. Without this a configured
     * `cdn.example.com` produces a URL every HTTP client rejects ("Expected URL scheme 'http' or
     * 'https'"), which surfaces as an opaque fetch failure rather than a bad-config message.
     *
     * A blank host is returned unchanged, and deliberately so. Adding a scheme to it would yield
     * `https:/api/features/<clientKey>`, which HTTP clients do not reject — OkHttp parses it as the
     * host `api`, which resolves wherever a DNS search domain is configured. An `apiHost` left
     * empty by a build config would then silently ship the client key to an unintended host instead
     * of failing. Left blank, the URL stays relative and the client rejects it, as it did before
     * scheme normalisation existed. [com.sdk.growthbook.utils.GBOptionsValidator] treats a blank
     * host as "not set" rather than a violation, so this is the only guard.
     */
    private fun withScheme(host: String): String {
        val trimmed = host.trim()
        return when {
            trimmed.isEmpty() -> trimmed
            trimmed.contains(SCHEME_SEPARATOR) -> trimmed
            else -> "https://$trimmed"
        }
    }

    companion object {
        /**
         * Context Path for Fetching Feature Details - Web Service
         */
        private const val FEATURE_PATH = "api/features"
        private const val EVENTS_PATH = "sub"
        private const val REMOTE_FEATURE_PATH = "api/eval"

        private const val DEFAULT_STREAMING_HOST = "https://cdn.growthbook.io"

        private const val SCHEME_SEPARATOR = "://"
    }
}
