package com.sdk.growthbook.integration

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GBTrackingCallback
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.tests.MockNetworkClient

internal fun buildSDK(
    json: String,
    attributes: Map<String, Any> = emptyMap(),
    trackingCallback: GBTrackingCallback = { _, _ -> },
): GrowthBookSDK {
    return GBSDKBuilder(
        "some_key",
        "http://host.com",
        attributes = attributes,
        encryptionKey = "",
        trackingCallback = trackingCallback,
        networkDispatcher = MockNetworkClient(
            json,
            null
        ),
        remoteEval = false,
    ).initialize()
}
