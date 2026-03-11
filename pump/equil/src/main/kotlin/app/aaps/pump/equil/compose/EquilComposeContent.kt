package app.aaps.pump.equil.compose

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.pump.BlePreCheckHost

class EquilComposeContent(
    private val protectionCheck: ProtectionCheck,
    private val blePreCheck: BlePreCheck
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val context = LocalContext.current
        val overviewViewModel: EquilOverviewViewModel = hiltViewModel()

        // Navigation state
        var showHistory by remember { mutableStateOf(false) }
        var showWizardWorkflow by remember { mutableStateOf(false) }
        var startWorkflow by remember { mutableStateOf<EquilWorkflow?>(null) }

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
            // Keep screen on and lock orientation during wizard workflow
            val activity = context as? Activity
            DisposableEffect(Unit) {
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val previousOrientation = activity?.requestedOrientation
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                onDispose {
                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    previousOrientation?.let { activity.requestedOrientation = it }
                }
            }

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
                                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                EquilWizardScreen(viewModel = wizardViewModel)
            }
        } else {
            EquilOverviewScreen(viewModel = overviewViewModel)
        }
    }
}
