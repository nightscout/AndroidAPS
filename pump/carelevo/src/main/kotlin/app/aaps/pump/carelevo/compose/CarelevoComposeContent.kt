package app.aaps.pump.carelevo.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.pump.BlePreCheckHost
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.common.CarelevoAlarmNotifier
import app.aaps.pump.carelevo.compose.alarm.CarelevoAlarmHost
import app.aaps.pump.carelevo.compose.patchflow.CarelevoPatchFlowScreen
import app.aaps.pump.carelevo.presentation.type.CarelevoScreenType
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoOverviewViewModel

class CarelevoComposeContent(
    private val aapsLogger: AAPSLogger,
    private val carelevoAlarmNotifier: CarelevoAlarmNotifier,
    private val protectionCheck: ProtectionCheck,
    private val blePreCheck: BlePreCheck,
    private val iconsProvider: IconsProvider
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val overviewViewModel: CarelevoOverviewViewModel = hiltViewModel()
        val overviewNavIcon: @Composable () -> Unit = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                )
            }
        }
        val settingsAction: @Composable RowScope.() -> Unit = {
            onSettings?.let { action ->
                IconButton(onClick = action) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = stringResource(app.aaps.core.ui.R.string.settings)
                    )
                }
            }
        }

        var manualWorkflowScreen by remember { mutableStateOf<CarelevoScreenType?>(null) }
        var latchedWorkflowScreen by remember { mutableStateOf<CarelevoScreenType?>(null) }
        val checkScreen by overviewViewModel.isCheckScreen.collectAsStateWithLifecycle()
        val activeWorkflowScreen = manualWorkflowScreen ?: latchedWorkflowScreen ?: checkScreen
        val pluginName = stringResource(R.string.carelevo)

        LaunchedEffect(checkScreen, manualWorkflowScreen) {
            if (manualWorkflowScreen == null) {
                when (checkScreen) {
                    CarelevoScreenType.SAFETY_CHECK,
                    CarelevoScreenType.NEEDLE_INSERTION -> latchedWorkflowScreen = checkScreen

                    null                                -> Unit
                    else                                -> latchedWorkflowScreen = null
                }
            }
        }

        LaunchedEffect(activeWorkflowScreen, pluginName) {
            if (activeWorkflowScreen == null) {
                setToolbarConfig(
                    ToolbarConfig(
                        title = pluginName,
                        navigationIcon = overviewNavIcon,
                        actions = settingsAction
                    )
                )
            }
        }

        val startWorkflow: (CarelevoScreenType) -> Unit = remember(protectionCheck) {
            { screenType ->
                if (screenType == CarelevoScreenType.CONNECTION_FLOW_START) {
                    protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                        if (result == ProtectionResult.GRANTED) {
                            manualWorkflowScreen = screenType
                            latchedWorkflowScreen = null
                        }
                    }
                } else {
                    manualWorkflowScreen = screenType
                    latchedWorkflowScreen = null
                }
            }
        }

        Box {
            when (activeWorkflowScreen) {
                null -> {
                    CarelevoOverviewScreen(
                        viewModel = overviewViewModel,
                        onStartWorkflow = startWorkflow
                    )
                }

                CarelevoScreenType.CONNECTION_FLOW_START -> {
                    BlePreCheckHost(
                        blePreCheck = blePreCheck,
                        onFailed = { manualWorkflowScreen = null }
                    )
                    CarelevoPatchFlowScreen(
                        screenType = activeWorkflowScreen,
                        setToolbarConfig = setToolbarConfig,
                        onExitFlow = {
                            manualWorkflowScreen = null
                            latchedWorkflowScreen = null
                        }
                    )
                }

                CarelevoScreenType.COMMUNICATION_CHECK -> {
                    CarelevoCommunicationCheckScreen(
                        setToolbarConfig = setToolbarConfig,
                        onExit = {
                            manualWorkflowScreen = null
                            latchedWorkflowScreen = null
                        }
                    )
                }

                else -> {
                    CarelevoPatchFlowScreen(
                        screenType = activeWorkflowScreen,
                        setToolbarConfig = setToolbarConfig,
                        onExitFlow = {
                            manualWorkflowScreen = null
                            latchedWorkflowScreen = null
                        }
                    )
                }
            }

            CarelevoAlarmHost(
                aapsLogger = aapsLogger,
                carelevoAlarmNotifier = carelevoAlarmNotifier,
                iconsProvider = iconsProvider
            )
        }
    }
}
