package info.nightscout.androidaps.utils

import android.content.Context
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HardLimits @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    private val sp: SP,
    private val resourceHelper: ResourceHelper,
    private val context: Context,
    private val nsUpload: NSUpload
) {

    val CHILD = 0
    val TEENAGE = 1
    val ADULT = 2
    val RESISTANTADULT = 3
    val PREGNANT = 4
    val MAXBOLUS = doubleArrayOf(5.0, 10.0, 17.0, 25.0, 60.0)

    // Very Hard Limits Ranges
    // First value is the Lowest and second value is the Highest a Limit can define
    val VERY_HARD_LIMIT_MIN_BG = intArrayOf(72, 180)
    val VERY_HARD_LIMIT_MAX_BG = intArrayOf(90, 270)
    val VERY_HARD_LIMIT_TARGET_BG = intArrayOf(80, 200)

    // Very Hard Limits Ranges for Temp Targets
    val VERY_HARD_LIMIT_TEMP_MIN_BG = intArrayOf(72, 180)
    val VERY_HARD_LIMIT_TEMP_MAX_BG = intArrayOf(72, 270)
    val VERY_HARD_LIMIT_TEMP_TARGET_BG = intArrayOf(72, 200)
    val MINDIA = doubleArrayOf(5.0, 5.0, 5.0, 5.0, 5.0)
    val MAXDIA = doubleArrayOf(7.0, 7.0, 7.0, 7.0, 10.0)
    val MINIC = doubleArrayOf(2.0, 2.0, 2.0, 2.0, 0.3)
    val MAXIC = doubleArrayOf(100.0, 100.0, 100.0, 100.0, 100.0)
    val MINISF = 2.0 // mgdl
    val MAXISF = 720.0 // mgdl
    val MAXIOB_AMA = doubleArrayOf(3.0, 5.0, 7.0, 12.0, 25.0)
    val MAXIOB_SMB = doubleArrayOf(3.0, 7.0, 12.0, 25.0, 40.0)
    val MAXBASAL = doubleArrayOf(2.0, 5.0, 10.0, 12.0, 25.0)

    //LGS Hard limits
    //No IOB at all
    val MAXIOB_LGS = 0.0

    private fun loadAge(): Int {
        val sp_age = sp.getString(R.string.key_age, "")
        val age: Int
        age = if (sp_age == resourceHelper.gs(R.string.key_child)) CHILD
        else if (sp_age == resourceHelper.gs(R.string.key_teenage)) TEENAGE
        else if (sp_age == resourceHelper.gs(R.string.key_adult)) ADULT
        else if (sp_age == resourceHelper.gs(R.string.key_resistantadult)) RESISTANTADULT
        else if (sp_age == resourceHelper.gs(R.string.key_pregnant)) PREGNANT
        else ADULT
        return age
    }

    fun maxBolus(): Double {
        return MAXBOLUS[loadAge()]
    }

    fun maxIobAMA(): Double {
        return MAXIOB_AMA[loadAge()]
    }

    fun maxIobSMB(): Double {
        return MAXIOB_SMB[loadAge()]
    }

    fun maxBasal(): Double {
        return MAXBASAL[loadAge()]
    }

    fun minDia(): Double {
        return MINDIA[loadAge()]
    }

    fun maxDia(): Double {
        return MAXDIA[loadAge()]
    }

    fun minIC(): Double {
        return MINIC[loadAge()]
    }

    fun maxIC(): Double {
        return MAXIC[loadAge()]
    }

    // safety checks
    fun checkOnlyHardLimits(value: Double, valueName: String?, lowLimit: Double, highLimit: Double): Boolean {
        return value == verifyHardLimits(value, valueName, lowLimit, highLimit)
    }

    fun verifyHardLimits(value: Double, valueName: String?, lowLimit: Double, highLimit: Double): Double {
        var newvalue = value
        if (newvalue < lowLimit || newvalue > highLimit) {
            newvalue = Math.max(newvalue, lowLimit)
            newvalue = Math.min(newvalue, highLimit)
            var msg = String.format(resourceHelper.gs(R.string.valueoutofrange), valueName)
            msg += ".\n"
            msg += String.format(resourceHelper.gs(R.string.valuelimitedto), value, newvalue)
            aapsLogger.error(msg)
            nsUpload.uploadError(msg)
            ToastUtils.showToastInUiThread(context, rxBus, msg, R.raw.error)
        }
        return newvalue
    }
}
