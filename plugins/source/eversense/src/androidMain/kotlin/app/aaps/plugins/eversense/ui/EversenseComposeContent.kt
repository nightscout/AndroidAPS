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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.source.CgmAttention
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
import app.aaps.plugins.eversense.pairing.EversenseAccount
import app.aaps.plugins.eversense.pairing.EversensePairingCard
import app.aaps.plugins.eversense.pairing.EversensePairingViewModel
import app.aaps.plugins.eversense.pairing.EversensePairingWizard
import app.aaps.plugins.eversense.placement.EversensePlacementActivity
import app.aaps.plugins.source.compose.BgSourceScreen
import app.aaps.plugins.source.compose.BgSourceViewModel
import app.aaps.core.ui.compose.stringResource

/**
 * The Eversense screen, opened from Manage (`ElementType.CGM`) as a pump's screen is, and from the bottom bar's "!"
 * when it needs the user: the pairing and the transmitter's status on the first tab, the BG list on the second. The
 * pairing wizard takes the whole screen while it runs.
 */
internal class EversenseComposeContent(private val title: String) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val pairingViewModel = metroViewModel<EversensePairingViewModel>()
        var wizardOpen by remember { mutableStateOf(false) }
        // start() before the wizard is shown, so it never sees the "finished" of the last run
        val openWizard = { signInOnly: Boolean ->
            pairingViewModel.start(signInOnly)
            wizardOpen = true
        }

        if (wizardOpen) {
            EversensePairingWizard(viewModel = pairingViewModel, setToolbarConfig = setToolbarConfig, onClose = { wizardOpen = false })
            return
        }

        PluginTabsContent(
            tabs = listOf(
                PluginTab(stringResource(R.string.eversense_status_title)) {
                    EversenseStatusTab(
                        viewModel = metroViewModel(),
                        title = title,
                        setToolbarConfig = setToolbarConfig,
                        onNavigateBack = onNavigateBack,
                        onSettings = onSettings,
                        onPair = { openWizard(false) },
                        onSignInAgain = { openWizard(true) }
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
    onSettings: (() -> Unit)?,
    onPair: () -> Unit,
    onSignInAgain: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val attention by viewModel.attention.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val transmitter by viewModel.transmitter.collectAsStateWithLifecycle()
    val navigate = LocalPluginNavigationRequest.current
    val context = LocalContext.current

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
        attention = attention,
        account = account,
        transmitter = transmitter,
        onPair = onPair,
        onSignInAgain = onSignInAgain,
        onUnpair = viewModel::unpair,
        onDays = viewModel::setSyncDays,
        onSync = viewModel::onSyncClick,
        // The AAPS calibration dialog: the master hands the value to EversensePlugin.calibrate()
        onCalibrate = { navigate(NavigationRequest.Element(ElementType.CALIBRATION)) },
        onPlacement = { context.startActivity(Intent(context, EversensePlacementActivity::class.java)) }
    )
}

/** The status tab without its view model, so that a test can show it. */
@Composable
internal fun EversenseStatusContent(
    state: EversenseStatusViewModel.UiState,
    attention: CgmAttention?,
    account: EversenseAccount?,
    transmitter: String?,
    onPair: () -> Unit,
    onSignInAgain: () -> Unit,
    onUnpair: () -> Unit,
    onDays: (Int) -> Unit,
    onSync: () -> Unit,
    onCalibrate: () -> Unit,
    onPlacement: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        EversensePairingCard(
            attention = attention,
            account = account,
            transmitter = transmitter,
            onPair = onPair,
            onSignInAgain = onSignInAgain,
            onUnpair = onUnpair
        )
        // What the transmitter reports means something only once one is paired
        if (attention != CgmAttention.NOT_PAIRED) {
            AapsCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(AapsSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
                    state.lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                }
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

private val SYNC_DAY_CHOICES = listOf(1, 3, 7, 14, 30)
