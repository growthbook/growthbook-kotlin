package com.sdk.growthbook.model

import com.sdk.growthbook.utils.GBFeatures

/** SDK-owned: produced by [diffFeatures] and handed to the features-change handler, read-only. */
@ConsistentCopyVisibility
data class GBFeatureChange internal constructor(val old: GBFeature, val new: GBFeature)

/** SDK-owned: produced by [diffFeatures] and handed to the features-change handler, read-only. */
@ConsistentCopyVisibility
data class GBFeaturesDiff internal constructor(
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