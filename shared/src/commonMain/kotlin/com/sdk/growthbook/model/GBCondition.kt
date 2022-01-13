package com.sdk.growthbook.model

import com.sdk.growthbook.Utils.GBCondition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/*
    TODO - Targeting condition based on MongoDB query syntax.
    For details on parsing and evaluating these conditions, view the reference Typescript implementation
    https://github.com/growthbook/growthbook/tree/main/packages/sdk-js/src/mongrule.ts
 */

typealias TestedObj = HashMap<String, Any>

enum class GBAttributeType {
    gbString, gbNumber, gbBoolean, gbArray, gbObject, gbNull, gbUndefined, gbUnknown
}

/*
    This function is just a case statement that handles all the possible operators
    There are basic comparison operators in the form attributeValue {op} conditionValue
 */
class GBConditionEvaluator(val gbCondition : GBCondition){

    /*
        This accepts a parsed JSON object as input and returns true if every key in the object starts with $
     */
    fun isOperatorObject(obj : JsonElement) : Boolean {
        var isOperator = true

        val hashMap = Json {  }.decodeFromJsonElement<HashMap<String, Any>>(obj)

        for (key in hashMap.keys){
            if (!key.startsWith("$")){
                isOperator = false
                break
            }
        }

        return isOperator
    }

    fun getType(attributeValue: JsonElement) : GBAttributeType {



        return GBAttributeType.gbUndefined
    }

    fun evalOperatorCondition(operator : String, attributeValue : JsonElement, conditionValue : JsonElement) : Boolean {


        /// There are three operators where conditionValue is an array
        if (conditionValue is JsonArray) {
            when (operator) {
                // attributeValue in the conditionValue array
                "\$in" -> {
                    return conditionValue.contains(attributeValue)
                }
                // attributeValue not in the conditionValue array
                "\$nin" -> {
                    return !conditionValue.contains(attributeValue)
                }
                "\$all" -> {

                    if (attributeValue is JsonArray) {
                        //TODO Loop through conditionValue array
                            // If none of the elements in the attributeValue array pass evalConditionValue(conditionValue[i], attributeValue[j]), return false
                        return true
                    } else {
                        // If attributeValue is not an array, return false
                        return false
                    }

                }
            }
        } else if (attributeValue is JsonPrimitive && conditionValue is JsonPrimitive) {
            val targetPrimitiveValue = conditionValue.content
            val sourcePrimitiveValue = attributeValue.content

            when (operator) {
                "\$eq" -> {
                    return  sourcePrimitiveValue == targetPrimitiveValue
                }
                "\$ne" -> {
                    return  sourcePrimitiveValue != targetPrimitiveValue
                }
                "\$lt" -> {
                    return  sourcePrimitiveValue < targetPrimitiveValue
                }
                "\$lte" -> {
                    return  sourcePrimitiveValue <= targetPrimitiveValue
                }
                "\$gt" -> {
                    return  sourcePrimitiveValue > targetPrimitiveValue
                }
                "\$gte" -> {
                    return  sourcePrimitiveValue >= targetPrimitiveValue
                }
                "\$regex" -> {
                    // TODO regex match
                    return  attributeValue == conditionValue
                }
            }

        }

        return false
    }

}