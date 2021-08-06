package info.nightscout.androidaps.utils

import android.content.Context
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.transactions.InsertTherapyEventAnnouncementTransaction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class HardLimits @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    private val sp: SP,
    private val resourceHelper: ResourceHelper,
    private val context: Context,
    private val repository: AppRepository
) {

    private val disposable = CompositeDisposable()

    companion object {

        private const val CHILD = 0
        private const val TEENAGE = 1
        private const val ADULT = 2
        private const val RESISTANT_ADULT = 3
        private const val PREGNANT = 4
        private val MAX_BOLUS = doubleArrayOf(5.0, 10.0, 17.0, 25.0, 60.0)

        // Very Hard Limits Ranges
        // First value is the Lowest and second value is the Highest a Limit can define
        val VERY_HARD_LIMIT_MIN_BG = intArrayOf(72, 180)
        val VERY_HARD_LIMIT_MAX_BG = intArrayOf(90, 270)
        val VERY_HARD_LIMIT_TARGET_BG = intArrayOf(80, 200)

        // Very Hard Limits Ranges for Temp Targets
        val VERY_HARD_LIMIT_TEMP_MIN_BG = intArrayOf(72, 180)
        val VERY_HARD_LIMIT_TEMP_MAX_BG = intArrayOf(72, 270)
        val VERY_HARD_LIMIT_TEMP_TARGET_BG = intArrayOf(72, 200)
        val MIN_DIA = doubleArrayOf(5.0, 5.0, 5.0, 5.0, 5.0)
        val MAX_DIA = doubleArrayOf(7.0, 7.0, 7.0, 7.0, 10.0)
        val MIN_IC = doubleArrayOf(2.0, 2.0, 2.0, 2.0, 0.3)
        val MAX_IC = doubleArrayOf(100.0, 100.0, 100.0, 100.0, 100.0)
        const val MIN_ISF = 2.0 // mgdl
        const val MAX_ISF = 720.0 // mgdl
        val MAX_IOB_AMA = doubleArrayOf(3.0, 5.0, 7.0, 12.0, 25.0)
        val MAX_IOB_SMB = doubleArrayOf(3.0, 7.0, 12.0, 25.0, 40.0)
        val MAX_BASAL = doubleArrayOf(2.0, 5.0, 10.0, 12.0, 25.0)

        //LGS Hard limits
        //No IOB at all
        const val MAX_IOB_LGS = 0.0

    }

    private fun loadAge(): Int = when (sp.getString(R.string.key_age, "")) {
        resourceHelper.gs(R.string.key_child)          -> CHILD
        resourceHelper.gs(R.string.key_teenage)        -> TEENAGE
        resourceHelper.gs(R.string.key_adult)          -> ADULT
        resourceHelper.gs(R.string.key_resistantadult) -> RESISTANT_ADULT
        resourceHelper.gs(R.string.key_pregnant)       -> PREGNANT
        else                                           -> ADULT
    }

    fun maxBolus(): Double = MAX_BOLUS[loadAge()]
    fun maxIobAMA(): Double = MAX_IOB_AMA[loadAge()]
    fun maxIobSMB(): Double = MAX_IOB_SMB[loadAge()]
    fun maxBasal(): Double = MAX_BASAL[loadAge()]
    fun minDia(): Double = MIN_DIA[loadAge()]
    fun maxDia(): Double = MAX_DIA[loadAge()]
    fun minIC(): Double = MIN_IC[loadAge()]
    fun maxIC(): Double = MAX_IC[loadAge()]

    // safety checks
    fun checkOnlyHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Boolean =
         value == verifyHardLimits(value, valueName, lowLimit, highLimit)

    fun verifyHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Double {
        var newValue = value
        if (newValue < lowLimit || newValue > highLimit) {
            newValue = max(newValue, lowLimit)
            newValue = min(newValue, highLimit)
            var msg = String.format(resourceHelper.gs(R.string.valueoutofrange), resourceHelper.gs(valueName))
            msg += ".\n"
            msg += String.format(resourceHelper.gs(R.string.valuelimitedto), value, newValue)
            aapsLogger.error(msg)
            disposable += repository.runTransaction(InsertTherapyEventAnnouncementTransaction(msg)).subscribe()
            ToastUtils.showToastInUiThread(context, rxBus, msg, R.raw.error)
        }
        return newValue
    }
}
