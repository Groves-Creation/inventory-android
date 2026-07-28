package com.inventory.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.inventory.mobile.data.InventoryRepository
import com.inventory.mobile.data.ItemDto
import com.inventory.mobile.data.PrinterSettings
import com.inventory.mobile.data.PrinterStore
import com.inventory.mobile.data.PurchaseOrderDetailDto
import com.inventory.mobile.data.PurchaseOrderDto
import com.inventory.mobile.data.PurchaseOrderLineDto
import com.inventory.mobile.data.UserDto
import com.inventory.mobile.data.dollarsToCents
import com.inventory.mobile.print.BrotherLabelPrinter
import com.inventory.mobile.print.LabelSpec
import com.inventory.mobile.print.PrintOutcome
import kotlinx.coroutines.launch

private val STATUS_FILTERS = listOf(null, "open", "received", "cancelled")

@Composable
fun PurchaseOrdersScreen(
    user: UserDto,
    storeId: String,
    repository: InventoryRepository,
    printerStore: PrinterStore,
    printer: BrotherLabelPrinter,
    snackbar: SnackbarHostState,
) {
    var openOrderId by remember { mutableStateOf<String?>(null) }
    val selected = openOrderId
    if (selected == null) {
        PurchaseOrderList(user, storeId, repository, snackbar) { openOrderId = it }
    } else {
        PurchaseOrderDetail(user, selected, repository, printerStore, printer, snackbar) { openOrderId = null }
    }
}

@Composable
private fun PurchaseOrderList(
    user: UserDto,
    storeId: String,
    repository: InventoryRepository,
    snackbar: SnackbarHostState,
    onOpen: (String) -> Unit,
) {
    var rows by remember { mutableStateOf<List<PurchaseOrderDto>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(storeId, status, refresh) {
        loading = true
        runCatching { repository.purchaseOrders(user.id, storeId, status) }
            .onSuccess { rows = it.page }
            .onFailure { snackbar.showSnackbar(it.message ?: "Unable to load purchase orders") }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("New purchase order") }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            STATUS_FILTERS.forEach { option ->
                FilterChip(
                    selected = option == status,
                    onClick = { status = option },
                    label = { Text(option?.replaceFirstChar(Char::uppercase) ?: "All") },
                )
            }
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (rows.isEmpty()) {
            Text("No purchase orders yet. Create one to stage a delivery and print all of its labels together.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { it.id }) { order ->
                    OutlinedButton(onClick = { onOpen(order.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(order.reference, fontWeight = FontWeight.Bold)
                            Text(
                                listOfNotNull(
                                    order.vendor,
                                    "${order.lineCount.toInt()} lines",
                                    "${order.labelCount.toInt()} labels",
                                    order.status,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (order.failedLineCount > 0) {
                                Text(
                                    "${order.failedLineCount.toInt()} line(s) failed to reach Clover",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        NewPurchaseOrderDialog(user, storeId, repository, snackbar, onDismiss = { creating = false }) { id ->
            creating = false
            refresh++
            onOpen(id)
        }
    }
}

@Composable
private fun NewPurchaseOrderDialog(
    user: UserDto,
    storeId: String,
    repository: InventoryRepository,
    snackbar: SnackbarHostState,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var reference by remember { mutableStateOf("") }
    var vendor by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New purchase order") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(reference, { reference = it.take(64) }, label = { Text("Reference or invoice number") })
                OutlinedTextField(vendor, { vendor = it.take(128) }, label = { Text("Vendor (optional)") })
                OutlinedTextField(note, { note = it.take(500) }, label = { Text("Note (optional)") })
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && reference.isNotBlank(),
                onClick = {
                    saving = true
                    scope.launch {
                        runCatching {
                            repository.createPurchaseOrder(
                                user.id,
                                storeId,
                                reference.trim(),
                                vendor.takeIf(String::isNotBlank),
                                note.takeIf(String::isNotBlank),
                            )
                        }
                            .onSuccess { onCreated(it.id) }
                            .onFailure { snackbar.showSnackbar(it.message ?: "Unable to create the purchase order") }
                        saving = false
                    }
                },
            ) { Text(if (saving) "Creating…" else "Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PurchaseOrderDetail(
    user: UserDto,
    purchaseOrderId: String,
    repository: InventoryRepository,
    printerStore: PrinterStore,
    printer: BrotherLabelPrinter,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val printerSettings by printerStore.settings.collectAsState(initial = PrinterSettings())
    var detail by remember { mutableStateOf<PurchaseOrderDetailDto?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var addingExisting by remember { mutableStateOf(false) }
    var addingNew by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(purchaseOrderId, refresh) {
        runCatching { repository.purchaseOrder(user.id, purchaseOrderId) }
            .onSuccess { detail = it }
            .onFailure { snackbar.showSnackbar(it.message ?: "Unable to load the purchase order") }
    }

    val current = detail
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val order = current.order
    val manager = user.canManageRole()

    fun printLabels() {
        busy = true
        scope.launch {
            runCatching { repository.labelSheet(user.id, purchaseOrderId) }
                .onSuccess { sheet ->
                    val specs = sheet.labels.map {
                        LabelSpec(name = it.name, priceCents = it.priceCents.toLong(), code = it.code, copies = it.copies.toInt())
                    }
                    if (specs.isEmpty()) {
                        snackbar.showSnackbar("No printable labels on this order")
                    } else {
                        when (val outcome = printer.print(printerSettings, specs)) {
                            is PrintOutcome.Success -> snackbar.showSnackbar(
                                "Sent ${outcome.labels} label(s)" + if (sheet.skipped > 0) ", skipped ${sheet.skipped.toInt()} without a barcode" else "",
                            )
                            is PrintOutcome.Failure -> snackbar.showSnackbar(outcome.display)
                        }
                    }
                }
                .onFailure { snackbar.showSnackbar(it.message ?: "Unable to build the label sheet") }
            busy = false
        }
    }

    fun receive() {
        busy = true
        scope.launch {
            runCatching { repository.receivePurchaseOrder(user.id, purchaseOrderId) }
                .onSuccess { summary ->
                    snackbar.showSnackbar(
                        if (summary.failed > 0) {
                            "Created ${summary.created.toInt()}, ${summary.failed.toInt()} failed. Fix the flagged lines and receive again."
                        } else {
                            "Received. ${summary.created.toInt()} new product(s) created in Clover."
                        },
                    )
                    refresh++
                }
                .onFailure { snackbar.showSnackbar(it.message ?: "Unable to receive the purchase order") }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("← All purchase orders") }
        Text(order.reference, style = MaterialTheme.typography.headlineSmall)
        Text(
            listOfNotNull(order.vendor, order.status, "${order.labelCount.toInt()} labels").joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
        )
        order.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (order.failedLineCount > 0) {
            Text(
                "${order.failedLineCount.toInt()} line(s) did not reach Clover. Correct them below, then receive again.",
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (order.isEditable) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { addingExisting = true }) { Text("Add item") }
                if (manager) OutlinedButton(onClick = { addingNew = true }) { Text("Add new product") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && order.labelCount > 0, onClick = ::printLabels) {
                Text(if (busy) "Working…" else "Print ${order.labelCount.toInt()} label(s)")
            }
            if (manager && order.isEditable && order.newProductCount > 0) {
                OutlinedButton(enabled = !busy, onClick = ::receive) { Text("Receive") }
            }
        }
        if (!printerSettings.isConfigured) {
            Text(
                "No printer is set up yet. Open More → Label printer first.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(current.lines, key = { it.id }) { line ->
                PurchaseOrderLineCard(
                    line = line,
                    editable = order.isEditable,
                    onCopies = { copies ->
                        scope.launch {
                            runCatching {
                                repository.updatePurchaseOrderLine(
                                    mapOf("userId" to user.id, "lineId" to line.id, "labelCopies" to copies.toDouble()),
                                )
                            }
                                .onSuccess { refresh++ }
                                .onFailure { snackbar.showSnackbar(it.message ?: "Unable to update the line") }
                        }
                    },
                    onRemove = {
                        scope.launch {
                            runCatching { repository.removePurchaseOrderLine(user.id, line.id) }
                                .onSuccess { refresh++ }
                                .onFailure { snackbar.showSnackbar(it.message ?: "Unable to remove the line") }
                        }
                    },
                )
            }
        }
    }

    if (addingExisting) {
        AddExistingItemDialog(user, order.storeId.orEmpty(), purchaseOrderId, repository, snackbar, onDismiss = { addingExisting = false }) {
            addingExisting = false
            refresh++
        }
    }
    if (addingNew) {
        AddNewProductDialog(user, purchaseOrderId, repository, snackbar, onDismiss = { addingNew = false }) {
            addingNew = false
            refresh++
        }
    }
}

@Composable
private fun PurchaseOrderLineCard(
    line: PurchaseOrderLineDto,
    editable: Boolean,
    onCopies: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(line.name, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(
                    line.code ?: line.sku,
                    moneyText(line.priceCents),
                    "qty ${line.quantity.toInt()}",
                    if (line.isNewProduct) "new product" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            line.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${line.labelCopies.toInt()} label(s)")
                if (editable) {
                    OutlinedButton(onClick = { onCopies((line.labelCopies.toInt() - 1).coerceAtLeast(0)) }) { Text("−") }
                    OutlinedButton(onClick = { onCopies(line.labelCopies.toInt() + 1) }) { Text("+") }
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun AddExistingItemDialog(
    user: UserDto,
    storeId: String,
    purchaseOrderId: String,
    repository: InventoryRepository,
    snackbar: SnackbarHostState,
    onDismiss: () -> Unit,
    onAdded: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ItemDto>>(emptyList()) }
    var quantity by remember { mutableStateOf("1") }

    LaunchedEffect(storeId, query) {
        if (storeId.isNotBlank()) {
            runCatching { repository.items(user.id, storeId, query) }.onSuccess { results = it.page }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add an existing item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Search items") })
                OutlinedTextField(
                    quantity,
                    { quantity = it },
                    label = { Text("Quantity ordered") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                LazyColumn(Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(results, key = { it.id }) { item ->
                        OutlinedButton(
                            enabled = quantity.toDoubleOrNull()?.let { it > 0 } == true,
                            onClick = {
                                val ordered = quantity.toDoubleOrNull() ?: return@OutlinedButton
                                scope.launch {
                                    runCatching {
                                        repository.addExistingItemLine(user.id, purchaseOrderId, item.id, ordered, null)
                                    }
                                        .onSuccess { onAdded() }
                                        .onFailure { snackbar.showSnackbar(it.message ?: "Unable to add the item") }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(item.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "${item.code ?: item.sku ?: "No barcode"} · ${moneyText(item.displayPriceCents)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun AddNewProductDialog(
    user: UserDto,
    purchaseOrderId: String,
    repository: InventoryRepository,
    snackbar: SnackbarHostState,
    onDismiss: () -> Unit,
    onAdded: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var saving by remember { mutableStateOf(false) }
    val priceCents = price.toDoubleOrNull()?.let(::dollarsToCents)
    val ordered = quantity.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a new product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "A barcode is reserved now so labels can be printed before the delivery is received.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(name, { name = it.take(255) }, label = { Text("Name") })
                OutlinedTextField(
                    price,
                    { price = it },
                    label = { Text("Retail price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(sku, { sku = it.take(128) }, label = { Text("SKU (optional)") })
                OutlinedTextField(
                    quantity,
                    { quantity = it },
                    label = { Text("Quantity ordered") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && name.isNotBlank() && priceCents != null && ordered != null && ordered > 0,
                onClick = {
                    saving = true
                    scope.launch {
                        runCatching {
                            repository.addNewProductLine(
                                userId = user.id,
                                purchaseOrderId = purchaseOrderId,
                                name = name.trim(),
                                priceCents = priceCents!!,
                                sku = sku.takeIf(String::isNotBlank),
                                code = null,
                                quantity = ordered!!,
                                labelCopies = null,
                            )
                        }
                            .onSuccess { onAdded() }
                            .onFailure { snackbar.showSnackbar(it.message ?: "Unable to add the product") }
                        saving = false
                    }
                },
            ) { Text(if (saving) "Adding…" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun UserDto.canManageRole() = role == "owner" || role == "manager"
private fun moneyText(cents: Double) = "$" + "%.2f".format(cents / 100.0)
