package info.nightscout.androidaps.plugins.constraints.objectives

import androidx.fragment.app.FragmentActivity
import com.google.common.base.Charsets
import com.google.common.hash.Hashing
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
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
    private val activePlugin: ActivePluginProvider,
    private val sp: SP,
    private val config: Config

) : PluginBase(PluginDescription()
    .mainType(PluginType.CONSTRAINTS)
    .fragmentClass(ObjectivesFragment::class.qualifiedName)
    .alwaysEnabled(config.APS)
    .showInList(config.APS)
    .pluginName(R.string.objectives)
    .shortName(R.string.objectives_shortname)
    .description(R.string.description_objectives),
    aapsLogger, resourceHelper, injector
), ConstraintsInterface {

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
        convertSP()
        setupObjectives()
    }

    override fun specialEnableCondition(): Boolean {
        return activePlugin.activePump.pumpDescription.isTempBasalCapable
    }

    // convert 2.3 SP version
    private fun convertSP() {
        doConvertSP(0, "config")
        doConvertSP(1, "openloop")
        doConvertSP(2, "maxbasal")
        doConvertSP(3, "maxiobzero")
        doConvertSP(4, "maxiob")
        doConvertSP(5, "autosens")
        doConvertSP(6, "ama")
        doConvertSP(7, "smb")
    }

    private fun doConvertSP(number: Int, name: String) {
        if (!sp.contains("Objectives_" + name + "_started")) {
            sp.putLong("Objectives_" + name + "_started", sp.getLong("Objectives" + number + "started", 0L))
            sp.putLong("Objectives_" + name + "_accomplished", sp.getLong("Objectives" + number + "accomplished", 0L))
        }
        // TODO: we can remove Objectives1accomplished sometimes later
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

    fun completeObjectives(activity: FragmentActivity, request: String) {
        val requestCode = sp.getString(R.string.key_objectives_request_code, "")
        var url = sp.getString(R.string.key_nsclientinternal_url, "").toLowerCase(Locale.getDefault())
        if (!url.endsWith("/")) url = "$url/"
        @Suppress("DEPRECATION") val hashNS = Hashing.sha1().hashString(url + BuildConfig.APPLICATION_ID + "/" + requestCode, Charsets.UTF_8).toString()
        if (request.equals(hashNS.substring(0, 10), ignoreCase = true)) {
            sp.putLong("Objectives_" + "openloop" + "_started", DateUtil.now())
            sp.putLong("Objectives_" + "openloop" + "_accomplished", DateUtil.now())
            sp.putLong("Objectives_" + "maxbasal" + "_started", DateUtil.now())
            sp.putLong("Objectives_" + "maxbasal" + "_accomplished", DateUtil.now())
            sp.putLong("Objectives_" + "maxiobzero" + "_started", DateUtil.now())
            sp.putLong("Objectives_" + "maxiobzero" + "_accomplished", DateUtil.now())
            sp.putLong("Objectives_" + "maxiob" + "_started", DateUtil.now())
            sp.putLong("Objectives_" + "maxiob" + "_accomplished", DateUtil.now())
            sp.putLong("Objectives_" + "autosens" + "_started", DateUtil.now())
            sp.putLong("Objectives_" + "autosens" + "_accomplished", DateUtil.now())
            sp.putLong("Objectives_" + "ama" + "_started", DateUtil.now())
            sp.putLong("Objectives_" + "ama" + "_accomplished", DateUtil.now())
            sp.putLong("Objectives_" + "smb" + "_started", DateUtil.now())
            sp.putLong("Objectives_" + "smb" + "_accomplished", DateUtil.now())
            sp.putLong("Objectives_" + "auto" + "_started", DateUtil.now())
            sp.putLong("Objectives_" + "auto" + "_accomplished", DateUtil.now())
            setupObjectives()
            OKDialog.show(activity, resourceHelper.gs(R.string.objectives), resourceHelper.gs(R.string.codeaccepted))
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
