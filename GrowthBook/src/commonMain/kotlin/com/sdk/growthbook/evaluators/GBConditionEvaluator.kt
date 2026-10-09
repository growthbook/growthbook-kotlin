package com.sdk.growthbook.evaluators

import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.utils.GBUtils

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

        // A primitive condition converts the attribute to the condition's type before comparing,
        // as the reference SDK does (`mongrule.ts`): `value + "" === condition` for a string,
        // `value * 1 === condition` for a number, `!!value === condition` for a boolean. So
        // `{"age": 25}` matches `"25"`, and `{"beta": true}` matches `1`.
        val actual = attributeValue ?: GBNull
        when (conditionValue) {
            is GBString -> {
                val text = actual.asJsText()
                return if (inSensitive) {
                    text.lowercase() == conditionValue.value.lowercase()
                } else {
                    text == conditionValue.value
                }
            }

            // Two numbers keep the exact comparison, so ids past 2^53 are not rounded into each other
            is GBNumber -> return if (actual is GBNumber) {
                numberEquals(conditionValue, actual)
            } else {
                actual.asJsNumber() == conditionValue.value.toDouble()
            }

            is GBBoolean -> return actual != GBNull && actual.isJsTruthy() == conditionValue.value

            GBNull -> return actual == GBNull

            else -> Unit
        }

        // If conditionValue is array, return true if it's "equal" - "equal"
        // should do a deep comparison for arrays.
        if (conditionValue is GBArray) {
            return attributeValue is GBArray && valueEquals(conditionValue, attributeValue)
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

        // Evaluate EXISTS operator - whether condition contains attribute. The reference SDK reads
        // the condition value in a boolean context (`expected ? actual != null : actual == null`),
        // so it follows JavaScript truthiness rather than requiring a JSON boolean: `$exists: 1`
        // asks for a present attribute, `$exists: 0` for an absent one. Reading only a boolean let
        // any other value fall through to the function's trailing false, for both polarities.
        if (operator == "\$exists") {
            val present = attributeValue != null && attributeValue !is GBNull
            return if (conditionValue.isJsTruthy()) present else !present
        }

        // Evaluate EQ / NE. Dispatched on the operator alone so the pair stays a strict negation
        // for every shape of value: reaching them only for a primitive attribute made both answer
        // false for an array or object, which cannot be right either way round.
        //
        // The reference SDK compares with `===`, which is value equality for primitives but
        // reference identity for arrays and objects. A condition and an attribute are always
        // decoded from separate JSON, so a non-primitive operand can never be identical — `$eq` is
        // false and `$ne` true regardless of the contents. That is reproduced rather than
        // "improved" into a deep comparison: a deep `$eq` would match here and not on the other
        // SDKs, and a rule that behaves differently per platform is worse than one that is
        // uniformly useless. Note plain equality (`{"tags": ["a"]}`) does compare deeply, in this
        // SDK and in the reference alike — that inconsistency is inherited, not introduced here.
        //
        // Numbers compare by value, so `$eq: 25` matches `25.0` as it does in the reference SDK;
        // see [valueEquals].
        if (operator == "\$eq" || operator == "\$ne") {
            val equal = attributeValue != null &&
                attributeValue.isPrimitiveValue() &&
                conditionValue.isPrimitiveValue() &&
                valueEquals(attributeValue, conditionValue)
            return if (operator == "\$eq") equal else !equal
        }

        // Evaluate the version operators. Dispatched on the operator alone, like the reference
        // SDK: `asVersionInput` already renders every shape of value, so there is nothing for an
        // attribute-shape branch to decide. Reaching them only for a primitive attribute made an
        // array or object attribute skip the comparison and fall through to false, where the
        // reference SDK compares it as version "0" (`$veq - array attribute is version 0`).
        val compareVersions: ((String, String) -> Boolean)? = when (operator) {
            "\$veq" -> { source, target -> source == target }
            "\$vne" -> { source, target -> source != target }
            "\$vgt" -> { source, target -> source > target }
            "\$vgte" -> { source, target -> source >= target }
            "\$vlt" -> { source, target -> source < target }
            "\$vlte" -> { source, target -> source <= target }
            else -> null
        }
        if (compareVersions != null) {
            return compareVersions(
                GBUtils.paddedVersionString(attributeValue.asVersionInput()),
                GBUtils.paddedVersionString(conditionValue.asVersionInput()),
            )
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

        // Evaluate the regex family. Dispatched on the operator alone, like the reference SDK:
        // `asRegexInput` renders the attribute as JavaScript would and `evalRegex` decides the
        // rest, so there is nothing for an attribute-shape branch to add. Reaching them only for
        // a primitive attribute made an array or object attribute skip the match and fall through
        // to false — for *both* polarities, the same defect fixed above for `$inGroup` and
        // `$notInGroup`.
        when (operator) {
            "\$regex" -> return evalRegex(
                conditionValue, attributeValue.asRegexInput(), ignoreCase = false, negate = false
            )

            "\$regexi" -> return evalRegex(
                conditionValue, attributeValue.asRegexInput(), ignoreCase = true, negate = false
            )

            "\$notRegex" -> return evalRegex(
                conditionValue, attributeValue.asRegexInput(), ignoreCase = false, negate = true
            )

            "\$notRegexi" -> return evalRegex(
                conditionValue, attributeValue.asRegexInput(), ignoreCase = true, negate = true
            )
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
            fun template(
                stringComparator: (String, String) -> Boolean,
                numberComparator: (Double, Double) -> Boolean,
            ) = comparisonTemplate(
                attributeValue, conditionValue,
                stringComparator, numberComparator
            )

            when (operator) {
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
            }
        }

        return false
    }

    private fun GBValue.asKey(): String =
        when (this) {
            is GBString -> this.value // without quotes
            else -> this.toString()
        }

    /**
     * The text a version operand contributes to a `$v*` comparison.
     *
     * Mirrors the reference SDK's coercion (`util.ts`: a number becomes its string form, and
     * anything else that is not a non-empty string becomes `"0"`). Casting the operand to
     * [GBString] instead silently turned a numeric attribute into version `"0"` and a numeric
     * condition into the empty string, so a payload that sent, say, a build number as a JSON
     * number never matched any version rule — without an error anywhere.
     *
     * An integral number renders without a fractional part (`10`, not `10.0`) because the
     * reference SDK has a single number type and `10.0 + ""` is `"10"` there; keeping the `.0`
     * would split into an extra version segment and change the comparison.
     */
    private fun GBValue?.asVersionInput(): String =
        when (this) {
            is GBString -> value.ifEmpty { "0" }
            is GBNumber -> asPlainString()
            else -> "0"
        }

    /**
     * The text a `$regex` family operand is matched against, or `null` if it has none.
     *
     * The reference SDK passes the attribute straight to `RegExp.prototype.test`, which converts
     * whatever it gets with JavaScript's `String(value)`, so the same conversion is reproduced
     * here (see [asJsText]): an array is matched as its elements joined by commas
     * (`["internal", "beta"]` → `"internal,beta"`), an object as `"[object Object]"`. Casting to
     * [GBString] instead made every non-string attribute fail the match outright, so a regex rule
     * on an id or a build number sent as a JSON number could never match.
     *
     * `null` is the one deliberate exception. JavaScript would render it `"null"`, which makes a
     * pattern like `ull` match a user who does not have the attribute at all — an artefact of the
     * conversion rather than intended targeting, and not pinned by the shared spec corpus.
     */
    private fun GBValue?.asRegexInput(): String? =
        when (this) {
            null, GBNull, GBValue.Unknown -> null
            else -> asJsText()
        }

    /**
     * JavaScript's `String(value)` for a decoded JSON value.
     *
     * Arrays follow `Array.prototype.join`: a `null` element renders as the empty string, and a
     * nested array flattens into the same comma-separated text (`[[1, 2], 3]` → `"1,2,3"`).
     */
    private fun GBValue.asJsText(): String =
        when (this) {
            is GBString -> value
            is GBNumber -> asPlainString()
            is GBBoolean -> value.toString()
            is GBArray -> joinToString(",") { element ->
                if (element is GBNull || element is GBValue.Unknown) "" else element.asJsText()
            }
            is GBJson -> "[object Object]"
            GBNull -> "null"
            GBValue.Unknown -> ""
        }

    /**
     * A number rendered as the reference SDK's single number type would render it: an integral
     * value carries no fractional part (`10`, not `10.0`), matching `String(10.0) === "10"`.
     *
     * Integral Kotlin types are printed as they are rather than routed through [Double]. A `Long`
     * past 2^53 cannot round-trip through a double — `1234567890123456789` comes back as
     * `1234567890123456768` — and ids of that size are ordinary (a snowflake id is 19 digits), so
     * normalising first would quietly rewrite the digits a `$regex` rule matches against.
     *
     * Only a floating-point value needs the round-trip, which is also what decides whether it is
     * integral at all. A double too large for [Long] keeps its own notation (`1.0E20` where
     * JavaScript writes `100000000000000000000`); nothing sane targets a version or a pattern at
     * such a value, and rendering it faithfully would need arbitrary-precision formatting that
     * common code does not have.
     */
    private fun GBNumber.asPlainString(): String =
        when (val number = value) {
            is Byte, is Short, is Int, is Long -> number.toString()
            else -> {
                val asDouble = number.toDouble()
                if (asDouble.isFinite() && asDouble == asDouble.toLong().toDouble()) {
                    asDouble.toLong().toString()
                } else {
                    number.toString()
                }
            }
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

        // Case-sensitive path uses GBArray.containsValue → HashSet membership.
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
                        inSensitive = inSensitive, visited = visited
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
                actualGbValue?.asJsNumber() ?: Double.NaN,
                expectedGbValue.asJsNumber(),
            )
        }
    }

    /**
     * JavaScript's `Number(value)` for a decoded JSON value: what the reference SDK's `<` / `>`
     * apply when the operands are not both strings.
     *
     * A value with no numeric reading is `NaN`, and every comparison with `NaN` is false. It used to
     * become `0`, so `{"v": {"$lt": 1}}` passed for `"abc"`; and a boolean was `0` either way, so
     * `true` was not greater than `0`.
     */
    private fun GBValue.asJsNumber(): Double =
        when (this) {
            is GBNumber -> value.toDouble()
            is GBBoolean -> if (value) 1.0 else 0.0
            GBNull -> 0.0
            is GBString -> value.asJsNumber()
            // `Number([5])` is `Number("5")`: an array converts through its text
            is GBArray -> asJsText().asJsNumber()
            is GBJson, GBValue.Unknown -> Double.NaN
        }

    /**
     * JavaScript's `Number(text)`. Kotlin's `toDoubleOrNull` is not that: it accepts a type suffix
     * (`"1f"`, `"1d"`), which JavaScript reads as `NaN`, and rejects `0x10`, which JavaScript reads
     * as 16. Surrounding whitespace is ignored and an empty string is 0, as in JavaScript.
     */
    private fun String.asJsNumber(): Double {
        val text = trim()
        if (text.isEmpty()) return 0.0
        if (JS_DECIMAL_LITERAL.matches(text)) return text.toDouble()
        val radix = when (text.take(2)) {
            "0x", "0X" -> 16
            "0o", "0O" -> 8
            "0b", "0B" -> 2
            else -> return Double.NaN
        }
        val digits = text.drop(2)
        if (digits.isEmpty() || digits.any { it.digitToIntOrNull(radix) == null }) return Double.NaN
        return digits.fold(0.0) { acc, digit -> acc * radix + digit.digitToInt(radix) }
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
     * The two operands are treated differently on purpose, following the reference SDK.
     *
     * [attributeValue] is data: it arrives already converted by [asRegexInput], because
     * `RegExp.prototype.test` stringifies whatever it is handed.
     *
     * [pattern] is not data — it is compiled. The reference SDK calls `String.replace` on it while
     * building the `RegExp`, so a pattern that is not a string throws there and is caught as "no
     * match". Requiring a [GBString] here reproduces that without relying on an exception.
     */
    private fun evalRegex(
        pattern: GBValue,
        attributeValue: String?,
        ignoreCase: Boolean,
        negate: Boolean
    ): Boolean {
        // A pattern that cannot be used fails both polarities, negated or not. That is the
        // reference SDK's contract too: it wraps the match in a try/catch that returns false, and
        // a non-string pattern throws on its way into the RegExp constructor.
        val patternText = (pattern as? GBString)?.value ?: return false

        // An absent or null attribute matches no pattern, and therefore does not-match every one.
        // The reference stringifies it instead — `null` becomes the text "null" — which
        // [asRegexInput] deliberately does not reproduce; but whichever way that is decided, the
        // pair has to stay a strict negation. Answering false both ways made "everyone without a
        // corporate email" match nobody.
        if (attributeValue == null) return negate

        return try {
            val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
            val regex = Regex(patternText, options)
            val matches = regex.containsMatchIn(attributeValue)
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
}