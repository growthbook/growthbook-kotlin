package com.sdk.growthbook.stickybucket

import kotlinx.coroutines.CoroutineScope
import com.sdk.growthbook.utils.GBStickyAssignmentsDocument
import com.sdk.growthbook.sandbox.CachingLayer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

internal class GBStickyBucketServiceImp(
    override val coroutineScope: CoroutineScope,
    private val prefix: String = "gbStickyBuckets__",
    private val localStorage: CachingLayer? = null
) : GBStickyBucketService {

    /**
     * Method for get assignment document from cache by attribute name and attribute value
     */
    override suspend fun getAssignments(
        attributeName: String,
        attributeValue: String
    ): GBStickyAssignmentsDocument? {
        val key = "$attributeName||$attributeValue"

        localStorage?.let { localStorage ->
            localStorage.getContent("$prefix$key")?.let { data ->
                return try {
                    Json.decodeFromJsonElement<GBStickyAssignmentsDocument>(data)
                } catch (e: Exception) {
                    null
                }
            }
        }

        return null
    }

    /**
     * Method for saving assignments document to cache
     */
    override suspend fun saveAssignments(doc: GBStickyAssignmentsDocument) {
        val key = "${doc.attributeName}||${doc.attributeValue}"

        localStorage?.let { localStorage ->
            try {
                val docDataString = Json.encodeToString(doc)
                val jsonElement: JsonElement = Json.parseToJsonElement(docDataString)
                localStorage.saveContent("$prefix$key", jsonElement)
            } catch (e: Exception) {
                // Handle JSON serialization error
            }
        }
    }

    /**
     * Method for getting sticky bucket assignments from attributes of context
     */
    override suspend fun getAllAssignments(
        attributes: Map<String, String>
    ): Map<String, GBStickyAssignmentsDocument> {
        val docs =
            mutableMapOf<String, GBStickyAssignmentsDocument>()

        attributes.forEach { (key, value) ->
            getAssignments(key, value)?.let { doc ->
                val docKey = "${doc.attributeName}||${doc.attributeValue}"
                docs[docKey] = doc
            }
        }

        return docs
    }
}