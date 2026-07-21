package com.inventory.mobile.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inventory.mobile.AppContainer
import com.inventory.mobile.BuildConfig
import com.inventory.mobile.data.CountQueueSync
import com.inventory.mobile.data.InventoryRepository
import com.inventory.mobile.data.ItemDto
import com.inventory.mobile.data.ProgressDto
import com.inventory.mobile.data.QueuedCount
import com.inventory.mobile.data.SearchGroupDto
import com.inventory.mobile.data.StoreDto
import com.inventory.mobile.data.UserDto
import com.inventory.mobile.update.AvailableUpdate
import com.inventory.mobile.update.GitHubReleaseChecker
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.UUID

private enum class Screen(val label: String) {
    Home("Home"), Items("Items"), Find("Find"), Counts("Count"), More("More"), Labels("Labels"), Variances("Variances"), Reports("Reports"), Audit("Audit"), Admin("Admin")
}

private val pilotLightColors = lightColorScheme(
    primary = Color(0xFF146C5A),
    secondary = Color(0xFF52616B),
    error = Color(0xFFB3261E),
    background = Color(0xFFF5F7FA),
    surface = Color.White,
)

private val pilotDarkColors = darkColorScheme(
    primary = Color(0xFF5BD0C0),
    onPrimary = Color(0xFF00372F),
    secondary = Color(0xFFA8B5BB),
    background = Color(0xFF0F1518),
    surface = Color(0xFF182126),
    surfaceVariant = Color(0xFF263238),
    onBackground = Color(0xFFEAF1F3),
    onSurface = Color(0xFFEAF1F3),
    onSurfaceVariant = Color(0xFFC7D1D5),
    outline = Color(0xFF71858E),
    error = Color(0xFFFFB4AB),
)

@Composable
fun InventoryApp(container: AppContainer, onInstallUpdate: (AvailableUpdate) -> Unit) {
    val user by container.sessionStore.user.collectAsStateWithLifecycle(initialValue = null)
    val savedDarkMode by container.sessionStore.darkMode.collectAsStateWithLifecycle(initialValue = null)
    val darkMode = savedDarkMode ?: isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val colorScheme = if (darkMode) pilotDarkColors else pilotLightColors
    var availableUpdate by remember { mutableStateOf<AvailableUpdate?>(null) }
    LaunchedEffect(Unit) {
        availableUpdate = runCatching {
            GitHubReleaseChecker.findAvailableUpdate(
                repository = BuildConfig.UPDATE_REPOSITORY,
                currentVersion = BuildConfig.VERSION_NAME,
            )
        }.getOrNull()
    }
    SideEffect {
        val activity = view.context as? ComponentActivity ?: return@SideEffect
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = colorScheme.background.toArgb(),
                darkScrim = colorScheme.background.toArgb(),
            ) { darkMode },
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = colorScheme.surface.toArgb(),
                darkScrim = colorScheme.surface.toArgb(),
            ) { darkMode },
        )
    }
    val toggleDarkMode = {
        scope.launch { container.sessionStore.saveDarkMode(!darkMode) }
        Unit
    }
    MaterialTheme(colorScheme = colorScheme) {
        if (!container.configured) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Missing INVENTORY_CONVEX_URL. Add it to ~/.gradle/gradle.properties or pass -PINVENTORY_CONVEX_URL=https://…convex.cloud.")
            }
        } else if (user == null) {
            LoginScreen(container.repository, darkMode, toggleDarkMode) { loggedIn ->
                scope.launch { container.sessionStore.save(loggedIn) }
            }
        } else {
            InventoryShell(user!!, container, darkMode, toggleDarkMode)
        }
        availableUpdate?.let { update ->
            AlertDialog(
                onDismissRequest = { availableUpdate = null },
                title = { Text("Update available") },
                text = { Text("Inventory ${update.versionName} is ready to install.") },
                confirmButton = {
                    Button(onClick = {
                        availableUpdate = null
                        onInstallUpdate(update)
                    }) { Text("Update") }
                },
                dismissButton = { TextButton(onClick = { availableUpdate = null }) { Text("Not now") } },
            )
        }
    }
}

@Composable
private fun LoginScreen(repository: InventoryRepository, darkMode: Boolean, onToggleDarkMode: () -> Unit, onLogin: (UserDto) -> Unit) {
    var users by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        runCatching { repository.loginUsers() }.onSuccess { users = it }.onFailure { error = it.message }
    }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        IconButton(onClick = onToggleDarkMode, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode, if (darkMode) "Switch to light mode" else "Switch to dark mode")
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Inventory", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Internal pilot — PIN access is not a production security boundary.", color = MaterialTheme.colorScheme.error)
                Text("Choose your name")
                users.forEach { name ->
                    OutlinedButton(onClick = { selected = name; pin = "" }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selected == name) "✓ $name" else name)
                    }
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                    label = { Text("4–6 digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = selected.isNotBlank() && pin.length >= 4 && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            runCatching { repository.login(selected, pin) }.onSuccess(onLogin).onFailure { error = it.message }
                            pin = ""
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Signing in…" else "Sign in") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryShell(user: UserDto, container: AppContainer, darkMode: Boolean, onToggleDarkMode: () -> Unit) {
    var screen by remember { mutableStateOf(Screen.Home) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Column { Text(screen.label); Text("${user.name} · ${user.role}", style = MaterialTheme.typography.labelSmall) } },
                actions = {
                    IconButton(onClick = onToggleDarkMode) {
                        Icon(if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode, if (darkMode) "Switch to light mode" else "Switch to dark mode")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            container.queueSync.clearUser(user.id)
                            container.sessionStore.save(null)
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.Logout, "Sign out") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple(Screen.Home, Icons.Default.Home, "Home"),
                    Triple(Screen.Items, Icons.Default.Inventory2, "Items"),
                    Triple(Screen.Find, Icons.Default.Search, "Find"),
                    Triple(Screen.Counts, Icons.Default.Checklist, "Count"),
                    Triple(Screen.More, Icons.Default.MoreHoriz, "More"),
                ).forEach { (target, icon, label) ->
                    NavigationBarItem(selected = screen == target, onClick = { screen = target }, icon = { Icon(icon, label) }, label = { Text(label) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (screen) {
                Screen.Home -> HomeScreen(user, container.repository, snackbar)
                Screen.Items -> ItemsScreen(user, container.repository, snackbar)
                Screen.Find -> FindScreen(user, container.repository)
                Screen.Counts -> CountsScreen(user, container.repository, container.queueSync, snackbar)
                Screen.More -> MoreScreen(user) { screen = it }
                Screen.Labels -> LabelsScreen(user, container.repository, snackbar)
                Screen.Variances -> VariancesScreen(user, container.repository, snackbar)
                Screen.Reports -> ReportsScreen(user, container.repository, snackbar)
                Screen.Audit -> AuditScreen(user, container.repository, snackbar)
                Screen.Admin -> AdminScreen(user, container.repository, snackbar)
            }
        }
    }
}

@Composable
private fun HomeScreen(user: UserDto, repository: InventoryRepository, snackbar: SnackbarHostState) {
    var stores by remember { mutableStateOf<List<StoreDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(user.id, refresh) {
        loading = true
        runCatching { repository.stores(user.id) }.onSuccess { stores = it }.onFailure { snackbar.showSnackbar(it.message ?: "Unable to load stores") }
        loading = false
    }
    ScreenList(loading = loading) {
        items(stores, key = { it.id }) { store ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(store.name, style = MaterialTheme.typography.titleLarge)
                    Text("${store.itemCount ?: 0} items · ${store.counted ?: 0}/${store.total ?: store.itemCount ?: 0} counted")
                    Text("Last sync ${store.lastSyncedAt ?: "not yet"}")
                    if (store.authFailed) Text("Clover authorization failed", color = MaterialTheme.colorScheme.error)
                    if (store.syncStale) Text("Clover sync is stale", color = MaterialTheme.colorScheme.error)
                    if (user.canManage()) Button(onClick = {
                        scope.launch {
                            runCatching { repository.syncStore(user.id, store.id) }
                                .onSuccess { refresh++ }
                                .onFailure { snackbar.showSnackbar(it.message ?: "Sync failed") }
                        }
                    }) { Text("Sync") }
                }
            }
        }
    }
}

@Composable
private fun ItemsScreen(user: UserDto, repository: InventoryRepository, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var stores by remember { mutableStateOf<List<StoreDto>>(emptyList()) }
    var storeId by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<ItemDto>>(emptyList()) }
    var selected by remember { mutableStateOf<ItemDto?>(null) }
    var adding by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var isDone by remember { mutableStateOf(true) }
    LaunchedEffect(user.id) {
        runCatching { repository.stores(user.id) }.onSuccess { stores = it; if (storeId.isBlank()) storeId = it.firstOrNull()?.id.orEmpty() }
    }
    LaunchedEffect(storeId, query, refresh) {
        if (storeId.isNotBlank()) runCatching { repository.items(user.id, storeId, query) }
            .onSuccess { rows = it.page; cursor = it.continueCursor; isDone = it.isDone }.onFailure { snackbar.showSnackbar(it.message ?: "Unable to load items") }
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StorePicker(stores, storeId) { storeId = it }
        SearchBox(query, { query = it }, { scanning = true })
        if (user.canManage()) Button(onClick = { adding = true }) { Icon(Icons.Default.Add, null); Text(" Add item") }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(rows, key = { it.id }) { item -> ItemCard(item) { selected = item } }
        }
        if (!isDone) OutlinedButton(onClick = {
            scope.launch {
                runCatching { repository.items(user.id, storeId, query, cursor) }.onSuccess { page ->
                    rows = rows + page.page
                    cursor = page.continueCursor
                    isDone = page.isDone
                }.onFailure { snackbar.showSnackbar(it.message ?: "Unable to load more") }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Load more") }
    }
    if (scanning) BarcodeScannerDialog(onResult = { query = it; scanning = false }, onDismiss = { scanning = false })
    selected?.let { item -> ItemDialog(user, item, repository, onDismiss = { selected = null }, onChanged = { selected = null; refresh++ }, snackbar = snackbar) }
    if (adding && storeId.isNotBlank()) AddItemDialog(user, storeId, repository, { adding = false }, { adding = false; refresh++ }, snackbar)
}

@Composable
private fun FindScreen(user: UserDto, repository: InventoryRepository) {
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<SearchGroupDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var isDone by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(query) {
        if (query.trim().length >= 2) runCatching { repository.find(user.id, query) }.onSuccess { rows = it.page; cursor = it.continueCursor; isDone = it.isDone }.onFailure { error = it.message }
        else rows = emptyList()
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchBox(query, { query = it }, { scanning = true })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows) { group ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(group.name, fontWeight = FontWeight.Bold)
                        Text("${group.sku ?: group.code ?: "No SKU"} · ${money(group.priceCents)}")
                        group.stores.forEach { Text("${it.storeName}: ${it.stockQuantity ?: "not tracked"}") }
                    }
                }
            }
        }
        if (!isDone) OutlinedButton(onClick = {
            scope.launch {
                runCatching { repository.find(user.id, query, cursor) }.onSuccess { page ->
                    rows = rows + page.page
                    cursor = page.continueCursor
                    isDone = page.isDone
                }.onFailure { error = it.message }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Load more") }
    }
    if (scanning) BarcodeScannerDialog(onResult = { query = it; scanning = false }, onDismiss = { scanning = false })
}

@Composable
private fun CountsScreen(user: UserDto, repository: InventoryRepository, queue: CountQueueSync, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val queued by queue.queued.collectAsStateWithLifecycle(initialValue = emptyList())
    var stores by remember { mutableStateOf<List<StoreDto>>(emptyList()) }
    var storeId by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<ItemDto>>(emptyList()) }
    var selected by remember { mutableStateOf<ItemDto?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<ProgressDto?>(null) }
    LaunchedEffect(user.id) {
        runCatching { repository.stores(user.id) }.onSuccess { stores = it; if (storeId.isBlank()) storeId = it.firstOrNull()?.id.orEmpty() }
        queue.flush(repository)
    }
    LaunchedEffect(storeId, query, queued.size) {
        if (storeId.isNotBlank()) runCatching { repository.progress(user.id, storeId) }.onSuccess { progress = it }
        if (storeId.isNotBlank() && query.trim().length >= 2) runCatching { repository.countSearch(user.id, storeId, query) }
            .onSuccess { rows = it }.onFailure { snackbar.showSnackbar(it.message ?: "Search unavailable") }
        else rows = emptyList()
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StorePicker(stores, storeId) { storeId = it }
        progress?.let { Text("${it.counted} of ${it.total} counted · ${it.remaining} remaining · ${it.pendingVariances} pending variances") }
        SearchBox(query, { query = it }, { scanning = true })
        queued.filter { it.userId == user.id }.forEach { entry -> QueuedCountCard(entry, queue, repository, snackbar) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.id }) { item ->
                ItemCard(item) { selected = item }
            }
        }
    }
    if (scanning) BarcodeScannerDialog(onResult = { query = it; scanning = false }, onDismiss = { scanning = false })
    selected?.let { item ->
        CountDialog(item, onDismiss = { selected = null }) { quantity, note ->
            scope.launch {
                runCatching {
                    queue.enqueue(user, storeId, item, quantity, note, YearMonth.now().toString())
                    queue.flush(repository)
                }.onSuccess {
                    snackbar.showSnackbar("Count saved or queued for sync")
                    selected = null
                }.onFailure { snackbar.showSnackbar(it.message ?: "Count queued") }
            }
        }
    }
}

@Composable
private fun QueuedCountCard(entry: QueuedCount, queue: CountQueueSync, repository: InventoryRepository, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${entry.itemName}: ${entry.countedQty}", fontWeight = FontWeight.Bold)
            if (entry.state == "conflict") {
                Text("Conflict: ${entry.conflictBy ?: "another user"} saved ${entry.conflictQty} at ${entry.conflictAt}", color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { runCatching { queue.force(repository, entry) }.onFailure { snackbar.showSnackbar(it.message ?: "Unable to submit") } } }) { Text("Submit mine") }
                    OutlinedButton(onClick = { scope.launch { queue.discard(entry.requestId) } }) { Text("Discard") }
                }
            } else Text("Waiting for network", color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun MoreScreen(user: UserDto, onNavigate: (Screen) -> Unit) {
    val choices = buildList {
        add(Screen.Labels)
        if (user.canManage()) addAll(listOf(Screen.Variances, Screen.Reports, Screen.Audit))
        if (user.role == "owner") add(Screen.Admin)
    }
    ScreenList {
        items(choices) { target ->
            OutlinedButton(onClick = { onNavigate(target) }, modifier = Modifier.fillMaxWidth()) { Text(target.label) }
        }
    }
}

@Composable
private fun ScreenList(loading: Boolean = false, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp), content = content)
}

@Composable
private fun StorePicker(stores: List<StoreDto>, selected: String, onSelect: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().height((stores.size.coerceAtMost(3) * 44 + 4).dp)) {
        items(stores, key = { it.id }) { store ->
            TextButton(onClick = { onSelect(store.id) }, modifier = Modifier.fillMaxWidth()) { Text(if (store.id == selected) "✓ ${store.name}" else store.name) }
        }
    }
}

@Composable
private fun SearchBox(value: String, onValue: (String) -> Unit, onScan: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text("Search name, SKU, or barcode") },
        singleLine = true,
        trailingIcon = { IconButton(onClick = onScan) { Icon(Icons.Default.CameraAlt, "Scan barcode") } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ItemCard(item: ItemDto, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text(item.name, fontWeight = FontWeight.Bold)
            Text("${item.sku ?: item.code ?: "No SKU"} · ${money(item.displayPriceCents)} · stock ${item.displayStock ?: "N/T"}")
        }
    }
}

@Composable
private fun CountDialog(item: ItemDto, onDismiss: () -> Unit, onSave: (Double, String?) -> Unit) {
    var quantity by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Blind count: expected quantity is shown after sync.")
                item.lastCountedAt?.let { Text("Last counted $it${item.lastCountedBy?.let { by -> " by $by" } ?: ""}") }
                OutlinedTextField(quantity, { quantity = it }, label = { Text("Counted quantity") })
                OutlinedTextField(note, { note = it }, label = { Text("Note") })
            }
        },
        confirmButton = { Button(enabled = quantity.toDoubleOrNull() != null, onClick = { onSave(quantity.toDouble(), note.takeIf(String::isNotBlank)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun UserDto.canManage() = role == "owner" || role == "manager"
private fun money(cents: Double) = "$" + "%.2f".format(cents / 100.0)
private fun requestId() = "android_${UUID.randomUUID().toString().replace("-", "_")}"
