package com.inventory.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inventory.mobile.data.AdminUserDto
import com.inventory.mobile.data.CloverVarianceDto
import com.inventory.mobile.data.dollarsToCents
import com.inventory.mobile.data.InventoryRepository
import com.inventory.mobile.data.ItemDto
import com.inventory.mobile.data.ReportRowDto
import com.inventory.mobile.data.StoreDto
import com.inventory.mobile.data.UserDto
import com.inventory.mobile.data.VarianceDto
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.UUID

@Composable
fun ItemDialog(
    user: UserDto,
    item: ItemDto,
    repository: InventoryRepository,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var price by remember { mutableStateOf((item.displayPriceCents / 100.0).toString()) }
    var stock by remember { mutableStateOf(item.displayStock?.toString().orEmpty()) }
    var reason by remember { mutableStateOf("") }
    var deleteText by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val manager = user.role == "owner" || user.role == "manager"
    fun runChange(change: Map<String, Any?>) {
        scope.launch {
            saving = true
            runCatching {
                repository.changeItem(mapOf("requestId" to mobileRequestId(), "userId" to user.id, "itemId" to item.id, "change" to change))
            }.onSuccess { result ->
                if (result.status == "ok") onChanged() else snackbar.showSnackbar(result.message ?: "Clover needs reconciliation")
            }.onFailure { snackbar.showSnackbar(it.message ?: "Update failed") }
            saving = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("SKU/barcode: ${item.sku ?: item.code ?: "None"}") }
                item { Text("Price: ${moneyText(item.displayPriceCents)} · Stock: ${item.displayStock ?: "N/T"}") }
                item {
                    OutlinedButton(
                        enabled = item.code?.matches(Regex("\\d{12}")) == true,
                        onClick = { shareLabelsPdf(context, listOf(item to 1)) },
                    ) { Text("Print/share 0.9 in label") }
                }
                if (manager) {
                    item { OutlinedTextField(price, { price = it }, label = { Text("New price") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
                    item { OutlinedTextField(stock, { stock = it }, label = { Text("Register quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
                    item { OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)) }
                    item {
                        val priceCents = price.toDoubleOrNull()?.let(::dollarsToCents)
                        val quantity = stock.toDoubleOrNull()
                        val priceChanged = priceCents != null && priceCents != item.displayPriceCents
                        val stockChanged = quantity != null && quantity != item.displayStock
                        Button(
                            enabled = !saving && (priceChanged || stockChanged),
                            onClick = {
                                val change = when {
                                    priceChanged && stockChanged -> mapOf("kind" to "price_stock", "priceCents" to priceCents, "quantity" to quantity, "reason" to reason.takeIf(String::isNotBlank))
                                    priceChanged -> mapOf("kind" to "price", "priceCents" to priceCents)
                                    else -> mapOf("kind" to "stock", "quantity" to quantity, "reason" to reason.takeIf(String::isNotBlank))
                                }
                                runChange(change)
                            },
                        ) { Text(if (saving) "Saving…" else "Save changes") }
                    }
                    item { OutlinedTextField(deleteText, { deleteText = it }, label = { Text("Type DELETE") }) }
                    item { Button(enabled = !saving && deleteText == "DELETE", onClick = { runChange(mapOf("kind" to "delete")) }) { Text("Delete permanently") } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun AddItemDialog(
    user: UserDto,
    storeId: String,
    repository: InventoryRepository,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add item") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(name, { name = it }, label = { Text("Name") }) }
                item { OutlinedTextField(price, { price = it }, label = { Text("Price") }) }
                item { OutlinedTextField(sku, { sku = it }, label = { Text("SKU") }) }
                item { OutlinedTextField(code, { code = it.filter(Char::isDigit).take(12) }, label = { Text("Barcode") }) }
                item { OutlinedButton(onClick = { scope.launch { runCatching { repository.generateBarcode(user.id) }.onSuccess { code = it }.onFailure { snackbar.showSnackbar(it.message ?: "Generation failed") } } }) { Text("Generate barcode") } }
                item { OutlinedTextField(stock, { stock = it }, label = { Text("Initial stock") }) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && name.isNotBlank() && price.toDoubleOrNull() != null && (sku.isNotBlank() || code.isNotBlank()),
                onClick = {
                    scope.launch {
                        busy = true
                        runCatching {
                            repository.createItem(
                                mapOf(
                                    "requestId" to mobileRequestId(), "userId" to user.id, "storeId" to storeId,
                                    "name" to name, "priceCents" to dollarsToCents(price.toDouble()),
                                    "sku" to sku.takeIf(String::isNotBlank), "code" to code.takeIf(String::isNotBlank),
                                    "initialQty" to stock.toDoubleOrNull(),
                                ),
                            )
                        }.onSuccess { if (it.status == "ok") onCreated() else snackbar.showSnackbar(it.message ?: "Clover needs reconciliation") }
                            .onFailure { snackbar.showSnackbar(it.message ?: "Creation failed") }
                        busy = false
                    }
                },
            ) { Text(if (busy) "Saving…" else "Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun LabelsScreen(user: UserDto, repository: InventoryRepository, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    var stores by remember { mutableStateOf<List<StoreDto>>(emptyList()) }
    var storeId by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<ItemDto>>(emptyList()) }
    val copies = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(user.id) { runCatching { repository.stores(user.id) }.onSuccess { stores = it; storeId = it.firstOrNull()?.id.orEmpty() } }
    LaunchedEffect(storeId, query) {
        if (storeId.isNotBlank()) runCatching { repository.items(user.id, storeId, query) }.onSuccess { rows = it.page }.onFailure { snackbar.showSnackbar(it.message ?: "Unable to load labels") }
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactStorePicker(stores, storeId) { storeId = it }
        OutlinedTextField(query, { query = it }, label = { Text("Search labels") }, modifier = Modifier.fillMaxWidth())
        Button(
            enabled = copies.values.sum() > 0,
            onClick = { shareLabelsPdf(context, rows.mapNotNull { item -> copies[item.id]?.takeIf { it > 0 }?.let { item to it } }) },
        ) { Text("Print/share ${copies.values.sum()} label(s)") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows.filter { it.code?.matches(Regex("\\d{12}")) == true }, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.Bold); Text(item.code.orEmpty()) }
                        OutlinedButton(onClick = { copies[item.id] = (copies[item.id] ?: 0).minus(1).coerceAtLeast(0) }) { Text("−") }
                        Text("${copies[item.id] ?: 0}", modifier = Modifier.padding(top = 12.dp))
                        Button(onClick = { copies[item.id] = (copies[item.id] ?: 0) + 1 }) { Text("+") }
                    }
                }
            }
        }
    }
}

@Composable
fun VariancesScreen(user: UserDto, repository: InventoryRepository, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var stores by remember { mutableStateOf<List<StoreDto>>(emptyList()) }
    var storeId by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<VarianceDto>>(emptyList()) }
    var cloverRows by remember { mutableStateOf<List<CloverVarianceDto>>(emptyList()) }
    var showingCloverVariances by remember { mutableStateOf(false) }
    var comparingClover by remember { mutableStateOf(false) }
    var cloverComparisonLimited by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var dismissing by remember { mutableStateOf<VarianceDto?>(null) }
    fun pushConvexValuesToClover(variance: CloverVarianceDto) {
        val itemId = variance.itemId ?: return
        val priceChanged = variance.localPriceCents != null && variance.localPriceCents != variance.cloverPriceCents
        val stockChanged = variance.localStockQuantity != null && variance.localStockQuantity != variance.cloverStockQuantity
        val change = when {
            priceChanged && stockChanged -> mapOf("kind" to "price_stock", "priceCents" to variance.localPriceCents, "quantity" to variance.localStockQuantity, "reason" to "Clover variance recovery")
            priceChanged -> mapOf("kind" to "price", "priceCents" to variance.localPriceCents)
            stockChanged -> mapOf("kind" to "stock", "quantity" to variance.localStockQuantity, "reason" to "Clover variance recovery")
            else -> return
        }
        scope.launch {
            runCatching {
                repository.changeItem(mapOf("requestId" to mobileRequestId(), "userId" to user.id, "itemId" to itemId, "change" to change))
            }.onSuccess { result ->
                if (result.status == "ok") {
                    cloverRows = cloverRows.filterNot { it.cloverId == variance.cloverId }
                    snackbar.showSnackbar("Convex values pushed to Clover")
                } else {
                    snackbar.showSnackbar(result.message ?: "Clover needs reconciliation")
                }
            }.onFailure { snackbar.showSnackbar(it.message ?: "Unable to push values to Clover") }
        }
    }
    LaunchedEffect(user.id) { runCatching { repository.stores(user.id) }.onSuccess { stores = it; storeId = it.firstOrNull()?.id.orEmpty() } }
    LaunchedEffect(storeId, refresh) { if (storeId.isNotBlank()) runCatching { repository.variances(user.id, storeId) }.onSuccess { rows = it.page }.onFailure { snackbar.showSnackbar(it.message ?: "Unable to load variances") } }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactStorePicker(stores, storeId) { storeId = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showingCloverVariances = false }) { Text(if (!showingCloverVariances) "✓ Count variances" else "Count variances") }
            OutlinedButton(onClick = { showingCloverVariances = true }) { Text(if (showingCloverVariances) "✓ Clover variances" else "Clover variances") }
        }
        if (showingCloverVariances) {
            Button(enabled = storeId.isNotBlank() && !comparingClover, onClick = {
                scope.launch {
                    comparingClover = true
                    runCatching { repository.cloverVariances(user.id, storeId) }
                        .onSuccess { result -> cloverRows = result.rows; cloverComparisonLimited = result.limited }
                        .onFailure { snackbar.showSnackbar(it.message ?: "Unable to compare with Clover") }
                    comparingClover = false
                }
            }) { Text(if (comparingClover) "Comparing…" else "Compare with Clover") }
            if (cloverComparisonLimited) Text("This checks up to 500 Clover items at a time. Missing-from-Convex results are accurate; run a Clover sync before relying on missing-from-Clover results.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cloverRows, key = { "${it.kind}_${it.cloverId}" }) { variance ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(variance.itemName, fontWeight = FontWeight.Bold)
                            Text(when (variance.kind) { "missing_in_convex" -> "Missing from Convex"; "missing_in_clover" -> "Missing from Clover"; "archived_in_convex" -> "Archived in Convex"; else -> "Price or quantity differs" })
                            Text("Convex: ${variance.localPriceCents?.let(::moneyText) ?: "N/T"} · ${variance.localStockQuantity ?: "N/T"}")
                            Text("Clover: ${variance.cloverPriceCents?.let(::moneyText) ?: "N/T"} · ${variance.cloverStockQuantity ?: "N/T"}")
                            if (variance.kind == "values_differ" && variance.itemId != null && (variance.localPriceCents != variance.cloverPriceCents || (variance.localStockQuantity != null && variance.localStockQuantity != variance.cloverStockQuantity))) {
                                Button(onClick = { pushConvexValuesToClover(variance) }) { Text("Push Convex values to Clover") }
                            }
                        }
                    }
                }
            }
        } else {
        Button(enabled = rows.isNotEmpty(), onClick = {
            scope.launch {
                for (row in rows) {
                    var completed = false
                    resolveVariance(repository, user, row, "adjust", null, snackbar) { completed = true }
                    if (!completed) break
                }
                refresh++
            }
        }) { Text("Apply loaded (${rows.size})") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.id }) { variance ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(variance.itemName, fontWeight = FontWeight.Bold)
                        Text("Expected ${variance.expectedQty ?: "N/T"} · counted ${variance.countedQty} · variance ${variance.variance}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { scope.launch { resolveVariance(repository, user, variance, "adjust", null, snackbar) { refresh++ } } }) { Text("Apply") }
                            OutlinedButton(onClick = { dismissing = variance }) { Text("Dismiss") }
                        }
                    }
                }
            }
        }
    }
    }
    dismissing?.let { variance ->
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dismissing = null },
            title = { Text("Dismiss variance") },
            text = { OutlinedTextField(note, { note = it }, label = { Text("Required note") }) },
            confirmButton = { Button(enabled = note.isNotBlank(), onClick = { scope.launch { resolveVariance(repository, user, variance, "dismiss", note, snackbar) { refresh++; dismissing = null } } }) { Text("Dismiss") } },
            dismissButton = { TextButton(onClick = { dismissing = null }) { Text("Cancel") } },
        )
    }
}

private suspend fun resolveVariance(repository: InventoryRepository, user: UserDto, row: VarianceDto, action: String, note: String?, snackbar: SnackbarHostState, success: () -> Unit) {
    runCatching { repository.resolveVariance(mapOf("requestId" to mobileRequestId(), "userId" to user.id, "recordId" to row.id, "action" to action, "note" to note)) }
        .onSuccess { if (it.status == "ok") success() else snackbar.showSnackbar(it.message ?: "Variance needs reconciliation") }
        .onFailure { snackbar.showSnackbar(it.message ?: "Variance update failed") }
}

@Composable
fun ReportsScreen(user: UserDto, repository: InventoryRepository, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stores by remember { mutableStateOf<List<StoreDto>>(emptyList()) }
    var storeId by remember { mutableStateOf("") }
    var month by remember { mutableStateOf(YearMonth.now().toString()) }
    var summary by remember { mutableStateOf<com.inventory.mobile.data.ReportSummaryDto?>(null) }
    LaunchedEffect(user.id) { runCatching { repository.stores(user.id) }.onSuccess { stores = it; storeId = it.firstOrNull()?.id.orEmpty() } }
    LaunchedEffect(storeId, month) { if (storeId.isNotBlank()) runCatching { repository.report(user.id, storeId, month) }.onSuccess { summary = it }.onFailure { snackbar.showSnackbar(it.message ?: "Unable to load report") } }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CompactStorePicker(stores, storeId) { storeId = it }
        OutlinedTextField(month, { month = it.take(7) }, label = { Text("Month (YYYY-MM)") })
        summary?.summary?.let { stats ->
            Text("Counted ${stats.counted} · uncounted ${stats.uncounted}")
            Text("Variances ${stats.variances} · shrinkage ${moneyText(stats.shrinkageCents)}")
        }
        Button(enabled = storeId.isNotBlank(), onClick = {
            scope.launch {
                runCatching {
                    val output = mutableListOf<ReportRowDto>()
                    for (kind in listOf("counted", "uncounted")) {
                        var cursor: String? = null
                        do {
                            val page = repository.reportRows(user.id, storeId, month, kind, cursor)
                            output += page.page
                            cursor = if (page.isDone) null else page.continueCursor
                        } while (cursor != null)
                    }
                    shareCsv(context, "monthly-report-$month.csv", reportCsv(output))
                }.onFailure { snackbar.showSnackbar(it.message ?: "Export failed") }
            }
        }) { Text("Export CSV") }
    }
}

@Composable
fun AuditScreen(user: UserDto, repository: InventoryRepository, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<com.inventory.mobile.data.AuditDto>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("") }
    LaunchedEffect(query, action) { runCatching { repository.audit(user.id, action = action.takeIf(String::isNotBlank), query = query.takeIf(String::isNotBlank)) }.onSuccess { rows = it.page }.onFailure { snackbar.showSnackbar(it.message ?: "Unable to load audit") } }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(query, { query = it }, label = { Text("Search audit") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(action, { action = it }, label = { Text("Action filter") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            scope.launch {
                runCatching {
                    val exported = mutableListOf<com.inventory.mobile.data.AuditDto>()
                    var cursor: String? = null
                    do {
                        val page = repository.audit(user.id, action = action.takeIf(String::isNotBlank), query = query.takeIf(String::isNotBlank), cursor = cursor)
                        exported += page.page
                        cursor = if (page.isDone) null else page.continueCursor
                    } while (cursor != null)
                    shareCsv(context, "audit.csv", auditCsv(exported))
                }.onFailure { snackbar.showSnackbar(it.message ?: "Export failed") }
            }
        }) { Text("Export CSV") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.id }) { row -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(row.description); Text(row.ts, style = MaterialTheme.typography.labelSmall) } } }
        }
    }
}

@Composable
fun AdminScreen(user: UserDto, repository: InventoryRepository, snackbar: SnackbarHostState) {
    var users by remember { mutableStateOf<List<AdminUserDto>>(emptyList()) }
    var stores by remember { mutableStateOf<List<StoreDto>>(emptyList()) }
    var runs by remember { mutableStateOf<List<com.inventory.mobile.data.SyncRunDto>>(emptyList()) }
    var health by remember { mutableStateOf<List<com.inventory.mobile.data.OperationHealthDto>>(emptyList()) }
    var adding by remember { mutableStateOf(false) }
    var pinUser by remember { mutableStateOf<AdminUserDto?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    fun retryCloverWrite(operation: com.inventory.mobile.data.OperationHealthDto) {
        val itemId = operation.itemId ?: return
        val change = mutableMapOf<String, Any?>("kind" to operation.kind)
        operation.priceCents?.let { change["priceCents"] = it }
        operation.quantity?.let { change["quantity"] = it }
        operation.reason?.let { change["reason"] = it }
        scope.launch {
            runCatching {
                repository.changeItem(mapOf("requestId" to operation.requestId, "userId" to user.id, "itemId" to itemId, "change" to change))
            }.onSuccess { result ->
                if (result.status == "ok") {
                    snackbar.showSnackbar("Clover write recovered")
                } else {
                    snackbar.showSnackbar(result.message ?: "Clover still needs reconciliation")
                }
                refresh++
            }.onFailure { snackbar.showSnackbar(it.message ?: "Unable to retry Clover write") }
        }
    }
    LaunchedEffect(refresh) {
        runCatching {
            stores = repository.stores(user.id)
            users = repository.adminUsers(user.id)
            runs = repository.syncRuns(user.id)
            health = repository.operationHealth(user.id)
        }.onFailure { snackbar.showSnackbar(it.message ?: "Unable to load admin data") }
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onClick = { adding = true }) { Text("Add user") } }
        items(users, key = { it.id }) { admin ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${admin.name} · ${admin.role} · ${if (admin.active) "active" else "inactive"}", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { scope.launch { repository.saveUser(mapOf("actorId" to user.id, "userId" to admin.id, "active" to !admin.active)); refresh++ } }) { Text(if (admin.active) "Disable" else "Enable") }
                        OutlinedButton(onClick = { pinUser = admin }) { Text("Change PIN") }
                    }
                    if (admin.role != "owner") stores.forEach { store ->
                        val enabled = store.id in admin.storeIds
                        TextButton(onClick = {
                            scope.launch {
                                val next = if (enabled) admin.storeIds - store.id else admin.storeIds + store.id
                                runCatching { repository.saveUser(mapOf("actorId" to user.id, "userId" to admin.id, "storeIds" to next)) }
                                    .onSuccess { refresh++ }.onFailure { snackbar.showSnackbar(it.message ?: "Unable to update access") }
                            }
                        }) { Text(if (enabled) "✓ ${store.name}" else "○ ${store.name}") }
                    }
                }
            }
        }
        item { Text("Sync history", style = MaterialTheme.typography.titleMedium) }
        items(runs.take(20), key = { it.id }) { run -> Text("${run.storeName}: ${run.status} · ${run.itemsUpserted} updated, ${run.itemsTombstoned} removed") }
        item { Text("Clover write recovery", style = MaterialTheme.typography.titleMedium) }
        items(health, key = { it.requestId }) { operation ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${operation.storeName}: ${operation.kind} · ${operation.status}", fontWeight = FontWeight.Bold)
                    Text(operation.errorMessage ?: "Requires attention")
                    if (operation.itemId != null) {
                        Button(onClick = { retryCloverWrite(operation) }) { Text("Retry in Clover") }
                    } else {
                        Text("Open the original item or variance entry to retry this write.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    if (adding) AddUserDialog(user, stores, repository, snackbar, { adding = false }, { adding = false; refresh++ })
    pinUser?.let { admin ->
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pinUser = null },
            title = { Text("Change ${admin.name}'s PIN") },
            text = { OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(6) }, label = { Text("New 4–6 digit PIN") }) },
            confirmButton = { Button(enabled = pin.length >= 4, onClick = { scope.launch { runCatching { repository.saveUser(mapOf("actorId" to user.id, "userId" to admin.id, "pin" to pin)) }.onSuccess { pinUser = null; refresh++ }.onFailure { snackbar.showSnackbar(it.message ?: "Unable to update PIN") } } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { pinUser = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AddUserDialog(owner: UserDto, stores: List<StoreDto>, repository: InventoryRepository, snackbar: SnackbarHostState, onDismiss: () -> Unit, onCreated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("clerk") }
    val selectedStores = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add user") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { OutlinedTextField(name, { name = it }, label = { Text("Name") }) }
                item { OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(6) }, label = { Text("PIN") }) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("clerk", "manager", "owner").forEach { option -> OutlinedButton(onClick = { role = option }) { Text(if (role == option) "✓ $option" else option) } }
                    }
                }
                if (role != "owner") items(stores, key = { it.id }) { store ->
                    TextButton(onClick = { if (store.id in selectedStores) selectedStores.remove(store.id) else selectedStores.add(store.id) }) { Text(if (store.id in selectedStores) "✓ ${store.name}" else store.name) }
                }
            }
        },
        confirmButton = { Button(enabled = name.isNotBlank() && pin.length >= 4, onClick = { scope.launch { runCatching { repository.saveUser(mapOf("actorId" to owner.id, "name" to name, "pin" to pin, "role" to role, "storeIds" to selectedStores.toList())) }.onSuccess { onCreated() }.onFailure { snackbar.showSnackbar(it.message ?: "Unable to create user") } } }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CompactStorePicker(stores: List<StoreDto>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        stores.take(4).forEach { store -> OutlinedButton(onClick = { onSelect(store.id) }) { Text(if (selected == store.id) "✓ ${store.name}" else store.name) } }
    }
}

private fun mobileRequestId() = "android_${UUID.randomUUID().toString().replace("-", "_")}"
private fun moneyText(cents: Double) = "$" + "%.2f".format(cents / 100.0)
