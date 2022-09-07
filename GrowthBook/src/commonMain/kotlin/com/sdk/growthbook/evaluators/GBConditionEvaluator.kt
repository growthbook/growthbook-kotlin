package com.sdk.growthbook.evaluators

import com.sdk.growthbook.utils.GBCondition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Both experiments and features can define targeting conditions using a syntax modeled after MongoDB queries.
 * These conditions can have arbitrary nesting levels and evaluating them requires recursion.
 * There are a handful of functions to define, and be aware that some of them may reference function definitions further below.
 */

/**
 * Enum For different Attribute Types supported by GrowthBook
 */
internal enum class  GBAttributeType {
    /**
     * String Type Attribute
     */
    gbString{
        override fun toString(): String = "string"
    },
    /**
     * Number Type Attribute
     */
    gbNumber{
        override fun toString(): String = "number"
    },
    /**
     * Boolean Type Attribute
     */
    gbBoolean{
        override fun toString(): String = "boolean"
    },
    /**
     * Array Type Attribute
     */
    gbArray{
        override fun toString(): String = "array"
    },
    /**
     * Object Type Attribute
     */
    gbObject{
        override fun toString(): String = "object"
    },
    /**
     * Null Type Attribute
     */
    gbNull{
        override fun toString(): String = "null"
    },
    /**
     * Not Supported Type Attribute
     */
    gbUnknown{
        override fun toString(): String = "unknown"
    }

}

/**
 * Evaluator Class for Conditions
 */
internal class GBConditionEvaluator{


    /**
     * This is the main function used to evaluate a condition.
     * - attributes : User Attributes
     * - condition : to be evaluated
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
            val targetItem = conditionObj.jsonObject["\$not"]
            if (targetItem != null) {
                return !evalCondition(attributes, targetItem)
            }

            // Loop through the conditionObj key/value pairs
            for (key in conditionObj.jsonObject.keys) {
                val element = getPath(attributes, key)
                val value = conditionObj.jsonObject[key]
                if (value != null) {
                    // If evalConditionValue(value, getPath(attributes, key)) is false, break out of loop and return false
                    if (!evalConditionValue(value, element)) {
                        return false
                    }
                }
            }
        }

        // Return true
        return true
    }

    /**
     * Evaluate OR conditions against given attributes
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

    /**
     * Evaluate AND conditions against given attributes
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


    /**
     * This accepts a parsed JSON object as input and returns true if every key in the object starts with $
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

    /**
     * This returns the data type of the passed in argument.
     */
    fun getType(obj: JsonElement?) : GBAttributeType {

        if (obj == JsonNull) {
            return GBAttributeType.gbNull
        }

        val value = obj

        if (value is JsonPrimitive) {

            val primitiveValue = value.jsonPrimitive

            if (primitiveValue.isString) {
                return GBAttributeType.gbString
            } else if (primitiveValue.content == "true" || primitiveValue.content == "false"){
                return GBAttributeType.gbBoolean
            } else {
                return GBAttributeType.gbNumber
            }

        }

        if (value is JsonArray) {
            return GBAttributeType.gbArray
        }

        if (value is JsonObject) {
            return GBAttributeType.gbObject
        }

        return GBAttributeType.gbUnknown
    }

    /**
     * Given attributes and a dot-separated path string,
     * @return the value at that path (or null if the path doesn't exist)
     */
    fun getPath(obj: JsonElement, key: String) : JsonElement? {

        val paths : ArrayList<String>

        if (key.contains(".")) {
            paths = key.split(".") as ArrayList<String>
        } else {
            paths = ArrayList()
            paths.add(key)
        }

        var element : JsonElement? = obj

        for (path in paths) {
            if (element == null || element is JsonArray) {
                return null
            }
            if (element is JsonObject) {
                element = element.get(path)
            } else {
                return null
            }
        }

        return element
    }

    /**
     * Evaluates Condition Value against given condition & attributes
     */
    fun evalConditionValue(conditionValue : JsonElement, attributeValue : JsonElement?) : Boolean {

        // If conditionValue is a string, number, boolean, return true if it's "equal" to attributeValue and false if not.
        if (conditionValue is JsonPrimitive && attributeValue is JsonPrimitive) {
            return conditionValue.content == attributeValue.content
        }

        if (conditionValue is JsonPrimitive && attributeValue == null) {
            return false
        }

        // If conditionValue is array, return true if it's "equal" - "equal" should do a deep comparison for arrays.
        if (conditionValue is JsonArray) {
            if (attributeValue is JsonArray) {
                if (conditionValue.size == attributeValue.size) {
                    val conditionArray = Json.decodeFromJsonElement<Array<JsonElement>>(conditionValue)
                    val attributeArray = Json.decodeFromJsonElement<Array<JsonElement>>(attributeValue)

                    return conditionArray.contentDeepEquals(attributeArray)
                } else {
                    return false
                }
            } else {
                return false
            }
        }

        // If conditionValue is an object, loop over each key/value pair:
        if (conditionValue is JsonObject) {

            if (isOperatorObject(conditionValue)) {
                for (key in  conditionValue.keys) {
                    // If evalOperatorCondition(key, attributeValue, value) is false, return false
                    if (!evalOperatorCondition(key, attributeValue, conditionValue[key]!!)) {
                        return false
                    }
                }
            } else if (attributeValue != null) {
                return conditionValue.equals(attributeValue)
            } else {
                return false
            }
        }

        // Return true
        return true
    }

    /**
     * This checks if attributeValue is an array, and if so at least one of the array items must match the condition
     */
    fun elemMatch(attributeValue: JsonElement, condition: JsonElement) : Boolean {

        if (attributeValue is JsonArray) {
            // Loop through items in attributeValue
            for (item in attributeValue) {
                // If isOperatorObject(condition)
                if (isOperatorObject(condition)) {
                    // If evalConditionValue(condition, item), break out of loop and return true
                    if (evalConditionValue(condition, item)) {
                        return true
                    }
                }
                // Else if evalCondition(item, condition), break out of loop and return true
                else if (evalCondition(item, condition)) {
                    return true
                }
            }
        }

        // If attributeValue is not an array, return false
        return false
    }

    /**
     * This function is just a case statement that handles all the possible operators
     * There are basic comparison operators in the form attributeValue {op} conditionValue
     */
    fun evalOperatorCondition(operator : String, attributeValue : JsonElement?, conditionValue : JsonElement) : Boolean {

        // Evaluate TYPE operator - whether both are of same type
        if (operator == "\$type") {
            return  getType(attributeValue).toString() == conditionValue.jsonPrimitive.content
        }

        // Evaluate NOT operator - whether condition doesn't contain attribute
        if (operator == "\$not") {
            return  !evalConditionValue(conditionValue, attributeValue)
        }

        // Evaluate EXISTS operator - whether condition contains attribute
        if (operator == "\$exists") {
            val targetPrimitiveValue = conditionValue.jsonPrimitive.content
            if (targetPrimitiveValue == "false" && attributeValue == null){
                return true
            } else if (targetPrimitiveValue == "true" && attributeValue != null) {
                return true
            }
        }

        /// There are three operators where conditionValue is an array
        if (conditionValue is JsonArray) {
            when (operator) {
                // Evaluate IN operator - attributeValue in the conditionValue array
                "\$in" -> {
                    return conditionValue.contains(attributeValue)
                }
                // Evaluate NIN operator - attributeValue not in the conditionValue array
                "\$nin" -> {
                    return !conditionValue.contains(attributeValue)
                }
                // Evaluate ALL operator - whether condition contains all attribute
                "\$all" -> {

                    if (attributeValue is JsonArray) {
                        // Loop through conditionValue array
                            // If none of the elements in the attributeValue array pass evalConditionValue(conditionValue[i], attributeValue[j]), return false
                       for (con in conditionValue) {
                           var result = false
                           for (attr in attributeValue) {
                                if (evalConditionValue(con, attr)) {
                                    result = true
                                }
                           }
                           if (!result) {
                               return result
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
                // Evaluate ELEMMATCH operator - whether condition matches attribute
                "\$elemMatch" -> {
                    return  elemMatch(attributeValue, conditionValue)
                }
                // Evaluate SIE operator - whether condition size is same as that of attribute
                "\$size" -> {
                    return evalConditionValue(conditionValue, JsonPrimitive(attributeValue.size))
                }
            }

        } else if (attributeValue is JsonPrimitive && conditionValue is JsonPrimitive) {
            val targetPrimitiveValue = conditionValue.content
            val sourcePrimitiveValue = attributeValue.content

            when (operator) {
                // Evaluate EQ operator - whether condition equals to attribute
                "\$eq" -> {
                    return  sourcePrimitiveValue == targetPrimitiveValue
                }
                // Evaluate NE operator - whether condition doesn't equal to attribute
                "\$ne" -> {
                    return  sourcePrimitiveValue != targetPrimitiveValue
                }
                // Evaluate LT operator - whether attribute less than to condition
                "\$lt" -> {
                    if (attributeValue.doubleOrNull != null && conditionValue.doubleOrNull != null){
                        return (attributeValue.doubleOrNull!! < conditionValue.doubleOrNull!!)
                    }
                    return  sourcePrimitiveValue < targetPrimitiveValue
                }
                // Evaluate LTE operator - whether attribute less than or equal to condition
                "\$lte" -> {
                    if (attributeValue.doubleOrNull != null && conditionValue.doubleOrNull != null){
                        return (attributeValue.doubleOrNull!! <= conditionValue.doubleOrNull!!)
                    }
                    return  sourcePrimitiveValue <= targetPrimitiveValue
                }
                // Evaluate GT operator - whether attribute greater than to condition
                "\$gt" -> {
                    if (attributeValue.doubleOrNull != null && conditionValue.doubleOrNull != null){
                        return (attributeValue.doubleOrNull!! > conditionValue.doubleOrNull!!)
                    }
                    return  sourcePrimitiveValue > targetPrimitiveValue
                }
                // Evaluate GTE operator - whether attribute greater than or equal to condition
                "\$gte" -> {
                    if (attributeValue.doubleOrNull != null && conditionValue.doubleOrNull != null){
                        return (attributeValue.doubleOrNull!! >= conditionValue.doubleOrNull!!)
                    }
                    return  sourcePrimitiveValue >= targetPrimitiveValue
                }
                // Evaluate REGEX operator - whether attribute contains condition regex
                "\$regex" -> {

                    try {

                        val regex = Regex(targetPrimitiveValue)
                        return  regex.containsMatchIn(sourcePrimitiveValue)
                    }catch (error : Throwable){
                        return false
                    }


                }
            }

        }

        return false
    }

}