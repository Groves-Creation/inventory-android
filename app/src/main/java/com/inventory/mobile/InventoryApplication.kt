package com.inventory.mobile

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.inventory.mobile.data.ConvexInventoryRepository
import com.inventory.mobile.data.CountQueueSync
import com.inventory.mobile.data.InventoryDatabase
import com.inventory.mobile.data.PrinterStore
import com.inventory.mobile.data.SessionStore
import com.inventory.mobile.print.BrotherLabelPrinter
import dev.convex.android.ConvexClient

class InventoryApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val endpoint = BuildConfig.CONVEX_URL.ifBlank { "https://missing-config.convex.cloud" }
        val database = InventoryDatabase.create(this)
        container = AppContainer(
            repository = ConvexInventoryRepository(ConvexClient(endpoint)),
            sessionStore = SessionStore(this),
            queueSync = CountQueueSync(this, database.countQueue()),
            printerStore = PrinterStore(this),
            printer = BrotherLabelPrinter(this),
            configured = BuildConfig.CONVEX_URL.isNotBlank(),
        )
        enqueueCountSync()
    }

    fun enqueueCountSync() {
        val request = OneTimeWorkRequestBuilder<CountSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork("count-sync", ExistingWorkPolicy.KEEP, request)
    }
}

data class AppContainer(
    val repository: com.inventory.mobile.data.InventoryRepository,
    val sessionStore: SessionStore,
    val queueSync: CountQueueSync,
    val printerStore: PrinterStore,
    val printer: BrotherLabelPrinter,
    val configured: Boolean,
)
