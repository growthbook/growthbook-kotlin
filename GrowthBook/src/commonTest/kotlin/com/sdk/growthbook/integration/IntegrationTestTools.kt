package com.sdk.growthbook.integration

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.tests.MockNetworkClient

internal fun buildSDK(json: String, attributes: Map<String, Any> = mapOf()): GrowthBookSDK {
    return GBSDKBuilder(
        "some_key",
        "http://host.com",
        attributes = attributes,
        encryptionKey = "",
        trackingCallback = { _, _ -> },
        networkDispatcher = MockNetworkClient(
            json,
            null
        )
    ).initialize()
}
