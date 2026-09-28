package app.aaps.pump.omnipod.dash.ui.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.ui.compose.OmnipodComposeHost
import app.aaps.pump.omnipod.dash.ui.wizard.compose.DashOmnipodWizardViewModel
import app.aaps.core.ui.R as CoreUiR

class OmnipodDashComposeContent(
    private val pluginName: String,
    private val protectionCheck: ProtectionCheck,
    private val blePreCheck: BlePreCheck
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val overviewViewModel: DashOverviewViewModel = metroViewModel()
        OmnipodComposeHost(
            pluginName = pluginName,
            blePreCheck = blePreCheck,
            setToolbarConfig = setToolbarConfig,
            onNavigateBack = onNavigateBack,
            onSettings = onSettings,
            overviewState = overviewViewModel.uiState,
            overviewEvents = overviewViewModel.events,
            wizardViewModel = {
                val viewModel: DashOmnipodWizardViewModel = metroViewModel()
                viewModel
            },
            onConfirmDiscardPod = overviewViewModel::confirmDiscardPod,
            showExtraContentForHistory = true,
            extraContent = { onBack ->
                val historyViewModel: DashPodHistoryViewModel = metroViewModel()
                val records by historyViewModel.records.collectAsStateWithLifecycle()
                val title = stringResource(R.string.omnipod_common_pod_management_button_pod_history)
                LaunchedEffect(title) {
                    setToolbarConfig(
                        ToolbarConfig(
                            title = title,
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiR.string.back))
                                }
                            },
                            actions = {}
                        )
                    )
                }
                DashPodHistoryScreen(
                    records = records,
                    rh = historyViewModel.rh,
                    profileUtil = historyViewModel.profileUtil
                )
            }
        )
    }
}
