package com.sdk.growthbook.model

import com.sdk.growthbook.Utils.GBCondition
import kotlinx.serialization.json.*

/*
    Both experiments and features can define targeting conditions using a syntax modeled after MongoDB queries.

    These conditions can have arbitrary nesting levels and evaluating them requires recursion.
    There are a handful of functions to define, and be aware that some of them may reference function definitions further below.
 */

typealias TestedObj = HashMap<String, Any>

enum class GBAttributeType {
    gbString, gbNumber, gbBoolean, gbArray, gbObject, gbNull, gbUndefined, gbUnknown
}

/*
    This function is just a case statement that handles all the possible operators
    There are basic comparison operators in the form attributeValue {op} conditionValue
 */
class GBConditionEvaluator{


    /*
        This is the main function used to evaluate a condition.
     */
    fun evalCondition(attributes : JsonElement, conditionObj : GBCondition) : Boolean {

        if (conditionObj is JsonArray) {
            return false
        } else {

            // If conditionObj has a key $or, return evalOr(attributes, condition["$or"])
            var targetItems = conditionObj.jsonObject["\$or"] as? JsonArray
            if (targetItems != null) {
                return evalOr(attributes, targetItems)
            }

            // If conditionObj has a key $nor, return !evalOr(attributes, condition["$nor"])
            targetItems = conditionObj.jsonObject["\$nor"] as? JsonArray
            if (targetItems != null) {
                return !evalOr(attributes, targetItems)
            }

            // If conditionObj has a key $and, return !evalAnd(attributes, condition["$and"])
            targetItems = conditionObj.jsonObject["\$and"] as? JsonArray
            if (targetItems != null) {
                return evalAnd(attributes, targetItems)
            }

            // If conditionObj has a key $not, return !evalCondition(attributes, condition["$not"])
            targetItems = conditionObj.jsonObject["\$not"] as? JsonArray
            if (targetItems != null) {
                return !evalAnd(attributes, targetItems)
            }

            // Loop through the conditionObj key/value pairs
            for (key in conditionObj.jsonObject.keys) {
                val element = getPath(attributes, key)
                if (element != null){
                    // If evalConditionValue(value, getPath(attributes, key)) is false, break out of loop and return false
                    return evalCondition(attributes, element)
                }
            }

        }

        // Return true
        return true
    }

    /*
        conditionObjs is an array of parsed condition objects

     */
    fun evalOr(attributes : JsonElement, conditionObjs : JsonArray): Boolean {
        // If conditionObjs is empty, return true
        if (conditionObjs.isEmpty()) {
            return true
        } else {
            // Loop through the conditionObjects
            for (item in conditionObjs) {
                // If evalCondition(attributes, conditionObjs[i]) is true, break out of the loop and return true
                if (evalCondition(attributes, item)) {
                    return true
                }
            }
        }
        // Return false
        return false
    }

    /*
        conditionObjs is an array of parsed condition objects

     */
    fun evalAnd(attributes : JsonElement, conditionObjs : JsonArray): Boolean {

        // Loop through the conditionObjects
        for (item in conditionObjs) {
            // If evalCondition(attributes, conditionObjs[i]) is false, break out of the loop and return false
            if (!evalCondition(attributes, item)) {
                return false
            }
        }

        // Return true
        return true
    }


    /*
        This accepts a parsed JSON object as input and returns true if every key in the object starts with $
     */
    fun isOperatorObject(obj : JsonElement) : Boolean {
        var isOperator = true
        if (obj is JsonObject && obj.keys.isNotEmpty()) {
            for (key in obj.keys){
                if (!key.startsWith("$")){
                    isOperator = false
                    break
                }
            }
        } else {
            isOperator = false
        }
        return isOperator
    }

    /*
        This returns the data type of the passed in argument.
     */

    fun getType(obj: JsonElement?) : GBAttributeType {

        val value = obj ?: return GBAttributeType.gbNull

        if (value is JsonPrimitive) {

            val primitiveValue = value.jsonPrimitive

            if (primitiveValue.isString) {
                return GBAttributeType.gbString
            } else if (primitiveValue.content == "true" || primitiveValue.content == "false"){
                return GBAttributeType.gbBoolean
            } else {
                return GBAttributeType.gbNumber
            }

            return GBAttributeType.gbUnknown
        }

        if (value is JsonArray) {
            return GBAttributeType.gbArray
        }

        if (value is JsonObject) {
            return GBAttributeType.gbObject
        }

        return GBAttributeType.gbUnknown
    }


    /*
        Given attributes and a dot-separated path string, return the value at that path (or null/undefined if the path doesn't exist)
     */
    fun getPath(obj: JsonElement, key: String) : JsonElement? {

        val paths = key.split(".")

        var element : JsonElement? = obj

        for (path in paths) {
            if (element == null || element is JsonArray) {
                return null
            }

            element = element.jsonObject[path]
        }

        return null
    }

    fun evalConditionValue(conditionValue : JsonElement, attributeValue : JsonElement) : Boolean {

        // If conditionValue is a string, number, boolean, return true if it's "equal" to attributeValue and false if not.
        if (conditionValue is JsonPrimitive && attributeValue is JsonPrimitive) {
            return conditionValue.content == attributeValue.content
        }

        // If conditionValue is array, return true if it's "equal" - "equal" should do a deep comparison for arrays.
        if (conditionValue is JsonArray && attributeValue is JsonArray && conditionValue.size == attributeValue.size) {

            val conditionArray = Json {  }.decodeFromJsonElement<Array<Any>>(conditionValue)
            val attributeArray = Json {  }.decodeFromJsonElement<Array<Any>>(attributeValue)

            return conditionArray.contentDeepEquals(attributeArray)
        }

        // If conditionValue is an object, loop over each key/value pair:
        if (conditionValue is JsonObject) {

            for (key in  conditionValue.keys) {
                // If evalOperatorCondition(key, attributeValue, value) is false, return false
                if (!evalOperatorCondition(key, attributeValue, conditionValue[key]!!)) {
                    return false
                }
            }

        }

        // Return true
        return true
    }

    /*
        This checks if attributeValue is an array, and if so at least one of the array items must match the condition
     */
    fun elemMatch(condition: JsonElement, attributeValue: JsonElement) : Boolean {

        if (attributeValue is JsonArray) {
            // Loop through items in attributeValue
            for (item in attributeValue) {
                // If isOperatorObject(condition)
                if (isOperatorObject(condition)) {
                    // If evalConditionValue(condition, item), break out of loop and return true
                    if (evalConditionValue(condition, item)) {
                        return true
                    }
                    // Else if evalCondition(item, condition), break out of loop and return true
                    else if (evalCondition(item, condition)) {
                        return true
                    }
                }
            }
        }

        // If attributeValue is not an array, return false
        return false
    }

    /*
        This function is just a case statement that handles all the possible operators
        There are basic comparison operators in the form attributeValue {op} conditionValue
     */
    fun evalOperatorCondition(operator : String, attributeValue : JsonElement, conditionValue : JsonElement) : Boolean {

        if (operator == "\$type") {
            return  getType(attributeValue) == getType(conditionValue)
        }

        if (operator == "\$not") {
            return  !evalConditionValue(conditionValue, attributeValue)
        }

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
                        // Loop through conditionValue array
                            // If none of the elements in the attributeValue array pass evalConditionValue(conditionValue[i], attributeValue[j]), return false
                       for (con in conditionValue) {
                           for (attr in attributeValue) {
                                if (evalConditionValue(con, attr)) {
                                    return true
                                }
                           }
                       }
                        return true
                    } else {
                        // If attributeValue is not an array, return false
                        return false
                    }

                }
            }
        } else if (attributeValue is JsonArray) {

            when (operator) {
                "\$elemMatch" -> {
                    return  elemMatch(conditionValue, attributeValue)
                }
                "\$size" -> {
                    return  conditionValue.jsonPrimitive.content == attributeValue.size.toString()
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
                    val regex = Regex(targetPrimitiveValue)
                    return  regex.matches(sourcePrimitiveValue)
                }
                "\$exists" -> {
                    // TODO Unit Test This
                    if (targetPrimitiveValue == "false" && (sourcePrimitiveValue == "null" || sourcePrimitiveValue == "undefined")){
                        return true
                    } else if (targetPrimitiveValue == "true" && (sourcePrimitiveValue != "null" && sourcePrimitiveValue != "undefined")) {
                        return true
                    }
                }
            }

        }

        return false
    }

}