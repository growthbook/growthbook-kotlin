package com.sdk.growthbook.utils

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extension over JsonObject class to convert that into HashMap
 */
internal fun JsonObject.toHashMap(): HashMap<String, Any> {
    val map: HashMap<String, Any> = HashMap()
    this.forEach {
        val key = it.key
        when (val value = it.value) {
            is JsonObject -> map[key] = value.toHashMap()
            is JsonArray -> map[key] = value.toList()
            else -> map[key] = value.jsonPrimitive.content
        }
    }
    return map
}

/**
 * Extension over JsonArray class to convert that into List
 */
internal fun JsonArray.toList(): List<*> {
    val list: MutableList<Any> = mutableListOf()
    this.forEach {
        when (val value = it) {
            is JsonObject -> list.add((value).toHashMap())
            is List<*> -> list.add(value.toList())
            else -> list.add(value.jsonPrimitive.content)
        }
    }
    return list
}

/**
 * Extension over List class to convert that into JsonArray
 */
internal fun List<*>.toJsonElement(): JsonElement {
    val list: MutableList<JsonElement> = mutableListOf()
    this.forEach {
        val value = it ?: return@forEach
        when (value) {
            is Map<*, *> -> list.add((value).toJsonElement())
            is List<*> -> list.add(value.toJsonElement())
            is Boolean -> list.add(JsonPrimitive(value))
            is Number -> list.add(JsonPrimitive(value))
            else -> list.add(JsonPrimitive(value.toString()))
        }
    }
    return JsonArray(list)
}

/**
 * Extension over Map class to convert that into JsonObject
 */
internal fun Map<*, *>.toJsonElement(): JsonElement {
    val map: MutableMap<String, JsonElement> = mutableMapOf()
    this.forEach {
        val key = it.key as? String ?: return@forEach
        val value = it.value ?: return@forEach
        map[key] = when (value) {
            is Map<*, *> -> (value).toJsonElement()
            is List<*> -> value.toJsonElement()
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
    }
    return JsonObject(map)
}
