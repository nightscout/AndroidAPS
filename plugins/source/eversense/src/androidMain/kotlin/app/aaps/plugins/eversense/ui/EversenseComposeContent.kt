package app.aaps.plugins.eversense.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.PluginTab
import app.aaps.core.ui.compose.PluginTabsContent
import app.aaps.core.ui.compose.SelectableListToolbar
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.navigation.LocalPluginNavigationRequest
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.plugins.eversense.R
import app.aaps.plugins.eversense.placement.EversensePlacementActivity
import app.aaps.plugins.source.compose.BgSourceScreen
import app.aaps.plugins.source.compose.BgSourceViewModel
import app.aaps.core.ui.compose.stringResource

/**
 * The Eversense screen, opened from Manage (`ElementType.CGM`) as a pump's screen is: the transmitter's status and
 * controls on the first tab, the BG list on the second.
 */
internal class EversenseComposeContent(private val title: String) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        PluginTabsContent(
            tabs = listOf(
                PluginTab(stringResource(R.string.eversense_status_title)) {
                    EversenseStatusTab(
                        viewModel = metroViewModel(),
                        title = title,
                        setToolbarConfig = setToolbarConfig,
                        onNavigateBack = onNavigateBack,
                        onSettings = onSettings
                    )
                },
                PluginTab(stringResource(CoreUiStrings.glucose)) {
                    BgSourceScreen(
                        viewModel = metroViewModel<BgSourceViewModel>(),
                        title = title,
                        setToolbarConfig = setToolbarConfig,
                        onNavigateBack = onNavigateBack,
                        onSettings = onSettings
                    )
                }
            )
        )
    }
}

@Composable
private fun EversenseStatusTab(
    viewModel: EversenseStatusViewModel,
    title: String,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit,
    onSettings: (() -> Unit)?
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigate = LocalPluginNavigationRequest.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        setToolbarConfig(
            SelectableListToolbar(
                isRemovingMode = false,
                selectedCount = 0,
                onExitRemovingMode = {},
                onNavigateBack = onNavigateBack,
                onDelete = {},
                rh = viewModel.rh,
                title = title,
                onSettings = onSettings
            )
        )
    }

    EversenseStatusContent(
        state = state,
        onConnect = viewModel::onConnectClick,
        onChangeTransmitter = viewModel::onChangeTransmitterClick,
        onDays = viewModel::setSyncDays,
        onSync = viewModel::onSyncClick,
        // The AAPS calibration dialog: the master hands the value to EversensePlugin.calibrate()
        onCalibrate = { navigate(NavigationRequest.Element(ElementType.CALIBRATION)) },
        onPlacement = { context.startActivity(Intent(context, EversensePlacementActivity::class.java)) },
        onDocumentation = { uriHandler.openUri(DOCUMENTATION_URL) }
    )

    TransmitterDialog(state = state, viewModel = viewModel)
}

/** The status tab without its view model, so that a test can show it. */
@Composable
internal fun EversenseStatusContent(
    state: EversenseStatusViewModel.UiState,
    onConnect: () -> Unit,
    onChangeTransmitter: () -> Unit,
    onDays: (Int) -> Unit,
    onSync: () -> Unit,
    onCalibrate: () -> Unit,
    onPlacement: () -> Unit,
    onDocumentation: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        AapsCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(AapsSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
                state.lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
        }
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.connected) R.string.eversense_status_disconnect else R.string.eversense_status_connect))
        }
        OutlinedButton(onClick = onChangeTransmitter, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.eversense_change_transmitter))
        }
        SyncDaysRow(syncDays = state.syncDays, onDays = onDays)
        Button(onClick = onSync, modifier = Modifier.fillMaxWidth(), enabled = state.connected) {
            Text(stringResource(R.string.eversense_information_last_sync))
        }

        HorizontalDivider(Modifier.padding(vertical = AapsSpacing.medium))

        OutlinedButton(onClick = onCalibrate, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.eversense_calibration_action))
        }
        OutlinedButton(onClick = onPlacement, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.eversense_placement_title))
        }
        OutlinedButton(onClick = onDocumentation, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.eversense_documentation))
        }
    }
}

@Composable
private fun SyncDaysRow(syncDays: Int, onDays: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.eversense_default_sync_days_label), Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(syncDays.toString()) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                for (days in SYNC_DAY_CHOICES) {
                    DropdownMenuItem(text = { Text(days.toString()) }, onClick = { expanded = false; onDays(days) })
                }
            }
        }
    }
}

/** Disconnect and change-transmitter confirmations, and the scan with its results. */
@Composable
private fun TransmitterDialog(state: EversenseStatusViewModel.UiState, viewModel: EversenseStatusViewModel) {
    val dialog = state.dialog
    if (dialog == EversenseStatusViewModel.Dialog.NONE) return
    val message = when (dialog) {
        EversenseStatusViewModel.Dialog.DISCONNECT -> R.string.eversense_disconnect_confirm
        EversenseStatusViewModel.Dialog.CHANGE     -> R.string.eversense_change_transmitter_confirm
        EversenseStatusViewModel.Dialog.SCANNING   -> R.string.eversense_scan_in_progress
        else                                       -> R.string.eversense_no_transmitters_found
    }
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = {
            Text(stringResource(if (dialog == EversenseStatusViewModel.Dialog.CHANGE) R.string.eversense_change_transmitter else R.string.eversense_scan_title))
        },
        text = {
            if (dialog == EversenseStatusViewModel.Dialog.RESULTS && state.found.isNotEmpty()) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    state.found.forEach { item -> TextButton(onClick = { viewModel.onDeviceClick(item) }) { Text(item.name) } }
                }
            } else Text(stringResource(message))
        },
        confirmButton = {
            when (dialog) {
                EversenseStatusViewModel.Dialog.DISCONNECT -> TextButton(onClick = viewModel::confirmDisconnect) {
                    Text(stringResource(R.string.eversense_status_disconnect))
                }

                EversenseStatusViewModel.Dialog.CHANGE     -> TextButton(onClick = viewModel::confirmChangeTransmitter) {
                    Text(stringResource(R.string.eversense_change_transmitter))
                }

                EversenseStatusViewModel.Dialog.RESULTS    -> if (state.found.isEmpty()) TextButton(onClick = viewModel::dismissDialog) {
                    Text(stringResource(CoreUiStrings.ok))
                }

                else                                       -> Unit
            }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.eversense_scan_cancel)) } }
    )
}

private val SYNC_DAY_CHOICES = listOf(1, 3, 7, 14, 30)

/** The user guide in the repository; the AAPS documentation has no Eversense page yet. */
private const val DOCUMENTATION_URL = "https://github.com/nightscout/AndroidAPS/blob/dev/plugins/source/eversense/README.md"
