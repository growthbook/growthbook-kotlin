package com.sdk.growthbook.utils

/**
 * Enum that used in strategy for building url
 */
enum class FeatureRefreshStrategy {
    STALE_WHILE_REVALIDATE, SERVER_SENT_EVENTS,
    SERVER_SENT_REMOTE_FEATURE_EVAL
}
