package com.sdk.growthbook.utils

internal fun Number.isIntegerValue(): Boolean =
    this is Byte || this is Short || this is Int || this is Long
