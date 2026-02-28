package app.aaps.plugins.sync.tidepool.compose

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.plugins.sync.R
import java.text.SimpleDateFormat
import java.util.Locale

private val timeFormatPreview = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun TidepoolScreen(
    viewModel: TidepoolViewModel,
    dateUtil: DateUtil,
    rh: ResourceHelper,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit,
    onSettings: (() -> Unit)?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onUploadNow: () -> Unit,
    onFullSync: () -> Unit,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Set up toolbar
    LaunchedEffect(Unit) {
        setToolbarConfig(
            ToolbarConfig(
                title = rh.gs(R.string.tidepool),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = rh.gs(app.aaps.core.ui.R.string.back)
                        )
                    }
                },
                actions = {
                    if (onSettings != null) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = rh.gs(app.aaps.core.ui.R.string.nav_plugin_preferences)
                            )
                        }
                    }
                    TidepoolMenu(
                        rh = rh,
                        onLogin = onLogin,
                        onLogout = onLogout,
                        onUploadNow = onUploadNow,
                        onFullSync = onFullSync,
                        onClearLog = onClearLog
                    )
                }
            )
        )
    }

    TidepoolScreenContent(
        uiState = uiState,
        dateUtil = dateUtil,
        modifier = modifier
    )
}

@Composable
fun TidepoolScreenContent(
    uiState: TidepoolUiState,
    dateUtil: DateUtil? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Status row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.status),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = uiState.connectionStatus,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        HorizontalDivider()

        // Logs
        val listState = rememberLazyListState()

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
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(
                items = uiState.logList,
                key = { it.id }
            ) { log ->
                Text(
                    text = buildAnnotatedString {
                        append(dateUtil?.timeStringWithSeconds(log.date) ?: timeFormatPreview.format(log.date))
                        append(" ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(log.status)
                        }
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

@Preview(showBackground = true)
@Composable
private fun TidepoolScreenPreview() {
    MaterialTheme {
        TidepoolScreenContent(
            uiState = TidepoolUiState(
                connectionStatus = "SESSION_ESTABLISHED",
                logList = listOf(
                    TidepoolLog(status = "Starting upload"),
                    TidepoolLog(status = "Uploading 24 records"),
                    TidepoolLog(status = "Upload successful"),
                    TidepoolLog(status = "Session token refreshed"),
                )
            )
        )
    }
}

@Composable
private fun TidepoolMenu(
    rh: ResourceHelper,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onUploadNow: () -> Unit,
    onFullSync: () -> Unit,
    onClearLog: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = rh.gs(app.aaps.core.ui.R.string.more_options)
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(rh.gs(app.aaps.core.ui.R.string.login)) },
                onClick = {
                    showMenu = false
                    onLogin()
                }
            )
            DropdownMenuItem(
                text = { Text(rh.gs(app.aaps.core.ui.R.string.logout)) },
                onClick = {
                    showMenu = false
                    onLogout()
                }
            )
            DropdownMenuItem(
                text = { Text(rh.gs(R.string.upload_now)) },
                onClick = {
                    showMenu = false
                    onUploadNow()
                }
            )
            DropdownMenuItem(
                text = { Text(rh.gs(R.string.full_sync)) },
                onClick = {
                    showMenu = false
                    onFullSync()
                }
            )
            DropdownMenuItem(
                text = { Text(rh.gs(R.string.clear_log)) },
                onClick = {
                    showMenu = false
                    onClearLog()
                }
            )
        }
    }
}
