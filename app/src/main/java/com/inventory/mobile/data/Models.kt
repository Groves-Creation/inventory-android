@file:kotlinx.serialization.UseSerializers(dev.convex.android.Float64ToDoubleDecoder::class)

package com.inventory.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(val id: String, val name: String, val role: String)

@Serializable
data class StoreDto(
    val id: String,
    val name: String,
    val merchantId: String? = null,
    val itemCount: Double? = null,
    val lastSyncedAt: String? = null,
    val authFailed: Boolean = false,
    val syncStale: Boolean = false,
    val counted: Double? = null,
    val total: Double? = null,
    val pendingVariances: Double? = null,
    val pendingOperations: Double? = null,
    val failedOperations: Double? = null,
)

@Serializable
data class ItemPeerDto(
    @SerialName("store_id") val storeId: String,
    @SerialName("store_name") val storeName: String,
    @SerialName("stock_quantity") val stockQuantity: Double? = null,
    @SerialName("price_cents") val priceCents: Double,
    @SerialName("item_id") val itemId: String,
)

@Serializable
data class ItemDto(
    val id: String,
    val cloverId: String,
    val name: String,
    val sku: String? = null,
    val code: String? = null,
    val priceCents: Double,
    @SerialName("price_cents") val priceCentsLegacy: Double? = null,
    val stockQuantity: Double? = null,
    @SerialName("stock_quantity") val stockQuantityLegacy: Double? = null,
    val hidden: Boolean,
    val productKey: String,
    @SerialName("other_stores") val otherStores: List<ItemPeerDto> = emptyList(),
    @SerialName("count_status") val countStatus: String? = null,
    @SerialName("counted_qty") val countedQty: Double? = null,
    @SerialName("counted_at") val countedAt: String? = null,
    @SerialName("last_counted_at") val lastCountedAt: String? = null,
    @SerialName("last_counted_by") val lastCountedBy: String? = null,
) {
    val displayPriceCents get() = priceCentsLegacy ?: priceCents
    val displayStock get() = stockQuantityLegacy ?: stockQuantity
}

@Serializable
data class SearchGroupDto(
    val name: String,
    val sku: String? = null,
    val code: String? = null,
    @SerialName("price_cents") val priceCents: Double,
    val stores: List<ItemPeerDto>,
)

@Serializable
data class ProgressDto(val counted: Double, val total: Double, val remaining: Double, val pendingVariances: Double)

@Serializable
data class CountRecordDto(
    val id: String,
    val countedQty: Double,
    val expectedQty: Double? = null,
    val status: String,
    val countedAt: String,
    val countedByName: String? = null,
)

@Serializable
data class CountConflictDto(
    val id: String,
    val countedQty: Double,
    val countedAt: String,
    val countedByName: String? = null,
    val status: String,
)

@Serializable
data class CountResultDto(
    val status: String,
    val record: CountRecordDto? = null,
    val current: CountConflictDto? = null,
)

@Serializable
data class PageDto<T>(val page: List<T>, val isDone: Boolean, val continueCursor: String)

@Serializable
data class VarianceDto(
    val id: String,
    @SerialName("item_name") val itemName: String,
    val expectedQty: Double? = null,
    val countedQty: Double,
    val variance: Double? = null,
    @SerialName("price_cents") val priceCents: Double,
)

@Serializable
data class ActionResultDto(val status: String, val message: String? = null)

@Serializable
data class SyncResultDto(
    val upserted: Double,
    val tombstoned: Double,
    val skipped: Boolean = false,
)

@Serializable
data class ReportSummaryDto(val summary: ReportStatsDto)

@Serializable
data class ReportStatsDto(val counted: Double, val uncounted: Double, val variances: Double, @SerialName("shrinkage_cents") val shrinkageCents: Double)

@Serializable
data class AuditDto(@SerialName("_id") val id: String, val ts: String, val description: String, val action: String? = null)

@Serializable
data class ReportRowDto(
    @SerialName("item_name") val itemName: String,
    val sku: String? = null,
    @SerialName("price_cents") val priceCents: Double = 0.0,
    @SerialName("stock_quantity") val stockQuantity: Double? = null,
    val expectedQty: Double? = null,
    val countedQty: Double? = null,
    val variance: Double? = null,
    val status: String? = null,
)

@Serializable
data class AdminUserDto(val id: String, val name: String, val role: String, val active: Boolean, @SerialName("store_ids") val storeIds: List<String> = emptyList())

@Serializable
data class SyncRunDto(val id: String, @SerialName("store_name") val storeName: String, val status: String, val itemsUpserted: Double, val itemsTombstoned: Double, val startedAt: String, val finishedAt: String? = null)

@Serializable
data class OperationHealthDto(
    val requestId: String,
    val storeName: String,
    val kind: String,
    val status: String,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val updatedAt: String,
    val itemId: String? = null,
    val priceCents: Double? = null,
    val quantity: Double? = null,
    val reason: String? = null,
)

@Serializable
data class CloverVarianceDto(
    val kind: String,
    val itemId: String? = null,
    val cloverId: String,
    val itemName: String,
    val sku: String? = null,
    val code: String? = null,
    val localPriceCents: Double? = null,
    val localStockQuantity: Double? = null,
    val cloverPriceCents: Double? = null,
    val cloverStockQuantity: Double? = null,
)

@Serializable
data class CloverVarianceResultDto(val rows: List<CloverVarianceDto>, val limited: Boolean = false)
