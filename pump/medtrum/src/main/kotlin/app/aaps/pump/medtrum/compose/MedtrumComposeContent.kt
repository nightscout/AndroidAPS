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
import androidx.hilt.navigation.compose.hiltViewModel
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.pump.BlePreCheckHost
import app.aaps.core.ui.compose.pump.KeepScreenOnEffect
import app.aaps.pump.medtrum.code.PatchStep

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

        // Patch workflow state
        var showPatchWorkflow by remember { mutableStateOf(false) }
        var startPatchStep by remember { mutableStateOf<PatchStep?>(null) }

        // Dialog state
        var showDialog by remember { mutableStateOf(false) }
        var dialogTitle by remember { mutableStateOf("") }
        var dialogMessage by remember { mutableStateOf("") }

        // Toolbar configuration
        val overviewNavIcon: @Composable () -> Unit = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(app.aaps.core.ui.R.string.back))
            }
        }
        val settingsAction: @Composable RowScope.() -> Unit = {
            onSettings?.let { action ->
                IconButton(onClick = action) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(app.aaps.core.ui.R.string.settings))
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
                        protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                            if (result == ProtectionResult.GRANTED) {
                                startPatchStep = event.startStep
                                showPatchWorkflow = true
                            }
                        }
                    }

                    is MedtrumOverviewEvent.ShowDialog         -> {
                        dialogTitle = event.title
                        dialogMessage = event.message
                        showDialog = true
                    }
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

            // Create PatchViewModel scoped to the workflow
            val patchViewModel: MedtrumPatchViewModel = hiltViewModel()

            // Reset and initialize with the start step
            LaunchedEffect(startPatchStep) {
                startPatchStep?.let { step ->
                    patchViewModel.reset()
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
