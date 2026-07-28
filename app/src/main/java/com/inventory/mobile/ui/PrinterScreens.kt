package com.inventory.mobile.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inventory.mobile.data.PrinterSettings
import com.inventory.mobile.data.PrinterStore
import com.inventory.mobile.print.BrotherLabelPrinter
import com.inventory.mobile.print.DiscoveredPrinter
import com.inventory.mobile.print.LabelSpec
import com.inventory.mobile.print.LabelStock
import com.inventory.mobile.print.PrintOutcome
import kotlinx.coroutines.launch

@Composable
fun PrinterSettingsScreen(
    printerStore: PrinterStore,
    printer: BrotherLabelPrinter,
    snackbar: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val saved by printerStore.settings.collectAsState(initial = PrinterSettings())
    var draft by remember { mutableStateOf<PrinterSettings?>(null) }
    var found by remember { mutableStateOf<List<DiscoveredPrinter>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    // Android 13 gates local network discovery behind the nearby-devices grant. Without it the
    // Brother SDK simply finds nothing, which reads as "the printer will not connect".
    val nearbyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) scope.launch { snackbar.showSnackbar("Search needs the nearby devices permission. Enter the IP address instead.") }
    }

    LaunchedEffect(saved) { if (draft == null) draft = saved }
    val settings = draft ?: saved

    fun update(change: (PrinterSettings) -> PrinterSettings) {
        draft = change(settings)
    }

    fun persist(next: PrinterSettings, message: String) {
        draft = next
        scope.launch {
            printerStore.save(next)
            snackbar.showSnackbar(message)
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Label printer", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Text(
                "The QL-810W connects over Wi-Fi only; it has no Bluetooth. Keep it on the same " +
                    "2.4 GHz network as this tablet.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            OutlinedTextField(
                value = settings.ipAddress,
                onValueChange = { value -> update { it.copy(ipAddress = value.filter { char -> char.isDigit() || char == '.' }) } },
                label = { Text("Printer IP address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !searching,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            nearbyPermission.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                        }
                        searching = true
                        scope.launch {
                            printer.discover()
                                .onSuccess { printers ->
                                    found = printers
                                    if (printers.isEmpty()) {
                                        snackbar.showSnackbar("No printers answered. Check the Wi-Fi light, then enter the IP address by hand.")
                                    }
                                }
                                .onFailure { snackbar.showSnackbar(it.message ?: "Search failed") }
                            searching = false
                        }
                    },
                ) { Text(if (searching) "Searching…" else "Search the network") }
                Button(
                    enabled = !testing && settings.isConfigured,
                    onClick = {
                        testing = true
                        scope.launch {
                            printerStore.save(settings)
                            when (val outcome = printer.testConnection(settings)) {
                                is PrintOutcome.Success -> snackbar.showSnackbar("Printer is online and ready")
                                is PrintOutcome.Failure -> snackbar.showSnackbar(outcome.display)
                            }
                            testing = false
                        }
                    },
                ) { Text(if (testing) "Testing…" else "Test connection") }
            }
        }
        items(found, key = { it.ipAddress }) { discovered ->
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(discovered.modelName.ifBlank { "Brother printer" }, fontWeight = FontWeight.Bold)
                    Text(discovered.ipAddress, style = MaterialTheme.typography.bodySmall)
                    discovered.serialNumber?.let { Text("Serial $it", style = MaterialTheme.typography.bodySmall) }
                    OutlinedButton(onClick = {
                        val model = BrotherLabelPrinter.SUPPORTED_MODELS
                            .firstOrNull { it.equals(discovered.modelName.replace('-', '_'), ignoreCase = true) }
                            ?: settings.model
                        persist(settings.copy(ipAddress = discovered.ipAddress, model = model), "Using ${discovered.ipAddress}")
                    }) { Text("Use this printer") }
                }
            }
        }
        item { Text("Printer model", style = MaterialTheme.typography.titleMedium) }
        item {
            ChipRow(
                options = BrotherLabelPrinter.SUPPORTED_MODELS,
                selected = settings.model,
                label = { it.replace('_', '-') },
                onSelect = { model -> update { it.copy(model = model) } },
            )
        }
        item { Text("Loaded label roll", style = MaterialTheme.typography.titleMedium) }
        items(LabelStock.entries.toList(), key = { it.id }) { stock ->
            OutlinedButton(
                onClick = { update { it.copy(labelStockId = stock.id) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(stock.displayName, fontWeight = FontWeight.Bold)
                    if (stock.id == settings.labelStockId) Text("Selected", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cut after every label")
                Switch(checked = settings.autoCut, onCheckedChange = { value -> update { it.copy(autoCut = value) } })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { persist(settings, "Printer settings saved") }) { Text("Save") }
                OutlinedButton(
                    enabled = settings.isConfigured,
                    onClick = {
                        scope.launch {
                            printerStore.save(settings)
                            val sample = LabelSpec(name = "Test label", priceCents = 199, code = "012345678905", copies = 1)
                            when (val outcome = printer.print(settings, listOf(sample))) {
                                is PrintOutcome.Success -> snackbar.showSnackbar("Test label sent")
                                is PrintOutcome.Failure -> snackbar.showSnackbar(outcome.display)
                            }
                        }
                    },
                ) { Text("Print a test label") }
            }
        }
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: String, label: (String) -> String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
                }
            }
        }
    }
}
