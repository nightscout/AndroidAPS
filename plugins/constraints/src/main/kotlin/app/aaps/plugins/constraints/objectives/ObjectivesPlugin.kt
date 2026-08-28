package app.aaps.plugins.constraints.objectives

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.Objectives
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
     *
     * SHTF eternal build: objectives gating disabled intentionally. See docs/SHTF_LOOP_RESILIENCE_PLAN.md
     * Stock behavior: each method below vetoed loop invocation / closed loop / SMB / autosens /
     * automation until the corresponding objective was started, and forced LGS while the LGS
     * objective was in progress. The owner has completed all objectives on the primary device;
     * this offline spare must never lose looping to objective state (including the stock code
     * path that silently wipes objective progress when the device clock is behind the stored
     * timestamps, unrecoverable offline because restarting an objective requires an internet
     * time check). All vetoes are therefore no-ops.
     */
    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> = value

    override fun isLgsForced(value: Constraint<Boolean>): Constraint<Boolean> = value

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> = value

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value

    override fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value

    override fun isAutomationEnabled(value: Constraint<Boolean>): Constraint<Boolean> = value

    // SHTF eternal build: report every objective as started/accomplished so nothing else
    // (e.g. the setup wizard's objectives screen) ever blocks on objective progress.
    override fun isAccomplished(index: Int) = true
    override fun isStarted(index: Int): Boolean = true
}
