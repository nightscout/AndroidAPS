package app.aaps.plugins.eversense.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.source.CgmAttention
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.PluginTab
import app.aaps.core.ui.compose.PluginTabsContent
import app.aaps.core.ui.compose.ScreenAction
import app.aaps.core.ui.compose.SelectableListToolbar
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.icons.IcPluginEversense
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.navigation.LocalPluginNavigationRequest
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.pump.PumpOverviewScreen
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.plugins.eversense.EversenseStrings
import app.aaps.plugins.eversense.pairing.EversensePairingViewModel
import app.aaps.plugins.eversense.pairing.EversensePairingWizard
import app.aaps.plugins.eversense.placement.EversensePlacementScreen
import app.aaps.plugins.eversense.placement.EversensePlacementViewModel
import app.aaps.plugins.source.compose.BgSourceScreen
import app.aaps.plugins.source.compose.BgSourceViewModel
import app.aaps.core.ui.compose.stringResource

/**
 * The Eversense screen, opened from Manage (`ElementType.CGM`) as a pump's screen is, and from the bottom bar's "!"
 * when it needs the user: the pairing and the transmitter's status on the first tab, the BG list on the second. The
 * pairing wizard and the placement guide take the whole screen while they are open.
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
        var placementOpen by remember { mutableStateOf(false) }
        // start() before the wizard is shown, so it never sees the "finished" of the last run
        val openWizard = { signInOnly: Boolean ->
            pairingViewModel.start(signInOnly)
            wizardOpen = true
        }

        if (wizardOpen) {
            EversensePairingWizard(viewModel = pairingViewModel, setToolbarConfig = setToolbarConfig, onClose = { wizardOpen = false })
            return
        }
        if (placementOpen) {
            EversensePlacementScreen(
                viewModel = metroViewModel<EversensePlacementViewModel>(),
                setToolbarConfig = setToolbarConfig,
                onClose = { placementOpen = false }
            )
            return
        }

        PluginTabsContent(
            tabs = listOf(
                PluginTab(stringResource(EversenseStrings.eversense_status_title)) {
                    EversenseStatusTab(
                        viewModel = metroViewModel(),
                        title = title,
                        setToolbarConfig = setToolbarConfig,
                        onNavigateBack = onNavigateBack,
                        onSettings = onSettings,
                        onPair = { openWizard(false) },
                        onSignInAgain = { openWizard(true) },
                        onPlacement = { placementOpen = true }
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
    onSignInAgain: () -> Unit,
    onPlacement: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigate = LocalPluginNavigationRequest.current

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
        onPair = onPair,
        onSignInAgain = onSignInAgain,
        onUnpair = viewModel::unpair,
        onSync = viewModel::onSyncClick,
        // The AAPS calibration dialog: the master hands the value to EversensePlugin.calibrate()
        onCalibrate = { navigate(NavigationRequest.Element(ElementType.CALIBRATION)) },
        onPlacement = onPlacement
    )
}

/**
 * The status tab without its view model, so that a test can show it. The pumps' own layout: the banner, the value
 * rows, the picture, and the buttons at the bottom. The pairing (pair, sign in, unpair) is in the buttons, as a pump's
 * "Pair" and "Unpair" are.
 */
@Composable
internal fun EversenseStatusContent(
    state: EversenseStatusViewModel.UiState,
    onPair: () -> Unit,
    onSignInAgain: () -> Unit,
    onUnpair: () -> Unit,
    onSync: () -> Unit,
    onCalibrate: () -> Unit,
    onPlacement: () -> Unit
) {
    var confirmUnpair by remember { mutableStateOf(false) }
    val paired = state.attention != CgmAttention.NOT_PAIRED
    val unpair = ScreenAction(stringResource(EversenseStrings.eversense_unpair), onClick = { confirmUnpair = true })
    val primaryActions =
        if (paired) listOf(
            // The link follows the plugin (no Connect or Disconnect); Sync waits for it
            ScreenAction(stringResource(EversenseStrings.eversense_information_last_sync), enabled = state.connected, onClick = onSync),
            // The AAPS calibration dialog
            ScreenAction(stringResource(EversenseStrings.eversense_calibration_action), onClick = onCalibrate),
            ScreenAction(stringResource(EversenseStrings.eversense_placement_title), onClick = onPlacement)
        ) else emptyList()
    val managementActions = when (state.attention) {
        CgmAttention.NOT_PAIRED     -> listOf(ScreenAction(stringResource(EversenseStrings.eversense_pair_title), onClick = onPair))
        CgmAttention.SIGN_IN_FAILED -> listOf(ScreenAction(stringResource(EversenseStrings.eversense_sign_in_again), onClick = onSignInAgain), unpair)
        // An E3 used without a login can still sign in
        null                        -> listOfNotNull(
            if (!state.signedIn) ScreenAction(stringResource(EversenseStrings.eversense_sign_in), onClick = onSignInAgain) else null,
            unpair
        )
    }

    PumpOverviewScreen(
        state = PumpOverviewUiState(
            statusBanner = state.banner,
            infoRows = state.rows,
            primaryActions = primaryActions,
            managementActions = managementActions
        ),
        customContent = {
            // The plugin's own icon: a single colour, so it takes the theme's colour in light and dark mode
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = IcPluginEversense,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth(PICTURE_WIDTH)
                        .aspectRatio(1f)
                )
            }
        }
    )

    if (confirmUnpair) {
        OkCancelDialog(
            title = stringResource(EversenseStrings.eversense_unpair),
            message = stringResource(EversenseStrings.eversense_unpair_confirm, state.transmitter.orEmpty()),
            onConfirm = {
                confirmUnpair = false
                onUnpair()
            },
            onDismiss = { confirmUnpair = false }
        )
    }
}

/** The part of the width the picture takes */
private const val PICTURE_WIDTH = 0.4f

