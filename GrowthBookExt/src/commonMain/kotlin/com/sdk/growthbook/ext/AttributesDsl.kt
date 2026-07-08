package com.sdk.growthbook.ext

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue

/**
 * Converts an arbitrary Kotlin value into its [GBValue] representation.
 *
 * Supported inputs:
 * - `null` -> [GBNull]
 * - [Boolean] -> [GBBoolean]
 * - [String] -> [GBString]
 * - [Number] (Int/Long/Float/Double/…) -> [GBNumber]
 * - [Map] with [String] keys -> [GBJson] (values converted recursively)
 * - [List] -> [GBArray] (elements converted recursively)
 * - an already-built [GBValue] -> returned unchanged
 *
 * Any other type — or a [Map] with a non-String key — throws
 * [IllegalArgumentException], since GrowthBook attributes have no representation
 * for it.
 *
 * @throws IllegalArgumentException when [value] cannot be represented as a [GBValue]
 */
fun Any?.toGBValue(): GBValue =
    when (this) {
        null -> GBNull
        is GBValue -> this
        is Boolean -> GBBoolean(this)
        is String -> GBString(this)
        is Number -> GBNumber(this)
        is Map<*, *> -> GBJson(
            entries.associate { (key, mapValue) ->
                require(key is String) {
                    "GrowthBook attributes keys must be String, but got: $key (${key?.let { it::class.simpleName }})"
                }
                key to mapValue.toGBValue()
            }
        )

        is List<*> -> GBArray(map { it.toGBValue() })
        else -> throw IllegalArgumentException("Cannot convert value of type ${this::class.simpleName} to GBValue: $this")
    }

/**
 * Marks the receiver scope of the attributes DSL so that an inner
 * [GBAttributesBuilder] cannot implicitly capture the outer builder's members.
 */
@DslMarker
annotation class GBAttributesDsl

/**
 * Builder scope for the attributes DSL. Collects key/value pairs into an ordered
 * map of [GBValue], preserving insertion order.
 *
 * Use [to] to add an entry and [obj] to nest a JSON object:
 * ```kotlin
 * val attrs = buildAttributes {
 *     "id" to "user-123"
 *     "premium" to true
 *     "age" to 42
 *     "tags" to listOf("a", "b")
 *     "address" to obj {
 *         "city" to "Kyiv"
 *     }
 * }
 * ```
 */
@GBAttributesDsl
class GBAttributesBuilder {
    @PublishedApi
    internal val entries: MutableMap<String, GBValue> = LinkedHashMap()

    /**
     * Associates this key with [value], converting it via [toGBValue].
     * A later entry with the same key overwrites the earlier one.
     */
    infix fun String.to(value: Any?) {
        entries[this] = value.toGBValue()
    }

    /**
     * Builds a nested JSON object using the same DSL, for use as a value:
     * `"address" to obj { "city" to "Kyiv" }`.
     */
    fun obj(block: GBAttributesBuilder.() -> Unit): GBValue =
        GBJson(GBAttributesBuilder().apply(block).entries)

    /** Returns the accumulated attributes as an immutable map. */
    fun build(): Map<String, GBValue> = entries.toMap()
}

/**
 * Builds a `Map<String, GBValue>` of user attributes using the attributes DSL,
 * hiding the [GBValue] wrappers behind plain Kotlin values.
 *
 * ```kotlin
 * val attrs = buildAttributes {
 *     "id" to "user-123"
 *     "country" to "UA"
 * }
 * ```
 */
inline fun buildAttributes(block: GBAttributesBuilder.() -> Unit): Map<String, GBValue> =
    GBAttributesBuilder().apply(block).build()

/**
 * Replaces the SDK's user attributes using the attributes DSL — a sugar bridge
 * over [GrowthBookSDK.setAttributes] that hides the [GBValue] wrappers.
 *
 * ```kotlin
 * sdk.setAttributes {
 *     "id" to "user-123"
 *     "premium" to true
 * }
 * ```
 */
inline fun GrowthBookSDK.setAttributes(block: GBAttributesBuilder.() -> Unit) {
    setAttributes(buildAttributes(block))
}

suspend inline fun GrowthBookSDK.setAttributesSync(block: GBAttributesBuilder.() -> Unit) {
    setAttributesSync(buildAttributes(block))
}