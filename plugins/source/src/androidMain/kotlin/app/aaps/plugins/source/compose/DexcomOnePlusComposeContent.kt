package app.aaps.plugins.source.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.source.DexcomOnePlusHost
import app.aaps.plugins.source.SourceStrings

/** How much text a share may carry, so a long session cannot break the send. */
private const val MAX_SHARED_CHARACTERS = 200_000

/** The two tabs of the ONE+ / G7 plugin, in the order they are shown. */
private enum class DexcomOnePlusTab { STATUS, READINGS }

/**
 * The plugin tab of the native Dexcom ONE+ / G7 source.
 *
 * Two tabs at the top: Status (the sensor, selected when the plugin opens) and Readings (the list
 * every BG source shows), the same pattern as the Production / Pre-soak choice of the Start screen.
 * The driver log opens from Status as a screen of its own; back, the toolbar arrow or the system
 * gesture, returns to Status. Starting a sensor and the warm-up countdown are opened through
 * [DexcomOnePlusHost.openScreen], because those screens are Activities of their own.
 *
 * The idea of a status screen and a log next to the readings list follows the G7 plugin tab in
 * nightscout/AndroidAPS pull request 5397 (Dexcom G7 direct, by t1dude, commit `bec1b16ba20`),
 * written again on this driver. See the NOTICE file of `:plugins:dexcom_oneplus`.
 */
internal class DexcomOnePlusComposeContent(
    private val title: String,
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?,
    ) {
        var tab by remember { mutableStateOf(DexcomOnePlusTab.STATUS) }
        var showLog by remember { mutableStateOf(false) }
        val readings = remember(title) { BgSourceComposeContent(title = title) }

        if (showLog) {
            LogScreen(setToolbarConfig, onBack = { showLog = false })
            return
        }
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                DexcomOnePlusTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        text = {
                            Text(
                                stringResource(
                                    when (entry) {
                                        DexcomOnePlusTab.STATUS   -> SourceStrings.dexcom_oneplus_tab_status
                                        DexcomOnePlusTab.READINGS -> SourceStrings.dexcom_oneplus_tab_readings
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    DexcomOnePlusTab.STATUS   -> {
                        val viewModel: DexcomOnePlusStatusViewModel = metroViewModel()
                        val actions = remember(viewModel) {
                            DexcomOnePlusStatusActions(
                                onOpenStart = { viewModel.host.openScreen(DexcomOnePlusHost.Screen.START) },
                                onOpenPresoak = { viewModel.host.openScreen(DexcomOnePlusHost.Screen.START_PRESOAK) },
                                onOpenWarmup = { viewModel.host.openScreen(DexcomOnePlusHost.Screen.WARMUP) },
                                onOpenLog = { showLog = true },
                            )
                        }
                        val backLabel = stringResource(CoreUiStrings.back)
                        val settingsLabel = stringResource(CoreUiStrings.settings)
                        // Keyed on the tab: coming back from Readings sets this tab's toolbar again.
                        LaunchedEffect(tab) {
                            setToolbarConfig(
                                ToolbarConfig(
                                    title = title,
                                    navigationIcon = {
                                        IconButton(onClick = onNavigateBack) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
                                        }
                                    },
                                    actions = settingsAction(onSettings, settingsLabel),
                                ),
                            )
                        }
                        DexcomOnePlusStatusOverview(viewModel = viewModel, actions = actions)
                    }

                    DexcomOnePlusTab.READINGS -> readings.Render(setToolbarConfig, onNavigateBack, onSettings)
                }
            }
        }
    }

    /** The driver log, opened from Status. Back returns to Status. */
    @Composable
    private fun LogScreen(setToolbarConfig: (ToolbarConfig) -> Unit, onBack: () -> Unit) {
        val viewModel: DexcomOnePlusStatusViewModel = metroViewModel()
        val host = viewModel.host
        var reloads by remember { mutableIntStateOf(0) }
        var lines by remember { mutableStateOf(emptyList<String>()) }
        val logTitle = stringResource(SourceStrings.cgm_driver_log_title)
        val backLabel = stringResource(CoreUiStrings.back)
        val refreshLabel = stringResource(SourceStrings.cgm_driver_log_refresh)
        val shareLabel = stringResource(SourceStrings.cgm_driver_log_share)
        LaunchedEffect(Unit) {
            setToolbarConfig(
                ToolbarConfig(
                    title = logTitle,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
                        }
                    },
                    actions = {
                        IconButton(onClick = { reloads++ }) {
                            Icon(Icons.Filled.Refresh, contentDescription = refreshLabel)
                        }
                        IconButton(
                            enabled = lines.isNotEmpty(),
                            onClick = { host.shareLog(lines.joinToString("\n").takeLast(MAX_SHARED_CHARACTERS)) },
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = shareLabel)
                        }
                    },
                ),
            )
        }
        BackToScreen(onBack)
        DexcomOnePlusLogScreen(host = host, reloads = reloads, onLinesChanged = { lines = it })
    }

    private fun settingsAction(onSettings: (() -> Unit)?, label: String): @Composable RowScope.() -> Unit = {
        onSettings?.let { action ->
            IconButton(onClick = action) {
                Icon(Icons.Filled.Settings, contentDescription = label)
            }
        }
    }
}

/**
 * The system back gesture goes one screen up inside the tab, the same way the toolbar arrow does,
 * instead of leaving the plugin.
 */
@Composable
private fun BackToScreen(onBack: () -> Unit) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = onBack,
    )
}
