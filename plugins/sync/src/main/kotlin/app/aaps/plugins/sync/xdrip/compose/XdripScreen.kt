package app.aaps.plugins.sync.xdrip.compose

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.plugins.sync.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatPreview = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
internal fun XdripScreen(
    viewModel: XdripViewModel,
    dateUtil: DateUtil,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit,
    onSettings: (() -> Unit)?,
    onClearLog: () -> Unit,
    onFullSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val title = stringResource(R.string.xdrip)

    // Set up toolbar
    LaunchedEffect(Unit) {
        setToolbarConfig(
            ToolbarConfig(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                        )
                    }
                },
                actions = {
                    if (onSettings != null) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(app.aaps.core.ui.R.string.nav_plugin_preferences)
                            )
                        }
                    }
                    XdripMenu(
                        onClearLog = onClearLog,
                        onFullSync = onFullSync
                    )
                }
            )
        )
    }

    XdripScreenContent(
        uiState = uiState,
        dateUtil = dateUtil,
        modifier = modifier
    )
}

@Composable
private fun XdripScreenContent(
    uiState: XdripUiState,
    dateUtil: DateUtil? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AapsSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
    ) {
        // Queue row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            Text(
                text = stringResource(R.string.queue),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = uiState.queue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        HorizontalDivider()

        // Logs
        val listState = rememberLazyListState()

        // Auto-scroll to top when new log arrives
        LaunchedEffect(uiState.logList.firstOrNull()?.date) {
            if (uiState.logList.isNotEmpty()) {
                listState.scrollToItem(0)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)
        ) {
            items(
                items = uiState.logList,
                key = { it.id }
            ) { log ->
                Text(
                    text = buildAnnotatedString {
                        append(dateUtil?.timeStringWithSeconds(log.date) ?: timeFormatPreview.format(Instant.ofEpochMilli(log.date)))
                        append(" ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(log.action)
                        }
                        append(" ")
                        append(log.logText ?: "")
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun XdripMenu(
    onClearLog: () -> Unit,
    onFullSync: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(app.aaps.core.ui.R.string.more_options)
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.clear_log)) },
                onClick = {
                    showMenu = false
                    onClearLog()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.full_sync)) },
                onClick = {
                    showMenu = false
                    onFullSync()
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun XdripScreenPreview() {
    MaterialTheme {
        XdripScreenContent(
            uiState = XdripUiState(
                queue = "3",
                logList = listOf(
                    XdripLog(action = "BG", logText = "Sending glucose value 5.5"),
                    XdripLog(action = "TREATMENT", logText = "Sending bolus 1.5U"),
                    XdripLog(action = "STATUS", logText = "Loop running, IOB 2.3U"),
                )
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun XdripScreenEmptyPreview() {
    MaterialTheme {
        XdripScreenContent(
            uiState = XdripUiState(
                queue = "0",
                logList = emptyList()
            )
        )
    }
}
