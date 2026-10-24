package app.aaps.plugins.sync.xdrip.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.log.LabelValueRow
import app.aaps.plugins.sync.log.SyncLogLayout
import app.aaps.plugins.sync.log.clearLogAction
import app.aaps.plugins.sync.log.fullSyncAction

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

    val title = stringResource(SyncStrings.xdrip)

    // Set up toolbar
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

    XdripScreenContent(
        uiState = uiState,
        dateUtil = dateUtil,
        onFullSync = onFullSync,
        onClearLog = onClearLog,
        modifier = modifier
    )
}

/**
 * @see XdripScreenPreview
 * @see XdripScreenEmptyPreview
 */
@Composable
internal fun XdripScreenContent(
    uiState: XdripUiState,
    dateUtil: DateUtil? = null,
    onFullSync: () -> Unit = {},
    onClearLog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    SyncLogLayout(
        logList = uiState.logList,
        dateUtil = dateUtil,
        actions = listOf(fullSyncAction(onFullSync), clearLogAction(onClearLog)),
        modifier = modifier
    ) {
        LabelValueRow(label = stringResource(SyncStrings.queue), value = uiState.queue)
    }
}
