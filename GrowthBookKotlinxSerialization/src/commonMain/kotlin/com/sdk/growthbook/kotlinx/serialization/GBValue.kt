package com.sdk.growthbook.kotlinx.serialization

import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import kotlinx.serialization.json.float
import kotlinx.serialization.json.double
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonPrimitive
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBBoolean

fun GBValue.gbSerialize(): JsonElement =
    when(this) {
        is GBBoolean -> JsonPrimitive(this.value)
        is GBString -> JsonPrimitive(this.value)
        is GBNumber -> JsonPrimitive(this.value)
        is GBArray -> JsonArray(
            this.map { it.gbSerialize() }
        )
        is GBJson -> JsonObject(
            this.mapValues { it.value.gbSerialize() }
        )
        is GBValue.Unknown -> JsonNull
    }

fun GBValue.Companion.from(jsonElement: JsonElement): GBValue =
    when(jsonElement) {
        is JsonPrimitive -> {
            when {
                jsonElement.isString -> GBString(jsonElement.content)
                jsonElement.intOrNull != null -> GBNumber(jsonElement.int)
                jsonElement.longOrNull != null -> GBNumber(jsonElement.long)
                jsonElement.floatOrNull != null -> GBNumber(jsonElement.float)
                jsonElement.doubleOrNull != null -> GBNumber(jsonElement.double)
                jsonElement.booleanOrNull != null -> GBBoolean(jsonElement.boolean)
                else -> GBValue.Unknown
            }
        }
        is JsonArray -> GBArray(
            jsonElement.map { from(it) }
        )
        is JsonObject -> GBJson(
            jsonElement.mapValues { from(it.value) }
        )
        else -> GBValue.Unknown
    }
