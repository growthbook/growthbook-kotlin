package com.sdk.growthbook.Configs

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.MockNetworkClient
import com.sdk.growthbook.Utils.GBError
import com.sdk.growthbook.Utils.GBOverrides
import com.sdk.growthbook.model.GBContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ConfigsViewModelTests : ConfigsFlowDelegate {

    val successResponse = "{\n" +
            "  \"status\": 200,\n" +
            "  \"overrides\": {\n" +
            "    \"123\": {\n" +
            "      \"status\": \"draft\"\n" +
            "    },\n" +
            "    \"website-hero\": {\n" +
            "      \"status\": \"draft\",\n" +
            "      \"url\": \"https://www.growthbook.io/solutions/for-engineers\"\n" +
            "    },\n" +
            "    \"copy-asanas-pricing-page\": {\n" +
            "      \"status\": \"draft\",\n" +
            "      \"url\": \"^/pricing/\"\n" +
            "    },\n" +
            "    \"new-idea\": {\n" +
            "      \"status\": \"draft\"\n" +
            "    },\n" +
            "    \"green_buttons\": {\n" +
            "      \"status\": \"stopped\",\n" +
            "      \"coverage\": 1,\n" +
            "      \"weights\": [\n" +
            "        0.5,\n" +
            "        0.5\n" +
            "      ],\n" +
            "      \"force\": 1\n" +
            "    },\n" +
            "    \"simple_registration\": {\n" +
            "      \"status\": \"running\",\n" +
            "      \"coverage\": 1,\n" +
            "      \"weights\": [\n" +
            "        0.5,\n" +
            "        0.5\n" +
            "      ]\n" +
            "    },\n" +
            "    \"purchase_cta\": {\n" +
            "      \"status\": \"running\",\n" +
            "      \"coverage\": 1,\n" +
            "      \"weights\": [\n" +
            "        0.5,\n" +
            "        0.5\n" +
            "      ]\n" +
            "    },\n" +
            "    \"sales-page-test\": {\n" +
            "      \"status\": \"running\",\n" +
            "      \"url\": \".*\",\n" +
            "      \"coverage\": 1,\n" +
            "      \"weights\": [\n" +
            "        0.5,\n" +
            "        0.5\n" +
            "      ]\n" +
            "    },\n" +
            "    \"add-user-reports\": {\n" +
            "      \"status\": \"draft\"\n" +
            "    }\n" +
            "  },\n" +
            "  \"experiments\": {\n" +
            "    \"exp_3zpu09rklcgteje\": {\n" +
            "      \"trackingKey\": \"website-hero\"\n" +
            "    },\n" +
            "    \"exp_19g613ktbh2dfz\": {\n" +
            "      \"trackingKey\": \"copy-asanas-pricing-page\"\n" +
            "    },\n" +
            "    \"exp_19g613ktblmiqj\": {\n" +
            "      \"trackingKey\": \"123\"\n" +
            "    },\n" +
            "    \"exp_19g614ktbpcn7y\": {\n" +
            "      \"trackingKey\": \"new-idea\"\n" +
            "    },\n" +
            "    \"exp_19g613kttbaoo1\": {\n" +
            "      \"trackingKey\": \"green_buttons\"\n" +
            "    },\n" +
            "    \"exp_19g613ktu5iu8b\": {\n" +
            "      \"trackingKey\": \"simple_registration\"\n" +
            "    },\n" +
            "    \"exp_19g613ktu5y9ms\": {\n" +
            "      \"trackingKey\": \"purchase_cta\"\n" +
            "    },\n" +
            "    \"exp_19g613kw5vcyqz\": {\n" +
            "      \"trackingKey\": \"sales-page-test\"\n" +
            "    },\n" +
            "    \"exp_19g613kxahs5sb\": {\n" +
            "      \"trackingKey\": \"add-user-reports\"\n" +
            "    }\n" +
            "  }\n" +
            "}"

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
        val viewModel = ConfigsViewModel(this, ConfigsDataSource(MockNetworkClient(successResponse, null)))

        viewModel.fetchConfigs()

        assertTrue(isSuccess)
        assertTrue(!isError)

    }

    @Test
    fun testError() {

        isSuccess = false
        isError = true
        val viewModel = ConfigsViewModel(this, ConfigsDataSource(MockNetworkClient(null, Throwable("UNKNOWN", null))))

        viewModel.fetchConfigs()

        assertTrue(!isSuccess)
        assertTrue(isError)

    }

    @Test
    fun testInvalid() {

        isSuccess = false
        isError = true
        val viewModel = ConfigsViewModel(this, ConfigsDataSource(MockNetworkClient("{}", null)))

        viewModel.fetchConfigs()

        assertTrue(!isSuccess)
        assertTrue(isError)

    }

    override fun configsFetchedSuccessfully(configs: GBOverrides, isRemote: Boolean) {
        isSuccess = true
        isError = false
    }

    override fun configsFetchFailed(error: GBError, isRemote: Boolean) {
        isSuccess = false
        isError = true
    }

}