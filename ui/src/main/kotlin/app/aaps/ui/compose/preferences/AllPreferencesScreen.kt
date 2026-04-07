package app.aaps.ui.compose.preferences

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ComposeScreenContent
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.preference.LocalNavigateToCompose
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.preference.ProvidePreferenceTheme
import app.aaps.core.ui.compose.preference.addPreferenceContent
import app.aaps.core.ui.compose.preference.rememberPreferenceSectionState
import app.aaps.core.ui.compose.preference.verticalScrollIndicators
import app.aaps.ui.search.BuiltInSearchables

/**
 * Screen for displaying all preferences from all plugins.
 * Maintains the same ordering as the legacy MyPreferenceFragment.
 *
 * Plugins are looked up via their interfaces through ActivePlugin, eliminating
 * direct dependencies on specific plugin implementations.
 *
 * @param activePlugin ActivePlugin instance for accessing plugins by interface
 * @param rh ResourceHelper instance
 * @param builtInSearchables BuiltInSearchables instance (single source of truth for built-in screens)
 * @param onBackClick Callback when back button is clicked
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllPreferencesScreen(
    activePlugin: ActivePlugin,
    rh: ResourceHelper,
    builtInSearchables: BuiltInSearchables,
    onBackClick: () -> Unit
) {
    val preferences = LocalPreferences.current
    val config = LocalConfig.current
    // Look up plugins by interface
    val smsCommunicatorPlugin = activePlugin.getSpecificPluginsListByInterface(SmsCommunicator::class.java).firstOrNull()
    val automationPlugin = activePlugin.getSpecificPluginsListByInterface(Automation::class.java).firstOrNull()
    val autotunePlugin = activePlugin.getSpecificPluginsListByInterface(Autotune::class.java).firstOrNull()

    // Built-in preference screens from BuiltInSearchables (single source of truth)
    val generalPreferences = builtInSearchables.general
    val appearancePreferences = builtInSearchables.appearance
    val protectionPreferences = builtInSearchables.protection
    val pumpPreferences = builtInSearchables.pump
    val alertsPreferences = builtInSearchables.alerts
    val maintenancePreferences = builtInSearchables.maintenance

    // Helper function to get preference content if plugin is enabled
    fun getPreferenceContentIfEnabled(plugin: PluginBase?, enabledCondition: Boolean = true): Any? {
        if (plugin == null) return null
        // Check simple mode visibility
        if (preferences.simpleMode && !plugin.pluginDescription.preferencesVisibleInSimpleMode && !config.isDev()) {
            return null
        }
        // Check if plugin is enabled
        if (!enabledCondition || !plugin.isEnabled()) {
            return null
        }
        // Only PreferenceSubScreenDef is supported
        return when (val content = plugin.getPreferenceScreenContent()) {
            is PreferenceSubScreenDef -> content
            else                      -> null
        }
    }

    // Build plugin preference screens in the same order as MyPreferenceFragment
    val pluginContents = buildList {
        // 1. Overview plugin (always enabled)
        getPreferenceContentIfEnabled(activePlugin.activeOverview as PluginBase)?.let { add(it) }

        // 2. Safety plugin (always enabled)
        getPreferenceContentIfEnabled(activePlugin.activeSafety as PluginBase)?.let { add(it) }

        // 3. BG Source plugin
        getPreferenceContentIfEnabled(activePlugin.activeBgSource as PluginBase)?.let { add(it) }

        // 4. LOOP type plugins (enabled only if APS is configured)
        activePlugin.getSpecificPluginsList(PluginType.LOOP).forEach { plugin ->
            getPreferenceContentIfEnabled(plugin, config.APS)?.let { add(it) }
        }

        // 5. APS plugin (enabled only if APS is configured)
        (activePlugin.activeAPS as? PluginBase)?.let { getPreferenceContentIfEnabled(it, config.APS)?.let { pref -> add(pref) } }

        // 6. Sensitivity plugin
        getPreferenceContentIfEnabled(activePlugin.activeSensitivity as PluginBase)?.let { add(it) }

        // 7. Pump plugin
        getPreferenceContentIfEnabled(activePlugin.activePumpInternal as PluginBase)?.let { add(it) }

        // 8. SYNC type plugins
        activePlugin.getSpecificPluginsList(PluginType.SYNC).forEach { plugin ->
            getPreferenceContentIfEnabled(plugin)?.let { add(it) }
        }

        // 10. SMS Communicator plugin (found via interface)
        getPreferenceContentIfEnabled(smsCommunicatorPlugin)?.let { add(it) }

        // 11. Automation plugin (found via interface)
        getPreferenceContentIfEnabled(automationPlugin)?.let { add(it) }

        // 12. Autotune plugin (found via interface)
        getPreferenceContentIfEnabled(autotunePlugin)?.let { add(it) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var composeScreen: ComposeScreenContent? by remember { mutableStateOf(null) }

    BackHandler(enabled = composeScreen != null) {
        composeScreen = null
    }

    composeScreen?.let { screen ->
        screen.Content(onBack = { composeScreen = null })
        return
    }

    CompositionLocalProvider(
        LocalSnackbarHostState provides snackbarHostState,
        LocalNavigateToCompose provides { screen -> composeScreen = screen }
    ) {
        ProvidePreferenceTheme {
            Scaffold(
                topBar = {
                    AapsTopAppBar(
                        title = {
                            Text(
                                text = stringResource(app.aaps.core.ui.R.string.settings),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                                )
                            }
                        }
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { paddingValues ->
                val listState = rememberLazyListState()
                val sectionState = rememberPreferenceSectionState()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScrollIndicators(listState),
                    state = listState
                ) {
                    // Built-in: General settings (first)
                    addPreferenceContent(generalPreferences, sectionState)
                    addPreferenceContent(appearancePreferences, sectionState)

                    // Built-in: Protection settings
                    addPreferenceContent(protectionPreferences, sectionState)

                    // Plugin preferences (in fixed order, only enabled plugins)
                    pluginContents.forEach { content ->
                        addPreferenceContent(content, sectionState)
                    }

                    // Built-in: Pump settings
                    addPreferenceContent(pumpPreferences, sectionState)

                    // Built-in: Alerts settings
                    addPreferenceContent(alertsPreferences, sectionState)

                    // Built-in: Maintenance settings (always last)
                    addPreferenceContent(maintenancePreferences, sectionState)
                }
            }
        }
    }
}
