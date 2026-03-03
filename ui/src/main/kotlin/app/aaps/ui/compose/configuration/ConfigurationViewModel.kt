package app.aaps.ui.compose.configuration

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
import app.aaps.ui.compose.main.DrawerCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ConfigurationUiState(
    val pluginCategories: List<DrawerCategory> = emptyList(),
    val pluginStateVersion: Int = 0,
    val isSimpleMode: Boolean = true,
    val hardwarePumpConfirmation: HardwarePumpConfirmation? = null
)

@Immutable
data class HardwarePumpConfirmation(
    val message: String,
    val plugin: PluginBase,
    val type: PluginType,
    val enabled: Boolean
)

@HiltViewModel
@Stable
class ConfigurationViewModel @Inject constructor(
    private val activePlugin: ActivePlugin,
    private val configBuilder: ConfigBuilder,
    private val config: Config,
    private val preferences: Preferences,
) : ViewModel() {

    val uiState: StateFlow<ConfigurationUiState>
        field = MutableStateFlow(ConfigurationUiState())

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            val categories = buildPluginCategories()
            uiState.update { state ->
                state.copy(
                    pluginCategories = categories,
                    isSimpleMode = preferences.simpleMode
                )
            }
        }
    }

    fun togglePluginEnabled(plugin: PluginBase, type: PluginType, enabled: Boolean) {
        val confirmationMessage = configBuilder.requestPluginSwitch(plugin, enabled, type)
        if (confirmationMessage != null) {
            uiState.update { state ->
                state.copy(
                    hardwarePumpConfirmation = HardwarePumpConfirmation(
                        message = confirmationMessage,
                        plugin = plugin,
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
        configBuilder.confirmPumpPluginSwitch(confirmation.plugin, confirmation.enabled, confirmation.type)
        uiState.update { it.copy(hardwarePumpConfirmation = null) }
        refreshCategories()
    }

    fun dismissHardwarePumpDialog() {
        uiState.update { it.copy(hardwarePumpConfirmation = null) }
        refreshCategories()
    }

    private fun refreshCategories() {
        val categories = buildPluginCategories()
        uiState.update { state ->
            state.copy(
                pluginCategories = categories,
                pluginStateVersion = state.pluginStateVersion + 1
            )
        }
    }

    private fun buildPluginCategories(): List<DrawerCategory> {
        val categories = mutableListOf<DrawerCategory>()

        if (config.APS || config.PUMPCONTROL || config.isEngineeringMode()) {
            activePlugin.getSpecificPluginsVisibleInList(PluginType.INSULIN).takeIf { it.isNotEmpty() }?.let { plugins ->
                categories.add(
                    DrawerCategory(
                        type = PluginType.INSULIN,
                        titleRes = app.aaps.core.ui.R.string.configbuilder_insulin,
                        plugins = plugins,
                        isMultiSelect = DrawerCategory.isMultiSelect(PluginType.INSULIN)
                    )
                )
            }
        }

        if (!config.AAPSCLIENT) {
            activePlugin.getSpecificPluginsVisibleInList(PluginType.BGSOURCE).takeIf { it.isNotEmpty() }?.let { plugins ->
                categories.add(
                    DrawerCategory(
                        type = PluginType.BGSOURCE,
                        titleRes = app.aaps.core.ui.R.string.configbuilder_bgsource,
                        plugins = plugins,
                        isMultiSelect = DrawerCategory.isMultiSelect(PluginType.BGSOURCE)
                    )
                )
            }

            activePlugin.getSpecificPluginsVisibleInList(PluginType.SMOOTHING).takeIf { it.isNotEmpty() }?.let { plugins ->
                categories.add(
                    DrawerCategory(
                        type = PluginType.SMOOTHING,
                        titleRes = app.aaps.core.ui.R.string.configbuilder_smoothing,
                        plugins = plugins,
                        isMultiSelect = DrawerCategory.isMultiSelect(PluginType.SMOOTHING)
                    )
                )
            }

            activePlugin.getSpecificPluginsVisibleInList(PluginType.PUMP).takeIf { it.isNotEmpty() }?.let { plugins ->
                categories.add(
                    DrawerCategory(
                        type = PluginType.PUMP,
                        titleRes = app.aaps.core.ui.R.string.configbuilder_pump,
                        plugins = plugins,
                        isMultiSelect = DrawerCategory.isMultiSelect(PluginType.PUMP)
                    )
                )
            }
        }

        if (config.APS || config.PUMPCONTROL || config.isEngineeringMode()) {
            activePlugin.getSpecificPluginsVisibleInList(PluginType.SENSITIVITY).takeIf { it.isNotEmpty() }?.let { plugins ->
                categories.add(
                    DrawerCategory(
                        type = PluginType.SENSITIVITY,
                        titleRes = app.aaps.core.ui.R.string.configbuilder_sensitivity,
                        plugins = plugins,
                        isMultiSelect = DrawerCategory.isMultiSelect(PluginType.SENSITIVITY)
                    )
                )
            }
        }

        if (config.APS) {
            activePlugin.getSpecificPluginsVisibleInList(PluginType.APS).takeIf { it.isNotEmpty() }?.let { plugins ->
                categories.add(
                    DrawerCategory(
                        type = PluginType.APS,
                        titleRes = app.aaps.core.ui.R.string.configbuilder_aps,
                        plugins = plugins,
                        isMultiSelect = DrawerCategory.isMultiSelect(PluginType.APS)
                    )
                )
            }

            activePlugin.getSpecificPluginsVisibleInList(PluginType.LOOP).takeIf { it.isNotEmpty() }?.let { plugins ->
                categories.add(
                    DrawerCategory(
                        type = PluginType.LOOP,
                        titleRes = app.aaps.core.ui.R.string.configbuilder_loop,
                        plugins = plugins,
                        isMultiSelect = DrawerCategory.isMultiSelect(PluginType.LOOP)
                    )
                )
            }

            activePlugin.getSpecificPluginsVisibleInList(PluginType.CONSTRAINTS).takeIf { it.isNotEmpty() }?.let { plugins ->
                categories.add(
                    DrawerCategory(
                        type = PluginType.CONSTRAINTS,
                        titleRes = app.aaps.core.ui.R.string.constraints,
                        plugins = plugins,
                        isMultiSelect = DrawerCategory.isMultiSelect(PluginType.CONSTRAINTS)
                    )
                )
            }
        }

        activePlugin.getSpecificPluginsVisibleInList(PluginType.SYNC).takeIf { it.isNotEmpty() }?.let { plugins ->
            categories.add(
                DrawerCategory(
                    type = PluginType.SYNC,
                    titleRes = app.aaps.core.ui.R.string.configbuilder_sync,
                    plugins = plugins,
                    isMultiSelect = DrawerCategory.isMultiSelect(PluginType.SYNC)
                )
            )
        }

        activePlugin.getSpecificPluginsVisibleInList(PluginType.GENERAL).takeIf { it.isNotEmpty() }?.let { plugins ->
            categories.add(
                DrawerCategory(
                    type = PluginType.GENERAL,
                    titleRes = app.aaps.core.ui.R.string.configbuilder_general,
                    plugins = plugins,
                    isMultiSelect = DrawerCategory.isMultiSelect(PluginType.GENERAL)
                )
            )
        }

        return categories
    }
}
