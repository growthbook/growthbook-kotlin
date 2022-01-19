package com.sdk.growthbook.Evaluators

import com.sdk.growthbook.Utils.GBCondition
import kotlinx.serialization.json.*

/*
    Both experiments and features can define targeting conditions using a syntax modeled after MongoDB queries.

    These conditions can have arbitrary nesting levels and evaluating them requires recursion.
    There are a handful of functions to define, and be aware that some of them may reference function definitions further below.
 */

enum class  GBAttributeType {
    gbString{
        override fun toString(): String = "string"
    },
    gbNumber{
        override fun toString(): String = "number"
    },
    gbBoolean{
        override fun toString(): String = "boolean"
    },
    gbArray{
        override fun toString(): String = "array"
    },
    gbObject{
        override fun toString(): String = "object"
    },
    gbNull{
        override fun toString(): String = "null"
    },
    gbUndefined{
        override fun toString(): String = "undefined"
    },
    gbUnknown{
        override fun toString(): String = "unknown"
    }

}

/*
    This function is just a case statement that handles all the possible operators
    There are basic comparison operators in the form attributeValue {op} conditionValue
 */
internal class GBConditionEvaluator{


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
            var targetItem = conditionObj.jsonObject["\$not"]
            if (targetItem != null) {
                return !evalCondition(attributes, targetItem)
            }

            // Loop through the conditionObj key/value pairs
            for (key in conditionObj.jsonObject.keys) {
                val element = getPath(attributes, key)
                val value = conditionObj.jsonObject[key]
                if (value != null){
                    // If evalConditionValue(value, getPath(attributes, key)) is false, break out of loop and return false
                    if(!evalConditionValue(value, element)) {
                        return false
                    }
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

        var paths : ArrayList<String>

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

    fun evalConditionValue(conditionValue : JsonElement, attributeValue : JsonElement?) : Boolean {

        // If conditionValue is a string, number, boolean, return true if it's "equal" to attributeValue and false if not.
        if (conditionValue is JsonPrimitive && attributeValue is JsonPrimitive) {
            return conditionValue.content == attributeValue.content
        }

        // If conditionValue is array, return true if it's "equal" - "equal" should do a deep comparison for arrays.
        if (conditionValue is JsonArray) {

            if (attributeValue is JsonArray) {
                if (conditionValue.size == attributeValue.size) {
                    val conditionArray = Json {  }.decodeFromJsonElement<Array<JsonElement>>(conditionValue)
                    val attributeArray = Json {  }.decodeFromJsonElement<Array<JsonElement>>(attributeValue)

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

    /*
        This checks if attributeValue is an array, and if so at least one of the array items must match the condition
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

    /*
        This function is just a case statement that handles all the possible operators
        There are basic comparison operators in the form attributeValue {op} conditionValue
     */
    fun evalOperatorCondition(operator : String, attributeValue : JsonElement?, conditionValue : JsonElement) : Boolean {

        if (operator == "\$type") {
            return  getType(attributeValue).toString() == conditionValue.jsonPrimitive.content
        }

        if (operator == "\$not") {
            return  !evalConditionValue(conditionValue, attributeValue)
        }

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
                "\$elemMatch" -> {
                    return  elemMatch(attributeValue, conditionValue)
                }
                "\$size" -> {
                    return evalConditionValue(conditionValue, JsonPrimitive(attributeValue.size))
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
                    if (attributeValue.doubleOrNull != null && conditionValue.doubleOrNull != null){
                        return (attributeValue.doubleOrNull!! < conditionValue.doubleOrNull!!)
                    }
                    return  sourcePrimitiveValue < targetPrimitiveValue
                }
                "\$lte" -> {
                    if (attributeValue.doubleOrNull != null && conditionValue.doubleOrNull != null){
                        return (attributeValue.doubleOrNull!! <= conditionValue.doubleOrNull!!)
                    }
                    return  sourcePrimitiveValue <= targetPrimitiveValue
                }
                "\$gt" -> {
                    if (attributeValue.doubleOrNull != null && conditionValue.doubleOrNull != null){
                        return (attributeValue.doubleOrNull!! > conditionValue.doubleOrNull!!)
                    }
                    return  sourcePrimitiveValue > targetPrimitiveValue
                }
                "\$gte" -> {
                    if (attributeValue.doubleOrNull != null && conditionValue.doubleOrNull != null){
                        return (attributeValue.doubleOrNull!! >= conditionValue.doubleOrNull!!)
                    }
                    return  sourcePrimitiveValue >= targetPrimitiveValue
                }
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