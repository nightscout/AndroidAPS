@file:OptIn(ExperimentalFoundationApi::class)

package app.aaps.plugins.main.general.nfcCommands

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.plugins.main.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat

private sealed class NfcRoute {
    object Main : NfcRoute()
    object Build : NfcRoute()
}

class NfcCommandsComposeContent(private val plugin: NfcCommandsPlugin) : ComposablePluginContent {
    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        var route by remember { mutableStateOf<NfcRoute>(NfcRoute.Main) }
        when (route) {
            is NfcRoute.Main  -> NfcCommandsScreen(
                plugin = plugin,
                setToolbarConfig = setToolbarConfig,
                onNavigateBack = onNavigateBack,
                onSettings = onSettings,
                onBuild = { route = NfcRoute.Build },
            )
            is NfcRoute.Build -> NfcBuildScreen(
                plugin = plugin,
                setToolbarConfig = setToolbarConfig,
                onBack = { route = NfcRoute.Main },
            )
        }
    }
}

@Composable
private fun NfcCommandsScreen(
    plugin: NfcCommandsPlugin,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit,
    onSettings: (() -> Unit)?,
    onBuild: () -> Unit,
) {
    val tabTitles = remember { listOf(R.string.nfccommands_tab_log, R.string.nfccommands_tab_my_tags) }
    val pagerState = rememberPagerState { tabTitles.size }
    val coroutineScope = rememberCoroutineScope()

    val title = stringResource(R.string.nfccommands)
    val backDesc = stringResource(app.aaps.core.ui.R.string.back)
    val settingsDesc = stringResource(app.aaps.core.ui.R.string.nav_plugin_preferences)
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
                            Icon(Icons.Filled.Settings, contentDescription = settingsDesc)
                        }
                    }
                },
            )
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            tabTitles.forEachIndexed { index, titleResId ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(stringResource(titleResId)) },
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> NfcLogScreen()
                else -> NfcTagsScreen(plugin = plugin, onBuild = onBuild)
            }
        }
    }
}

@Composable
private fun NfcLogScreen() {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<NfcLogEntry>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        entries = NfcTagStore.loadLog(context)
    }

    DisposableEffect(context) {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == NfcTagStore.PREFS_LOG) refreshKey++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            items(entries) { entry ->
                NfcLogEntryCard(entry)
            }
        }
    }
}

@Composable
private fun NfcLogEntryCard(entry: NfcLogEntry) {
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val actionLabel = when (entry.action) {
        "WRITE" -> stringResource(R.string.nfccommands_log_action_write)
        "READ" -> stringResource(R.string.nfccommands_log_action_read)
        "MANUAL" -> stringResource(R.string.nfccommands_log_action_manual)
        else -> null
    }

    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 1.dp)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (actionLabel != null) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(actionLabel) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Text(
                    text = entry.tagName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatter.format(entry.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun NfcTagsScreen(plugin: NfcCommandsPlugin, onBuild: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    var tags by remember { mutableStateOf<List<NfcCreatedTag>>(emptyList()) }
    var deleteTarget by remember { mutableStateOf<NfcCreatedTag?>(null) }
    var renameTarget by remember { mutableStateOf<NfcCreatedTag?>(null) }
    var renameText by remember { mutableStateOf("") }
    var executeTarget by remember { mutableStateOf<NfcCreatedTag?>(null) }

    LaunchedEffect(refreshKey) {
        tags = NfcTagStore.loadCreatedTags(context)
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
                    NfcTagStore.deleteCreatedTag(context, tag.tagUid)
                    refreshKey++
                    deleteTarget = null
                }) { Text(stringResource(R.string.nfccommands_delete_confirm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    renameTarget?.let { tag ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.nfccommands_rename_tag_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.nfccommands_rename_tag)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = renameText.trim()
                    if (newName.isNotBlank() && newName != tag.name) {
                        NfcTagStore.saveCreatedTag(context, tag.copy(name = newName))
                        refreshKey++
                    }
                    renameTarget = null
                }) { Text(stringResource(R.string.nfccommands_rename_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    executeTarget?.let { tag ->
        val commandsText = tag.commands.mapIndexed { i, cmd ->
            context.getString(R.string.nfccommands_cascade_step_label, i + 1, cmd)
        }.joinToString("\n")
        AlertDialog(
            onDismissRequest = { executeTarget = null },
            title = { Text(stringResource(R.string.nfccommands_execute_confirm_title, tag.name)) },
            text = { Text(stringResource(R.string.nfccommands_execute_confirm_msg, commandsText)) },
            confirmButton = {
                TextButton(onClick = {
                    val commands = tag.commands
                    val tagName = tag.name
                    executeTarget = null
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) { plugin.executeWithFeedback(commands, tagName, action = "MANUAL") }
                        refreshKey++
                    }
                }) { Text(stringResource(R.string.nfccommands_execute_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { executeTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
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
            LazyColumn(modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp)) {
                items(tags, key = { it.tagUid }) { tag ->
                    NfcTagCard(
                        tag = tag,
                        onExecute = { executeTarget = tag },
                        onRename = { renameTarget = tag; renameText = tag.name },
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

@Composable
private fun NfcTagCard(
    tag: NfcCreatedTag,
    onExecute: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val dateFormatter = remember { DateFormat.getDateInstance(DateFormat.SHORT) }

    val commandsText = tag.commands.mapIndexed { i, cmd ->
        context.getString(R.string.nfccommands_cascade_step_label, i + 1, cmd)
    }.joinToString("\n")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = tag.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = commandsText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                IconButton(onClick = onExecute) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.nfccommands_execute_tag))
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.nfccommands_rename_tag))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.nfccommands_disable_tag))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SuggestionChip(onClick = {}, label = { Text(tag.tagUid) })
                if (tag.lastScannedAtMillis != null) {
                    Text(
                        text = dateFormatter.format(tag.lastScannedAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
