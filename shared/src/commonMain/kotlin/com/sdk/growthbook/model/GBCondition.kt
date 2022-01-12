package com.sdk.growthbook.model

import kotlinx.serialization.Serializable

/*
    TODO - Targeting condition based on MongoDB query syntax.
    For details on parsing and evaluating these conditions, view the reference Typescript implementation
    https://github.com/growthbook/growthbook/tree/main/packages/sdk-js/src/mongrule.ts
 */

@Serializable
class GBCondition {

}

typealias TestedObj = HashMap<String, Any>