package com.inventory.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inventory.mobile.data.ContactDto
import com.inventory.mobile.data.InventoryRepository
import com.inventory.mobile.data.MessageDto
import com.inventory.mobile.data.StoreDto
import com.inventory.mobile.data.UserDto
import kotlinx.coroutines.launch

/**
 * Inbox for the signed-in user. Every user can read what was sent to them and write to anyone;
 * owners and managers can additionally broadcast to everyone or to the store they are working in.
 */
@Composable
fun InboxScreen(
    user: UserDto,
    store: StoreDto,
    repository: InventoryRepository,
    snackbar: SnackbarHostState,
    onInboxChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showingSent by remember { mutableStateOf(false) }
    var unreadOnly by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<MessageDto>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var isDone by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var reading by remember { mutableStateOf<MessageDto?>(null) }
    var composing by remember { mutableStateOf(false) }

    LaunchedEffect(user.id, showingSent, unreadOnly, refresh) {
        loading = true
        runCatching { if (showingSent) repository.sentMessages(user.id) else repository.inbox(user.id, unreadOnly) }
            .onSuccess { page -> rows = page.page; cursor = page.continueCursor; isDone = page.isDone }
            .onFailure { snackbar.showSnackbar(it.message ?: "Unable to load messages") }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showingSent = false }) { Text(if (!showingSent) "✓ Inbox" else "Inbox") }
            OutlinedButton(onClick = { showingSent = true }) { Text(if (showingSent) "✓ Sent" else "Sent") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { composing = true }) { Icon(Icons.Default.Edit, null); Text(" New message") }
            if (!showingSent) {
                OutlinedButton(onClick = { unreadOnly = !unreadOnly }) { Text(if (unreadOnly) "✓ Unread only" else "Unread only") }
            }
        }
        if (!showingSent) {
            OutlinedButton(
                enabled = rows.any { !it.read },
                onClick = {
                    scope.launch {
                        runCatching { repository.markAllMessagesRead(user.id) }
                            .onSuccess { result ->
                                refresh++
                                onInboxChanged()
                                if (result.hasMore) snackbar.showSnackbar("Marked ${result.marked.toInt()} read. Run again for the rest.")
                            }
                            .onFailure { snackbar.showSnackbar(it.message ?: "Unable to mark messages read") }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Mark all read") }
        }
        if (!loading && rows.isEmpty()) {
            Text(
                if (showingSent) "You have not sent any messages." else if (unreadOnly) "No unread messages." else "Your inbox is empty.",
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(rows, key = { it.id }) { message ->
                MessageCard(message, showingSent) {
                    reading = message
                    if (!showingSent && !message.read) {
                        scope.launch {
                            runCatching { repository.markMessageRead(user.id, message.messageId) }
                                .onSuccess {
                                    rows = rows.map { if (it.id == message.id) it.copy(read = true) else it }
                                    onInboxChanged()
                                }
                                .onFailure { snackbar.showSnackbar(it.message ?: "Unable to mark read") }
                        }
                    }
                }
            }
        }
        if (!isDone) OutlinedButton(onClick = {
            scope.launch {
                runCatching { if (showingSent) repository.sentMessages(user.id, cursor) else repository.inbox(user.id, unreadOnly, cursor) }
                    .onSuccess { page -> rows = rows + page.page; cursor = page.continueCursor; isDone = page.isDone }
                    .onFailure { snackbar.showSnackbar(it.message ?: "Unable to load more") }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Load more") }
    }

    reading?.let { message ->
        MessageDialog(
            message = message,
            showingSent = showingSent,
            onDismiss = { reading = null },
            onRemove = {
                scope.launch {
                    runCatching { repository.removeMessage(user.id, message.messageId) }
                        .onSuccess { reading = null; refresh++; onInboxChanged() }
                        .onFailure { snackbar.showSnackbar(it.message ?: "Unable to remove message") }
                }
            },
        )
    }
    if (composing) {
        ComposeMessageDialog(
            user = user,
            store = store,
            repository = repository,
            snackbar = snackbar,
            onDismiss = { composing = false },
            onSent = { composing = false; refresh++ },
        )
    }
}

@Composable
private fun MessageCard(message: MessageDto, showingSent: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (message.isBroadcast) Icon(Icons.Default.Campaign, "Broadcast")
                Text(
                    if (showingSent) message.audienceLabel() else message.senderName,
                    fontWeight = if (message.read) FontWeight.Normal else FontWeight.Bold,
                )
            }
            message.subject?.let { Text(it, fontWeight = if (message.read) FontWeight.Normal else FontWeight.Bold) }
            Text(message.body.lineSequence().first().take(80), style = MaterialTheme.typography.bodySmall)
            Text(
                messageTimestamp(message.createdAt) + if (!showingSent && !message.read) " · Unread" else "",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun MessageDialog(message: MessageDto, showingSent: Boolean, onDismiss: () -> Unit, onRemove: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(message.subject ?: if (message.isBroadcast) "Broadcast" else "Message") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        if (showingSent) "To ${message.audienceLabel()}" else "From ${message.senderName}",
                        fontWeight = FontWeight.Bold,
                    )
                }
                item { Text(messageTimestamp(message.createdAt), style = MaterialTheme.typography.labelSmall) }
                item { Text(message.body) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { if (!showingSent) TextButton(onClick = onRemove) { Text("Remove") } },
    )
}

@Composable
private fun ComposeMessageDialog(
    user: UserDto,
    store: StoreDto,
    repository: InventoryRepository,
    snackbar: SnackbarHostState,
    onDismiss: () -> Unit,
    onSent: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val canBroadcast = user.role == "owner" || user.role == "manager"
    var broadcasting by remember { mutableStateOf(false) }
    var everyone by remember { mutableStateOf(true) }
    var contacts by remember { mutableStateOf<List<ContactDto>>(emptyList()) }
    var recipient by remember { mutableStateOf<ContactDto?>(null) }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    LaunchedEffect(user.id) {
        runCatching { repository.contacts(user.id) }
            .onSuccess { contacts = it }
            .onFailure { snackbar.showSnackbar(it.message ?: "Unable to load people") }
    }
    val ready = body.isNotBlank() && (broadcasting || recipient != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (broadcasting) "Broadcast message" else "New message") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canBroadcast) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { broadcasting = false }) { Text(if (!broadcasting) "✓ One person" else "One person") }
                            OutlinedButton(onClick = { broadcasting = true }) { Text(if (broadcasting) "✓ Everyone" else "Everyone") }
                        }
                    }
                }
                if (broadcasting) {
                    item { Text("Send to", fontWeight = FontWeight.Bold) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { everyone = true }) { Text(if (everyone) "✓ All users" else "All users") }
                            OutlinedButton(onClick = { everyone = false }) { Text(if (!everyone) "✓ ${store.name}" else store.name) }
                        }
                    }
                    item {
                        Text(
                            if (everyone) "Every active user receives this in their inbox."
                            else "Users with access to ${store.name}, plus owners, receive this.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    item { Text("To", fontWeight = FontWeight.Bold) }
                    if (contacts.isEmpty()) item { Text("No other users are available.", color = MaterialTheme.colorScheme.error) }
                    items(contacts, key = { it.id }) { contact ->
                        TextButton(onClick = { recipient = contact }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (recipient?.id == contact.id) "✓ ${contact.name} · ${contact.role}" else "${contact.name} · ${contact.role}")
                        }
                    }
                }
                item { OutlinedTextField(subject, { subject = it.take(120) }, label = { Text("Subject (optional)") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(body, { body = it.take(4_000) }, label = { Text("Message") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            Button(
                enabled = ready && !sending,
                onClick = {
                    scope.launch {
                        sending = true
                        runCatching {
                            if (broadcasting) {
                                repository.broadcastMessage(user.id, if (everyone) null else store.id, subject.takeIf(String::isNotBlank), body)
                            } else {
                                repository.sendDirectMessage(user.id, recipient!!.id, subject.takeIf(String::isNotBlank), body)
                            }
                        }.onSuccess { result ->
                            snackbar.showSnackbar("Sent to ${result.recipientCount.toInt()} ${if (result.recipientCount == 1.0) "person" else "people"}")
                            onSent()
                        }.onFailure { snackbar.showSnackbar(it.message ?: "Unable to send message") }
                        sending = false
                    }
                },
            ) { Text(if (sending) "Sending…" else "Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun MessageDto.audienceLabel() = when {
    !isBroadcast -> "1 person"
    storeName != null -> "$storeName · ${recipientCount.toInt()} people"
    else -> "Everyone · ${recipientCount.toInt()} people"
}

/** Convex stores ISO-8601 timestamps; show the minute precision part without the timezone noise. */
private fun messageTimestamp(value: String) = value.take(16).replace('T', ' ')
