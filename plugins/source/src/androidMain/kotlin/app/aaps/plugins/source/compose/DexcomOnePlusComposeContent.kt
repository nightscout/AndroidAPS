package app.aaps.plugins.source.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.icons.IcPluginByoda
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.source.DexcomOnePlusHost
import app.aaps.plugins.source.SourceStrings

/** How much text a share may carry, so a long session cannot break the send. */
private const val MAX_SHARED_CHARACTERS = 200_000

/** The screens of the ONE+ / G7 plugin tab. */
private enum class DexcomOnePlusTabScreen { READINGS, STATUS, LOG }

/**
 * The plugin tab of the native Dexcom ONE+ / G7 source.
 *
 * It opens on the readings list every BG source shows. A sensor button in its toolbar opens the
 * sensor status, and the status leads on to the driver log. Back, the toolbar arrow or the system
 * gesture, goes up one screen. Starting a sensor and the warm-up countdown are opened through
 * [DexcomOnePlusHost.openScreen], because those screens are Activities of their own.
 *
 * The idea follows the G7 plugin tab (readings list, then status, then log) in
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
        var screen by remember { mutableStateOf(DexcomOnePlusTabScreen.READINGS) }
        val sensorLabel = stringResource(SourceStrings.dexcom_oneplus_status_title)
        val readings = remember(title) {
            BgSourceComposeContent(title = title) {
                IconButton(onClick = { screen = DexcomOnePlusTabScreen.STATUS }) {
                    Icon(IcPluginByoda, contentDescription = sensorLabel)
                }
            }
        }

        when (screen) {
            DexcomOnePlusTabScreen.READINGS -> readings.Render(setToolbarConfig, onNavigateBack, onSettings)

            DexcomOnePlusTabScreen.STATUS   -> {
                val viewModel: DexcomOnePlusStatusViewModel = metroViewModel()
                val actions = remember(viewModel) {
                    DexcomOnePlusStatusActions(
                        onOpenStart = { viewModel.host.openScreen(DexcomOnePlusHost.Screen.START) },
                        onOpenWarmup = { viewModel.host.openScreen(DexcomOnePlusHost.Screen.WARMUP) },
                        onOpenLog = { screen = DexcomOnePlusTabScreen.LOG },
                    )
                }
                val backLabel = stringResource(CoreUiStrings.back)
                val settingsLabel = stringResource(CoreUiStrings.settings)
                LaunchedEffect(Unit) {
                    setToolbarConfig(
                        ToolbarConfig(
                            title = sensorLabel,
                            navigationIcon = {
                                IconButton(onClick = { screen = DexcomOnePlusTabScreen.READINGS }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
                                }
                            },
                            actions = settingsAction(onSettings, settingsLabel),
                        ),
                    )
                }
                BackToScreen { screen = DexcomOnePlusTabScreen.READINGS }
                DexcomOnePlusStatusOverview(viewModel = viewModel, actions = actions)
            }

            DexcomOnePlusTabScreen.LOG      -> {
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
                                IconButton(onClick = { screen = DexcomOnePlusTabScreen.STATUS }) {
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
                BackToScreen { screen = DexcomOnePlusTabScreen.STATUS }
                DexcomOnePlusLogScreen(host = host, reloads = reloads, onLinesChanged = { lines = it })
            }
        }
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
