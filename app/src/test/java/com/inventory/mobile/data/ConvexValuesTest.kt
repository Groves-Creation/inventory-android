package com.inventory.mobile.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvexValuesTest {
    @Test
    fun paginationUsesFloat64CompatibleNumber() {
        val args = paginationArgs(50, null)
        assertTrue(args["numItems"] is Double)
        assertEquals(50.0, args["numItems"])
        assertEquals(null, args["cursor"])
    }

    @Test
    fun currencyUsesFloat64CompatibleCents() {
        val cents = dollarsToCents(12.99)
        assertEquals(1299.0, cents, 0.0)
    }

    @Test
    fun syncResultMatchesConvexActionReturnValue() {
        val result = Json.decodeFromString<SyncResultDto>(
            """{"upserted":12,"tombstoned":3}""",
        )

        assertEquals(12.0, result.upserted, 0.0)
        assertEquals(3.0, result.tombstoned, 0.0)
        assertEquals(false, result.skipped)
    }
}
