package com.sdk.growthbook

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

        val successResponse = "{\n" +
                "  \"status\": 200,\n" +
                "  \"overrides\": {\n" +
                "    \"onboarding\": {\n" +
                "      \"status\": \"running\",\n" +
                "      \"url\": \"/login\",\n" +
                "      \"coverage\": 1,\n" +
                "      \"weights\": [\n" +
                "        0.25,\n" +
                "        0.25,\n" +
                "        0.25,\n" +
                "        0.25\n" +
                "      ]\n" +
                "    },\n" +
                "    \"qrscan\": {\n" +
                "      \"status\": \"running\",\n" +
                "      \"url\": \"/qrscan\",\n" +
                "      \"coverage\": 1,\n" +
                "      \"weights\": [\n" +
                "        0.34,\n" +
                "        0.33,\n" +
                "        0.33\n" +
                "      ]\n" +
                "    },\n" +
                "    \"editprofile\": {\n" +
                "      \"status\": \"running\",\n" +
                "      \"url\": \"/profile\",\n" +
                "      \"groups\": [\n" +
                "        \"internal\"\n" +
                "      ],\n" +
                "      \"coverage\": 0.5,\n" +
                "      \"weights\": [\n" +
                "        0.34,\n" +
                "        0.33,\n" +
                "        0.33\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                "  \"experiments\": {\n" +
                "    \"exp_19g61nkya8wdf2\": {\n" +
                "      \"trackingKey\": \"onboarding\"\n" +
                "    },\n" +
                "    \"exp_19g61mkycahx9q\": {\n" +
                "      \"trackingKey\": \"qrscan\"\n" +
                "    },\n" +
                "    \"exp_19g61mkycbage4\": {\n" +
                "      \"trackingKey\": \"editprofile\"\n" +
                "    }\n" +
                "  },\n" +
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
    }
}