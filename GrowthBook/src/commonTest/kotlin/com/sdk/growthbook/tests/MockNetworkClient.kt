package com.sdk.growthbook.tests

import com.sdk.growthbook.Network.NetworkDispatcher

class MockNetworkClient (val succesResponse : String?, val error: Throwable?) : NetworkDispatcher {
    override fun consumeGETRequest(
        request: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {

        try {
            if (succesResponse != null) {
                onSuccess(succesResponse)
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

        val errorResponse = "{}"

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
    }
}