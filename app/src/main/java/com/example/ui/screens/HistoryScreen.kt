package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.export.ConversationExporter
import com.example.data.model.AiMode
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.UiState
import com.example.ui.components.GlowingCard
import com.example.ui.theme.LocalXproColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HistoryScreen(
    conversationsState: UiState<List<Conversation>>,
    onOpenConversation: (Conversation) -> Unit,
    onTogglePin: (Conversation) -> Unit,
    onRename: (Conversation, String) -> Unit,
    onDelete: (String) -> Unit,
    onFetchMessagesForExport: (Conversation, (List<ChatMessage>) -> Unit) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf<AiMode?>(null) }
    var renamingConv by remember { mutableStateOf<Conversation?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var deletingConv by remember { mutableStateOf<Conversation?>(null) }
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val xproColors = LocalXproColors.current
    val dateFormatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    val exportTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val content = pendingExportText
        pendingExportText = null
        if (uri != null && content != null) {
            val result = ConversationExporter.writeTextToUri(context, uri, content)
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (result.isSuccess) "Conversation exported as text file"
                    else result.exceptionOrNull()?.localizedMessage ?: "Export failed"
                )
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            Text(
                text = "Cloud Chat History",
                style = MaterialTheme.typography.headlineMedium
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search saved conversations...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedMode == null,
                    onClick = { selectedMode = null },
                    label = { Text("All") },
                    modifier = Modifier.testTag("history_filter_all")
                )
                AiMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = if (selectedMode == mode) null else mode },
                        label = { Text(mode.displayName) },
                        modifier = Modifier.testTag("history_filter_${mode.name.lowercase()}")
                    )
                }
            }

            when (conversationsState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = xproColors.neonGlow)
                    }
                }

                is UiState.Error -> {
                    GlowingCard {
                        Text(
                            text = conversationsState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                is UiState.Success -> {
                    val filtered = remember(conversationsState.data, searchQuery, selectedMode) {
                        conversationsState.data.filter { conv ->
                            val matchesMode = selectedMode == null || conv.aiMode == selectedMode
                            val matchesQuery = searchQuery.isBlank() ||
                                conv.title.contains(searchQuery, ignoreCase = true) ||
                                conv.lastMessagePreview.contains(searchQuery, ignoreCase = true)
                            matchesMode && matchesQuery
                        }
                    }

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = xproColors.neonGlow,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "No matching conversations",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Your cloud-synced AI sessions will appear here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.id }) { conv ->
                                GlowingCard(
                                    accentBorder = conv.pinned,
                                    onClick = { onOpenConversation(conv) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("history_item_${conv.id}")
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = conv.aiMode.displayName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = conv.aiProvider.displayName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = conv.updatedAt?.toDate()?.let { dateFormatter.format(it) } ?: "Just now",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = conv.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (conv.lastMessagePreview.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = conv.lastMessagePreview,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${conv.messageCount} messages",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row {
                                                IconButton(
                                                    onClick = {
                                                        onFetchMessagesForExport(conv) { msgs ->
                                                            pendingExportText = ConversationExporter.formatAsPlainText(
                                                                title = conv.title,
                                                                mode = conv.aiMode,
                                                                provider = conv.aiProvider,
                                                                messages = msgs
                                                            )
                                                            exportTxtLauncher.launch(
                                                                ConversationExporter.buildSuggestedFilename(
                                                                    conv.title,
                                                                    conv.aiMode
                                                                )
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("export_conv_${conv.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Download,
                                                        contentDescription = "Export as text file",
                                                        tint = xproColors.neonGlow
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onTogglePin(conv) },
                                                    modifier = Modifier
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("pin_conv_${conv.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = if (conv.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                        contentDescription = if (conv.pinned) "Unpin" else "Pin",
                                                        tint = if (conv.pinned) xproColors.neonGlow else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        renamingConv = conv
                                                        renameDraft = conv.title
                                                    },
                                                    modifier = Modifier
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("rename_conv_${conv.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Edit,
                                                        contentDescription = "Rename",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { deletingConv = conv },
                                                    modifier = Modifier
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("delete_conv_${conv.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Delete,
                                                        contentDescription = "Delete",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (renamingConv != null) {
        AlertDialog(
            onDismissRequest = { renamingConv = null },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renamingConv!!
                    renamingConv = null
                    onRename(target, renameDraft)
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingConv = null }) { Text("Cancel") }
            }
        )
    }

    if (deletingConv != null) {
        AlertDialog(
            onDismissRequest = { deletingConv = null },
            title = { Text("Delete Conversation?") },
            text = { Text("This will permanently remove \"${deletingConv!!.title}\" from cloud history.") },
            confirmButton = {
                TextButton(onClick = {
                    val targetId = deletingConv!!.id
                    deletingConv = null
                    onDelete(targetId)
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingConv = null }) { Text("Cancel") }
            }
        )
    }
}
