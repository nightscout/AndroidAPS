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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ComposeScreenContent
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.MasterOfflineBanner
import app.aaps.core.ui.compose.masterEditingEnabled
import app.aaps.core.ui.compose.preference.LocalNavigateToCompose
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.preference.ProvidePreferenceTheme
import app.aaps.core.ui.compose.preference.addPreferenceContent
import app.aaps.core.ui.compose.preference.rememberPreferenceSectionState
import app.aaps.core.ui.compose.preference.verticalScrollIndicators
import app.aaps.ui.search.BuiltInSearchables
import kotlinx.coroutines.launch

/**
 * Screen for displaying all preferences from all plugins.
 *
 * Plugins are looked up via their interfaces through ActivePlugin, eliminating
 * direct dependencies on specific plugin implementations.
 *
 * @param activePlugin ActivePlugin instance for accessing plugins by interface
 * @param rh TextResolver instance
 * @param builtInSearchables BuiltInSearchables instance (single source of truth for built-in screens)
 * @param configBuilder ConfigBuilder for the synced-selection gate (client APS visibility)
 * @param onBackClick Callback when back button is clicked
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllPreferencesScreen(
    activePlugin: ActivePlugin,
    rh: TextResolver,
    builtInSearchables: BuiltInSearchables,
    configBuilder: ConfigBuilder,
    onBackClick: () -> Unit
) {
    val preferences = LocalPreferences.current
    val config = LocalConfig.current
    // Look up plugins by interface
    val autotunePlugin = activePlugin.getSpecificPluginsListByInterface(Autotune::class).firstOrNull()

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

    val pluginContents = buildList {
        // 2. Safety plugin (always enabled)
        getPreferenceContentIfEnabled(activePlugin.activeSafety as PluginBase)?.let { add(it) }

        // 3. BG Source plugin — master only: on a client the collector runs on the master and can't
        // be changed or configured locally (mirrors the Configuration screen's category gate)
        if (!config.AAPSCLIENT) {
            getPreferenceContentIfEnabled(activePlugin.activeBgSource as PluginBase)?.let { add(it) }
        }

        // 4. LOOP type plugins (enabled only if APS is configured)
        activePlugin.getSpecificPluginsList(PluginType.LOOP).forEach { plugin ->
            getPreferenceContentIfEnabled(plugin, config.APS)?.let { add(it) }
        }

        // 5. APS plugin — on a master when APS is configured; also on a client when the APS selection
        // syncs (its settings are Bidirectional-synced, so they are editable from the client)
        val apsAvailable = config.APS || (config.AAPSCLIENT && PluginType.APS in configBuilder.syncedSelectionTypes)
        (activePlugin.activeAPS as? PluginBase)?.let { getPreferenceContentIfEnabled(it, apsAvailable)?.let { pref -> add(pref) } }

        // 6. Sensitivity plugin
        getPreferenceContentIfEnabled(activePlugin.activeSensitivity as PluginBase)?.let { add(it) }

        // 7. Pump plugin — master only: on a client the pump is virtual and its settings come from
        // the master (mirrors the Configuration screen's category gate)
        if (!config.AAPSCLIENT) {
            getPreferenceContentIfEnabled(activePlugin.activePumpInternal as PluginBase)?.let { add(it) }
        }

        // 8. SYNC type plugins
        activePlugin.getSpecificPluginsList(PluginType.SYNC).forEach { plugin ->
            getPreferenceContentIfEnabled(plugin)?.let { add(it) }
        }

        // 11. Automation settings (standalone feature, from BuiltInSearchables; master-only — the
        // location-provider setting only has effect where automation executes).
        if (config.APS) add(builtInSearchables.automation)

        // 12. Autotune plugin (found via interface)
        getPreferenceContentIfEnabled(autotunePlugin)?.let { add(it) }
    }

    val snackbarHostState = LocalSnackbarHostState.current
    val snackbarScope = rememberCoroutineScope()
    val onShowMessage: (String) -> Unit = { message ->
        snackbarScope.launch { snackbarHostState.showSnackbar(message) }
    }
    var composeScreen: ComposeScreenContent? by remember { mutableStateOf(null) }

    BackHandler(enabled = composeScreen != null) {
        composeScreen = null
    }

    composeScreen?.let { screen ->
        screen.Content(onBack = { composeScreen = null })
        return
    }

    CompositionLocalProvider(
        LocalNavigateToCompose provides { screen -> composeScreen = screen }
    ) {
        ProvidePreferenceTheme {
            Scaffold(
                topBar = {
                    AapsTopAppBar(
                        title = {
                            Text(
                                text = stringResource(CoreUiStrings.settings),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(CoreUiStrings.back)
                                )
                            }
                        }
                    )
                }
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
                    item { MasterOfflineBanner(editingEnabled = masterEditingEnabled()) }
                    // Built-in: General settings (first)
                    addPreferenceContent(generalPreferences, onShowMessage, sectionState)
                    addPreferenceContent(appearancePreferences, onShowMessage, sectionState)

                    // Built-in: Protection settings
                    addPreferenceContent(protectionPreferences, onShowMessage, sectionState)

                    // Plugin preferences (in fixed order, only enabled plugins)
                    pluginContents.forEach { content ->
                        addPreferenceContent(content, onShowMessage, sectionState)
                    }

                    // Built-in: Pump settings
                    addPreferenceContent(pumpPreferences, onShowMessage, sectionState)

                    // Built-in: Alerts settings
                    addPreferenceContent(alertsPreferences, onShowMessage, sectionState)

                    // Built-in: Maintenance settings (always last)
                    addPreferenceContent(maintenancePreferences, onShowMessage, sectionState)
                }
            }
        }
    }
}
