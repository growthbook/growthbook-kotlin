package com.sdk.growthbook.Configs

import com.sdk.growthbook.Features.FeaturesDataSource
import com.sdk.growthbook.Features.FeaturesFlowDelegate
import com.sdk.growthbook.Features.FeaturesViewModel
import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.MockNetworkClient
import com.sdk.growthbook.Utils.GBError
import com.sdk.growthbook.Utils.GBFeatures
import com.sdk.growthbook.Utils.GBOverrides
import com.sdk.growthbook.model.GBContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class FeaturesViewModelTests : FeaturesFlowDelegate {

    val successResponse = "{\n" +
            "  \"status\": 200,\n" +
            "  \"features\": {\n" +
            "    \"onboarding\": {\n" +
            "      \"defaultValue\": \"top\",\n" +
            "      \"rules\": [\n" +
            "        {\n" +
            "          \"condition\": {\n" +
            "            \"id\": \"2435245\",\n" +
            "            \"loggedIn\": false\n" +
            "          },\n" +
            "          \"variations\": [\n" +
            "            \"top\",\n" +
            "            \"bottom\",\n" +
            "            \"center\"\n" +
            "          ],\n" +
            "          \"weights\": [\n" +
            "            0.25,\n" +
            "            0.5,\n" +
            "            0.25\n" +
            "          ],\n" +
            "          \"hashAttribute\": \"id\"\n" +
            "        }\n" +
            "      ]\n" +
            "    },\n" +
            "    \"qrscanpayment\": {\n" +
            "      \"defaultValue\": {\n" +
            "        \"scanType\": \"static\"\n" +
            "      },\n" +
            "      \"rules\": [\n" +
            "        {\n" +
            "          \"condition\": {\n" +
            "            \"loggedIn\": true,\n" +
            "            \"employee\": true,\n" +
            "            \"company\": \"merchant\"\n" +
            "          },\n" +
            "          \"variations\": [\n" +
            "            {\n" +
            "              \"scanType\": \"static\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"scanType\": \"dynamic\"\n" +
            "            }\n" +
            "          ],\n" +
            "          \"weights\": [\n" +
            "            0.5,\n" +
            "            0.5\n" +
            "          ],\n" +
            "          \"hashAttribute\": \"id\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"force\": {\n" +
            "            \"scanType\": \"static\"\n" +
            "          },\n" +
            "          \"coverage\": 0.69,\n" +
            "          \"hashAttribute\": \"id\"\n" +
            "        }\n" +
            "      ]\n" +
            "    },\n" +
            "    \"editprofile\": {\n" +
            "      \"defaultValue\": false,\n" +
            "      \"rules\": [\n" +
            "        {\n" +
            "          \"force\": false,\n" +
            "          \"coverage\": 0.67,\n" +
            "          \"hashAttribute\": \"id\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"force\": false\n" +
            "        },\n" +
            "        {\n" +
            "          \"variations\": [\n" +
            "            false,\n" +
            "            true\n" +
            "          ],\n" +
            "          \"weights\": [\n" +
            "            0.5,\n" +
            "            0.5\n" +
            "          ],\n" +
            "          \"key\": \"eduuybkbybk\",\n" +
            "          \"hashAttribute\": \"id\"\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  }\n" +
            "}"

    val errorResponse = "{}"

    var isSuccess : Boolean = false
    var isError : Boolean = false

    @BeforeTest
    fun setUp() {
        GrowthBookSDK.gbContext = GBContext("", true, HashMap(), "", HashMap(), false, { _, _ ->

        })
    }

    @Test
    fun testSuccess() {

        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(this, FeaturesDataSource(MockNetworkClient(successResponse, null)))

        viewModel.fetchFeatures()

        assertTrue(isSuccess)
        assertTrue(!isError)

    }

    @Test
    fun testError() {

        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(this, FeaturesDataSource(MockNetworkClient(null, Throwable("UNKNOWN", null))))

        viewModel.fetchFeatures()

        assertTrue(!isSuccess)
        assertTrue(isError)

    }

    @Test
    fun testInvalid() {

        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(this, FeaturesDataSource(MockNetworkClient(errorResponse, null)))

        viewModel.fetchFeatures()

        assertTrue(!isSuccess)
        assertTrue(isError)

    }

    override fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean) {
        isSuccess = true
        isError = false
    }

    override fun featuresFetchFailed(error: GBError, isRemote: Boolean) {
        isSuccess = false
        isError = true
    }

}