package app.aaps.ui.compose.quickLaunch

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.tempTargets.toTTPresets
import app.aaps.core.ui.compose.formatMinutesAsDuration
import app.aaps.ui.compose.tempTarget.toTTPresetsWithNameRes
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardMode
import app.aaps.core.data.model.TT
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.icons.IcTtActivity
import app.aaps.core.ui.compose.icons.IcTtEatingSoon
import app.aaps.core.ui.compose.icons.IcTtHypo
import app.aaps.core.ui.compose.icons.IcTtManual
import app.aaps.core.ui.compose.navigation.descriptionResId
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.labelResId
import app.aaps.ui.compose.navigation.ElementAvailability
import app.aaps.ui.compose.scenes.SceneIcons
import app.aaps.core.interfaces.scenes.SceneStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared resolver for QuickLaunch actions — resolves actions to display items
 * (label, description, icon) and validates dynamic entries.
 * Used by both MainViewModel and QuickLaunchConfigViewModel.
 */
@Singleton
class QuickLaunchResolver @Inject constructor(
    private val preferences: Preferences,
    private val quickWizard: QuickWizard,
    private val automation: Automation,
    private val activePlugin: ActivePlugin,
    private val profileRepository: ProfileRepository,
    private val sceneRepository: SceneStore,
    private val rh: ResourceHelper,
    private val elementAvailability: ElementAvailability
) {

    fun resolveItem(action: QuickLaunchAction): ResolvedQuickLaunchItem {
        if (action is QuickLaunchAction.PluginAction) {
            val plugin = findPlugin(action.className)
            if (plugin != null) return resolvePluginItem(plugin)
        }
        val icon = resolveIcon(action)
        return ResolvedQuickLaunchItem(
            action = action,
            label = resolveLabel(action),
            icon = icon,
            enabled = true,
            description = resolveDescription(action)
        )
    }

    private fun resolveIcon(action: QuickLaunchAction): ImageVector = when (action) {
        is QuickLaunchAction.QuickWizardAction -> quickWizard.get(action.guid)?.let { entry ->
            when (entry.mode()) {
                QuickWizardMode.INSULIN -> IcBolus
                QuickWizardMode.CARBS   -> IcCarbs
                QuickWizardMode.WIZARD  -> action.elementType.icon()
            }
        } ?: action.elementType.icon()

        is QuickLaunchAction.TempTargetPreset  -> {
            val preset = preferences.get(StringNonKey.TempTargetPresets).toTTPresets()
                .find { it.id == action.presetId }
            when (preset?.reason) {
                TT.Reason.ACTIVITY     -> IcTtActivity
                TT.Reason.EATING_SOON  -> IcTtEatingSoon
                TT.Reason.HYPOGLYCEMIA -> IcTtHypo
                else                   -> IcTtManual
            }
        }

        is QuickLaunchAction.SceneAction       -> sceneRepository.getScene(action.sceneId)
            ?.let { SceneIcons.fromKey(it.icon).icon }
            ?: action.elementType.icon()

        else                                   -> action.elementType?.icon() ?: Icons.Default.Extension
    }

    fun isValid(action: QuickLaunchAction): Boolean = when (action) {
        is QuickLaunchAction.QuickWizardAction -> quickWizard.get(action.guid) != null

        is QuickLaunchAction.AutomationAction  -> {
            // Automation executes on master only — invalid (hidden) on a client.
            val event = automation.findEventById(action.automationId)
            automation.executionEnabled && event != null && event.isEnabled
        }

        is QuickLaunchAction.TempTargetPreset  -> {
            val presets = preferences.get(StringNonKey.TempTargetPresets).toTTPresets()
            presets.any { it.id == action.presetId }
        }

        is QuickLaunchAction.ProfileAction     -> {
            val profileList = profileRepository.profile.value?.getProfileList()
            profileList?.any { it.toString() == action.profileName } == true
        }

        is QuickLaunchAction.SceneAction       -> sceneRepository.getScene(action.sceneId)?.isEnabled == true

        is QuickLaunchAction.PluginAction      -> {
            val plugin = findPlugin(action.className)
            plugin != null && plugin.isEnabled(plugin.pluginDescription.mainType) && plugin.hasComposeContent()
        }

        else                                   -> action.elementType?.let { elementAvailability.isAvailable(it) } ?: true
    }

    fun resolveLabel(action: QuickLaunchAction): String = when (action) {
        is QuickLaunchAction.QuickWizardAction -> quickWizard.get(action.guid)?.buttonText() ?: "?"
        is QuickLaunchAction.AutomationAction  -> automation.findEventById(action.automationId)?.title ?: "?"

        is QuickLaunchAction.TempTargetPreset  -> {
            val presets = preferences.get(StringNonKey.TempTargetPresets).toTTPresetsWithNameRes()
            val preset = presets.find { it.id == action.presetId }
            preset?.name ?: preset?.nameRes?.let { rh.gs(it) } ?: "?"
        }

        is QuickLaunchAction.ProfileAction     -> buildProfileLabel(action)
        is QuickLaunchAction.SceneAction       -> sceneRepository.getScene(action.sceneId)?.name ?: "?"
        is QuickLaunchAction.PluginAction      -> findPlugin(action.className)?.let { rh.gs(it.pluginDescription.pluginName) } ?: "?"

        else                                   -> {
            val resId = action.elementType?.labelResId() ?: 0
            if (resId != 0) rh.gs(resId) else action.typeId
        }
    }

    fun resolveDescription(action: QuickLaunchAction): String? = when (action) {
        is QuickLaunchAction.QuickWizardAction -> quickWizard.get(action.guid)?.let { entry ->
            when (entry.mode()) {
                QuickWizardMode.INSULIN -> {
                    val insulin = entry.insulin()
                    if (insulin > 0.0) rh.gs(app.aaps.core.ui.R.string.format_insulin_units, insulin) else null
                }

                QuickWizardMode.CARBS   -> {
                    val carbs = entry.carbs()
                    if (carbs > 0) "${carbs}g" else null
                }

                QuickWizardMode.WIZARD  -> {
                    val carbs = entry.carbs()
                    if (carbs > 0) "${carbs}g" else null
                }
            }
        }

        is QuickLaunchAction.AutomationAction  -> automation.findEventById(action.automationId)
            ?.actionsDescription()?.joinToString(", ")

        is QuickLaunchAction.TempTargetPreset  -> {
            val presets = preferences.get(StringNonKey.TempTargetPresets).toTTPresets()
            val preset = presets.find { it.id == action.presetId }
            preset?.let {
                val durationMin = (it.duration / 60000L).toInt()
                formatMinutesAsDuration(durationMin, rh)
            }
        }

        is QuickLaunchAction.ProfileAction     -> null // label already shows profile name + params
        is QuickLaunchAction.SceneAction       -> {
            val scene = sceneRepository.getScene(action.sceneId)
            scene?.let { "${it.actions.size} actions" }
        }

        is QuickLaunchAction.PluginAction      -> findPlugin(action.className)
            ?.pluginDescription?.description?.takeIf { it != -1 }?.let { rh.gs(it) }

        else                                   -> {
            val resId = action.elementType?.descriptionResId() ?: 0
            if (resId != 0) rh.gs(resId) else null
        }
    }

    fun resolvePluginItem(plugin: PluginBase): ResolvedQuickLaunchItem {
        val action = QuickLaunchAction.PluginAction(plugin.javaClass.simpleName)
        val icon = plugin.pluginDescription.icon ?: Icons.Default.Extension
        val label = rh.gs(plugin.pluginDescription.pluginName)
        val desc = plugin.pluginDescription.description.takeIf { it != -1 }?.let { rh.gs(it) }
        return ResolvedQuickLaunchItem(
            action = action,
            label = label,
            icon = icon,
            enabled = true,
            description = desc
        )
    }

    private fun findPlugin(className: String): PluginBase? =
        activePlugin.getPluginsList().find { it.javaClass.simpleName == className }

    companion object {

        fun buildProfileLabel(action: QuickLaunchAction.ProfileAction): String = buildString {
            append(action.profileName)
            if (action.percentage != 100) append(" ${action.percentage}%")
            if (action.durationMinutes > 0) {
                val hours = action.durationMinutes / 60
                val mins = action.durationMinutes % 60
                append(" ")
                when {
                    hours > 0 && mins > 0 -> append("${hours}h${mins}m")
                    hours > 0             -> append("${hours}h")
                    else                  -> append("${mins}m")
                }
            }
        }
    }
}
