package com.sdk.growthbook.evaluators

import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.utils.GBUtils
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.utils.toJsString

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
    ): Boolean = evalConditionResult(attributes, conditionObj, savedGroups) == true

    /**
     * `true` or `false`, or `null` when the result depends on an operator or saved group
     * type this SDK doesn't support. The public functions treat `null` as failing, so such
     * a condition also fails under `$not` and `$nor`.
     */
    private fun evalConditionResult(
        attributes: Map<String, GBValue>,
        conditionObj: GBJson,
        savedGroups: Map<String, GBValue>?,
        visited: Set<String> = emptySet()
    ): Boolean? {
        var result: Boolean? = true
        for ((key, value) in conditionObj) {
            val entryResult: Boolean? = when (key) {
                // A value of the wrong shape is skipped here; evalConditionSafely fails it
                "\$or" -> if (value is GBArray) evalOr(attributes, value, savedGroups, visited) else true
                "\$nor" -> if (value is GBArray) not(evalOr(attributes, value, savedGroups, visited)) else true
                "\$and" -> if (value is GBArray) evalAnd(attributes, value, savedGroups, visited) else true
                "\$not" -> if (value is GBJson) not(evalConditionResult(attributes, value, savedGroups, visited)) else true
                "\$savedGroup" -> evalSavedGroup(attributes, value, savedGroups, visited)
                else -> evalConditionValueResult(value, getPath(attributes, key), savedGroups, visited = visited)
            }
            // One failing entry decides it, wherever an unsupported one is
            if (entryResult == false) return false
            if (entryResult == null) result = null
        }
        return result
    }

    private fun not(result: Boolean?): Boolean? = result?.not()

    /**
     * Evaluates a condition during feature and experiment evaluation, including prerequisites
     * (`parentConditions`). A malformed condition, such as `{"$not": null}`, fails instead of
     * throwing, so the rule or experiment is skipped.
     */
    fun evalConditionSafely(
        attributes: Map<String, GBValue>,
        conditionObj: GBJson,
        savedGroups: Map<String, GBValue>?
    ): Boolean = try {
        !isMalformed(conditionObj) && evalCondition(attributes, conditionObj, savedGroups)
    } catch (e: Exception) {
        false
    }

    // The shapes the JS SDK cannot evaluate: a null where a condition object is required
    private fun isMalformed(conditionObj: GBJson): Boolean =
        conditionObj.any { (key, value) ->
            when (key) {
                "\$or", "\$nor", "\$and" -> value is GBNull || value is GBArray &&
                    value.any { it is GBNull || (it is GBJson && isMalformed(it)) }
                "\$not" -> value is GBNull || (value is GBJson && isMalformed(value))
                "\$savedGroup" -> false
                else -> isMalformedValue(value)
            }
        }

    private fun isMalformedValue(conditionValue: GBValue): Boolean =
        conditionValue is GBJson && isOperatorObject(conditionValue) &&
            conditionValue.any { (operator, value) ->
                when (operator) {
                    "\$elemMatch" -> value is GBNull || (
                        value is GBJson &&
                            if (isOperatorObject(value)) isMalformedValue(value) else isMalformed(value)
                        )
                    "\$not", "\$size" -> isMalformedValue(value)
                    else -> false
                }
            }

    /**
     * Evaluate OR conditions against given attributes
     */
    private fun evalOr(
        attributes: Map<String, GBValue>,
        conditionObjs: GBArray,
        savedGroups: Map<String, GBValue>?,
        visited: Set<String>
    ): Boolean? {
        // If conditionObjs is empty, return true
        if (conditionObjs.isEmpty()) return true

        var result: Boolean? = false
        for (item in conditionObjs) {
            val gbJson = item as? GBJson ?: return false
            when (evalConditionResult(attributes, gbJson, savedGroups, visited)) {
                true -> return true
                null -> result = null
                false -> {}
            }
        }
        return result
    }

    /**
     * Evaluate AND conditions against given attributes
     */
    private fun evalAnd(
        attributes: Map<String, GBValue>,
        conditionObjs: GBArray,
        savedGroups: Map<String, GBValue>?,
        visited: Set<String>
    ): Boolean? {
        var result: Boolean? = true
        for (item in conditionObjs) {
            val gbJson = item as? GBJson ?: return false
            when (evalConditionResult(attributes, gbJson, savedGroups, visited)) {
                false -> return false
                null -> result = null
                true -> {}
            }
        }
        return result
    }

    /**
     * This accepts a GBJson as input and returns true
     * if every key in the object starts with $.
     * An empty object has no operators, so as a condition value it always passes.
     */
    fun isOperatorObject(obj: GBJson): Boolean = obj.keys.all { it.startsWith("$") }

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

        // Like JS getPath: arrays are indexable, anything else that isn't an object ends in null
        for (path in paths.subList(1, paths.size)) {
            element = when (element) {
                is GBJson -> element[path] ?: return GBNull
                is GBArray -> when {
                    path == "length" -> GBNumber(element.size)
                    else -> path.toIntOrNull()
                        ?.takeIf { it.toString() == path && it in element.indices }
                        ?.let { element[it] }
                        ?: return GBNull
                }
                else -> return GBNull
            }
        }

        return element
    }

    /**
     * Evaluates Condition Value against given condition & attributes
     */
    fun evalConditionValue(conditionValue: GBValue, attributeValue: GBValue?, savedGroups: Map<String, GBValue>?, inSensitive: Boolean = false): Boolean =
        evalConditionValueResult(conditionValue, attributeValue, savedGroups, inSensitive) == true

    private fun evalConditionValueResult(
        conditionValue: GBValue,
        attributeValue: GBValue?,
        savedGroups: Map<String, GBValue>?,
        inSensitive: Boolean = false,
        visited: Set<String> = emptySet()
    ): Boolean? {

        // A string, number or boolean only equals an attribute of the same type: no coercion
        if (conditionValue is GBString || conditionValue is GBNumber || conditionValue is GBBoolean) {
            if (getType(attributeValue) != getType(conditionValue)) {
                return false
            }
            if (inSensitive && conditionValue is GBString && attributeValue is GBString) {
                return conditionValue.value.equals(attributeValue.value, ignoreCase = true)
            }
            return conditionValue == attributeValue
        }

        if (conditionValue is GBNull) {
            return attributeValue == GBNull
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
                var result: Boolean? = true
                for ((operator, value) in conditionValue) {
                    when (evalOperatorResult(operator, attributeValue, value, savedGroups, visited)) {
                        false -> return false
                        null -> result = null
                        true -> {}
                    }
                }
                return result
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
     * Resolves a `$savedGroup` reference: `{"id": "...", "attributeKey"?: "..."}`.
     *
     * A `list` entry tests one attribute (the reference's `attributeKey` wins over the entry's) against
     * its values; a `condition` entry is evaluated in full. Anything unreadable matches nobody, and a
     * group type added after this SDK was built is unsupported (`null`). [visited] holds the ids being
     * resolved, so a group that references itself, directly or through others, fails instead of
     * recursing forever.
     */
    private fun evalSavedGroup(
        attributes: Map<String, GBValue>,
        reference: GBValue,
        savedGroups: Map<String, GBValue>?,
        visited: Set<String>
    ): Boolean? {
        val ref = reference as? GBJson ?: return false
        val id = (ref["id"] as? GBString)?.value ?: return false
        if (id in visited) return false
        // Falling back to the entry's attribute would check a different population
        val overrideKey = when (val raw = ref["attributeKey"]) {
            null -> null
            is GBString -> raw.value
            else -> return false
        }

        // Absent, or a v1 bare array
        val entry = savedGroups?.get(id) as? GBJson ?: return false

        return when ((entry["type"] as? GBString)?.value) {
            "list" -> {
                val key = overrideKey ?: (entry["attributeKey"] as? GBString)?.value ?: return false
                val values = entry["values"] as? GBArray ?: return false
                isIn(getPath(attributes, key), values)
            }
            // A condition group has no single attribute, so any override is ignored
            "condition" -> {
                val condition = entry["condition"] as? GBJson ?: return false
                evalConditionResult(attributes, condition, savedGroups, visited + id)
            }
            else -> null
        }
    }

    /**
     * This checks if attributeValue is an array,
     * and if so at least one of the array items must match the condition
     */
    private fun elemMatch(
        attributeValue: GBValue,
        condition: GBValue,
        savedGroups: Map<String, GBValue>?,
        visited: Set<String>
    ): Boolean? {
        if (attributeValue !is GBArray) return false

        // Operators test each element itself. A condition, including one that only
        // uses $or, $and, $nor or $savedGroup, tests each element's fields.
        val testsElement = condition is GBJson && isOperatorObject(condition) &&
            condition.keys.none { it in CONDITION_OPERATORS }
        var result: Boolean? = false
        for (item in attributeValue) {
            // Only skip null elements; falsy values like 0, "" and false must still be tested
            if (item is GBNull) continue
            val itemResult = if (testsElement) {
                evalConditionValueResult(condition, item, savedGroups, visited = visited)
            } else {
                evalConditionResult(ownFields(item), condition as? GBJson ?: GBJson(emptyMap()), savedGroups, visited)
            }
            when (itemResult) {
                true -> return true
                null -> result = null
                false -> {}
            }
        }
        return result
    }

    /**
     * The fields a condition sees on an `$elemMatch` element, like JS own keys: an object's keys,
     * an array's indices and `length`, and none for a string, number or boolean.
     */
    private fun ownFields(item: GBValue): Map<String, GBValue> =
        when (item) {
            is GBJson -> item
            is GBArray -> item.withIndex().associate { (index, value) -> index.toString() to value } +
                ("length" to GBNumber(item.size))
            else -> emptyMap()
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
    ): Boolean = evalOperatorResult(operator, attributeValue, conditionValue, savedGroups) == true

    private fun evalOperatorResult(
        operator: String,
        attributeValue: GBValue?,
        conditionValue: GBValue,
        savedGroups: Map<String, GBValue>?,
        visited: Set<String> = emptySet(),
    ): Boolean? {

        // An operator this SDK can't evaluate makes the result unknown. Keys it knows on a
        // condition rather than on a value never match there.
        if (operator !in VALUE_OPERATORS) {
            return if (operator in CONDITION_OPERATORS) false else null
        }

        // Evaluate TYPE operator - whether both are of same type
        if (operator == "\$type") {
            val expectedType = (conditionValue as? GBString)?.value
            return getType(attributeValue).toString() == expectedType
        }

        // Evaluate NOT operator - whether condition doesn't contain attribute
        if (operator == "\$not") {
            return not(evalConditionValueResult(conditionValue, attributeValue, savedGroups, visited = visited))
        }

        // Evaluate EXISTS operator - whether condition contains attribute
        // The value is read as a JS boolean, so `$exists: 1` asks for a present attribute and `0` or "" for an absent one
        if (operator == "\$exists") {
            val present = attributeValue != null && attributeValue !is GBNull
            return if (conditionValue.isJsTruthy()) present else !present
        }

        // Equality, ordering and version operators never coerce types: on a type mismatch the
        // positive operator fails and its negation ($ne, $vne) matches
        when (operator) {
            "\$eq" -> return isStrictlyEqual(attributeValue, conditionValue)
            "\$ne" -> return !isStrictlyEqual(attributeValue, conditionValue)
            "\$lt", "\$lte", "\$gt", "\$gte" -> return evalComparison(operator, attributeValue, conditionValue)
            "\$veq", "\$vne", "\$vgt", "\$vgte", "\$vlt", "\$vlte" ->
                return evalVersion(operator, attributeValue, conditionValue)
        }

        if (operator == "\$inGroup" || operator == "\$notInGroup") {
            val entry = savedGroups?.get(conditionValue.asKey())
            if (isNewGroupType(entry)) return null
            // No values means matching nobody. An empty list would pass everyone.
            val values = savedGroupValues(entry) ?: return false
            val found = isIn(attributeValue, values)
            return if (operator == "\$inGroup") found else !found
        }

        /// There are three operators where conditionValue is an array
        if (conditionValue is GBArray) {
            when (operator) {
                // Evaluate IN operator - attributeValue in the conditionValue array
                "\$in" -> {
                    return isIn(attributeValue, conditionValue)
                }
                // Evaluate INI operator - attributeValue in the conditionValue array
                "\$ini" -> {
                    return isIn(attributeValue, conditionValue, true)
                }
                // Evaluate NINI operator - attributeValue in the conditionValue array
                "\$nini" -> {
                    return !isIn(attributeValue, conditionValue, true)
                }
                // Evaluate NIN operator - attributeValue not in the conditionValue array
                "\$nin" -> {
                    return !isIn(attributeValue, conditionValue)
                }
                // Evaluate ALL operator - whether condition contains all attribute
                "\$all" -> {
                    if (attributeValue !is GBArray) return false
                    return isInAll(attributeValue, conditionValue, savedGroups, false, visited)
                }
                // Evaluate ALLI operator - whether condition contains all attribute
                "\$alli" -> {
                    if (attributeValue !is GBArray) return false
                    return isInAll(attributeValue, conditionValue, savedGroups, true, visited)
                }
            }
        } else if (attributeValue is GBArray) {

            when (operator) {
                // Evaluate ElemMATCH operator - whether condition matches attribute
                "\$elemMatch" -> {
                    return elemMatch(attributeValue, conditionValue, savedGroups, visited)
                }
                // Evaluate SIE operator - whether condition size is same as that of attribute
                "\$size" -> {
                    return evalConditionValueResult(
                        conditionValue,
                        GBNumber(attributeValue.size),
                        savedGroups,
                        visited = visited
                    )
                }
            }
        } else if (attributeValue?.isPrimitiveValue() == true) {
            val targetPrimitiveValue = conditionValue as? GBString
            val sourcePrimitiveValue = attributeValue as? GBString

            when (operator) {
                // Evaluate REGEX operator - whether attribute contains condition regex
                "\$regex" -> {
                    return evalRegex(conditionValue = targetPrimitiveValue,
                        attributeValue = sourcePrimitiveValue,
                        ignoreCase = false,
                        negate = false
                    )
                }

                "\$regexi" -> {
                    return evalRegex(conditionValue = targetPrimitiveValue,
                        attributeValue = sourcePrimitiveValue,
                        ignoreCase = true,
                        negate = false
                    )
                }

                "\$notRegex" -> {
                    return evalRegex(conditionValue = targetPrimitiveValue,
                        attributeValue = sourcePrimitiveValue,
                        ignoreCase = false,
                        negate = true
                    )
                }

                "\$notRegexi" -> {
                    return evalRegex(conditionValue = targetPrimitiveValue,
                        attributeValue = sourcePrimitiveValue,
                        ignoreCase = true,
                        negate = true
                    )
                }
            }
        }

        return false
    }

    // An unknown id behaves like an empty list. A condition group or malformed entry has
    // no values, and a typed `list` entry holds them in `values`.
    private fun savedGroupValues(entry: GBValue?): GBArray? =
        when (entry) {
            null -> GBArray(emptyList())
            is GBArray -> entry
            is GBJson -> if ((entry["type"] as? GBString)?.value == "list") entry["values"] as? GBArray else null
            else -> null
        }

    // A saved group type added after this SDK was built
    private fun isNewGroupType(entry: GBValue?): Boolean {
        if (entry !is GBJson) return false
        val type = (entry["type"] as? GBString)?.value
        return type != "list" && type != "condition"
    }

    /** JS truthiness: `false`, `0`, `NaN`, `""` and null are falsy; arrays and objects are truthy. */
    private fun GBValue.isJsTruthy(): Boolean =
        when (this) {
            is GBBoolean -> value
            is GBNumber -> value.toDouble().let { it != 0.0 && !it.isNaN() }
            is GBString -> value.isNotEmpty()
            GBNull, GBValue.Unknown -> false
            is GBArray, is GBJson -> true
        }

    private fun GBValue.asKey(): String =
        when (this) {
            is GBString -> this.value // without quotes
            else -> this.toString()
        }

    /**
     * Like the JS SDK's `isIn`: an array attribute matches if any of its items is in
     * [conditionValue]. Only a string, number, boolean or null can match, because JS compares
     * arrays and objects by reference, so an array or object item is never in the list.
     */
    private fun isIn(actualValue: GBValue?, conditionValue: GBArray, inSensitive: Boolean = false): Boolean {
        fun caseFold(value: GBValue): GBValue =
            if (value is GBString) GBString(value.value.lowercase()) else value

        fun matches(value: GBValue?): Boolean {
            if (value == null || !value.isPrimitiveValue()) return false
            // Case-sensitive lookup uses GBArray.contains, a HashSet for large lists
            if (!inSensitive) return conditionValue.contains(value)
            val folded = caseFold(value)
            return conditionValue.any { it.isPrimitiveValue() && caseFold(it) == folded }
        }

        if (actualValue is GBArray) return actualValue.any { matches(it) }
        return matches(actualValue)
    }

    private fun isInAll(
        actual: GBValue,
        expected: GBArray,
        savedGroups: Map<String, GBValue>?,
        inSensitive: Boolean,
        visited: Set<String>
    ): Boolean? {
        if (actual !is GBArray) return false

        var result: Boolean? = true
        for (expectedItem in expected) {
            var passed: Boolean? = false
            for (actualItem in actual) {
                when (evalConditionValueResult(expectedItem, actualItem, savedGroups, inSensitive, visited)) {
                    true -> {
                        passed = true
                        break
                    }
                    null -> passed = null
                    false -> {}
                }
            }
            if (passed == false) return false
            if (passed == null) result = null
        }
        return result
    }

    /** Mirrors JS `===`: only a string, number, boolean or null can be equal. */
    private fun isStrictlyEqual(attributeValue: GBValue?, conditionValue: GBValue): Boolean =
        attributeValue != null && attributeValue.isPrimitiveValue() && attributeValue == conditionValue

    /**
     * Two strings compare as text and two numbers numerically.
     * Anything else, including booleans and mixed types, is not ordered.
     */
    private fun evalComparison(operator: String, attributeValue: GBValue?, conditionValue: GBValue): Boolean {
        if (attributeValue is GBString && conditionValue is GBString) {
            val order = attributeValue.value.compareTo(conditionValue.value)
            return when (operator) {
                "\$lt" -> order < 0
                "\$lte" -> order <= 0
                "\$gt" -> order > 0
                else -> order >= 0
            }
        }
        if (attributeValue is GBNumber && conditionValue is GBNumber) {
            val actual = attributeValue.value.toDouble()
            val expected = conditionValue.value.toDouble()
            return when (operator) {
                "\$lt" -> actual < expected
                "\$lte" -> actual <= expected
                "\$gt" -> actual > expected
                else -> actual >= expected
            }
        }
        return false
    }

    /**
     * Version operators need both sides to be strings, or both numbers.
     * On a type mismatch only `$vne` matches, as the negation of `$veq`.
     */
    private fun evalVersion(operator: String, attributeValue: GBValue?, conditionValue: GBValue): Boolean {
        val actual = versionInput(attributeValue)
        val expected = versionInput(conditionValue)
        val sameType = (attributeValue is GBString && conditionValue is GBString) ||
            (attributeValue is GBNumber && conditionValue is GBNumber)
        if (!sameType || actual == null || expected == null) {
            return operator == "\$vne"
        }
        val order = GBUtils.paddedVersionString(actual)
            .compareTo(GBUtils.paddedVersionString(expected))
        return when (operator) {
            "\$veq" -> order == 0
            "\$vne" -> order != 0
            "\$vgt" -> order > 0
            "\$vgte" -> order >= 0
            "\$vlt" -> order < 0
            else -> order <= 0
        }
    }

    /** The version string for a string or number, using JS number formatting; "" means version 0. */
    private fun versionInput(value: GBValue?): String? =
        when (value) {
            is GBString -> value.value.ifEmpty { "0" }
            is GBNumber -> value.toJsString()
            else -> null
        }

    private fun evalRegex(conditionValue: GBString?, attributeValue: GBString?, ignoreCase: Boolean, negate: Boolean): Boolean {
        if (conditionValue == null || attributeValue == null) return false

        return try {
            val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
            val regex = Regex(conditionValue.value, options)
            val matches = regex.containsMatchIn(attributeValue.value)
            if (negate) !matches else matches
        } catch (t: Throwable) {
            false
        }
    }

    private companion object {
        // Keys a condition can have that aren't attributes, besides $not, which is also an
        // operator on a value
        val CONDITION_OPERATORS = setOf("\$or", "\$nor", "\$and", "\$savedGroup")

        // Operators this SDK evaluates on a value
        val VALUE_OPERATORS = setOf(
            "\$type", "\$not", "\$exists", "\$eq", "\$ne", "\$lt", "\$lte", "\$gt", "\$gte",
            "\$veq", "\$vne", "\$vgt", "\$vgte", "\$vlt", "\$vlte",
            "\$in", "\$nin", "\$ini", "\$nini", "\$all", "\$alli", "\$elemMatch", "\$size",
            "\$regex", "\$regexi", "\$notRegex", "\$notRegexi", "\$inGroup", "\$notInGroup",
        )
    }
}