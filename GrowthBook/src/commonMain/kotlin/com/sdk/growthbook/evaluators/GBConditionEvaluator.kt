package com.sdk.growthbook.evaluators

import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.utils.GBUtils
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBBoolean

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
    fun evalCondition(
        attributes: Map<String, GBValue>,
        conditionObj: GBJson,
        savedGroups: Map<String, GBValue>?
    ): Boolean {
        for ((key, value) in conditionObj) {
            when (key) {
                "\$or" -> {
                    // If conditionObj has a key $or, return evalOr(attributes, condition["$or"])
                    val targetItems = conditionObj[key] as? GBArray
                    if (targetItems != null) {
                        if (!evalOr(attributes, targetItems, savedGroups)) {
                            return false
                        }
                    }
                }

                "\$nor" -> {
                    // If conditionObj has a key $nor, return !evalOr(attributes, condition["$nor"])
                    val targetItems = conditionObj[key] as? GBArray
                    if (targetItems != null) {
                        if (evalOr(attributes, targetItems, savedGroups)) {
                            return false
                        }
                    }
                }

                "\$and" -> {
                    // If conditionObj has a key $and, return !evalAnd(attributes, condition["$and"])
                    val targetItems = conditionObj[key] as? GBArray
                    if (targetItems != null) {
                        if (!evalAnd(attributes, targetItems, savedGroups)) {
                            return false
                        }
                    }
                }

                "\$not" -> {
                    // If conditionObj has a key $not, return !evalCondition(attributes, condition["$not"])
                    val targetItem = conditionObj[key] as? GBJson
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

        // If none of the entries failed their checks, `evalCondition` returns true
        return true
    }

    /**
     * Evaluate OR conditions against given attributes
     */
    private fun evalOr(
        attributes: Map<String, GBValue>,
        conditionObjs: GBArray,
        savedGroups: Map<String, GBValue>?
    ): Boolean {
        // If conditionObjs is empty, return true
        if (conditionObjs.isEmpty()) {
            return true
        } else {
            // Loop through the conditionObjects
            for (item in conditionObjs) {
                val gbJson = item as? GBJson ?: return false

                // If evalCondition(attributes, conditionObjs[i]) is true,
                // break out of the loop and return true
                if (evalCondition(attributes, gbJson, savedGroups)) {
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
    private fun evalAnd(
        attributes: Map<String, GBValue>,
        conditionObjs: GBArray,
        savedGroups: Map<String, GBValue>?
    ): Boolean {

        // Loop through the conditionObjects
        for (item in conditionObjs) {
            val gbJson = item as? GBJson ?: return false

            // If evalCondition(attributes, conditionObjs[i]) is false,
            // break out of the loop and return false
            if (!evalCondition(attributes, gbJson, savedGroups)) {
                return false
            }
        }

        // Return true
        return true
    }

    /**
     * This accepts a GBJson as input and returns true
     * if every key in the object starts with $
     */
    fun isOperatorObject(obj: GBJson): Boolean {
        var isOperator = true
        if (obj.keys.isNotEmpty()) {
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
    fun getType(obj: GBValue?): GBAttributeType {

        if (obj == GBNull) {
            return GBAttributeType.GbNull
        }

        if (obj?.isPrimitiveValue() == true) {
            return when (obj) {
                is GBString -> GBAttributeType.GbString
                is GBBoolean -> GBAttributeType.GbBoolean
                is GBNumber -> GBAttributeType.GbNumber
                else -> GBAttributeType.GbUnknown
            }
        }

        if (obj is GBArray) {
            return GBAttributeType.GbArray
        }

        if (obj is GBJson) {
            return GBAttributeType.GbObject
        }

        return GBAttributeType.GbUnknown
    }

    /**
     * Given attributes and a dot-separated path string,
     * @return the value at that path (or null if the path doesn't exist)
     */
    fun getPath(attributes: Map<String, GBValue>, key: String): GBValue {
        val paths: ArrayList<String>

        if (key.contains(".")) {
            paths = key.split(".") as ArrayList<String>
        } else {
            paths = ArrayList()
            paths.add(key)
        }

        var element: GBValue = attributes[paths[0]] ?: GBNull

        for (path in paths.subList(1, paths.size)) {
            if (element is GBJson) {
                element = element[path] ?: GBNull
            }
        }

        return element
    }

    /**
     * Evaluates Condition Value against given condition & attributes
     */
    fun evalConditionValue(
        conditionValue: GBValue,
        attributeValue: GBValue?,
        savedGroups: Map<String, GBValue>?
    ): Boolean {

        // If conditionValue is a string, number, boolean, return true
        // if it's "equal" to attributeValue and false if not.
        if (
            conditionValue.isPrimitiveValue() &&
            (attributeValue == null || attributeValue.isPrimitiveValue())
        ) {
            return conditionValue == attributeValue
        }

        if (conditionValue.isPrimitiveValue() && attributeValue == null) {
            return false
        }

        // If conditionValue is array, return true if it's "equal" - "equal"
        // should do a deep comparison for arrays.
        if (conditionValue is GBArray) {
            return if (attributeValue is GBArray) {
                arraysEqual(conditionValue, attributeValue)
            } else {
                false
            }
        }

        // If conditionValue is an object, loop over each key/value pair:
        if (conditionValue is GBJson) {

            if (isOperatorObject(conditionValue)) {
                for (key in conditionValue.keys) {
                    // If evalOperatorCondition(key, attributeValue, value) is false, return false
                    if (!evalOperatorCondition(
                            key,
                            attributeValue,
                            conditionValue[key]!!,
                            savedGroups
                        )
                    ) {
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

    private fun arraysEqual(arr1: GBArray, arr2: GBArray): Boolean {
        if (arr1.size == arr2.size) {
            for (i in arr1.indices) {
                if (arr1[i] != arr2[i]) {
                    return false
                }
            }
        } else {
            return false
        }

        return true
    }

    /**
     * This checks if attributeValue is an array,
     * and if so at least one of the array items must match the condition
     */
    private fun elemMatch(
        attributeValue: GBValue,
        condition: GBValue,
        savedGroups: Map<String, GBValue>?
    ): Boolean {

        if (attributeValue is GBArray) {
            // Loop through items in attributeValue
            for (item in attributeValue) {
                val attributes = if (item is GBJson) {
                    HashMap(item)
                } else {
                    mapOf("value" to item)
                }

                // If isOperatorObject(condition)
                if (condition is GBJson && isOperatorObject(condition)) {
                    // If evalConditionValue(condition, item), break out of loop and return true
                    if (evalConditionValue(condition, item, savedGroups)) {
                        return true
                    }
                }
                // Else if evalCondition(item, condition), break out of loop and return true
                else if (evalCondition(
                        attributes,
                        condition as? GBJson ?: GBJson(emptyMap()),
                        savedGroups
                    )
                ) {
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
        attributeValue: GBValue?,
        conditionValue: GBValue,
        savedGroups: Map<String, GBValue>?,
    ): Boolean {

        // Evaluate TYPE operator - whether both are of same type
        if (operator == "\$type") {
            val expectedType = (conditionValue as? GBString)?.value
            return getType(attributeValue).toString() == expectedType
        }

        // Evaluate NOT operator - whether condition doesn't contain attribute
        if (operator == "\$not") {
            return !evalConditionValue(conditionValue, attributeValue, savedGroups)
        }

        // Evaluate EXISTS operator - whether condition contains attribute
        if (operator == "\$exists") {
            val targetPrimitiveValue = conditionValue as? GBBoolean
            val gate2 = (attributeValue == null || attributeValue is GBNull)
            if (targetPrimitiveValue?.value == false && gate2) {
                return true
            } else {
                val gate3 = (targetPrimitiveValue?.value == true)
                val gate4 = (attributeValue != null)
                val gate5 = (attributeValue !is GBNull)
                if (gate3 && gate4 && gate5) {
                    return true
                }
            }
        }

        /// There are three operators where conditionValue is an array
        if (conditionValue is GBArray) {
            when (operator) {
                // Evaluate IN operator - attributeValue in the conditionValue array
                "\$in" -> {
                    return if (attributeValue is GBArray) {
                        isIn(attributeValue, conditionValue)
                    } else conditionValue.contains(attributeValue)
                }
                // Evaluate NIN operator - attributeValue not in the conditionValue array
                "\$nin" -> {
                    return if (attributeValue is GBArray) {
                        !isIn(attributeValue, conditionValue)
                    } else !conditionValue.contains(attributeValue)
                }
                // Evaluate ALL operator - whether condition contains all attribute
                "\$all" -> {

                    if (attributeValue is GBArray) {
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
        } else if (attributeValue is GBArray) {

            when (operator) {
                // Evaluate ElemMATCH operator - whether condition matches attribute
                "\$elemMatch" -> {
                    return elemMatch(attributeValue, conditionValue, savedGroups)
                }
                // Evaluate SIE operator - whether condition size is same as that of attribute
                "\$size" -> {
                    return evalConditionValue(
                        conditionValue,
                        GBNumber(attributeValue.size),
                        savedGroups
                    )
                }
            }
        } else if (attributeValue?.isPrimitiveValue() == true) {
            val targetPrimitiveValue = conditionValue as? GBString
            val sourcePrimitiveValue = attributeValue as? GBString
            val paddedVersionTarget =
                GBUtils.paddedVersionString(targetPrimitiveValue?.value.orEmpty())
            val paddedVersionSource =
                GBUtils.paddedVersionString(sourcePrimitiveValue?.value ?: "0")

            fun template(
                stringComparator: (String, String) -> Boolean,
                numberComparator: (Double, Double) -> Boolean,
            ) = comparisonTemplate(
                attributeValue, conditionValue,
                stringComparator, numberComparator
            )

            when (operator) {
                // Evaluate EQ operator - whether condition equals to attribute
                "\$eq" -> {
                    if (sourcePrimitiveValue == null || getType(attributeValue) == GBAttributeType.GbNull) return false
                    return sourcePrimitiveValue == targetPrimitiveValue
                }
                // Evaluate NE operator - whether condition doesn't equal to attribute
                "\$ne" -> {
                    // return sourcePrimitiveValue != targetPrimitiveValue
                    return conditionValue != attributeValue
                }
                // Evaluate LT operator - whether attribute less than to condition
                "\$lt" -> {
                    return template(
                        stringComparator = { actual, expected ->
                            actual < expected
                        },
                        numberComparator = { actual, expected ->
                            actual < expected
                        },
                    )
                }
                // Evaluate LTE operator - whether attribute less than or equal to condition
                "\$lte" -> {
                    return template(
                        stringComparator = { actual, expected ->
                            actual <= expected
                        },
                        numberComparator = { actual, expected ->
                            actual <= expected
                        },
                    )
                }
                // Evaluate GT operator - whether attribute greater than to condition
                "\$gt" -> {
                    return template(
                        stringComparator = { actual, expected ->
                            actual > expected
                        },
                        numberComparator = { actual, expected ->
                            actual > expected
                        },
                    )
                }
                // Evaluate GTE operator - whether attribute greater than or equal to condition
                "\$gte" -> {
                    return template(
                        stringComparator = { actual, expected ->
                            actual >= expected
                        },
                        numberComparator = { actual, expected ->
                            actual >= expected
                        },
                    )
                }
                // Evaluate REGEX operator - whether attribute contains condition regex
                "\$regex" -> {

                    return try {

                        val regex = Regex(targetPrimitiveValue?.value.orEmpty())
                        regex.containsMatchIn(sourcePrimitiveValue?.value ?: "0")
                    } catch (error: Throwable) {
                        false
                    }
                }

                "\$regexi" -> {
                    return try {
                        val regex =
                            Regex(targetPrimitiveValue?.value.orEmpty(), RegexOption.IGNORE_CASE)
                        regex.containsMatchIn(sourcePrimitiveValue?.value ?: "0")
                    } catch (error: Throwable) {
                        false
                    }
                }

                "\$notRegex" -> {
                    return try {
                        val regex =
                            Regex(targetPrimitiveValue?.value.orEmpty())
                        !regex.containsMatchIn(sourcePrimitiveValue?.value ?: "0")
                    } catch (error: Throwable) {
                        false
                    }
                }

                "\$notRegexi" -> {
                    return try {
                        val regex =
                            Regex(targetPrimitiveValue?.value.orEmpty(), RegexOption.IGNORE_CASE)
                        !regex.containsMatchIn(sourcePrimitiveValue?.value ?: "0")
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
                    val gbArray =
                        savedGroups?.get(conditionValue.asKey()) as? GBArray ?: GBArray(emptyList())
                    return isIn(attributeValue, gbArray)
                }

                "\$notInGroup" -> {
                    val gbArray =
                        savedGroups?.get(conditionValue.asKey()) as? GBArray ?: GBArray(emptyList())
                    return !isIn(attributeValue, gbArray)
                }
            }
        }

        return false
    }

    private fun GBValue.asKey(): String =
        when (this) {
            is GBString -> this.value // without quotes
            else -> this.toString()
        }

    private fun isIn(actualValue: GBValue, conditionValue: GBArray): Boolean {

        if (actualValue !is GBArray) return conditionValue.contains(actualValue)

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

    private fun comparisonTemplate(
        actualGbValue: GBValue?,
        expectedGbValue: GBValue,
        stringComparator: (String, String) -> Boolean,
        numberComparator: (Double, Double) -> Boolean,
    ): Boolean {
        val isAlphabeticComparison = actualGbValue is GBString && expectedGbValue is GBString

        return if (isAlphabeticComparison) {
            stringComparator.invoke(
                (actualGbValue as? GBString)?.value.orEmpty(),
                (expectedGbValue as? GBString)?.value.orEmpty(),
            )
        } else {
            numberComparator.invoke(
                actualGbValue?.tryRetrieveDouble() ?: 0.0,
                expectedGbValue.tryRetrieveDouble(),
            )
        }
    }

    private fun GBValue.tryRetrieveDouble(): Double =
        when (this) {
            is GBNumber -> this.value.toDouble()
            is GBString -> this.value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
}