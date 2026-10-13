package app.aaps.plugins.constraints.objectives

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.constraints.Objectives.Companion.AUTOSENS_OBJECTIVE
import app.aaps.core.interfaces.constraints.Objectives.Companion.AUTO_OBJECTIVE
import app.aaps.core.interfaces.constraints.Objectives.Companion.CLOSED_LOOP_OBJECTIVE
import app.aaps.core.interfaces.constraints.Objectives.Companion.FIRST_OBJECTIVE
import app.aaps.core.interfaces.constraints.Objectives.Companion.LGS_OBJECTIVE
import app.aaps.core.interfaces.constraints.Objectives.Companion.SMB_OBJECTIVE
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.R
import app.aaps.plugins.constraints.objectives.keys.ObjectivesBooleanComposedKey
import app.aaps.plugins.constraints.objectives.keys.ObjectivesLongComposedKey
import app.aaps.plugins.constraints.objectives.objectives.Objective
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObjectivesPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    config: Config,
    val objectives: List<@JvmSuppressWildcards Objective>
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .fragmentClass(ObjectivesFragment::class.qualifiedName)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_graduation)
        .pluginName(app.aaps.core.ui.R.string.objectives)
        .shortName(R.string.objectives_shortname)
        .enableByDefault(config.APS)
        .description(R.string.description_objectives),
    ownPreferences = listOf(ObjectivesBooleanComposedKey::class.java, ObjectivesLongComposedKey::class.java),
    aapsLogger, rh, preferences
), PluginConstraints, Objectives {

    fun reset() {
        for (objective in objectives) {
            objective.startedOn = 0
            objective.accomplishedOn = 0
        }
        preferences.put(BooleanNonKey.ObjectivesBgIsAvailableInNs, false)
        preferences.put(BooleanNonKey.ObjectivesPumpStatusIsAvailableInNS, false)
        preferences.put(IntNonKey.ObjectivesManualEnacts, 0)
        preferences.put(BooleanNonKey.ObjectivesProfileSwitchUsed, false)
        preferences.put(BooleanNonKey.ObjectivesDisconnectUsed, false)
        preferences.put(BooleanNonKey.ObjectivesReconnectUsed, false)
        preferences.put(BooleanNonKey.ObjectivesTempTargetUsed, false)
        preferences.put(BooleanNonKey.ObjectivesActionsUsed, false)
        preferences.put(BooleanNonKey.ObjectivesLoopUsed, false)
        preferences.put(BooleanNonKey.ObjectivesScaleUsed, false)
    }

    fun allPriorAccomplished(position: Int): Boolean {
        var accomplished = true
        for (i in 0 until position) {
            accomplished = accomplished && objectives[i].isAccomplished
        }
        return accomplished
    }

    /**
     * Constraints interface
     */
    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        // Check if initialized
        if (objectives.isEmpty()) return value
        if (!objectives[FIRST_OBJECTIVE].isStarted)
            value.set(false, rh.gs(R.string.objectivenotstarted, FIRST_OBJECTIVE + 1), this)
        return value
    }

    override fun isLgsForced(value: Constraint<Boolean>): Constraint<Boolean> {
        // Check if initialized
        if (objectives.isEmpty()) return value
        if (objectives[LGS_OBJECTIVE].isStarted && !objectives[LGS_OBJECTIVE].isAccomplished)
            value.set(true, rh.gs(R.string.objectivenotfinished, LGS_OBJECTIVE + 1), this)
        return value
    }

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        // Check if initialized
        if (objectives.isEmpty()) return value
        if (!objectives[CLOSED_LOOP_OBJECTIVE].isStarted)
            value.set(false, rh.gs(R.string.objectivenotstarted, CLOSED_LOOP_OBJECTIVE + 1), this)
        return value
    }

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        // Check if initialized
        if (objectives.isEmpty()) return value
        if (!objectives[AUTOSENS_OBJECTIVE].isStarted)
            value.set(false, rh.gs(R.string.objectivenotstarted, AUTOSENS_OBJECTIVE + 1), this)
        return value
    }

    override fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        // Check if initialized
        if (objectives.isEmpty()) return value
        if (!objectives[SMB_OBJECTIVE].isStarted)
            value.set(false, rh.gs(R.string.objectivenotstarted, SMB_OBJECTIVE + 1), this)
        return value
    }

    override fun isAutomationEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        // Check if initialized
        if (objectives.isEmpty()) return value
        if (!objectives[AUTO_OBJECTIVE].isStarted)
            value.set(false, rh.gs(R.string.objectivenotstarted, AUTO_OBJECTIVE + 1), this)
        return value
    }

    override fun isAccomplished(index: Int) = objectives[index].isAccomplished
    override fun isStarted(index: Int): Boolean = objectives[index].isStarted
}
