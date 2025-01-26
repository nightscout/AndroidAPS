package app.aaps.plugins.insulin

import androidx.fragment.app.FragmentActivity
import app.aaps.core.data.iob.Iob
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.Preferences
import app.aaps.core.objects.extensions.fromJson
import app.aaps.core.objects.extensions.toJson
import app.aaps.core.ui.toast.ToastUtils
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.pow

/**
 * Created by Philoul on 29.12.2024.
 *
 *
 */

@Singleton
class InsulinPlugin @Inject constructor(
    val preferences: Preferences,
    rh: ResourceHelper,
    val profileFunction: ProfileFunction,
    val rxBus: RxBus,
    private val sp: SP,
    aapsLogger: AAPSLogger,
    val config: Config,
    val hardLimits: HardLimits,
    val uiInteraction: UiInteraction,
    val uel: UserEntryLogger
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.INSULIN)
        .fragmentClass(InsulinFragment::class.java.name)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_insulin)
        .pluginName(R.string.insulin_plugin)
        .shortName(R.string.insulin_shortname)
        .visibleByDefault(true)
        .neverVisible(config.AAPSCLIENT)
        .description(R.string.description_insulin_plugin),
    aapsLogger, rh
), Insulin {

    override val id = Insulin.InsulinType.UNKNOWN
    override var friendlyName: String = "Insulin"

    override val dia
        get(): Double {
            val dia = userDefinedDia
            return if (dia >= hardLimits.minDia() && dia <= hardLimits.maxDia()) {
                dia
            } else {
                if (dia < hardLimits.minDia())
                    hardLimits.minDia()
                else
                    hardLimits.maxDia()
            }
        }
    override val peak: Int
        get(): Int {
            val peak = userDefinedPeak
            return if (peak >= hardLimits.minPeak() && dia <= hardLimits.maxPeak()) {
                peak.toInt()
            } else {
                if (peak < hardLimits.minPeak())
                    hardLimits.minPeak().toInt()
                else
                    hardLimits.maxDia().toInt()
            }
        }

    override fun setDefault(insulin: ICfg?) {
        insulins.forEachIndexed { index, iCfg ->
            if(iCfg.isEqual(insulin))
                defaultInsulinIndex = index
        }
        currentInsulinIndex = defaultInsulinIndex
    }

    override val iCfg: ICfg
        get() = insulins[defaultInsulinIndex]

    lateinit var currentInsulin: ICfg
    private var lastWarned: Long = 0
    var insulins: ArrayList<ICfg> = ArrayList()
    var defaultInsulinIndex = 0
    var currentInsulinIndex = 0
    val numOfInsulins get() = insulins.size
    var isEdited: Boolean = false
    val userDefinedDia: Double
        get() {
            val profile = profileFunction.getProfile()
            return profile?.dia ?: hardLimits.minDia()
        }
    val userDefinedPeak: Double
        get() {
            val profile = profileFunction.getProfile()
            return profile?.insulinProfile?.peak?.toDouble() ?: hardLimits.minPeak()
        }

    override fun onStart() {
        super.onStart()
        loadSettings()
    }

    override fun insulinList(): ArrayList<CharSequence> {
        val ret = ArrayList<CharSequence>()
        insulins.forEach { ret.add(it.insulinLabel) }
        return ret
    }

    override fun getOrCreateInsulin(iCfg: ICfg): ICfg {
        // First Check insulin with hardlimits, and set default value if not consistent
        if (iCfg.getPeak() < hardLimits.minPeak() || iCfg.getPeak() > hardLimits.maxPeak())
            iCfg.peak = insulins[defaultInsulinIndex].peak
        if (iCfg.getDia() < hardLimits.minDia() || iCfg.getDia() > hardLimits.maxDia())
            iCfg.insulinEndTime = insulins[defaultInsulinIndex].insulinEndTime
        insulins.forEach {
            if (iCfg.isEqual(it))
                return it
        }
        return addNewInsulin(iCfg, true)
    }

    override fun getInsulin(insulinLabel: String): ICfg {
        insulins.forEach {
            if (it.insulinLabel == insulinLabel)
                return it
        }
        aapsLogger.debug(LTag.APS, "Insulin $insulinLabel not found, return default insulin ${insulins[defaultInsulinIndex].insulinLabel}")
        return insulins[defaultInsulinIndex]
    }

    fun insulinTemplateList(): ArrayList<CharSequence> {
        val ret = ArrayList<CharSequence>()
        ret.add(rh.gs(Insulin.InsulinType.OREF_RAPID_ACTING.label))
        ret.add(rh.gs(Insulin.InsulinType.OREF_ULTRA_RAPID_ACTING.label))
        ret.add(rh.gs(Insulin.InsulinType.OREF_LYUMJEV.label))
        ret.add(rh.gs(Insulin.InsulinType.OREF_FREE_PEAK.label))
        return ret
    }

    fun sendShortDiaNotification(dia: Double) {
        // Todo Check if we need this kind of function to send notification
        if (System.currentTimeMillis() - lastWarned > 60 * 1000) {
            lastWarned = System.currentTimeMillis()
            uiInteraction.addNotification(Notification.SHORT_DIA, String.format(notificationPattern, dia, hardLimits.minDia()), Notification.URGENT)
        }
    }
    private val notificationPattern: String
        get() = rh.gs(R.string.dia_too_short)


    fun addNewInsulin(template: ICfg, autoName: Boolean = false): ICfg {
        if (autoName)
            template.insulinLabel = createNewInsulinLabel(template)
        val newInsulin = ICfg(
                insulinLabel = template.insulinLabel,
                peak = template.peak,
                insulinEndTime = template.insulinEndTime
            )
        insulins.add(newInsulin)
        uel.log(Action.NEW_INSULIN, Sources.Insulin, value = ValueWithUnit.SimpleString(newInsulin.insulinLabel))
        currentInsulinIndex = insulins.size - 1
        currentInsulin = newInsulin.deepClone()
        storeSettings()
        return newInsulin
    }

    fun removeCurrentInsulin(activity: FragmentActivity?) {
        // activity included to include PopUp or Toast when Remove can't be done (default insulin or insulin used within profile
        // Todo include Remove authorization and message
        val insulinRemoved = currentInsulin().insulinLabel
        insulins.removeAt(currentInsulinIndex)
        uel.log(Action.INSULIN_REMOVED, Sources.Insulin, value = ValueWithUnit.SimpleString(insulinRemoved))
        currentInsulinIndex = defaultInsulinIndex
        currentInsulin = currentInsulin().deepClone()
        storeSettings()
    }

    fun createNewInsulinLabel(iCfg: ICfg, includingCurrent: Boolean = true): String {
        val template = Insulin.InsulinType.fromPeak(iCfg.peak)
        var insulinLabel = when (template) {
            Insulin.InsulinType.OREF_FREE_PEAK -> "${rh.gs(template.label)}_${iCfg.getPeak()}_${iCfg.getDia()}"
            else                               -> "${rh.gs(template.label)}_${iCfg.getDia()}"
        }
        if (insulinLabelAlreadyExists(insulinLabel, if (includingCurrent) 10000  else currentInsulinIndex)) {
            for (i in 1..10000) {
                if (!insulinLabelAlreadyExists("${insulinLabel}_$i", if (includingCurrent) 10000  else currentInsulinIndex)) {
                    insulinLabel = "${insulinLabel}_$i"
                    break
                }
            }
        }
        return insulinLabel
    }


    @Synchronized
    fun loadSettings() {
        val jsonString = sp.getString(app.aaps.core.utils.R.string.key_insulin_configuration, "")
        try {
            JSONObject(jsonString).let {
                applyConfiguration(it)
            }
        } catch (_: Exception) {
            //
        }
    }

    @Synchronized
    fun storeSettings() {
        sp.putString(app.aaps.core.utils.R.string.key_insulin_configuration, configuration().toString())
    }

    override fun iobCalcForTreatment(bolus: BS, time: Long, iCfg: ICfg): Iob {
        return iobCalc(bolus, time, (iCfg.peak / 60000).toDouble(), (iCfg.insulinEndTime / 3600.0 / 1000.0))
    }

    override fun iobCalcForTreatment(bolus: BS, time: Long, dia: Double): Iob {
        assert(dia != 0.0)
        assert(peak != 0)
        return iobCalc(bolus, time, peak.toDouble(), dia)
        /*
        val result = Iob()
        if (bolus.amount != 0.0) {
            val bolusTime = bolus.timestamp
            val t = (time - bolusTime) / 1000.0 / 60.0
            val td = dia * 60 //getDIA() always >= MIN_DIA
            val tp = peak.toDouble()
            // force the IOB to 0 if over DIA hours have passed
            if (t < td) {
                val tau = tp * (1 - tp / td) / (1 - 2 * tp / td)
                val a = 2 * tau / td
                val s = 1 / (1 - a + (1 + a) * exp(-td / tau))
                result.activityContrib = bolus.amount * (s / tau.pow(2.0)) * t * (1 - t / td) * exp(-t / tau)
                result.iobContrib = bolus.amount * (1 - s * (1 - a) * ((t.pow(2.0) / (tau * td * (1 - a)) - t / tau - 1) * exp(-t / tau) + 1))
            }
        }
        return result
         */
    }

    private fun iobCalc(bolus: BS, time: Long, peak: Double, dia: Double): Iob {
        val result = Iob()
        if (bolus.amount != 0.0) {
            val bolusTime = bolus.timestamp
            val t = (time - bolusTime) / 1000.0 / 60.0
            val td = dia * 60 //getDIA() always >= MIN_DIA
            val tp = peak
            // force the IOB to 0 if over DIA hours have passed
            if (t < td) {
                val tau = tp * (1 - tp / td) / (1 - 2 * tp / td)
                val a = 2 * tau / td
                val s = 1 / (1 - a + (1 + a) * exp(-td / tau))
                result.activityContrib = bolus.amount * (s / tau.pow(2.0)) * t * (1 - t / td) * exp(-t / tau)
                result.iobContrib = bolus.amount * (1 - s * (1 - a) * ((t.pow(2.0) / (tau * td * (1 - a)) - t / tau - 1) * exp(-t / tau) + 1))
            }
        }
        return result
    }

    @Synchronized
    override fun configuration(): JSONObject {
        val json = JSONObject()
        val jsonArray = JSONArray()
        insulins.forEach {
            try {
                jsonArray.put(it.toJson())
            } catch (e: Exception) {
                //
            }
        }
        json.put("insulins", jsonArray)
        json.put("default_insulin", defaultInsulinIndex)
        json.put("current_insulin", currentInsulinIndex)
        return json
    }

    override fun applyConfiguration(configuration: JSONObject) {
        insulins.clear()
        configuration.optJSONArray("insulins")?.let {
            for (index in 0 until (it.length())) {
                try {
                    val o = it.getJSONObject(index)
                    insulins.add(ICfg.fromJson(o))

                } catch (e: Exception) {
                    //
                }
            }
        }
        defaultInsulinIndex = configuration.optInt("default_insulin")
        currentInsulinIndex = configuration.optInt("current_insulin")
    }

    override val comment
        get(): String {
            var comment = commentStandardText()
            val userDia = userDefinedDia
            if (userDia < hardLimits.minDia()) {
                comment += "\n" + rh.gs(R.string.dia_too_short, userDia, hardLimits.minDia())
            }
            return comment
        }

    fun commentStandardText(): String {
        return rh.gs(R.string.insulin_peak_time) + ": " + peak
    }

    @Synchronized
    fun isValidEditState(activity: FragmentActivity?, verbose: Boolean = true): Boolean {
        with(currentInsulin) {
            if (insulinEndTime < hardLimits.minDia() || dia > hardLimits.maxDia()) {
                if (verbose)
                    ToastUtils.errorToast(activity, rh.gs(app.aaps.core.ui.R.string.value_out_of_hard_limits, rh.gs(app.aaps.core.ui.R.string.insulin_dia), dia))
                return false
            }
            if (peak < hardLimits.minPeak() || dia > hardLimits.maxPeak()) {
                if (verbose)
                    ToastUtils.errorToast(activity, rh.gs(app.aaps.core.ui.R.string.value_out_of_hard_limits, rh.gs(app.aaps.core.ui.R.string.insulin_peak), peak))
                return false
            }
            if (insulinLabel.isEmpty()) {
                if (verbose)
                    ToastUtils.errorToast(activity, rh.gs(R.string.missing_insulin_name))
                return false
            }
            // Check Inulin name is unique and insulin parameters is unique
            if (insulinLabelAlreadyExists(this.insulinLabel, currentInsulinIndex)) {
                if (verbose)
                    ToastUtils.errorToast(activity, rh.gs(R.string.insulin_name_exists, insulinLabel))
                return false
            }

            insulins.forEachIndexed { index, iCfg ->
                if (index != currentInsulinIndex) {
                    if (isEqual(iCfg)) {
                        if (verbose)
                            ToastUtils.errorToast(activity, rh.gs(R.string.insulin_duplicated, iCfg.insulinLabel))
                        return false
                    }
                }
            }
        }
        return true
    }

    private fun insulinLabelAlreadyExists(insulinLabel: String, currentIndex: Int): Boolean {
        insulins.forEachIndexed { index, iCfg ->
            if (index != currentIndex) {
                if (iCfg.insulinLabel == insulinLabel) {
                    return true
                }
            }
        }
        return false
    }

    fun currentInsulin(): ICfg = insulins[currentInsulinIndex]
}