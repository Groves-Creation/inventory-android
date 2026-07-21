package com.inventory.mobile.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "queued_counts")
data class QueuedCount(
    @androidx.room.PrimaryKey val requestId: String,
    val userId: String,
    val storeId: String,
    val itemId: String,
    val itemName: String,
    val countedQty: Double,
    val note: String?,
    val month: String,
    val expectedCountedAt: String?,
    val state: String = "pending",
    val conflictQty: Double? = null,
    val conflictAt: String? = null,
    val conflictBy: String? = null,
)

@Dao
interface CountQueueDao {
    @Query("SELECT * FROM queued_counts ORDER BY rowid")
    fun observeAll(): Flow<List<QueuedCount>>

    @Query("SELECT * FROM queued_counts WHERE state = 'pending' ORDER BY rowid")
    suspend fun pending(): List<QueuedCount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: QueuedCount)

    @Query("DELETE FROM queued_counts WHERE requestId = :requestId")
    suspend fun delete(requestId: String)

    @Query("DELETE FROM queued_counts WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)

    @Query("UPDATE queued_counts SET state = 'conflict', conflictQty = :qty, conflictAt = :at, conflictBy = :by WHERE requestId = :requestId")
    suspend fun conflict(requestId: String, qty: Double?, at: String?, by: String?)
}

@Database(entities = [QueuedCount::class], version = 1, exportSchema = true)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun countQueue(): CountQueueDao

    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, InventoryDatabase::class.java, "inventory-pilot.db").build()
    }
}
