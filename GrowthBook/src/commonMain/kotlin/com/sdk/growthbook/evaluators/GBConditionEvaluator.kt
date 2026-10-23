package com.sdk.growthbook.evaluators

import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.utils.GBUtils
import com.sdk.growthbook.model.GBNumber

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
 * A JavaScript decimal numeric string: an optional sign, then `Infinity` or digits with an optional
 * fraction and exponent (`5`, `.5`, `5.`, `1e3`). No type suffix and no non-decimal prefix.
 */
private val JS_DECIMAL_LITERAL = Regex("""[+-]?(Infinity|(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?)""")

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
        savedGroups: Map<String, GBValue>?,
        visited: Set<String> = emptySet()
    ): Boolean {
        for ((key, value) in conditionObj) {
            when (key) {
                "\$or" -> {
                    // If conditionObj has a key $or, return evalOr(attributes, condition["$or"])
                    val targetItems = conditionObj[key] as? GBArray
                    if (targetItems != null) {
                        if (!evalOr(attributes, targetItems, savedGroups, visited)) {
                            return false
                        }
                    }
                }

                "\$nor" -> {
                    // If conditionObj has a key $nor, return !evalOr(attributes, condition["$nor"])
                    val targetItems = conditionObj[key] as? GBArray
                    if (targetItems != null) {
                        if (evalOr(attributes, targetItems, savedGroups, visited)) {
                            return false
                        }
                    }
                }

                "\$and" -> {
                    // If conditionObj has a key $and, return !evalAnd(attributes, condition["$and"])
                    val targetItems = conditionObj[key] as? GBArray
                    if (targetItems != null) {
                        if (!evalAnd(attributes, targetItems, savedGroups, visited)) {
                            return false
                        }
                    }
                }

                "\$not" -> {
                    // If conditionObj has a key $not, return !evalCondition(attributes, condition["$not"])
                    val targetItem = conditionObj[key] as? GBJson
                    if (targetItem != null) {
                        if (evalCondition(attributes, targetItem, savedGroups, visited)) {
                            return false
                        }
                    }
                }

                "\$savedGroup" -> {
                    if (!evalSavedGroup(
                            attributes = attributes,
                            reference = value,
                            savedGroups = savedGroups,
                            visited = visited
                        )
                    ) {
                        return false
                    }
                }

                else -> {
                    val element = getPath(attributes, key)
                    // If evalConditionValue(value, getPath(attributes, key)) is false,
                    // break out of loop and return false
                    if (!evalConditionValue(
                            conditionValue = value,
                            attributeValue = element,
                            savedGroups = savedGroups,
                            visited = visited
                        )
                    ) {
                        return false
                    }
                }
            }
        }

        // If none of the entries failed their checks, `evalCondition` returns true
        return true
    }

    /**
     * Evaluates a prerequisite (`parentConditions`) condition.
     * A malformed condition, such as `{"$not": null}`, never matches, so the gated rule
     * or experiment is skipped. Attribute targeting conditions are unaffected.
     */
    fun evalParentCondition(
        attributes: Map<String, GBValue>,
        conditionObj: GBJson,
        savedGroups: Map<String, GBValue>?
    ): Boolean = !isMalformed(conditionObj) && evalCondition(attributes, conditionObj, savedGroups)

    // The shapes the JS SDK cannot evaluate: a null where a condition object is required
    private fun isMalformed(conditionObj: GBJson): Boolean =
        conditionObj.any { (key, value) ->
            when (key) {
                "\$or", "\$nor", "\$and" -> value is GBArray &&
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
                if (evalCondition(attributes, gbJson, savedGroups, visited)) {
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
        savedGroups: Map<String, GBValue>?,
        visited: Set<String>
    ): Boolean {

        // Loop through the conditionObjects
        for (item in conditionObjs) {
            val gbJson = item as? GBJson ?: return false

            // If evalCondition(attributes, conditionObjs[i]) is false,
            // break out of the loop and return false
            if (!evalCondition(attributes, gbJson, savedGroups, visited)) {
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
     * @return the value at that path, or [GBNull] if the path does not exist.
     *
     * A segment that cannot be descended into ends the walk. Skipping it instead and returning
     * the last value reached made a path resolve to a value it does not name: `user.id` against
     * `{"user": "u_1"}` answered `"u_1"`, so a rule targeting `user.id` matched a user who has no
     * such attribute — and, through a saved group's `attributeKey`, could place them inside a
     * group rather than outside it.
     *
     * Only objects are descended into. The reference SDK also reaches array indices and `length`,
     * since both are `in` an array in JavaScript; that is an artefact of the host language rather
     * than targeting anyone meant to express, and it is not reproduced.
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
            val nested = element as? GBJson ?: return GBNull
            element = nested[path] ?: GBNull
        }

        return element
    }

    /**
     * Evaluates Condition Value against given condition & attributes
     */
    fun evalConditionValue(
        conditionValue: GBValue,
        attributeValue: GBValue?,
        savedGroups: Map<String, GBValue>?,
        inSensitive: Boolean = false,
        visited: Set<String> = emptySet()
    ): Boolean {

        // A string, number or boolean only equals an attribute of the same type: no coercion
        if (conditionValue is GBString || conditionValue is GBNumber || conditionValue is GBBoolean) {
            if (getType(attributeValue) != getType(conditionValue)) {
                return false
            }
            if (inSensitive && conditionValue is GBString && attributeValue is GBString) {
                return conditionValue.value.equals(attributeValue.value, ignoreCase = true)
            }
            return valueEquals(conditionValue, attributeValue)
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
                for (key in conditionValue.keys) {
                    // If evalOperatorCondition(key, attributeValue, value) is false, return false
                    if (!evalOperatorCondition(
                            key,
                            attributeValue,
                            conditionValue[key]!!,
                            savedGroups,
                            visited
                        )
                    ) {
                        return false
                    }
                }
            } else if (attributeValue != null) {
                return valueEquals(conditionValue, attributeValue)
            } else {
                return false
            }
        }

        // Return true
        return true
    }

    /**
     * Resolves a `$savedGroup` reference: is the user a member of the group it names?
     *
     * Unlike `$inGroup`, this is a top-level operator with no attribute of its own, so the entry
     * decides what membership means — a list entry names the attribute to test, and a condition
     * entry is evaluated in full.
     *
     * Anything this SDK cannot make sense of matches nobody rather than throwing. A payload is
     * allowed to be newer than the SDK reading it, and a group type added later must neither take
     * the host app down nor quietly let everyone through.
     *
     * [visited] holds the ids currently being resolved, so a group that references itself —
     * directly or along a chain — stops instead of recursing forever.
     */
    private fun evalSavedGroup(
        attributes: Map<String, GBValue>,
        reference: GBValue,
        savedGroups: Map<String, GBValue>?,
        visited: Set<String>
    ): Boolean {
        // The operator takes an object; a bare id string or an array is not one
        val ref = reference as? GBJson ?: return false

        val id = (ref["id"] as? GBString)?.value ?: return false
        if (id in visited) return false

        // An override that is present but unusable is not ignored: falling back to the entry's
        // own attribute would test a different population than the payload asked for.
        val overrideKey = when (val raw = ref["attributeKey"]) {
            null -> null
            is GBString -> raw.value
            else -> return false
        }

        // Absent from the payload, or a v1 bare array, which carries neither a type to act on
        // nor an attribute for this operator to use
        val entry = savedGroups?.get(id) as? GBJson ?: return false

        return when ((entry["type"] as? GBString)?.value) {
            "list" -> {
                val attributeKey = overrideKey
                    ?: (entry["attributeKey"] as? GBString)?.value
                    ?: return false
                val values = entry["values"] as? GBArray ?: return false
                isIn(actualValue = getPath(attributes, attributeKey), conditionValue = values)
            }

            // A condition group has no single attribute, so an override has nothing to override
            "condition" -> {
                val condition = entry["condition"] as? GBJson ?: return false
                evalCondition(attributes, condition, savedGroups, visited + id)
            }

            // A group type added after this SDK was built
            else -> false
        }
    }

    private fun arraysEqual(arr1: GBArray, arr2: GBArray): Boolean {
        if (arr1.size == arr2.size) {
            for (i in arr1.indices) {
                if (!valueEquals(arr1[i], arr2[i])) {
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
        savedGroups: Map<String, GBValue>?,
        visited: Set<String>
    ): Boolean {

        if (attributeValue is GBArray) {
            // Loop through items in attributeValue
            for (item in attributeValue) {
                // Skip null elements only, like the reference SDK. Falsy-but-present members
                // (0, false, "") are valid values and must still be tested against the condition
                // — guarding on truthiness instead is the defect sdk-js fixed in #6323.
                if (item is GBNull) continue

                val attributes = if (item is GBJson) {
                    HashMap(item)
                } else {
                    mapOf("value" to item)
                }

                // If isOperatorObject(condition)
                if (condition is GBJson && isOperatorObject(condition)) {
                    // If evalConditionValue(condition, item), break out of loop and return true
                    if (evalConditionValue(
                            conditionValue = condition,
                            attributeValue = item,
                            savedGroups = savedGroups,
                            visited = visited
                        )
                    ) {
                        return true
                    }
                }
                // Else if evalCondition(item, condition), break out of loop and return true
                else if (evalCondition(
                        attributes,
                        condition as? GBJson ?: GBJson(emptyMap()),
                        savedGroups,
                        visited
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
        visited: Set<String> = emptySet()
    ): Boolean {

        // Evaluate TYPE operator - whether both are of same type
        if (operator == "\$type") {
            val expectedType = (conditionValue as? GBString)?.value
            return getType(attributeValue).toString() == expectedType
        }

        // Evaluate NOT operator - whether condition doesn't contain attribute
        if (operator == "\$not") {
            return !evalConditionValue(
                conditionValue = conditionValue,
                attributeValue = attributeValue,
                savedGroups = savedGroups,
                visited = visited
            )
        }

        // Evaluate EXISTS operator - whether condition contains attribute
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

        // Evaluate INGROUP / NOTINGROUP operators - whether the attribute is a member of a saved
        // group. Dispatched on the operator alone, like the reference SDK, rather than from the
        // attribute-shape branches below: `isIn` already covers an array attribute (intersection),
        // a primitive one, and an absent one. Reaching these only for a primitive attribute made
        // both operators answer false for a multi-value attribute such as `tags: ["a", "b"]`, so
        // an exclusion by saved group silently matched nobody — and so did its inclusion twin.
        if (operator == "\$inGroup" || operator == "\$notInGroup") {
            // An entry these operators cannot read fails *both* of them closed. Substituting an
            // empty list would be the loud failure: `$notInGroup` would pass everyone through an
            // exclusion rule. An id merely absent from the payload still resolves to an empty
            // list, so `$notInGroup` keeps passing for one — documented behaviour every SDK
            // implements, with its own conformance case.
            val group = savedGroupListValues(savedGroups?.get(conditionValue.asKey()))
                ?: return false
            val isMember = isIn(attributeValue, group)
            return if (operator == "\$inGroup") isMember else !isMember
        }

        /// There are three operators where conditionValue is an array
        if (conditionValue is GBArray) {
            when (operator) {
                // Evaluate IN operator - attributeValue in the conditionValue array
                "\$in" -> {
                    return if (attributeValue is GBArray) {
                        isIn(attributeValue, conditionValue)
                    } else {
                        attributeValue != null && conditionValue.containsValue(attributeValue)
                    }
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
                    return if (attributeValue is GBArray) {
                        !isIn(attributeValue, conditionValue)
                    } else {
                        attributeValue == null || !conditionValue.containsValue(attributeValue)
                    }
                }
                // Evaluate ALL operator - whether condition contains all attribute
                "\$all" -> {
                    if (attributeValue !is GBArray) return false
                    return isInAll(
                        actual = attributeValue,
                        expected = conditionValue,
                        savedGroups = savedGroups,
                        inSensitive = false,
                        visited = visited
                    )
                }
                // Evaluate ALLI operator - whether condition contains all attribute
                "\$alli" -> {
                    if (attributeValue !is GBArray) return false
                    return isInAll(
                        actual = attributeValue,
                        expected = conditionValue,
                        savedGroups = savedGroups,
                        inSensitive = true,
                        visited = visited
                    )
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
                    return evalConditionValue(
                        conditionValue = conditionValue,
                        attributeValue = GBNumber(attributeValue.size),
                        savedGroups = savedGroups,
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
            }
        }

        return false
    }

    private fun GBValue.asKey(): String =
        when (this) {
            is GBString -> this.value // without quotes
            else -> this.toString()
        }

    private fun isIn(
        actualValue: GBValue?,
        conditionValue: GBArray,
        inSensitive: Boolean = false
    ): Boolean {
        fun caseFold(value: GBValue?): GBValue? {
            return if (inSensitive && value is GBString) {
                GBString(value.value.lowercase())
            } else {
                value
            }
        }

        // Case-sensitive path uses GBArray.contains → HashSet membership.
        if (!inSensitive) {
            if (actualValue is GBArray) {
                if (actualValue.isEmpty()) return false
                return actualValue.any { conditionValue.containsValue(it) }
            }
            return actualValue != null && conditionValue.containsValue(actualValue)
        }

        if (actualValue is GBArray) {
            if (actualValue.isEmpty()) return false

            return actualValue.any { attr ->
                conditionValue.any { cond ->
                    valueEquals(caseFold(attr), caseFold(cond))
                }
            }
        }

        return conditionValue.any {
            valueEquals(caseFold(actualValue), caseFold(it))
        }
    }

    private fun isInAll(
        actual: GBValue,
        expected: GBArray,
        savedGroups: Map<String, GBValue>?,
        inSensitive: Boolean = false,
        visited: Set<String>
    ): Boolean {
        if (actual !is GBArray) return false

        for (expectedItem in expected) {
            var passed = false

            for (actualItem in actual) {
                if (evalConditionValue(
                        conditionValue = expectedItem,
                        attributeValue = actualItem,
                        savedGroups = savedGroups,
                        inSensitive = inSensitive,
                        visited = visited
                    )
                ) {
                    passed = true
                    break
                }
            }
            if (!passed) return false
        }
        return true
    }

    /** Mirrors JS `===`: only a string, number, boolean or null can be equal. */
    private fun isStrictlyEqual(attributeValue: GBValue?, conditionValue: GBValue): Boolean =
        attributeValue != null && attributeValue.isPrimitiveValue() && valueEquals(attributeValue, conditionValue)

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
            is GBNumber -> {
                val number = value.value.toDouble()
                if (number % 1.0 == 0.0 && number.isFinite()) number.toLong().toString()
                else value.value.toString()
            }
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

    /**
     * The values `$inGroup` / `$notInGroup` compare against, or `null` when the entry carries
     * none and both operators must therefore fail closed.
     *
     * A `savedGroups` entry is either the bare array these operators were built for, or a
     * `savedGroupReferencesV2` typed entry. Only the list flavour of the latter has values to
     * offer: a condition group is resolved through `$savedGroup`, which knows how to evaluate it
     * and where to find an attribute for it — neither of which is true here.
     *
     * The split between an absent id (an empty list) and an unreadable entry (`null`) is
     * deliberate; the call site explains why.
     */
    private fun savedGroupListValues(entry: GBValue?): GBArray? = when (entry) {
        null -> GBArray(emptyList())
        is GBArray -> entry
        is GBJson if (entry["type"] as? GBString)?.value == "list" -> entry["values"] as? GBArray
        else -> null
    }

    /**
     * JavaScript truthiness, for the reference SDK's boolean contexts: `false`, `0`, `NaN`, `""`
     * and `null` are falsy; everything else, arrays and objects included, is truthy.
     */
    private fun GBValue.isJsTruthy(): Boolean =
        when (this) {
            is GBBoolean -> value
            is GBNumber -> value.toDouble().let { it != 0.0 && !it.isNaN() }
            is GBString -> value.isNotEmpty()
            GBNull, GBValue.Unknown -> false
            is GBArray, is GBJson -> true
        }

    /**
     * Equality between two decoded JSON values as targeting sees it: a number equals another number
     * of the same value whatever its Kotlin type, as in the reference SDK, where JavaScript has a
     * single number type and `25 === 25.0`. Arrays and objects compare element by element under
     * the same rule.
     *
     * [GBNumber]'s own `equals` deliberately keeps integer and floating-point values distinct, and
     * it is left alone: that is a property of the value model, used well beyond targeting. A rule
     * written as `{"age": 25}` must still match an app that sets the attribute as `25.0`, so the
     * evaluator compares by value instead.
     */
    private fun valueEquals(a: GBValue?, b: GBValue?): Boolean =
        when {
            a is GBNumber && b is GBNumber -> numberEquals(a, b)
            a is GBArray && b is GBArray ->
                a.size == b.size && a.indices.all { valueEquals(a[it], b[it]) }
            a is GBJson && b is GBJson ->
                a.keys == b.keys && a.keys.all { valueEquals(a[it], b[it]) }
            else -> a == b
        }

    private fun numberEquals(a: GBNumber, b: GBNumber): Boolean {
        val x = a.value
        val y = b.value
        // Two integral values compare exactly, so ids past 2^53 are not rounded into each other
        if (x.isIntegral() && y.isIntegral()) return x.toLong() == y.toLong()
        return x.toDouble() == y.toDouble()
    }

    private fun Number.isIntegral(): Boolean =
        this is Byte || this is Short || this is Int || this is Long

    /**
     * Membership by value, under the same rule as [valueEquals].
     *
     * [GBArray.contains] is a hash lookup under [GBNumber]'s own equality, which keeps `25` and
     * `25.0` apart. Rather than scan the list, a number is also looked up in its other form — an
     * integral value as a double, an integral double as a long — which keeps the lookup O(1) for
     * the large `$in` lists the hash index exists for.
     */
    private fun GBArray.containsValue(value: GBValue): Boolean {
        if (contains(value)) return true
        if (value !is GBNumber) return false
        val number = value.value
        val otherForm = if (number.isIntegral()) {
            GBNumber(number.toLong().toDouble())
        } else {
            val asDouble = number.toDouble()
            // Only a finite integral double within Long range has an integer twin
            if (!asDouble.isFinite() || asDouble != kotlin.math.truncate(asDouble) ||
                asDouble < Long.MIN_VALUE.toDouble() || asDouble > Long.MAX_VALUE.toDouble()
            ) {
                return false
            }
            GBNumber(asDouble.toLong())
        }
        return contains(otherForm)
    }
}