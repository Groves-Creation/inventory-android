package com.inventory.mobile.data

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
}
