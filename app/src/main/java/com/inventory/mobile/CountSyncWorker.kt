package com.inventory.mobile

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CountSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as InventoryApplication
        return if (app.container.queueSync.flush(app.container.repository)) Result.success() else Result.retry()
    }
}
