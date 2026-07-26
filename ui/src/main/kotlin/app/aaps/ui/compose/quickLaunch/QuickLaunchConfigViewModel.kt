package app.aaps.ui.compose.quickLaunch

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.tempTargets.toTTPresets
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.objects.extensions.profileNames
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.ui.compose.pluginCategoryTitleRes
import app.aaps.core.interfaces.scenes.SceneStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@Immutable
data class QuickLaunchConfigUiState(
    val selectedItems: List<ResolvedQuickLaunchItem> = emptyList(),
    val availableStaticItems: List<ResolvedQuickLaunchItem> = emptyList(),
    val availableQuickWizardItems: List<ResolvedQuickLaunchItem> = emptyList(),
    val availableAutomationItems: List<ResolvedQuickLaunchItem> = emptyList(),
    val availableTtPresetItems: List<ResolvedQuickLaunchItem> = emptyList(),
    val availableProfileItems: List<ResolvedQuickLaunchItem> = emptyList(),
    val availableSceneItems: List<ResolvedQuickLaunchItem> = emptyList(),
    val availablePluginGroups: List<PluginGroup> = emptyList()
)

@Immutable
data class PluginGroup(
    val pluginType: PluginType,
    val labelResId: Int,
    val items: List<ResolvedQuickLaunchItem>
)

@HiltViewModel
@Stable
class QuickLaunchConfigViewModel @Inject constructor(
    private val preferences: Preferences,
    private val quickWizard: QuickWizard,
    private val automation: Automation,
    private val activePlugin: ActivePlugin,
    private val profileRepository: ProfileRepository,
    private val sceneRepository: SceneStore,
    private val resolver: QuickLaunchResolver,
    private val visibilityContext: VisibilityContext
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickLaunchConfigUiState())
    val uiState: StateFlow<QuickLaunchConfigUiState> = _uiState.asStateFlow()

    fun loadState() {
        val json = preferences.get(StringNonKey.QuickLaunchActions)
        val selectedActions = QuickLaunchSerializer.fromJson(json)
            .filter { it != QuickLaunchAction.QuickLaunchConfig } // Config button managed separately

        val selectedSet = selectedActions.map { actionKey(it) }.toSet()

        // Resolve selected items
        val selectedResolved = selectedActions.map { resolver.resolveItem(it) }

        // Available static items (not already selected, visible in current mode). isVisibleInMode applies the
        // same ElementVisibility gate as search/Manage — e.g. MASTER_OR_PAIRED_CLIENT hides bolus/carbs/fill on
        // an unpaired client, so they can't be pinned to the toolbar there.
        val availableStatic = QuickLaunchAction.staticActions
            .filter { actionKey(it) !in selectedSet }
            .filter { it.isVisibleInMode() }
            .map { resolver.resolveItem(it) }

        // Available QuickWizard items (not already selected)
        val availableQw = quickWizard.list()
            .map { QuickLaunchAction.QuickWizardAction(it.guid()) }
            .filter { actionKey(it) !in selectedSet }
            .filter { it.isVisibleInMode() }
            .map { resolver.resolveItem(it) }

        // Available Automation items (user actions only). Reads the flow snapshot directly so the
        // filter set is explicit at the call site instead of hidden behind userEvents().
        // Master-only: automation executes on master, so a client cannot add automation quick launches.
        val availableAuto = if (!automation.executionEnabled) emptyList()
        else automation.events.value
            .filter { it.userAction && it.isEnabled }
            .map { QuickLaunchAction.AutomationAction(it.id) }
            .filter { actionKey(it) !in selectedSet }
            .map { resolver.resolveItem(it) }

        // Available TT presets
        val presets = preferences.get(StringNonKey.TempTargetPresets).toTTPresets()
        val availableTt = presets
            .map { QuickLaunchAction.TempTargetPreset(it.id) }
            .filter { actionKey(it) !in selectedSet }
            .filter { it.isVisibleInMode() }
            .map { resolver.resolveItem(it) }

        // Available Profiles (always show all — duplicates with different presets are allowed)
        val profileNames = profileRepository.profileNames()
        val availableProfiles = profileNames
            .map { QuickLaunchAction.ProfileAction(it) }
            .filter { it.isVisibleInMode() }
            .map { resolver.resolveItem(it) }

        // Available Scenes
        val availableScenes = sceneRepository.getScenes()
            .map { QuickLaunchAction.SceneAction(it.id) }
            .filter { actionKey(it) !in selectedSet }
            .filter { it.isVisibleInMode() }
            .map { resolver.resolveItem(it) }

        // Available Plugins — enabled with compose content, grouped by PluginType
        val pluginGroups = buildPluginGroups(selectedSet)

        _uiState.update {
            QuickLaunchConfigUiState(
                selectedItems = selectedResolved,
                availableStaticItems = availableStatic,
                availableQuickWizardItems = availableQw,
                availableAutomationItems = availableAuto,
                availableTtPresetItems = availableTt,
                availableProfileItems = availableProfiles,
                availableSceneItems = availableScenes,
                availablePluginGroups = pluginGroups
            )
        }
    }

    fun addAction(action: QuickLaunchAction) {
        val current = currentActions()
        val updated = current + action
        saveAndReload(updated)
    }

    fun removeActionAt(index: Int) {
        val current = currentActions().toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            saveAndReload(current)
        }
    }

    fun updateProfileActionAt(index: Int, newPercentage: Int, newDuration: Int) {
        val current = currentActions().toMutableList()
        if (index in current.indices && current[index] is QuickLaunchAction.ProfileAction) {
            current[index] = (current[index] as QuickLaunchAction.ProfileAction)
                .copy(percentage = newPercentage, durationMinutes = newDuration)
            saveAndReload(current)
        }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val current = currentActions().toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        saveAndReload(current)
    }

    private fun currentActions(): List<QuickLaunchAction> {
        val json = preferences.get(StringNonKey.QuickLaunchActions)
        return QuickLaunchSerializer.fromJson(json)
            .filter { it != QuickLaunchAction.QuickLaunchConfig }
    }

    private fun saveAndReload(actions: List<QuickLaunchAction>) {
        // Always append ToolbarConfig at the end
        val withConfig = actions + QuickLaunchAction.QuickLaunchConfig
        preferences.put(StringNonKey.QuickLaunchActions, QuickLaunchSerializer.toJson(withConfig))
        loadState()
    }

    private fun buildPluginGroups(selectedSet: Set<String>): List<PluginGroup> {
        val typeOrder = listOf(
            PluginType.PUMP, PluginType.BGSOURCE, PluginType.APS,
            PluginType.SENSITIVITY, PluginType.SMOOTHING, PluginType.CALIBRATION,
            PluginType.CONSTRAINTS,
            PluginType.SYNC, PluginType.GENERAL
        )

        val plugins = activePlugin.getPluginsList()
            // showInList mirrors search/plugin-list gating: a plugin hidden from its list (e.g. VirtualPump on
            // a client) must not be offered as a quick-launch either.
            .filter {
                it.isEnabled(it.pluginDescription.mainType) && it.hasComposeContent() &&
                    it.showInList(it.pluginDescription.mainType)
            }

        return typeOrder.mapNotNull { type ->
            val items = plugins
                .filter { it.pluginDescription.mainType == type }
                .map { plugin -> resolver.resolvePluginItem(plugin) }
                .filter { actionKey(it.action) !in selectedSet }
            if (items.isNotEmpty()) PluginGroup(type, pluginCategoryTitleRes(type), items) else null
        }
    }

    private fun actionKey(action: QuickLaunchAction): String =
        action.dynamicId?.let { "${action.typeId}_$it" } ?: action.typeId

    /**
     * Mode-based visibility for an action's backing element — the same [ElementVisibility] gate search and the
     * Manage sheet use (e.g. MASTER_OR_PAIRED_CLIENT hides command actions on an unpaired client). Actions with no
     * element (plugins) are visible; those are gated by showInList in [buildPluginGroups] instead.
     */
    private fun QuickLaunchAction.isVisibleInMode(): Boolean =
        elementType?.visibility?.isVisible(visibilityContext) ?: true
}
