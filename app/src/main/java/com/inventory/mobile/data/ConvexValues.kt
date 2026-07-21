package com.inventory.mobile.data

import kotlin.math.round

/** Convex v.number() is Float64; Kotlin Int would be encoded as Convex Int64. */
internal fun paginationArgs(pageSize: Int, cursor: String?): Map<String, Any?> =
    mapOf("numItems" to pageSize.toDouble(), "cursor" to cursor)

internal fun dollarsToCents(value: Double): Double = round(value * 100.0)
