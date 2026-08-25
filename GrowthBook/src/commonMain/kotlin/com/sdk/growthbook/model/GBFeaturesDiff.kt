package com.sdk.growthbook.model

import com.sdk.growthbook.utils.GBFeatures

data class GBFeatureChange(val old: GBFeature, val new: GBFeature)

data class GBFeaturesDiff(
    val added: GBFeatures,
    val removed: GBFeatures,
    val changed: Map<String, GBFeatureChange>
) {
    val hasChanges: Boolean = added.isNotEmpty() || removed.isNotEmpty() || changed.isNotEmpty()

    val changedKeys: Set<String> = added.keys + removed.keys + changed.keys
}

internal fun diffFeatures(old: GBFeatures, new: GBFeatures): GBFeaturesDiff {
    val added = new.filterKeys { it !in old }
    val removed = old.filterKeys { it !in new }

    val changed = new.mapNotNull { (key, newFeature) ->
        val oldFeature = old[key] ?: return@mapNotNull null
        if (oldFeature != newFeature) key to GBFeatureChange(oldFeature, newFeature) else null
    }.toMap()
    return GBFeaturesDiff(added, removed, changed)
}