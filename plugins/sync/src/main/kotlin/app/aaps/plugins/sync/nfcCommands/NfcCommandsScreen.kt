@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package app.aaps.plugins.sync.nfcCommands

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.DateFormat
import app.aaps.plugins.sync.R
import app.aaps.core.ui.R as CoreUiR

private sealed class NfcRoute {
    object Main : NfcRoute()
    object Build : NfcRoute()
}

class NfcCommandsComposeContent(val plugin: NfcCommandsPlugin) : ComposablePluginContent {
    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?,
    ) {
        var route by remember { mutableStateOf<NfcRoute>(NfcRoute.Main) }
        var initialTab by remember { mutableIntStateOf(1) } // Default to My Tags tab
        var editTarget by remember { mutableStateOf<NfcCreatedTag?>(null) }

        when (route) {
            NfcRoute.Main ->
                NfcCommandsScreen(
                    plugin = plugin,
                    nfcTagStore = plugin.nfcTagStore,
                    setToolbarConfig = setToolbarConfig,
                    onNavigateBack = onNavigateBack,
                    onSettings = onSettings,
                    initialTab = initialTab,
                    onBuild = {
                        editTarget = null
                        initialTab = 1
                        route = NfcRoute.Build
                    },
                    onEdit = { tag ->
                        editTarget = tag
                        initialTab = 1
                        route = NfcRoute.Build
                    },
                    onTabChanged = { initialTab = it }
                )
            NfcRoute.Build ->
                NfcBuildScreen(
                    plugin = plugin,
                    setToolbarConfig = setToolbarConfig,
                    onBack = {
                        initialTab = 1
                        route = NfcRoute.Main
                    },
                    onTagWritten = {
                        initialTab = 1
                        route = NfcRoute.Main
                    },
                    initialTag = editTarget
                )
        }
    }
}

@Composable
fun NfcCommandsScreen(
    plugin: NfcCommandsPlugin,
    nfcTagStore: NfcTagStore,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit,
    onSettings: (() -> Unit)?,
    initialTab: Int,
    onBuild: () -> Unit,
    onEdit: (NfcCreatedTag) -> Unit,
    onTabChanged: (Int) -> Unit,
) {
    val tabTitles = listOf(
        stringResource(R.string.nfccommands_tab_log),
        stringResource(R.string.nfccommands),
    )

    val pagerState = rememberPagerState(initialPage = initialTab) { tabTitles.size }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        onTabChanged(pagerState.currentPage)
    }

    val title = stringResource(R.string.nfccommands)
    val backDesc = stringResource(CoreUiR.string.back)

    LaunchedEffect(Unit) {
        setToolbarConfig(
            ToolbarConfig(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDesc)
                    }
                },
                actions = {
                    if (onSettings != null) {
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(CoreUiR.string.settings))
                        }
                    }
                },
            ),
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> NfcLogScreen(nfcTagStore = nfcTagStore)
                    1 -> NfcTagsScreen(plugin = plugin, nfcTagStore = nfcTagStore, onBuild = onBuild, onEdit = onEdit)
                }
            }
        }
    }
}

@Composable
private fun NfcLogScreen(nfcTagStore: NfcTagStore) {
    var entries by remember { mutableStateOf<List<NfcLogEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        nfcTagStore.logUpdates.collect {
            entries = nfcTagStore.loadLog()
        }
    }

    LaunchedEffect(Unit) {
        entries = nfcTagStore.loadLog()
    }

    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.nfccommands_log_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = stringResource(R.string.nfccommands_log_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(entries) { entry ->
                NfcLogEntryCard(entry)
            }
        }
    }
}

@Composable
private fun NfcLogEntryCard(entry: NfcLogEntry) {
    val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val color = if (entry.success) Color(0xFF4CAF50) else Color(0xFFF44336)
    val actionLabel = when (entry.action) {
        "READ" -> stringResource(R.string.nfccommands_log_action_read)
        "WRITE" -> stringResource(R.string.nfccommands_log_action_write)
        "MANUAL" -> stringResource(R.string.nfccommands_log_action_manual)
        else -> entry.action
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = entry.tagName, style = MaterialTheme.typography.titleSmall)
                Text(text = dateFormatter.format(entry.timestamp), style = MaterialTheme.typography.bodySmall)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Surface(
                    color = color.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text = actionLabel,
                        color = color,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = entry.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NfcTagsScreen(
    plugin: NfcCommandsPlugin,
    nfcTagStore: NfcTagStore,
    onBuild: () -> Unit,
    onEdit: (NfcCreatedTag) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    var tags by remember { mutableStateOf<List<NfcCreatedTag>>(emptyList()) }
    var deleteTarget by remember { mutableStateOf<NfcCreatedTag?>(null) }
    var executeTarget by remember { mutableStateOf<NfcCreatedTag?>(null) }

    LaunchedEffect(refreshKey) {
        tags = nfcTagStore.loadCreatedTags()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    deleteTarget?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.nfccommands_delete_confirm_title)) },
            text = { Text(stringResource(R.string.nfccommands_delete_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    nfcTagStore.deleteCreatedTag(tag.tagUid)
                    refreshKey++
                    deleteTarget = null
                }) { Text(stringResource(CoreUiR.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    executeTarget?.let { tag ->
        NfcExecutionConfirmationDialog(
            tag = tag,
            plugin = plugin,
            onConfirm = {
                val commands = tag.commands
                val tagName = tag.name
                executeTarget = null
                coroutineScope.launch {
                    withContext(Dispatchers.IO) { plugin.executeWithFeedback(commands, tagName, action = "MANUAL") }
                    refreshKey++
                }
            },
            onDismiss = { executeTarget = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (tags.isEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.nfccommands_empty_state_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = stringResource(R.string.nfccommands_empty_state_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(bottom = 72.dp)) {
                items(tags, key = { it.tagUid + it.createdAtMillis }) { tag ->
                    NfcTagCard(
                        plugin = plugin,
                        tag = tag,
                        onExecute = { executeTarget = tag },
                        onEdit = { onEdit(tag) },
                        onDelete = { deleteTarget = tag },
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = onBuild,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.nfccommands_add_tag),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NfcTagCard(
    plugin: NfcCommandsPlugin,
    tag: NfcCreatedTag,
    onExecute: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateFormatter = remember { DateFormat.getDateInstance(DateFormat.SHORT) }

    Card(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (tag.lastScannedAtMillis != null) {
                    Text(
                        text = dateFormatter.format(tag.lastScannedAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tag.commands.forEach { cmdJson ->
                        NfcIconOnlyDisplay(commandJson = cmdJson)
                    }
                }
                IconButton(onClick = onExecute, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.nfccommands_execute_tag))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.nfccommands_disable_tag))
                }
            }
        }
    }
}

@Composable
private fun NfcIconOnlyDisplay(commandJson: String) {
    val json = remember(commandJson) { runCatching { JSONObject(commandJson) }.getOrNull() }
    val codeName = json?.optString("code")
    val code = remember(codeName) { if (codeName != null) runCatching { NfcCommandCode.valueOf(codeName) }.getOrNull() else null }

    if (code != null) {
        Icon(
            imageVector = code.icon,
            contentDescription = null,
            tint = code.getIconColor(),
            modifier = Modifier.size(20.dp)
        )
    }
}
