package com.inventory.mobile.ui

import com.inventory.mobile.data.AuditDto
import com.inventory.mobile.data.ReportRowDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportUtilsTest {
    @Test
    fun reportCsvEscapesNamesAndPreservesCounts() {
        val csv = reportCsv(
            listOf(
                ReportRowDto(
                    itemName = "Coffee, 12\" bag",
                    sku = "BEAN-1",
                    priceCents = 1299.0,
                    expectedQty = 5.0,
                    countedQty = 4.0,
                    variance = -1.0,
                    status = "variance_pending",
                ),
            ),
        )
        assertTrue(csv.contains("\"Coffee, 12\"\" bag\""))
        assertTrue(csv.contains("BEAN-1,1299.0"))
        assertTrue(csv.contains("5.0,4.0,-1.0,variance_pending"))
    }

    @Test
    fun auditCsvIncludesStableColumns() {
        val csv = auditCsv(listOf(AuditDto("audit-1", "2026-07-09T12:00:00Z", "Owner changed price", "price_change")))
        assertEquals("timestamp,action,description\n2026-07-09T12:00:00Z,price_change,Owner changed price\n", csv.replace("\r\n", "\n"))
    }
}
