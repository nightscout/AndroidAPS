package app.aaps.pump.eopatch.compose

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.pump.BlePreCheckHost
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.code.PatchStep

class EopatchComposeContent(
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
        val overviewViewModel: EopatchOverviewViewModel = hiltViewModel()

        // Patch workflow state
        var showPatchWorkflow by remember { mutableStateOf(false) }
        var startPatchStep by remember { mutableStateOf<PatchStep?>(null) }
        var forceDiscard by remember { mutableStateOf(false) }
        var isAlarmHandling by remember { mutableStateOf(false) }

        // Toolbar configuration
        val pluginName = stringResource(R.string.eopatch)
        val overviewNavIcon: @Composable () -> Unit = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(app.aaps.core.ui.R.string.back))
            }
        }
        val settingsAction: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            onSettings?.let { action ->
                IconButton(onClick = action) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(app.aaps.core.ui.R.string.settings))
                }
            }
        }

        // Restore overview toolbar when not in workflow
        LaunchedEffect(showPatchWorkflow) {
            if (!showPatchWorkflow) {
                setToolbarConfig(ToolbarConfig(title = pluginName, navigationIcon = overviewNavIcon, actions = settingsAction))
            }
        }

        // Handle one-time events from overview
        LaunchedEffect(overviewViewModel) {
            overviewViewModel.events.collect { event ->
                when (event) {
                    is EopatchOverviewEvent.StartPatchWorkflow -> {
                        protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                            if (result == ProtectionResult.GRANTED) {
                                startPatchStep = event.startStep
                                forceDiscard = event.forceDiscard
                                isAlarmHandling = event.isAlarmHandling
                                showPatchWorkflow = true
                            }
                        }
                    }

                    is EopatchOverviewEvent.ShowToast          -> {
                        // Toast is handled in EopatchOverviewScreen
                    }
                }
            }
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

            // BLE pre-check
            BlePreCheckHost(
                blePreCheck = blePreCheck,
                onFailed = {
                    showPatchWorkflow = false
                    startPatchStep = null
                }
            )

            // Create PatchViewModel scoped to the workflow
            val patchViewModel: EopatchPatchViewModel = hiltViewModel()

            // Reset and initialize with the start step
            LaunchedEffect(startPatchStep) {
                startPatchStep?.let { step ->
                    patchViewModel.reset()
                    patchViewModel.forceDiscard = forceDiscard
                    patchViewModel.isInAlarmHandling = isAlarmHandling
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

                        else                 -> { /* handled by step composables */
                        }
                    }
                }
            }

            EopatchPatchScreen(viewModel = patchViewModel, setToolbarConfig = setToolbarConfig)
        } else {
            EopatchOverviewScreen(viewModel = overviewViewModel)
        }
    }
}
