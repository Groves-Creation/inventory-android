package com.inventory.mobile.data

import kotlin.math.round

/** Convex v.number() is Float64; Kotlin Int would be encoded as Convex Int64. */
internal fun paginationArgs(pageSize: Int, cursor: String?): Map<String, Any?> =
    mapOf("numItems" to pageSize.toDouble(), "cursor" to cursor)

/** Convex v.optional() accepts omitted fields, not explicit JSON null. */
internal fun mutationArgs(vararg pairs: Pair<String, Any?>): Map<String, Any?> =
    buildMap { pairs.forEach { (key, value) -> if (value != null) put(key, value) } }

internal fun dollarsToCents(value: Double): Double = round(value * 100.0)
