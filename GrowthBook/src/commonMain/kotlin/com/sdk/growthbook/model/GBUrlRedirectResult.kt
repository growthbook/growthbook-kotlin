package com.sdk.growthbook.model

/**
 * The outcome of one URL-redirect (split-URL) experiment evaluated by
 * [com.sdk.growthbook.GrowthBookSDK.getUrlRedirects].
 *
 * At most one is emitted per call: evaluation stops at the first redirect experiment the user is
 * enrolled in — i.e. the one that fired an exposure.
 */
data class GBUrlRedirectResult(

    /**
     * Whether the user was enrolled in the experiment. Always true for an emitted result; the field
     * exists for parity with the cross-SDK conformance cases.
     */
    val inExperiment: Boolean,

    /**
     * The `urlRedirect` declared on the assigned variation, or null when the assigned variation
     * (typically the control) declares none.
     */
    val urlRedirect: String?,

    /**
     * The resolved redirect URL, after any `persistQueryString` merging — the URL the caller should
     * send the user to. Empty when no redirect applied.
     */
    val urlWithParams: String,

    /**
     * The full experiment result that produced this redirect.
     */
    val experimentResult: GBExperimentResult,

    /**
     * The experiment that was evaluated.
     */
    val experiment: GBExperiment,
)
