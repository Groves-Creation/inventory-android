package com.inventory.mobile.data

import dev.convex.android.ConvexClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface InventoryRepository {
    val connectionState: Flow<*>
    suspend fun loginUsers(): List<String>
    suspend fun login(name: String, pin: String): UserDto
    suspend fun stores(userId: String): List<StoreDto>
    suspend fun items(userId: String, storeId: String, query: String, cursor: String? = null): PageDto<ItemDto>
    suspend fun find(userId: String, query: String, cursor: String? = null): PageDto<SearchGroupDto>
    suspend fun countSearch(userId: String, storeId: String, query: String): List<ItemDto>
    suspend fun progress(userId: String, storeId: String): ProgressDto
    suspend fun recordCount(args: CountSubmission): CountResultDto
    suspend fun generateBarcode(userId: String): String
    suspend fun syncStore(userId: String, storeId: String): ActionResultDto
    suspend fun createItem(args: Map<String, Any?>): ActionResultDto
    suspend fun changeItem(args: Map<String, Any?>): ActionResultDto
    suspend fun variances(userId: String, storeId: String, cursor: String? = null): PageDto<VarianceDto>
    suspend fun resolveVariance(args: Map<String, Any?>): ActionResultDto
    suspend fun cloverVariances(userId: String, storeId: String): CloverVarianceResultDto
    suspend fun report(userId: String, storeId: String, month: String): ReportSummaryDto
    suspend fun reportRows(userId: String, storeId: String, month: String, kind: String, cursor: String? = null): PageDto<ReportRowDto>
    suspend fun audit(userId: String, storeId: String? = null, action: String? = null, query: String? = null, cursor: String? = null): PageDto<AuditDto>
    suspend fun adminUsers(userId: String): List<AdminUserDto>
    suspend fun saveUser(args: Map<String, Any?>)
    suspend fun syncRuns(userId: String): List<SyncRunDto>
    suspend fun operationHealth(userId: String): List<OperationHealthDto>
}

data class CountSubmission(
    val requestId: String,
    val userId: String,
    val storeId: String,
    val itemId: String,
    val countedQty: Double,
    val note: String?,
    val month: String,
    val expectedCountedAt: String?,
    val force: Boolean = false,
)

class ConvexInventoryRepository(private val client: ConvexClient) : InventoryRepository {
    override val connectionState: Flow<*> = client.webSocketStateFlow

    private suspend inline fun <reified T> query(name: String, args: Map<String, Any?> = emptyMap()): T =
        client.subscribe<T>(name, args).first().getOrThrow()

    override suspend fun loginUsers() = query<List<String>>("inventory:loginUsers")
    override suspend fun login(name: String, pin: String) = client.mutation<UserDto>("inventory:login", mapOf("name" to name, "pin" to pin))
    override suspend fun stores(userId: String) = query<List<StoreDto>>("inventory:storeDashboard", mapOf("userId" to userId))
    override suspend fun items(userId: String, storeId: String, query: String, cursor: String?) = query<PageDto<ItemDto>>(
        "inventory:listItems",
        mapOf("userId" to userId, "storeId" to storeId, "q" to query, "paginationOpts" to paginationArgs(50, cursor)),
    )
    override suspend fun find(userId: String, query: String, cursor: String?) = query<PageDto<SearchGroupDto>>(
        "inventory:searchAllItems",
        mapOf("userId" to userId, "q" to query, "paginationOpts" to paginationArgs(50, cursor)),
    )
    override suspend fun countSearch(userId: String, storeId: String, query: String) = query<List<ItemDto>>(
        "inventory:searchForCounting", mapOf("userId" to userId, "storeId" to storeId, "q" to query),
    )
    override suspend fun progress(userId: String, storeId: String) = query<ProgressDto>("inventory:progress", mapOf("userId" to userId, "storeId" to storeId))
    override suspend fun recordCount(args: CountSubmission) = client.mutation<CountResultDto>(
        "inventory:recordCount",
        mapOf(
            "requestId" to args.requestId,
            "userId" to args.userId,
            "storeId" to args.storeId,
            "itemId" to args.itemId,
            "countedQty" to args.countedQty,
            "note" to args.note,
            "month" to args.month,
            "expectedCountedAt" to args.expectedCountedAt,
            "force" to args.force,
        ),
    )
    override suspend fun generateBarcode(userId: String) = client.mutation<String>("inventory:generateBarcode", mapOf("userId" to userId))
    override suspend fun syncStore(userId: String, storeId: String) = client.action<ActionResultDto>("cloverActions:syncStore", mapOf("userId" to userId, "storeId" to storeId))
    override suspend fun createItem(args: Map<String, Any?>) = client.action<ActionResultDto>("cloverActions:createCloverItem", args)
    override suspend fun changeItem(args: Map<String, Any?>) = client.action<ActionResultDto>("cloverActions:changeCloverItem", args)
    override suspend fun variances(userId: String, storeId: String, cursor: String?) = query<PageDto<VarianceDto>>(
        "inventory:pendingVariances", mapOf("userId" to userId, "storeId" to storeId, "paginationOpts" to paginationArgs(30, cursor)),
    )
    override suspend fun resolveVariance(args: Map<String, Any?>) = client.action<ActionResultDto>("cloverActions:resolveVariance", args)
    override suspend fun cloverVariances(userId: String, storeId: String) = client.action<CloverVarianceResultDto>("cloverActions:compareStoreWithClover", mapOf("userId" to userId, "storeId" to storeId))
    override suspend fun report(userId: String, storeId: String, month: String) = query<ReportSummaryDto>("inventory:reportSummary", mapOf("userId" to userId, "storeId" to storeId, "month" to month))
    override suspend fun reportRows(userId: String, storeId: String, month: String, kind: String, cursor: String?) = query<PageDto<ReportRowDto>>(
        "inventory:reportRows",
        mapOf("userId" to userId, "storeId" to storeId, "month" to month, "kind" to kind, "paginationOpts" to paginationArgs(50, cursor)),
    )
    override suspend fun audit(userId: String, storeId: String?, action: String?, query: String?, cursor: String?): PageDto<AuditDto> {
        val args = mutableMapOf<String, Any?>("userId" to userId, "paginationOpts" to paginationArgs(50, cursor))
        storeId?.let { args["storeId"] = it }
        action?.let { args["action"] = it }
        query?.let { args["q"] = it }
        return this.query("inventory:auditRows", args)
    }
    override suspend fun adminUsers(userId: String) = query<List<AdminUserDto>>("inventory:adminUsers", mapOf("actorId" to userId))
    override suspend fun saveUser(args: Map<String, Any?>) { client.mutation<String>("inventory:saveUser", args) }
    override suspend fun syncRuns(userId: String) = query<List<SyncRunDto>>("inventory:syncRuns", mapOf("userId" to userId))
    override suspend fun operationHealth(userId: String) = query<List<OperationHealthDto>>("inventory:cloverOperationHealth", mapOf("userId" to userId))
}
