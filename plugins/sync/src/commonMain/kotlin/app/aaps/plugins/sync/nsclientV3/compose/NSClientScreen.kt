package app.aaps.plugins.sync.nsclientV3.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ScreenAction
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.log.LabelValueRow
import app.aaps.plugins.sync.log.SyncLogLayout
import app.aaps.plugins.sync.log.clearLogAction
import app.aaps.plugins.sync.log.fullSyncAction

@Composable
fun NSClientScreen(
    viewModel: NSClientViewModel,
    dateUtil: DateUtil,
    title: String,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit,
    onPauseChanged: (Boolean) -> Unit,
    onClearLog: () -> Unit,
    onSendNow: () -> Unit,
    onFullSync: () -> Unit,
    onSettings: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        setToolbarConfig(
            ToolbarConfig(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreUiStrings.back)
                        )
                    }
                },
                actions = {
                    if (onSettings != null) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(CoreUiStrings.nav_plugin_preferences)
                            )
                        }
                    }
                }
            )
        )
    }

    NSClientScreenContent(
        uiState = uiState,
        dateUtil = dateUtil,
        onPauseChanged = onPauseChanged,
        onSendNow = onSendNow,
        onFullSync = onFullSync,
        onClearLog = onClearLog,
        modifier = modifier
    )
}

/**
 * @see NSClientScreenPreview
 */
@Composable
fun NSClientScreenContent(
    uiState: NSClientUiState,
    dateUtil: DateUtil? = null,
    onPauseChanged: (Boolean) -> Unit = {},
    onSendNow: () -> Unit = {},
    onFullSync: () -> Unit = {},
    onClearLog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    SyncLogLayout(
        logList = uiState.logList,
        dateUtil = dateUtil,
        actions = listOf(
            ScreenAction(label = stringResource(SyncStrings.deliver_now), icon = Icons.AutoMirrored.Filled.Send, onClick = onSendNow),
            fullSyncAction(onFullSync),
            clearLogAction(onClearLog)
        ),
        modifier = modifier
    ) {
        // URL row - only URL text is clickable
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(SyncStrings.ns_client_url),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            ClickableUrlText(url = uiState.url)
        }

        // Status and Queue with Pause button on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
            ) {
                LabelValueRow(label = stringResource(SyncStrings.status_label), value = uiState.status)
                LabelValueRow(label = stringResource(SyncStrings.queue), value = uiState.queue)
            }

            // One switch for a screen reader ("Running, switch, on"): the Switch alone had no name.
            Row(
                modifier = Modifier
                    .padding(start = AapsSpacing.extraLarge)
                    .align(Alignment.CenterVertically)
                    .toggleable(value = !uiState.paused, role = Role.Switch, onValueChange = { isRunning -> onPauseChanged(!isRunning) }),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(if (uiState.paused) CoreUiStrings.paused else CoreUiStrings.running),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(checked = !uiState.paused, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun ClickableUrlText(
    url: String,
    modifier: Modifier = Modifier
) {
    if (url.isNotEmpty() && url.startsWith("http", ignoreCase = true)) {

        Text(
            buildAnnotatedString {
                withLink(LinkAnnotation.Url(url = url)) { append(url) }
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
        )
    } else {
        Text(
            text = url,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier
        )
    }
}
