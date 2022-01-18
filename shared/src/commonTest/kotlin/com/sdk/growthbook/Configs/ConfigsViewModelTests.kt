package com.sdk.growthbook.Configs

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.MockNetworkClient
import com.sdk.growthbook.MockResponse
import com.sdk.growthbook.Utils.GBError
import com.sdk.growthbook.Utils.GBOverrides
import com.sdk.growthbook.model.GBContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ConfigsViewModelTests : ConfigsFlowDelegate {

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
        val viewModel = ConfigsViewModel(this, ConfigsDataSource(MockNetworkClient(MockResponse.successResponse, null)))

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
        val viewModel = ConfigsViewModel(this, ConfigsDataSource(MockNetworkClient(MockResponse.errorResponse, null)))

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