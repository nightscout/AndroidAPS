package info.nightscout.androidaps.plugins.constraints.objectives

import androidx.fragment.app.FragmentActivity
import com.google.common.base.Charsets
import com.google.common.hash.Hashing
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.*
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObjectivesPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    private val activePlugin: ActivePlugin,
    private val sp: SP,
    config: Config,
    private val dateUtil: DateUtil,
    private val uel: UserEntryLogger
) : PluginBase(PluginDescription()
    .mainType(PluginType.CONSTRAINTS)
    .fragmentClass(ObjectivesFragment::class.qualifiedName)
    .alwaysEnabled(config.APS)
    .showInList(config.APS)
    .pluginIcon(R.drawable.ic_graduation)
    .pluginName(R.string.objectives)
    .shortName(R.string.objectives_shortname)
    .description(R.string.description_objectives),
    aapsLogger, resourceHelper, injector
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
        const val AMA_OBJECTIVE = 8
        const val SMB_OBJECTIVE = 9
        const val AUTO_OBJECTIVE = 10
    }

    public override fun onStart() {
        super.onStart()
        setupObjectives()
    }

    override fun specialEnableCondition(): Boolean {
        return activePlugin.activePump.pumpDescription.isTempBasalCapable
    }

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
        objectives.add(Objective8(injector))
        objectives.add(Objective9(injector))
        objectives.add(Objective10(injector))
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

    @kotlin.ExperimentalStdlibApi
    fun completeObjectives(activity: FragmentActivity, request: String) {
        val requestCode = sp.getString(R.string.key_objectives_request_code, "")
        var url = sp.getString(R.string.key_nsclientinternal_url, "").lowercase(Locale.getDefault())
        if (!url.endsWith("/")) url = "$url/"
        @Suppress("DEPRECATION", "UnstableApiUsage") val hashNS = Hashing.sha1().hashString(url + BuildConfig.APPLICATION_ID + "/" + requestCode, Charsets.UTF_8).toString()
        if (request.equals(hashNS.substring(0, 10), ignoreCase = true)) {
            sp.putLong("Objectives_" + "openloop" + "_started", dateUtil.now())
            sp.putLong("Objectives_" + "openloop" + "_accomplished", dateUtil.now())
            sp.putLong("Objectives_" + "maxbasal" + "_started", dateUtil.now())
            sp.putLong("Objectives_" + "maxbasal" + "_accomplished", dateUtil.now())
            sp.putLong("Objectives_" + "maxiobzero" + "_started", dateUtil.now())
            sp.putLong("Objectives_" + "maxiobzero" + "_accomplished", dateUtil.now())
            sp.putLong("Objectives_" + "maxiob" + "_started", dateUtil.now())
            sp.putLong("Objectives_" + "maxiob" + "_accomplished", dateUtil.now())
            sp.putLong("Objectives_" + "autosens" + "_started", dateUtil.now())
            sp.putLong("Objectives_" + "autosens" + "_accomplished", dateUtil.now())
            sp.putLong("Objectives_" + "ama" + "_started", dateUtil.now())
            sp.putLong("Objectives_" + "ama" + "_accomplished", dateUtil.now())
            sp.putLong("Objectives_" + "smb" + "_started", dateUtil.now())
            sp.putLong("Objectives_" + "smb" + "_accomplished", dateUtil.now())
            sp.putLong("Objectives_" + "auto" + "_started", dateUtil.now())
            sp.putLong("Objectives_" + "auto" + "_accomplished", dateUtil.now())
            setupObjectives()
            OKDialog.show(activity, resourceHelper.gs(R.string.objectives), resourceHelper.gs(R.string.codeaccepted))
            uel.log(Action.OBJECTIVES_SKIPPED, Sources.Objectives)
        } else {
            OKDialog.show(activity, resourceHelper.gs(R.string.objectives), resourceHelper.gs(R.string.codeinvalid))
        }
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
            value.set(aapsLogger, false, String.format(resourceHelper.gs(R.string.objectivenotstarted), FIRST_OBJECTIVE + 1), this)
        return value
    }

    override fun isLgsAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!objectives[MAXBASAL_OBJECTIVE].isStarted)
            value.set(aapsLogger, false, String.format(resourceHelper.gs(R.string.objectivenotstarted), MAXBASAL_OBJECTIVE + 1), this)
        return value
    }

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!objectives[MAXIOB_ZERO_CL_OBJECTIVE].isStarted)
            value.set(aapsLogger, false, String.format(resourceHelper.gs(R.string.objectivenotstarted), MAXIOB_ZERO_CL_OBJECTIVE + 1), this)
        return value
    }

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!objectives[AUTOSENS_OBJECTIVE].isStarted)
            value.set(aapsLogger, false, String.format(resourceHelper.gs(R.string.objectivenotstarted), AUTOSENS_OBJECTIVE + 1), this)
        return value
    }

    override fun isAMAModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!objectives[AMA_OBJECTIVE].isStarted)
            value.set(aapsLogger, false, String.format(resourceHelper.gs(R.string.objectivenotstarted), AMA_OBJECTIVE + 1), this)
        return value
    }

    override fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!objectives[SMB_OBJECTIVE].isStarted)
            value.set(aapsLogger, false, String.format(resourceHelper.gs(R.string.objectivenotstarted), SMB_OBJECTIVE + 1), this)
        return value
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        if (objectives[MAXIOB_ZERO_CL_OBJECTIVE].isStarted && !objectives[MAXIOB_ZERO_CL_OBJECTIVE].isAccomplished)
            maxIob.set(aapsLogger, 0.0, String.format(resourceHelper.gs(R.string.objectivenotfinished), MAXIOB_ZERO_CL_OBJECTIVE + 1), this)
        return maxIob
    }

    override fun isAutomationEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!objectives[AUTO_OBJECTIVE].isStarted)
            value.set(aapsLogger, false, String.format(resourceHelper.gs(R.string.objectivenotstarted), AUTO_OBJECTIVE + 1), this)
        return value
    }
}
