package info.nightscout.androidaps.plugins.constraints.objectives

import android.app.Activity
import com.google.common.base.Charsets
import com.google.common.hash.Hashing
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.constraints.objectives.objectives.*
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.OKDialog
import info.nightscout.androidaps.utils.SP
import java.util.*

/**
 * Created by mike on 05.08.2016.
 */
object ObjectivesPlugin : PluginBase(PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .fragmentClass(ObjectivesFragment::class.qualifiedName)
        .alwaysEnabled(Config.APS)
        .showInList(Config.APS)
        .pluginName(R.string.objectives)
        .shortName(R.string.objectives_shortname)
        .description(R.string.description_objectives)), ConstraintsInterface {

    var objectives: MutableList<Objective> = ArrayList()

    val FIRST_OBJECTIVE = 0
    val USAGE_OBJECTIVE = 1
    val EXAM_OBJECTIVE = 2
    val OPENLOOP_OBJECTIVE = 3
    val MAXBASAL_OBJECTIVE = 4
    val MAXIOB_ZERO_CL_OBJECTIVE = 5
    val MAXIOB_OBJECTIVE = 6
    val AUTOSENS_OBJECTIVE = 7
    val AMA_OBJECTIVE = 8
    val SMB_OBJECTIVE = 9

    init {
        convertSP()
        setupObjectives()
    }

    override fun specialEnableCondition(): Boolean {
        val pump = ConfigBuilderPlugin.getPlugin().activePump
        return pump == null || pump.pumpDescription.isTempBasalCapable
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
        if (!SP.contains("Objectives_" + name + "_started")) {
            SP.putLong("Objectives_" + name + "_started", SP.getLong("Objectives" + number + "started", 0L))
            SP.putLong("Objectives_" + name + "_accomplished", SP.getLong("Objectives" + number + "accomplished", 0L))
        }
        // TODO: we can remove Objectives1accomplished sometimes later
    }

    private fun setupObjectives() {
        objectives.clear()
        objectives.add(Objective0())
        objectives.add(Objective1())
        objectives.add(Objective2())
        objectives.add(Objective3())
        objectives.add(Objective4())
        objectives.add(Objective5())
        objectives.add(Objective6())
        objectives.add(Objective7())
        objectives.add(Objective8())
        objectives.add(Objective9())
    }

    fun reset() {
        for (objective in objectives) {
            objective.startedOn = 0
            objective.accomplishedOn = 0
        }
        SP.putBoolean(R.string.key_ObjectivesbgIsAvailableInNS, false)
        SP.putBoolean(R.string.key_ObjectivespumpStatusIsAvailableInNS, false)
        SP.putInt(R.string.key_ObjectivesmanualEnacts, 0)
        SP.putBoolean(R.string.key_objectiveuseprofileswitch, false)
        SP.putBoolean(R.string.key_objectiveusedisconnect, false)
        SP.putBoolean(R.string.key_objectiveusereconnect, false)
        SP.putBoolean(R.string.key_objectiveusetemptarget, false)
        SP.putBoolean(R.string.key_objectiveuseactions, false)
        SP.putBoolean(R.string.key_objectiveuseloop, false)
        SP.putBoolean(R.string.key_objectiveusescale, false)
    }

    fun completeObjectives(activity: Activity, request: String) {
        val requestCode = SP.getString(R.string.key_objectives_request_code, "")
        var url = SP.getString(R.string.key_nsclientinternal_url, "").toLowerCase()
        if (!url.endsWith("\"")) url = "$url/"
        val hashNS = Hashing.sha1().hashString(url + BuildConfig.APPLICATION_ID + "/" + requestCode, Charsets.UTF_8).toString()
        if (request.equals(hashNS.substring(0, 10), ignoreCase = true)) {
            SP.putLong("Objectives_" + "openloop" + "_started", DateUtil.now())
            SP.putLong("Objectives_" + "openloop" + "_accomplished", DateUtil.now())
            SP.putLong("Objectives_" + "maxbasal" + "_started", DateUtil.now())
            SP.putLong("Objectives_" + "maxbasal" + "_accomplished", DateUtil.now())
            SP.putLong("Objectives_" + "maxiobzero" + "_started", DateUtil.now())
            SP.putLong("Objectives_" + "maxiobzero" + "_accomplished", DateUtil.now())
            SP.putLong("Objectives_" + "maxiob" + "_started", DateUtil.now())
            SP.putLong("Objectives_" + "maxiob" + "_accomplished", DateUtil.now())
            SP.putLong("Objectives_" + "autosens" + "_started", DateUtil.now())
            SP.putLong("Objectives_" + "autosens" + "_accomplished", DateUtil.now())
            SP.putLong("Objectives_" + "ama" + "_started", DateUtil.now())
            SP.putLong("Objectives_" + "ama" + "_accomplished", DateUtil.now())
            SP.putLong("Objectives_" + "smb" + "_started", DateUtil.now())
            SP.putLong("Objectives_" + "smb" + "_accomplished", DateUtil.now())
            setupObjectives()
            OKDialog.show(activity, "", MainApp.gs(R.string.codeaccepted), null)
        } else {
            OKDialog.show(activity, "", MainApp.gs(R.string.codeinvalid), null)
        }
    }

    /**
     * Constraints interface
     */
    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!objectives[FIRST_OBJECTIVE].isStarted)
            value.set(false, String.format(MainApp.gs(R.string.objectivenotstarted), FIRST_OBJECTIVE + 1), this)
        return value
    }

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!objectives[MAXIOB_ZERO_CL_OBJECTIVE].isStarted)
            value.set(false, String.format(MainApp.gs(R.string.objectivenotstarted), MAXIOB_ZERO_CL_OBJECTIVE + 1), this)
        return value
    }

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!objectives[AUTOSENS_OBJECTIVE].isStarted)
            value.set(false, String.format(MainApp.gs(R.string.objectivenotstarted), AUTOSENS_OBJECTIVE + 1), this)
        return value
    }

    override fun isAMAModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!objectives[AMA_OBJECTIVE].isStarted)
            value.set(false, String.format(MainApp.gs(R.string.objectivenotstarted), AMA_OBJECTIVE + 1), this)
        return value
    }

    override fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!objectives[SMB_OBJECTIVE].isStarted)
            value.set(false, String.format(MainApp.gs(R.string.objectivenotstarted), SMB_OBJECTIVE + 1), this)
        return value
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        if (objectives[MAXIOB_ZERO_CL_OBJECTIVE].isStarted && !objectives[MAXIOB_ZERO_CL_OBJECTIVE].isAccomplished)
            maxIob.set(0.0, String.format(MainApp.gs(R.string.objectivenotfinished), MAXIOB_ZERO_CL_OBJECTIVE + 1), this)
        return maxIob
    }

}
