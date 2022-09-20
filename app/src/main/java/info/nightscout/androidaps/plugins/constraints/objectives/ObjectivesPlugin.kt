package info.nightscout.androidaps.plugins.constraints.objectives

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.*
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObjectivesPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    private val sp: SP,
    config: Config
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .fragmentClass(ObjectivesFragment::class.qualifiedName)
        .alwaysEnabled(config.APS)
        .showInList(config.APS)
        .pluginIcon(R.drawable.ic_graduation)
        .pluginName(R.string.objectives)
        .shortName(R.string.objectives_shortname)
        .description(R.string.description_objectives),
    aapsLogger, rh, injector
), Constraints {

    var objectives: MutableList<Objective> = ArrayList()

    companion object {

        const val FIRST_OBJECTIVE = 0
        @Suppress("unused") const val USAGE_OBJECTIVE = 1
        @Suppress("unused") const val EXAM_OBJECTIVE = 2
        @Suppress("unused") const val OPENLOOP_OBJECTIVE = 3
        @Suppress("unused") const val MAXBASAL_OBJECTIVE = 4
        const val MAXIOB_ZERO_CL_OBJECTIVE = 5
        @Suppress("unused") const val MAXIOB_OBJECTIVE = 6
        const val AUTOSENS_OBJECTIVE = 7
        const val SMB_OBJECTIVE = 8
        const val AUTO_OBJECTIVE = 9
    }

    public override fun onStart() {
        super.onStart()
        setupObjectives()
    }

    override fun specialEnableCondition(): Boolean =
        activePlugin.activePump.pumpDescription.isTempBasalCapable

    private fun setupObjectives() {
        objectives.clear()
        objectives.add(Objective0(injector))
        objectives.add(Objective1(injector))
        objectives.add(Objective2(injector))
        objectives.add(Objective3(injector))
        objectives.add(Objective4(injector))
        objectives.add(Objective5(injector))
        objectives.add(Objective6(injector))
        objectives.add(Objective7(injector))
        objectives.add(Objective9(injector))
        objectives.add(Objective10(injector))
        // edit companion object if you remove/add Objective
    }

    fun reset() {
        for (objective in objectives) {
            objective.startedOn = 0
            objective.accomplishedOn = 0
        }
        sp.putBoolean(R.string.key_ObjectivesbgIsAvailableInNS, false)
        sp.putBoolean(R.string.key_ObjectivespumpStatusIsAvailableInNS, false)
        sp.putInt(R.string.key_ObjectivesmanualEnacts, 0)
        sp.putBoolean(R.string.key_objectiveuseprofileswitch, false)
        sp.putBoolean(R.string.key_objectiveusedisconnect, false)
        sp.putBoolean(R.string.key_objectiveusereconnect, false)
        sp.putBoolean(R.string.key_objectiveusetemptarget, false)
        sp.putBoolean(R.string.key_objectiveuseactions, false)
        sp.putBoolean(R.string.key_objectiveuseloop, false)
        sp.putBoolean(R.string.key_objectiveusescale, false)
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
        if (!objectives[FIRST_OBJECTIVE].isStarted)
            value.set(aapsLogger, false, rh.gs(R.string.objectivenotstarted, FIRST_OBJECTIVE + 1), this)
        return value
    }

    override fun isLgsAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!objectives[MAXBASAL_OBJECTIVE].isStarted)
            value.set(aapsLogger, false, rh.gs(R.string.objectivenotstarted, MAXBASAL_OBJECTIVE + 1), this)
        return value
    }

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!objectives[MAXIOB_ZERO_CL_OBJECTIVE].isStarted)
            value.set(aapsLogger, false, rh.gs(R.string.objectivenotstarted, MAXIOB_ZERO_CL_OBJECTIVE + 1), this)
        return value
    }

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!objectives[AUTOSENS_OBJECTIVE].isStarted)
            value.set(aapsLogger, false, rh.gs(R.string.objectivenotstarted, AUTOSENS_OBJECTIVE + 1), this)
        return value
    }

    override fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!objectives[SMB_OBJECTIVE].isStarted)
            value.set(aapsLogger, false, rh.gs(R.string.objectivenotstarted, SMB_OBJECTIVE + 1), this)
        return value
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        if (objectives[MAXIOB_ZERO_CL_OBJECTIVE].isStarted && !objectives[MAXIOB_ZERO_CL_OBJECTIVE].isAccomplished)
            maxIob.set(aapsLogger, 0.0, rh.gs(R.string.objectivenotfinished, MAXIOB_ZERO_CL_OBJECTIVE + 1), this)
        return maxIob
    }

    override fun isAutomationEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!objectives[AUTO_OBJECTIVE].isStarted)
            value.set(aapsLogger, false, rh.gs(R.string.objectivenotstarted, AUTO_OBJECTIVE + 1), this)
        return value
    }
}
