package com.inventory.mobile.print

import android.content.Context
import android.graphics.Bitmap
import com.brother.sdk.lmprinter.Channel
import com.brother.sdk.lmprinter.GetStatusError
import com.brother.sdk.lmprinter.NetworkSearchOption
import com.brother.sdk.lmprinter.OpenChannelError
import com.brother.sdk.lmprinter.PrintError
import com.brother.sdk.lmprinter.PrinterDriver
import com.brother.sdk.lmprinter.PrinterDriverGenerator
import com.brother.sdk.lmprinter.PrinterModel
import com.brother.sdk.lmprinter.PrinterSearcher
import com.brother.sdk.lmprinter.PrinterStatus
import com.brother.sdk.lmprinter.setting.QLPrintSettings
import com.inventory.mobile.data.PrinterSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class DiscoveredPrinter(val ipAddress: String, val modelName: String, val serialNumber: String?)

sealed interface PrintOutcome {
    data class Success(val labels: Int) : PrintOutcome

    /** [hint] is the operator-facing next step; it is what makes a failure fixable at the counter. */
    data class Failure(val message: String, val hint: String? = null) : PrintOutcome {
        val display get() = if (hint == null) message else "$message. $hint"
    }
}

/**
 * Wraps the Brother Print SDK for the QL series.
 *
 * The SDK forbids using a PrinterDriver from more than one thread, so every job runs on a
 * single IO dispatch behind [jobLock]. Only Wi-Fi channels are offered: the QL-810W has no
 * Bluetooth radio, so exposing a Bluetooth path would only invite failed pairing attempts.
 */
class BrotherLabelPrinter(private val context: Context) {
    private val jobLock = Mutex()

    suspend fun discover(durationSeconds: Double = 6.0): Result<List<DiscoveredPrinter>> = withContext(Dispatchers.IO) {
        runCatching {
            val found = LinkedHashMap<String, DiscoveredPrinter>()
            PrinterSearcher.startNetworkSearch(context, NetworkSearchOption(durationSeconds, false)) { channel ->
                val extra = channel.extraInfo
                val address = extra?.get(Channel.ExtraInfoKey.IpAddress)?.takeIf(String::isNotBlank)
                    ?: channel.channelInfo
                if (!address.isNullOrBlank()) {
                    found[address] = DiscoveredPrinter(
                        ipAddress = address,
                        modelName = extra?.get(Channel.ExtraInfoKey.ModelName).orEmpty(),
                        // Brother's SDK misspells this key as SerialNubmer.
                        serialNumber = extra?.get(Channel.ExtraInfoKey.SerialNubmer),
                    )
                }
            }
            found.values.toList()
        }
    }

    suspend fun testConnection(settings: PrinterSettings): PrintOutcome = jobLock.withLock {
        withContext(Dispatchers.IO) {
            withDriver(settings) { driver ->
                val result = driver.printerStatus
                when (result.error.code) {
                    GetStatusError.ErrorCode.NoError -> {
                        val printerError = result.printerStatus?.errorCode
                        if (printerError == null || printerError == PrinterStatus.ErrorCode.NoError) {
                            PrintOutcome.Success(0)
                        } else {
                            statusFailure(printerError, settings)
                        }
                    }
                    GetStatusError.ErrorCode.Timeout -> PrintOutcome.Failure(
                        "The printer at ${settings.ipAddress} did not answer",
                        "Confirm the Wi-Fi light is solid and that this tablet is on the same 2.4 GHz network.",
                    )
                    GetStatusError.ErrorCode.PrinterNotFound -> PrintOutcome.Failure(
                        "No printer answered at ${settings.ipAddress}",
                        "Print the printer's settings page and update the IP under More → Label printer.",
                    )
                }
            }
        }
    }

    suspend fun print(settings: PrinterSettings, labels: List<LabelSpec>): PrintOutcome = jobLock.withLock {
        withContext(Dispatchers.IO) {
            val queued = labels.filter { it.copies > 0 }
            if (queued.isEmpty()) return@withContext PrintOutcome.Failure("There are no labels to print")
            val stock = LabelStock.fromId(settings.labelStockId)
            val workDirectory = File(context.cacheDir, "labels").apply { mkdirs() }
            workDirectory.listFiles()?.forEach { it.delete() }

            val paths = mutableListOf<String>()
            queued.forEachIndexed { index, label ->
                val file = File(workDirectory, "label-$index.png")
                val bitmap = LabelRenderer.render(label, stock)
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                repeat(label.copies) { paths.add(file.absolutePath) }
            }

            val outcome = withDriver(settings) { driver ->
                val printSettings = QLPrintSettings(resolveModel(settings.model)).apply {
                    labelSize = stock.brotherSize
                    isAutoCut = settings.autoCut
                    workPath = workDirectory.absolutePath
                }
                val error = driver.printImage(paths.toTypedArray(), printSettings)
                if (error.code == PrintError.ErrorCode.NoError) {
                    PrintOutcome.Success(paths.size)
                } else {
                    printFailure(error, settings)
                }
            }
            workDirectory.listFiles()?.forEach { it.delete() }
            outcome
        }
    }

    private inline fun withDriver(settings: PrinterSettings, block: (PrinterDriver) -> PrintOutcome): PrintOutcome {
        if (!settings.isConfigured) {
            return PrintOutcome.Failure(
                "No label printer is set up",
                "Open More → Label printer and search for the QL-810W or enter its IP address.",
            )
        }
        val result = runCatching {
            PrinterDriverGenerator.openChannel(Channel.newWifiChannel(settings.ipAddress))
        }.getOrElse { error ->
            return PrintOutcome.Failure(
                "Could not reach the printer at ${settings.ipAddress}",
                error.message ?: "Check that the tablet and printer are on the same 2.4 GHz network.",
            )
        }
        if (result.error.code != OpenChannelError.ErrorCode.NoError) {
            return connectionFailure(result.error.code, settings.ipAddress)
        }
        val driver = result.driver
        return try {
            block(driver)
        } catch (error: Exception) {
            PrintOutcome.Failure("The print job stopped unexpectedly", error.message)
        } finally {
            driver.closeChannel()
        }
    }

    private fun connectionFailure(code: OpenChannelError.ErrorCode, ipAddress: String) = when (code) {
        OpenChannelError.ErrorCode.Timeout -> PrintOutcome.Failure(
            "The printer at $ipAddress did not answer",
            "Confirm the Wi-Fi light on the printer is solid, and that the tablet is on the same 2.4 GHz network rather than a 5 GHz or guest one.",
        )
        OpenChannelError.ErrorCode.OpenStreamFailure -> PrintOutcome.Failure(
            "The printer at $ipAddress refused the connection",
            "It may be busy with another device, or its IP address has changed. Print the printer's settings page to confirm the address.",
        )
        else -> PrintOutcome.Failure("Could not open a connection to $ipAddress")
    }

    private fun statusFailure(code: PrinterStatus.ErrorCode, settings: PrinterSettings): PrintOutcome.Failure {
        val stock = LabelStock.fromId(settings.labelStockId)
        return when (code) {
            PrinterStatus.ErrorCode.NoPaper -> PrintOutcome.Failure(
                "The printer is out of labels",
                "Load a ${stock.displayName} roll and try again.",
            )
            PrinterStatus.ErrorCode.CoverOpen -> PrintOutcome.Failure(
                "The printer cover is open",
                "Close the roll compartment and try again.",
            )
            PrinterStatus.ErrorCode.PaperJam -> PrintOutcome.Failure(
                "The label roll is jammed",
                "Open the cover, clear the roll, and reseat it against the guides.",
            )
            PrinterStatus.ErrorCode.Busy -> PrintOutcome.Failure(
                "The printer is busy with another job",
                "Wait for the current job to finish, then try again.",
            )
            else -> PrintOutcome.Failure("The printer reported an error", code.name)
        }
    }

    private fun printFailure(error: PrintError, settings: PrinterSettings): PrintOutcome.Failure {
        val stock = LabelStock.fromId(settings.labelStockId)
        return when (error.code) {
            PrintError.ErrorCode.PrinterStatusErrorPaperEmpty -> PrintOutcome.Failure(
                "The printer is out of labels",
                "Load a ${stock.displayName} roll and try again.",
            )
            PrintError.ErrorCode.PrinterStatusErrorCoverOpen -> PrintOutcome.Failure(
                "The printer cover is open",
                "Close the roll compartment and try again.",
            )
            PrintError.ErrorCode.PrinterStatusErrorPaperJam,
            PrintError.ErrorCode.PrinterStatusErrorMediaCannotBeFed -> PrintOutcome.Failure(
                "The label roll is jammed",
                "Open the cover, clear the roll, and reseat it against the guides.",
            )
            PrintError.ErrorCode.SetLabelSizeError,
            PrintError.ErrorCode.PrintSettingsError -> PrintOutcome.Failure(
                "The loaded roll does not match the selected label size",
                "The app is set to ${stock.displayName}. Change the setting or load that roll.",
            )
            PrintError.ErrorCode.PrinterStatusErrorBusy -> PrintOutcome.Failure(
                "The printer is busy with another job",
                "Wait for the current job to finish, then try again.",
            )
            PrintError.ErrorCode.PrinterStatusErrorPrinterTurnedOff -> PrintOutcome.Failure(
                "The printer is powered off",
                "Switch it on and wait for the status light to settle.",
            )
            PrintError.ErrorCode.PrinterModelError,
            PrintError.ErrorCode.SetModelError -> PrintOutcome.Failure(
                "The printer is not the model the app expects",
                "The app is set to ${settings.model.replace('_', '-')}. Update it under More → Label printer.",
            )
            PrintError.ErrorCode.ChannelTimeout -> PrintOutcome.Failure(
                "The printer stopped responding partway through",
                "This is usually a weak Wi-Fi signal. Move the printer closer to the access point.",
            )
            else -> PrintOutcome.Failure(
                "The printer reported an error",
                error.errorDescription?.takeIf(String::isNotBlank) ?: error.code.name,
            )
        }
    }

    private fun resolveModel(model: String) =
        runCatching { PrinterModel.valueOf(model) }.getOrDefault(PrinterModel.QL_810W)

    companion object {
        /** QL models a store is plausibly running; the label layouts assume 300 dpi QL hardware. */
        val SUPPORTED_MODELS = listOf("QL_810W", "QL_820NWB", "QL_1110NWB", "QL_1115NWB", "QL_720NW", "QL_710W")
    }
}
