package com.sdk.growthbook.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

data class GBBoolean(val value: Boolean): GBValue()
data class GBString(val value: String): GBValue()
data class GBNumber(val value: Number): GBValue()
data class GBJson(
    private val value: Map<String, GBValue>,
): GBValue(), Map<String, GBValue> by value

sealed class GBValue {
    data object Unknown: GBValue()

    internal fun gbSerialize(): JsonElement =
        when(this) {
            is GBBoolean -> JsonPrimitive(this.value)
            is GBString -> JsonPrimitive(this.value)
            is GBNumber -> JsonPrimitive(this.value)
            is GBJson -> JsonObject(
                this.mapValues { it.value.gbSerialize() }
            )
            is Unknown -> JsonNull
        }

    companion object {
        internal fun from(anyObj: Any): GBValue =
            when(anyObj) {
                is Boolean -> GBBoolean(anyObj)
                is String -> GBString(anyObj)
                is Number -> GBNumber(anyObj)
                else -> Unknown
            }

        internal fun from(jsonElement: JsonElement): GBValue =
            when(jsonElement) {
                is JsonPrimitive -> {
                    when {
                        jsonElement.isString -> GBString(jsonElement.content)
                        jsonElement.intOrNull != null -> GBNumber(jsonElement.int)
                        jsonElement.longOrNull != null -> GBNumber(jsonElement.long)
                        jsonElement.floatOrNull != null -> GBNumber(jsonElement.float)
                        jsonElement.doubleOrNull != null -> GBNumber(jsonElement.double)
                        jsonElement.booleanOrNull != null -> GBBoolean(jsonElement.boolean)
                        else -> Unknown
                    }
                }
                is JsonObject -> GBJson(
                    jsonElement.mapValues { from(it.value) }
                )
                else -> Unknown
            }
    }
}
