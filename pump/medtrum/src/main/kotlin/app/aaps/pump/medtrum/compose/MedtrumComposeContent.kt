package app.aaps.pump.medtrum.compose

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
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.pump.BlePreCheckHost
import app.aaps.core.ui.compose.pump.KeepScreenOnEffect
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.medtrum.R as MedtrumR

class MedtrumComposeContent(
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
        val overviewViewModel: MedtrumOverviewViewModel = hiltViewModel()
        val patchViewModel: MedtrumPatchViewModel = hiltViewModel()

        // Patch workflow state
        var showPatchWorkflow by remember { mutableStateOf(false) }
        var startPatchStep by remember { mutableStateOf<PatchStep?>(null) }

        // Dialog state
        var showDialog by remember { mutableStateOf(false) }
        var dialogTitle by remember { mutableStateOf("") }
        var dialogMessage by remember { mutableStateOf("") }
        var showUnpairConfirm by remember { mutableStateOf(false) }

        // Toolbar configuration
        val overviewNavIcon: @Composable () -> Unit = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiR.string.back))
            }
        }
        val settingsAction: @Composable RowScope.() -> Unit = {
            onSettings?.let { action ->
                IconButton(onClick = action) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(CoreUiR.string.settings))
                }
            }
        }
        // Restore overview toolbar when not in workflow
        LaunchedEffect(showPatchWorkflow) {
            if (!showPatchWorkflow) {
                setToolbarConfig(
                    ToolbarConfig(
                        title = pluginName,
                        navigationIcon = overviewNavIcon,
                        actions = settingsAction
                    )
                )
            }
        }

        // Handle one-time events from overview
        LaunchedEffect(overviewViewModel) {
            overviewViewModel.events.collect { event ->
                when (event) {
                    is MedtrumOverviewEvent.StartPatchWorkflow -> {
                        if (!showPatchWorkflow) {
                            protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                                if (result == ProtectionResult.GRANTED) {
                                    patchViewModel.reset()
                                    startPatchStep = event.startStep
                                    showPatchWorkflow = true
                                }
                            }
                        }
                    }

                    is MedtrumOverviewEvent.ShowDialog         -> {
                        dialogTitle = event.title
                        dialogMessage = event.message
                        showDialog = true
                    }

                    is MedtrumOverviewEvent.ConfirmUnpair      -> showUnpairConfirm = true
                }
            }
        }

        if (showDialog) {
            OkDialog(
                title = dialogTitle,
                message = dialogMessage,
                onDismiss = { showDialog = false }
            )
        }

        if (showUnpairConfirm) {
            OkCancelDialog(
                title = stringResource(CoreUiR.string.pump_unpair_confirm_title),
                message = stringResource(MedtrumR.string.unpair_confirm_message),
                onConfirm = {
                    showUnpairConfirm = false
                    overviewViewModel.performUnpair()
                },
                onDismiss = { showUnpairConfirm = false }
            )
        }

        if (showPatchWorkflow) {
            KeepScreenOnEffect()

            // BLE pre-check — shows dialog on failure, cancels workflow
            BlePreCheckHost(
                blePreCheck = blePreCheck,
                onFailed = {
                    showPatchWorkflow = false
                    startPatchStep = null
                }
            )

            // Initialize with the start step (reset was already called before showing workflow)
            LaunchedEffect(startPatchStep) {
                startPatchStep?.let { step ->
                    patchViewModel.initializePatchStep(step)
                }
            }

            // Observe finish events
            LaunchedEffect(patchViewModel) {
                patchViewModel.events.collect { event ->
                    when (event) {
                        is PatchEvent.Finish -> {
                            showPatchWorkflow = false
                            startPatchStep = null
                        }

                        is PatchEvent.ShowError -> { /* handled by step composables */
                        }
                    }
                }
            }

            MedtrumPatchScreen(viewModel = patchViewModel, setToolbarConfig = setToolbarConfig)
        } else {
            MedtrumOverviewScreen(viewModel = overviewViewModel)
        }
    }
}
