package com.inventory.mobile

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.inventory.mobile.ui.InventoryApp
import com.inventory.mobile.update.AvailableUpdate

class MainActivity : ComponentActivity() {
    private val downloadManager by lazy { getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }
    private var updateDownloadId: Long? = null
    private var pendingInstallUri: Uri? = null

    private val updateDownloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId != updateDownloadId) return

            updateDownloadId = null
            val apkUri = downloadManager.getUriForDownloadedFile(downloadId)
            if (apkUri == null) {
                Toast.makeText(this@MainActivity, "Update download failed", Toast.LENGTH_LONG).show()
            } else {
                installApk(apkUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            updateDownloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val container = (application as InventoryApplication).container
        setContent { InventoryApp(container, onInstallUpdate = ::downloadUpdate) }
    }

    override fun onResume() {
        super.onResume()
        pendingInstallUri?.takeIf { packageManager.canRequestPackageInstalls() }?.let { apkUri ->
            pendingInstallUri = null
            openPackageInstaller(apkUri)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(updateDownloadReceiver)
        super.onDestroy()
    }

    private fun downloadUpdate(update: AvailableUpdate) {
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("Inventory ${update.versionName}")
            .setDescription("Downloading update")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                this,
                Environment.DIRECTORY_DOWNLOADS,
                "Inventory-${update.versionName}-${System.currentTimeMillis()}.apk",
            )
        updateDownloadId = downloadManager.enqueue(request)
        Toast.makeText(this, "Downloading update…", Toast.LENGTH_SHORT).show()
    }

    private fun installApk(apkUri: Uri) {
        if (!packageManager.canRequestPackageInstalls()) {
            pendingInstallUri = apkUri
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }
        openPackageInstaller(apkUri)
    }

    private fun openPackageInstaller(apkUri: Uri) {
        startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }
}
