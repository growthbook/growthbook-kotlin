package com.sdk.growthbook.tests

import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.utils.Resource
import kotlinx.coroutines.flow.Flow

class MockNetworkClient(
    private val successResponse: String?,
    private val error: Throwable?
) : NetworkDispatcher {

    override fun consumeGETRequest(
        request: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {

        try {
            if (successResponse != null) {
                onSuccess(successResponse)
            } else if (error != null) {
                onError(error)
            }
        } catch (ex: Exception) {
            onError(ex)
        }
    }

    override fun consumeSSEConnection(url: String): Flow<Resource<String>> {
        TODO("Not yet implemented")
    }

    override fun consumePOSTRequest(
        url: String,
        bodyParams: Map<String, Any>,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        try {
            if (successResponse != null) {
                onSuccess(successResponse)
            } else if (error != null) {
                onError(error)
            }
        } catch (ex: Exception) {
            onError(ex)
        }
    }
}

class MockResponse {
    companion object {

        const val ERROR_RESPONSE = "{}"

        val successResponse = """
            {
              "status": 200,
              "features": {
                "onboarding": {
                  "defaultValue": "top",
                  "rules": [
                    {
                      "condition": {
                        "id": "2435245",
                        "loggedIn": false
                      },
                      "variations": [
                        "top",
                        "bottom",
                        "center"
                      ],
                      "weights": [
                        0.25,
                        0.5,
                        0.25
                      ],
                      "hashAttribute": "id"
                    }
                  ]
                },
                "qrscanpayment": {
                  "defaultValue": {
                    "scanType": "static"
                  },
                  "rules": [
                    {
                      "condition": {
                        "loggedIn": true,
                        "employee": true,
                        "company": "merchant"
                      },
                      "variations": [
                        {
                          "scanType": "static"
                        },
                        {
                          "scanType": "dynamic"
                        }
                      ],
                      "weights": [
                        0.5,
                        0.5
                      ],
                      "hashAttribute": "id"
                    },
                    {
                      "force": {
                        "scanType": "static"
                      },
                      "coverage": 0.69,
                      "hashAttribute": "id"
                    }
                  ]
                },
                "editprofile": {
                  "defaultValue": false,
                  "rules": [
                    {
                      "force": false,
                      "coverage": 0.67,
                      "hashAttribute": "id"
                    },
                    {
                      "force": false
                    },
                    {
                      "variations": [
                        false,
                        true
                      ],
                      "weights": [
                        0.5,
                        0.5
                      ],
                      "key": "eduuybkbybk",
                      "hashAttribute": "id"
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val successResponseEncryptedFeatures = """
       {
            "status":200,
            "encryptedFeatures":"uBvUuovxK3mJIhamxLHHRQ==.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",
            "dateUpdated":"2023-01-06T04:49:22.608Z"
        }
    """.trimIndent()
    }
}
