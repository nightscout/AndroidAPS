package app.aaps.plugins.insulin

import app.aaps.core.data.iob.Iob
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.Preferences
import app.aaps.core.objects.extensions.toJson
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
    val uiInteraction: UiInteraction
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.INSULIN)
        .fragmentClass(InsulinFragment::class.java.name)
        .pluginIcon(R.drawable.ic_insulin)
        .pluginName(R.string.insulin_plugin)
        .shortName(R.string.insulin_shortname)
        .visibleByDefault(true)
        .neverVisible(config.NSCLIENT)
        .description(R.string.description_insulin_plugin),
    aapsLogger, rh
), Insulin {

    override val id = Insulin.InsulinType.UNKNOWN
    override var friendlyName: String = "Insulin"
    private var lastWarned: Long = 0

    override val dia
        get(): Double {
            val dia = userDefinedDia
            return if (dia >= hardLimits.minDia() && dia <= hardLimits.maxDia()) {
                dia
            } else {
                sendShortDiaNotification(dia)
                if (dia >= hardLimits.minDia())
                    hardLimits.minDia()
                else
                    hardLimits.maxDia()
            }
        }
    private var insulins: ArrayList<ICfg> = ArrayList()
    private var defaultInsulinIndex = 0
    private var currentInsulinIndex = 0

    fun sendShortDiaNotification(dia: Double) {
        if (System.currentTimeMillis() - lastWarned > 60 * 1000) {
            lastWarned = System.currentTimeMillis()
            uiInteraction.addNotification(Notification.SHORT_DIA, String.format(notificationPattern, dia, hardLimits.minDia()), Notification.URGENT)
        }
    }

    private val notificationPattern: String
        get() = rh.gs(R.string.dia_too_short)

    val userDefinedDia: Double
        get() {
            val profile = profileFunction.getProfile()
            return profile?.dia ?: hardLimits.minDia()
        }

    override fun onStart() {
        super.onStart()
        loadSettings()
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

    override val iCfg: ICfg
        get() = insulins[currentInsulinIndex]      // ICfg(friendlyName, (dia * 1000.0 * 3600.0).toLong(), T.mins(peak.toLong()).msecs())

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
        json.put("insulins", jsonArray.toString())
        json.put("default_insulin", defaultInsulinIndex)
        json.put("current_insulin", currentInsulinIndex)
        return json
    }

    override fun applyConfiguration(configuration: JSONObject) {
        configuration.optJSONArray("insulins")?.let {
            for (index in 0 until (it.length() - 1)) {
                try {
                    val o = it.getJSONObject(index)
                    insulins.add(insulinfromJson(o))

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

    override val peak: Int
        get() = preferences.get(IntKey.InsulinOrefPeak)

    fun commentStandardText(): String {
        return rh.gs(R.string.insulin_peak_time) + ": " + peak
    }

    fun insulinfromJson(json: JSONObject): ICfg =
        ICfg(
            insulinLabel = json.optString("insulinLabel", ""),
            insulinEndTime = json.optLong("insulinEndTime", 6 * 3600 * 1000),
            peak = json.optLong("peak", )
        )
}