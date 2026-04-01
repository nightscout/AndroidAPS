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
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.keys.interfaces.Preferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ConfigurationUiState(
    val categories: List<ConfigCategoryUiModel> = emptyList(),
    val isSimpleMode: Boolean = true,
    val hardwarePumpConfirmation: HardwarePumpConfirmation? = null
)

@Immutable
data class HardwarePumpConfirmation(
    val message: String,
    val pluginId: String,
    val type: PluginType,
    val enabled: Boolean
)

@HiltViewModel
@Stable
class ConfigurationViewModel @Inject constructor(
    private val activePlugin: ActivePlugin,
    private val configBuilder: ConfigBuilder,
    private val config: Config,
    private val preferences: Preferences
) : ViewModel() {

    val uiState: StateFlow<ConfigurationUiState>
        field = MutableStateFlow(ConfigurationUiState())

    // Keep plugin references for toggle callbacks (UI only sees IDs)
    private var pluginLookup: Map<String, PluginBase> = emptyMap()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            refreshCategories()
        }
    }

    fun togglePluginEnabled(pluginId: String, type: PluginType, enabled: Boolean) {
        val plugin = pluginLookup[pluginId] ?: return
        val confirmationMessage = configBuilder.requestPluginSwitch(plugin, enabled, type)
        if (confirmationMessage != null) {
            uiState.update { state ->
                state.copy(
                    hardwarePumpConfirmation = HardwarePumpConfirmation(
                        message = confirmationMessage,
                        pluginId = pluginId,
                        type = type,
                        enabled = enabled
                    )
                )
            }
        } else {
            refreshCategories()
        }
    }

    fun confirmHardwarePumpSwitch() {
        val confirmation = uiState.value.hardwarePumpConfirmation ?: return
        val plugin = pluginLookup[confirmation.pluginId] ?: return
        configBuilder.confirmPumpPluginSwitch(plugin, confirmation.enabled, confirmation.type)
        uiState.update { it.copy(hardwarePumpConfirmation = null) }
        refreshCategories()
    }

    fun dismissHardwarePumpDialog() {
        uiState.update { it.copy(hardwarePumpConfirmation = null) }
        refreshCategories()
    }

    private fun refreshCategories() {
        val isSimple = preferences.simpleMode
        val categories = buildCategories(isSimple)
        uiState.update { state ->
            state.copy(
                categories = categories,
                isSimpleMode = isSimple
            )
        }
    }

    private fun buildCategories(isSimpleMode: Boolean): List<ConfigCategoryUiModel> {
        val lookup = mutableMapOf<String, PluginBase>()
        val categories = mutableListOf<ConfigCategoryUiModel>()

        fun addCategory(type: PluginType, titleRes: Int) {
            val plugins = activePlugin.getSpecificPluginsVisibleInList(type)
            if (plugins.isEmpty()) return
            val isMultiSelect = isMultiSelect(type)

            val pluginModels = plugins.map { plugin ->
                val id = plugin.javaClass.simpleName
                lookup[id] = plugin
                val pluginEnabled = plugin.isEnabled(type)
                val hasPreferences = plugin.preferencesId != PluginDescription.PREFERENCE_NONE
                ConfigPluginUiModel(
                    id = id,
                    name = plugin.name,
                    description = plugin.description,
                    menuIcon = plugin.menuIcon,
                    composeIcon = plugin.pluginDescription.icon,
                    isEnabled = pluginEnabled,
                    canToggle = !plugin.pluginDescription.alwaysEnabled && (isMultiSelect || !pluginEnabled),
                    showPreferences = hasPreferences && pluginEnabled && (!isSimpleMode || plugin.pluginDescription.preferencesVisibleInSimpleMode)
                )
            }

            val enabledPlugins = pluginModels.filter { it.isEnabled }
            val subtitle = when {
                enabledPlugins.size == 1 -> enabledPlugins.first().name
                isMultiSelect && enabledPlugins.isNotEmpty() -> "${enabledPlugins.size}"
                else -> "-"
            }

            val singleEnabled = enabledPlugins.singleOrNull()
            val defaultIcon = when (type) {
                PluginType.SYNC -> Icons.Default.Sync
                PluginType.GENERAL -> Icons.Default.Extension
                else -> Icons.Default.Settings
            }

            categories.add(
                ConfigCategoryUiModel(
                    type = type,
                    titleRes = titleRes,
                    plugins = pluginModels,
                    isMultiSelect = isMultiSelect,
                    subtitle = subtitle,
                    categoryIcon = singleEnabled?.composeIcon ?: defaultIcon,
                    categoryIconRes = singleEnabled?.let { if (it.menuIcon != -1) it.menuIcon else null }
                )
            )
        }

        if (config.APS || config.PUMPCONTROL || config.isEngineeringMode()) {
            addCategory(PluginType.INSULIN, app.aaps.core.ui.R.string.configbuilder_insulin)
        }
        if (!config.AAPSCLIENT) {
            addCategory(PluginType.BGSOURCE, app.aaps.core.ui.R.string.configbuilder_bgsource)
            addCategory(PluginType.SMOOTHING, app.aaps.core.ui.R.string.configbuilder_smoothing)
            addCategory(PluginType.PUMP, app.aaps.core.ui.R.string.configbuilder_pump)
        }
        if (config.APS || config.PUMPCONTROL || config.isEngineeringMode()) {
            addCategory(PluginType.SENSITIVITY, app.aaps.core.ui.R.string.configbuilder_sensitivity)
        }
        if (config.APS) {
            addCategory(PluginType.APS, app.aaps.core.ui.R.string.configbuilder_aps)
            addCategory(PluginType.LOOP, app.aaps.core.ui.R.string.configbuilder_loop)
            addCategory(PluginType.CONSTRAINTS, app.aaps.core.ui.R.string.constraints)
        }
        addCategory(PluginType.SYNC, app.aaps.core.ui.R.string.configbuilder_sync)
        addCategory(PluginType.GENERAL, app.aaps.core.ui.R.string.configbuilder_general)

        pluginLookup = lookup
        return categories
    }

    companion object {

        private fun isMultiSelect(type: PluginType): Boolean =
            type == PluginType.GENERAL ||
                type == PluginType.CONSTRAINTS ||
                type == PluginType.LOOP ||
                type == PluginType.SYNC
    }
}
