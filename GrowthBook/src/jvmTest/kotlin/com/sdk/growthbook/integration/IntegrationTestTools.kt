package com.sdk.growthbook.integration

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GBTrackingCallback
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.tests.MockNetworkClient
import com.sdk.growthbook.network.NetworkDispatcher

internal fun buildSDK(
    json: String,
    attributes: Map<String, GBValue> = emptyMap(),
    networkDispatcher: NetworkDispatcher = MockNetworkClient(
        json, null
    ),
    trackingCallback: GBTrackingCallback = { _, _ -> },
): GrowthBookSDK {
    return GBSDKBuilder(
        "some_key",
        "http://host.com",
        attributes = attributes,
        remoteEval = false,
        encryptionKey = "",
        trackingCallback = trackingCallback,
        networkDispatcher = networkDispatcher,
    ).initialize()
}
