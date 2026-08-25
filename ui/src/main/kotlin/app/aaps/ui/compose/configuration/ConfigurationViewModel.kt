package app.aaps.ui.compose.configuration

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.ConfigPluginUiModel
import app.aaps.core.ui.compose.pluginCategoryTitleRes
import app.aaps.ui.plugin.HardwarePumpConfirmation
import app.aaps.ui.plugin.PluginSwitchConfirmation
import app.aaps.ui.plugin.PluginSwitchDialogs
import app.aaps.ui.plugin.PluginSwitchHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ConfigurationUiState(
    val categories: List<ConfigCategoryUiModel> = emptyList(),
    val isSimpleMode: Boolean = true,
    val hardwarePumpConfirmation: HardwarePumpConfirmation? = null,
    val pluginSwitchConfirmation: PluginSwitchConfirmation? = null
)

@HiltViewModel
@Stable
class ConfigurationViewModel @Inject constructor(
    private val activePlugin: ActivePlugin,
    private val configBuilder: ConfigBuilder,
    private val config: Config,
    private val preferences: Preferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigurationUiState())
    val uiState: StateFlow<ConfigurationUiState> = _uiState.asStateFlow()

    // Keep plugin references for toggle callbacks (UI only sees IDs)
    private var pluginLookup: Map<String, PluginBase> = emptyMap()

    // Shared enable/disable orchestration (swap confirmation + hardware-pump gate + serialized off-main switch).
    // onSwitched rebuilds the categories once a switch commits, same as before.
    private val switchHandler = PluginSwitchHandler(viewModelScope, activePlugin, configBuilder, onSwitched = ::refreshCategories)

    init {
        loadCategories()
        // Refresh live when a synced selection changes — including a master→client push adopted while the
        // screen is open (fires on putRemote too), not just the user's own toggles.
        viewModelScope.launch {
            configBuilder.activeSelectionChanges.collect { refreshCategories() }
        }
        // Mirror the handler's confirmation dialogs into this screen's UI state (covers async updates such as
        // the hardware-pump gate appearing after the switch call returns).
        viewModelScope.launch {
            switchHandler.dialogs.collect { syncSwitchDialogs(it) }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            refreshCategories()
        }
    }

    fun togglePluginEnabled(pluginId: String, type: PluginType, enabled: Boolean) {
        val plugin = pluginLookup[pluginId] ?: return
        switchHandler.toggle(plugin, type, enabled)
        syncSwitchDialogs(switchHandler.dialogs.value)
    }

    fun confirmPluginSwitch() {
        switchHandler.confirmSwitch()
        syncSwitchDialogs(switchHandler.dialogs.value)
    }

    fun dismissPluginSwitchDialog() {
        switchHandler.dismissSwitch()
        syncSwitchDialogs(switchHandler.dialogs.value)
    }

    fun confirmHardwarePumpSwitch() {
        switchHandler.confirmHardwarePump()
        syncSwitchDialogs(switchHandler.dialogs.value)
    }

    fun dismissHardwarePumpDialog() {
        switchHandler.dismissHardwarePump()
        syncSwitchDialogs(switchHandler.dialogs.value)
    }

    private fun syncSwitchDialogs(dialogs: PluginSwitchDialogs) {
        _uiState.update {
            it.copy(
                hardwarePumpConfirmation = dialogs.hardwarePumpConfirmation,
                pluginSwitchConfirmation = dialogs.pluginSwitchConfirmation
            )
        }
    }

    private fun refreshCategories() {
        val isSimple = preferences.simpleMode
        val categories = buildCategories(isSimple)
        _uiState.update { state ->
            state.copy(
                categories = categories,
                isSimpleMode = isSimple
            )
        }
    }

    /** A single-select category whose selection syncs, viewed on a client — drives both client visibility and
     *  the sync badge. SSOT-derived (configBuilder.syncedSelectionTypes); always false on a master. */
    private fun clientSynced(type: PluginType) = config.AAPSCLIENT && type in configBuilder.syncedSelectionTypes

    private fun buildCategories(isSimpleMode: Boolean): List<ConfigCategoryUiModel> {
        val lookup = mutableMapOf<String, PluginBase>()
        val categories = mutableListOf<ConfigCategoryUiModel>()

        fun addCategory(type: PluginType) {
            val plugins = activePlugin.getSpecificPluginsVisibleInList(type)
            if (plugins.isEmpty()) return
            val titleRes = pluginCategoryTitleRes(type)
            val isMultiSelect = isMultiSelect(type)

            val pluginModels = plugins.map { plugin ->
                val id = plugin.javaClass.simpleName
                lookup[id] = plugin
                val pluginEnabled = plugin.isEnabled(type)
                val hasPreferences = plugin.hasPreferences()
                ConfigPluginUiModel(
                    id = id,
                    name = plugin.name,
                    description = plugin.description,
                    composeIcon = plugin.pluginDescription.icon,
                    isEnabled = pluginEnabled,
                    canToggle = !plugin.pluginDescription.alwaysEnabled && (isMultiSelect || !pluginEnabled),
                    showPreferences = hasPreferences && pluginEnabled && (!isSimpleMode || plugin.pluginDescription.preferencesVisibleInSimpleMode),
                    hasContent = plugin.hasComposeContent()
                )
            }

            val enabledPlugins = pluginModels.filter { it.isEnabled }
            val subtitle = when {
                enabledPlugins.size == 1                     -> enabledPlugins.first().name
                isMultiSelect && enabledPlugins.isNotEmpty() -> "${enabledPlugins.size}"
                else                                         -> "-"
            }

            val singleEnabled = enabledPlugins.singleOrNull()
            val defaultIcon = when (type) {
                PluginType.SYNC    -> Icons.Default.Sync
                PluginType.GENERAL -> Icons.Default.Extension
                else               -> Icons.Default.Settings
            }

            categories.add(
                ConfigCategoryUiModel(
                    type = type,
                    titleRes = titleRes,
                    plugins = pluginModels,
                    isMultiSelect = isMultiSelect,
                    subtitle = subtitle,
                    categoryIcon = singleEnabled?.composeIcon ?: defaultIcon,
                    synced = clientSynced(type)
                )
            )
        }

        // A category is also shown on a client when its selection syncs (clientSynced, SSOT-derived) — folded
        // into each existing gate (rather than appended) so client and master keep the same category order.
        if (!config.AAPSCLIENT) addCategory(PluginType.BGSOURCE)
        if (!config.AAPSCLIENT || clientSynced(PluginType.SMOOTHING)) addCategory(PluginType.SMOOTHING)
        if (!config.AAPSCLIENT || clientSynced(PluginType.CALIBRATION)) addCategory(PluginType.CALIBRATION)
        if (!config.AAPSCLIENT) addCategory(PluginType.PUMP)
        if (config.APS || config.PUMPCONTROL || config.isEngineeringMode() || clientSynced(PluginType.SENSITIVITY)) {
            addCategory(PluginType.SENSITIVITY)
        }
        if (config.APS || clientSynced(PluginType.APS)) {
            addCategory(PluginType.APS)
        }
        if (config.APS) {
            addCategory(PluginType.LOOP)
            addCategory(PluginType.CONSTRAINTS)
        }
        addCategory(PluginType.SYNC)
        addCategory(PluginType.GENERAL)

        pluginLookup = lookup
        return categories
    }

    companion object {

        private fun isMultiSelect(type: PluginType): Boolean = !type.singleSelect
    }
}
