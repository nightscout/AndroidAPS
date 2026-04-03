package app.aaps.pump.equil.compose

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.pump.BlePreCheckHost
import app.aaps.core.ui.compose.pump.KeepScreenOnEffect

class EquilComposeContent(
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
        LocalContext.current
        val snackbarHostState = LocalSnackbarHostState.current
        val overviewViewModel: EquilOverviewViewModel = hiltViewModel()

        // Navigation state
        var showHistory by remember { mutableStateOf(false) }
        var showWizardWorkflow by remember { mutableStateOf(false) }
        var startWorkflow by remember { mutableStateOf<EquilWorkflow?>(null) }

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
        // Restore overview toolbar when not in wizard
        LaunchedEffect(showWizardWorkflow) {
            if (!showWizardWorkflow) {
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
                    is EquilOverviewEvent.StartWizard  -> {
                        if (event.workflow == EquilWorkflow.CHANGE_INSULIN) {
                            protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                                if (result == ProtectionResult.GRANTED) {
                                    startWorkflow = event.workflow
                                    showWizardWorkflow = true
                                }
                            }
                        } else {
                            startWorkflow = event.workflow
                            showWizardWorkflow = true
                        }
                    }

                    is EquilOverviewEvent.StartHistory -> {
                        showHistory = true
                    }
                }
            }
        }

        if (showHistory) {
            val historyViewModel: EquilHistoryViewModel = hiltViewModel()
            EquilHistoryScreen(
                viewModel = historyViewModel,
                onNavigateBack = { showHistory = false }
            )
        } else if (showWizardWorkflow) {
            KeepScreenOnEffect()

            // BLE pre-check — gate wizard until BLE is confirmed ready
            var bleReady by remember { mutableStateOf(false) }
            BlePreCheckHost(
                blePreCheck = blePreCheck,
                onReady = { bleReady = true },
                onFailed = {
                    showWizardWorkflow = false
                    startWorkflow = null
                }
            )

            if (bleReady) {
                // Create WizardViewModel scoped to the workflow
                val wizardViewModel: EquilWizardViewModel = hiltViewModel()
                // Initialize with the start workflow
                LaunchedEffect(startWorkflow) {
                    startWorkflow?.let { workflow ->
                        wizardViewModel.initializeWorkflow(workflow)
                    }
                }

                // Observe finish events
                LaunchedEffect(wizardViewModel) {
                    wizardViewModel.events.collect { event ->
                        when (event) {
                            is EquilWizardViewModel.EquilWizardEvent.Finish      -> {
                                showWizardWorkflow = false
                                startWorkflow = null
                            }

                            is EquilWizardViewModel.EquilWizardEvent.ShowMessage -> {
                                snackbarHostState.showSnackbar(event.message)
                            }
                        }
                    }
                }

                EquilWizardScreen(viewModel = wizardViewModel, setToolbarConfig = setToolbarConfig)
            }
        } else {
            EquilOverviewScreen(viewModel = overviewViewModel)
        }
    }
}
