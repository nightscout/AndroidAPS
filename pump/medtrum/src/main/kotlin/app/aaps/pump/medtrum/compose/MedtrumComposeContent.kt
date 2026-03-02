package app.aaps.pump.medtrum.compose

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.pump.BlePreCheckHost
import app.aaps.pump.medtrum.code.PatchStep

class MedtrumComposeContent(
    private val protectionCheck: ProtectionCheck,
    private val blePreCheck: BlePreCheck,
    private val viewModelFactory: ViewModelProvider.Factory
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val context = LocalContext.current
        val viewModelStoreOwner = context as ViewModelStoreOwner
        val overviewViewModel: MedtrumOverviewViewModel = remember(viewModelStoreOwner) {
            ViewModelProvider(viewModelStoreOwner, viewModelFactory)[MedtrumOverviewViewModel::class.java]
        }

        // Patch workflow state
        var showPatchWorkflow by remember { mutableStateOf(false) }
        var startPatchStep by remember { mutableStateOf<PatchStep?>(null) }

        // Dialog state
        var showDialog by remember { mutableStateOf(false) }
        var dialogTitle by remember { mutableStateOf("") }
        var dialogMessage by remember { mutableStateOf("") }

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
            // Keep screen on and lock orientation during patch workflow
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

            // BLE pre-check — shows dialog on failure, cancels workflow
            BlePreCheckHost(
                blePreCheck = blePreCheck,
                onFailed = {
                    showPatchWorkflow = false
                    startPatchStep = null
                }
            )

            // Create PatchViewModel scoped to the workflow
            val patchViewModel: MedtrumPatchViewModel = remember(viewModelStoreOwner) {
                ViewModelProvider(viewModelStoreOwner, viewModelFactory)[MedtrumPatchViewModel::class.java]
            }

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

            MedtrumPatchScreen(viewModel = patchViewModel)
        } else {
            MedtrumOverviewScreen(viewModel = overviewViewModel)
        }
    }
}
