package com.sdk.growthbook.evaluators

import com.sdk.growthbook.utils.GBCondition
import com.sdk.growthbook.utils.GBUtils
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Both experiments and features can define targeting conditions using a syntax modeled
 * after MongoDB queries. These conditions can have arbitrary nesting levels
 * and evaluating them requires recursion. There are a handful of functions to define,
 * and be aware that some of them may reference function definitions further below.
 */

/**
 * Enum For different Attribute Types supported by GrowthBook
 */
internal enum class GBAttributeType {
    /**
     * String Type Attribute
     */
    GbString {
        override fun toString(): String = "string"
    },

    /**
     * Number Type Attribute
     */
    GbNumber {
        override fun toString(): String = "number"
    },

    /**
     * Boolean Type Attribute
     */
    GbBoolean {
        override fun toString(): String = "boolean"
    },

    /**
     * Array Type Attribute
     */
    GbArray {
        override fun toString(): String = "array"
    },

    /**
     * Object Type Attribute
     */
    GbObject {
        override fun toString(): String = "object"
    },

    /**
     * Null Type Attribute
     */
    GbNull {
        override fun toString(): String = "null"
    },

    /**
     * Not Supported Type Attribute
     */
    GbUnknown {
        override fun toString(): String = "unknown"
    }
}

/**
 * Evaluator Class for Conditions
 */
internal class GBConditionEvaluator {

    /**
     * This is the main function used to evaluate a condition.
     * It loops through the condition key/value pairs and checks each entry:
     * - attributes is the user's attributes
     * - condition to be evaluated
     */
    fun evalCondition(attributes: Map<String, JsonElement>, conditionObj: GBCondition, savedGroups: JsonObject?): Boolean {

        if (conditionObj is JsonArray) {
            return false
        } else {
            // Loop through the conditionObj key/value pairs
            for ((key, value) in conditionObj.jsonObject) {
                when (key) {
                    "\$or" -> {
                        // If conditionObj has a key $or, return evalOr(attributes, condition["$or"])
                        val targetItems = conditionObj.jsonObject[key] as? JsonArray
                        if (targetItems != null) {
                            if (!evalOr(attributes, targetItems, savedGroups)) {
                                return false
                            }
                        }
                    }

                    "\$nor" -> {
                        // If conditionObj has a key $nor, return !evalOr(attributes, condition["$nor"])
                        val targetItems = conditionObj.jsonObject[key] as? JsonArray
                        if (targetItems != null) {
                            if (evalOr(attributes, targetItems, savedGroups)) {
                                return false
                            }
                        }
                    }

                    "\$and" -> {
                        // If conditionObj has a key $and, return !evalAnd(attributes, condition["$and"])
                        val targetItems = conditionObj.jsonObject[key] as? JsonArray
                        if (targetItems != null) {
                            if (!evalAnd(attributes, targetItems, savedGroups)) {
                                return false
                            }
                        }
                    }

                    "\$not" -> {
                        // If conditionObj has a key $not, return !evalCondition(attributes, condition["$not"])
                        val targetItem = conditionObj.jsonObject[key]
                        if (targetItem != null) {
                            if (evalCondition(attributes, targetItem, savedGroups)) {
                                return false
                            }
                        }
                    }

                    else -> {
                        val element = getPath(attributes, key)
                            // If evalConditionValue(value, getPath(attributes, key)) is false,
                            // break out of loop and return false
                            if (!evalConditionValue(value, element, savedGroups)) {
                                return false
                            }
                    }
                }
            }
        }

        // If none of the entries failed their checks, `evalCondition` returns true
        return true
    }

    /**
     * Evaluate OR conditions against given attributes
     */
    private fun evalOr(attributes: Map<String, JsonElement>, conditionObjs: JsonArray, savedGroups: JsonObject?): Boolean {
        // If conditionObjs is empty, return true
        if (conditionObjs.isEmpty()) {
            return true
        } else {
            // Loop through the conditionObjects
            for (item in conditionObjs) {
                // If evalCondition(attributes, conditionObjs[i]) is true,
                // break out of the loop and return true
                if (evalCondition(attributes, item, savedGroups)) {
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
    private fun evalAnd(attributes: Map<String, JsonElement>, conditionObjs: JsonArray, savedGroups: JsonObject?): Boolean {

        // Loop through the conditionObjects
        for (item in conditionObjs) {
            // If evalCondition(attributes, conditionObjs[i]) is false,
            // break out of the loop and return false
            if (!evalCondition(attributes, item, savedGroups)) {
                return false
            }
        }

        // Return true
        return true
    }

    /**
     * This accepts a parsed JSON object as input and returns true
     * if every key in the object starts with $
     */
    fun isOperatorObject(obj: JsonElement): Boolean {
        var isOperator = true
        if (obj is JsonObject && obj.keys.isNotEmpty()) {
            for (key in obj.keys) {
                if (!key.startsWith("$")) {
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
    fun getType(obj: JsonElement?): GBAttributeType {

        if (obj == JsonNull) {
            return GBAttributeType.GbNull
        }

        if (obj is JsonPrimitive) {

            val primitiveValue = obj.jsonPrimitive

            return if (primitiveValue.isString) {
                GBAttributeType.GbString
            } else if (primitiveValue.content == "true" || primitiveValue.content == "false") {
                GBAttributeType.GbBoolean
            } else {
                GBAttributeType.GbNumber
            }
        }

        if (obj is JsonArray) {
            return GBAttributeType.GbArray
        }

        if (obj is JsonObject) {
            return GBAttributeType.GbObject
        }

        return GBAttributeType.GbUnknown
    }

    /**
     * Given attributes and a dot-separated path string,
     * @return the value at that path (or null if the path doesn't exist)
     */
    fun getPath(attributes: Map<String, JsonElement>, key: String): JsonElement {
        val paths: ArrayList<String>

        if (key.contains(".")) {
            paths = key.split(".") as ArrayList<String>
        } else {
            paths = ArrayList()
            paths.add(key)
        }

        var element: JsonElement = attributes[paths[0]] ?: JsonNull

        for (path in paths.subList(1, paths.size)) {
            if (element is JsonObject) {
                element = element[path] ?: JsonNull
            }
        }

        return element
    }

    /**
     * Evaluates Condition Value against given condition & attributes
     */
    fun evalConditionValue(conditionValue: JsonElement, attributeValue: JsonElement?, savedGroups: JsonObject?): Boolean {

        // If conditionValue is a string, number, boolean, return true
        // if it's "equal" to attributeValue and false if not.
        if (
            conditionValue is JsonPrimitive &&
            (attributeValue is JsonPrimitive || attributeValue == null)
        ) {
            return conditionValue.jsonPrimitive.contentOrNull == attributeValue?.jsonPrimitive?.contentOrNull
        }

        if (conditionValue is JsonPrimitive && attributeValue == null) {
            return false
        }

        // If conditionValue is array, return true if it's "equal" - "equal"
        // should do a deep comparison for arrays.
        if (conditionValue is JsonArray) {
            return if (attributeValue is JsonArray) {
                if (conditionValue.size == attributeValue.size) {
                    val conditionArray = Json.decodeFromJsonElement(
                        ListSerializer(JsonElement.serializer()),
                        conditionValue
                    )
                    val attributeArray = Json.decodeFromJsonElement(
                        ListSerializer(JsonElement.serializer()),
                        attributeValue
                    )

                    conditionArray == attributeArray
                } else {
                    false
                }
            } else {
                false
            }
        }

        // If conditionValue is an object, loop over each key/value pair:
        if (conditionValue is JsonObject) {

            if (isOperatorObject(conditionValue)) {
                for (key in conditionValue.keys) {
                    // If evalOperatorCondition(key, attributeValue, value) is false, return false
                    if (!evalOperatorCondition(key, attributeValue, conditionValue[key]!!, savedGroups)) {
                        return false
                    }
                }
            } else if (attributeValue != null) {
                return conditionValue == attributeValue
            } else {
                return false
            }
        }

        // Return true
        return true
    }

    /**
     * This checks if attributeValue is an array,
     * and if so at least one of the array items must match the condition
     */
    private fun elemMatch(attributeValue: JsonElement, condition: JsonElement, savedGroups: JsonObject?): Boolean {

        if (attributeValue is JsonArray) {
            // Loop through items in attributeValue
            for (item in attributeValue) {
                // If isOperatorObject(condition)
                if (isOperatorObject(condition)) {
                    // If evalConditionValue(condition, item), break out of loop and return true
                    if (evalConditionValue(condition, item, savedGroups)) {
                        return true
                    }
                }
                // Else if evalCondition(item, condition), break out of loop and return true
                else if (evalCondition(mapOf("value" to item), condition, savedGroups)) {
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
    fun evalOperatorCondition(
        operator: String,
        attributeValue: JsonElement?,
        conditionValue: JsonElement,
        savedGroups: JsonObject?
    ): Boolean {

        // Evaluate TYPE operator - whether both are of same type
        if (operator == "\$type") {
            return getType(attributeValue).toString() == conditionValue.jsonPrimitive.content
        }

        // Evaluate NOT operator - whether condition doesn't contain attribute
        if (operator == "\$not") {
            return !evalConditionValue(conditionValue, attributeValue, savedGroups)
        }

        // Evaluate EXISTS operator - whether condition contains attribute
        if (operator == "\$exists") {
            val targetPrimitiveValue = conditionValue.jsonPrimitive.content
            val gate2 = (attributeValue == null || attributeValue is JsonNull)
            if (targetPrimitiveValue == "false" && gate2) {
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
                    return if (attributeValue is JsonArray) {
                        isIn(attributeValue, conditionValue)
                    } else conditionValue.contains(attributeValue)
                }
                // Evaluate NIN operator - attributeValue not in the conditionValue array
                "\$nin" -> {
                    return if (attributeValue is JsonArray) {
                        !isIn(attributeValue, conditionValue)
                    } else !conditionValue.contains(attributeValue)
                }
                // Evaluate ALL operator - whether condition contains all attribute
                "\$all" -> {

                    if (attributeValue is JsonArray) {
                        // Loop through conditionValue array
                        // If none of the elements in the attributeValue array pass
                        // evalConditionValue(conditionValue[i], attributeValue[j]), return false
                        for (con in conditionValue) {
                            var result = false
                            for (attr in attributeValue) {
                                if (evalConditionValue(con, attr, savedGroups)) {
                                    result = true
                                }
                            }
                            if (!result) {
                                return false
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
                // Evaluate ElemMATCH operator - whether condition matches attribute
                "\$elemMatch" -> {
                    return elemMatch(attributeValue, conditionValue, savedGroups)
                }
                // Evaluate SIE operator - whether condition size is same as that of attribute
                "\$size" -> {
                    return evalConditionValue(conditionValue, JsonPrimitive(attributeValue.size), savedGroups)
                }
            }
        } else if (attributeValue is JsonPrimitive? && conditionValue is JsonPrimitive) {
            val targetPrimitiveValue = conditionValue.content
            val sourcePrimitiveValue = attributeValue?.content
            val paddedVersionTarget = GBUtils.paddedVersionString(targetPrimitiveValue)
            val paddedVersionSource = GBUtils.paddedVersionString(sourcePrimitiveValue ?: "0")

            when (operator) {
                // Evaluate EQ operator - whether condition equals to attribute
                "\$eq" -> {
                    return sourcePrimitiveValue == targetPrimitiveValue
                }
                // Evaluate NE operator - whether condition doesn't equal to attribute
                "\$ne" -> {
                    return sourcePrimitiveValue != targetPrimitiveValue
                }
                // Evaluate LT operator - whether attribute less than to condition
                "\$lt" -> {
                    if (
                        attributeValue?.doubleOrNull != null && conditionValue.doubleOrNull != null
                    ) {
                        return (attributeValue?.doubleOrNull!! < conditionValue.doubleOrNull!!)
                    }
                    return if (sourcePrimitiveValue == null) {
                        0.0 < targetPrimitiveValue.toDouble()
                    } else {
                        sourcePrimitiveValue < targetPrimitiveValue
                    }
                }
                // Evaluate LTE operator - whether attribute less than or equal to condition
                "\$lte" -> {
                    if (
                        attributeValue?.doubleOrNull != null && conditionValue.doubleOrNull != null
                    ) {
                        return (attributeValue?.doubleOrNull!! <= conditionValue.doubleOrNull!!)
                    }
                    return if (sourcePrimitiveValue == null) {
                        0.0 <= targetPrimitiveValue.toDouble()
                    } else {
                        sourcePrimitiveValue <= targetPrimitiveValue
                    }
                }
                // Evaluate GT operator - whether attribute greater than to condition
                "\$gt" -> {
                    if (
                        attributeValue?.doubleOrNull != null && conditionValue.doubleOrNull != null
                    ) {
                        return (attributeValue?.doubleOrNull!! > conditionValue.doubleOrNull!!)
                    }
                    return if (sourcePrimitiveValue == null) {
                        0.0 > targetPrimitiveValue.toDouble()
                    } else {
                        sourcePrimitiveValue > targetPrimitiveValue
                    }
                }
                // Evaluate GTE operator - whether attribute greater than or equal to condition
                "\$gte" -> {
                    if (
                        attributeValue?.doubleOrNull != null && conditionValue.doubleOrNull != null
                    ) {
                        return (attributeValue?.doubleOrNull!! >= conditionValue.doubleOrNull!!)
                    }
                    return if (sourcePrimitiveValue == null) {
                        0.0 >= targetPrimitiveValue.toDouble()
                    } else {
                        sourcePrimitiveValue >= targetPrimitiveValue
                    }
                }
                // Evaluate REGEX operator - whether attribute contains condition regex
                "\$regex" -> {

                    return try {

                        val regex = Regex(targetPrimitiveValue)
                        regex.containsMatchIn(sourcePrimitiveValue ?: "0")
                    } catch (error: Throwable) {
                        false
                    }
                }
                // Evaluate VEQ operator - whether versions are equals
                "\$veq" -> return paddedVersionSource == paddedVersionTarget
                // Evaluate VNE operator - whether versions are not equals to attribute
                "\$vne" -> return paddedVersionSource != paddedVersionTarget
                // Evaluate VGT operator - whether the first version is greater
                // than the second version
                "\$vgt" -> return paddedVersionSource > paddedVersionTarget
                // Evaluate VGTE operator - whether the first version is greater
                // than or equal the second version
                "\$vgte" -> return paddedVersionSource >= paddedVersionTarget
                // Evaluate VLT operator - whether the first version is lesser
                // than the second version
                "\$vlt" -> return paddedVersionSource < paddedVersionTarget
                // Evaluate VLTE operator - whether the first version is lesser
                // than or equal the second version
                "\$vlte" -> return paddedVersionSource <= paddedVersionTarget
                "\$inGroup" -> {
                    if (attributeValue != null) {
                        return isIn(attributeValue, savedGroups?.get(conditionValue.content)?.jsonArray ?: JsonArray(
                            emptyList()
                        )
                        )
                    }
                }
                "\$notInGroup" -> {
                    if (attributeValue != null /*&& conditionValue != null*/) {
                        return !isIn(attributeValue, savedGroups?.get(conditionValue.content)?.jsonArray ?: JsonArray(
                            emptyList()
                        )
                        )
                    }
                }
            }
        }

        return false
    }

    private fun isIn(actualValue: JsonElement, conditionValue: JsonArray): Boolean {

        if (actualValue !is JsonArray) return conditionValue.contains(actualValue)

        if (actualValue.size == 0) return false

        actualValue.forEach {
            if (getType(it) == GBAttributeType.GbString ||
                getType(it) == GBAttributeType.GbBoolean ||
                getType(it) == GBAttributeType.GbNumber
            ) {
                if (conditionValue.contains(it)) {
                    return true
                }
            }
        }
        return false
    }
}