package com.inventory.mobile.data

import android.content.Context
import com.inventory.mobile.InventoryApplication
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class CountQueueSync(private val context: Context, private val dao: CountQueueDao) {
    val queued: Flow<List<QueuedCount>> = dao.observeAll()

    suspend fun enqueue(
        user: UserDto,
        storeId: String,
        item: ItemDto,
        quantity: Double,
        note: String?,
        month: String,
    ): String {
        val requestId = "android_${UUID.randomUUID().toString().replace("-", "_")}"
        dao.upsert(
            QueuedCount(
                requestId = requestId,
                userId = user.id,
                storeId = storeId,
                itemId = item.id,
                itemName = item.name,
                countedQty = quantity,
                note = note?.trim()?.takeIf(String::isNotEmpty),
                month = month,
                expectedCountedAt = item.countedAt,
            ),
        )
        (context.applicationContext as InventoryApplication).enqueueCountSync()
        return requestId
    }

    suspend fun flush(repository: InventoryRepository): Boolean {
        for (queued in dao.pending()) {
            val result = runCatching { repository.recordCount(queued.submission()) }.getOrElse { return false }
            if (result.status == "saved") dao.delete(queued.requestId)
            else dao.conflict(
                queued.requestId,
                result.current?.countedQty,
                result.current?.countedAt,
                result.current?.countedByName,
            )
        }
        return true
    }

    suspend fun force(repository: InventoryRepository, queued: QueuedCount) {
        val result = repository.recordCount(queued.submission(force = true))
        if (result.status == "saved") dao.delete(queued.requestId)
    }

    suspend fun discard(requestId: String) = dao.delete(requestId)
    suspend fun clearUser(userId: String) = dao.deleteForUser(userId)
}

private fun QueuedCount.submission(force: Boolean = false) = CountSubmission(
    requestId = requestId,
    userId = userId,
    storeId = storeId,
    itemId = itemId,
    countedQty = countedQty,
    note = note,
    month = month,
    expectedCountedAt = expectedCountedAt,
    force = force,
)
