package info.nightscout.androidaps.utils

import android.content.Context
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.transactions.InsertTherapyEventAnnouncementTransaction
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@OpenForTesting
@Singleton
class HardLimits @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val sp: SP,
    private val rh: ResourceHelper,
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
        val VERY_HARD_LIMIT_MIN_BG = doubleArrayOf(80.0, 180.0)
        val VERY_HARD_LIMIT_MAX_BG = doubleArrayOf(90.0, 200.0)
        val VERY_HARD_LIMIT_TARGET_BG = doubleArrayOf(80.0, 200.0)

        // Very Hard Limits Ranges for Temp Targets
        val VERY_HARD_LIMIT_TEMP_MIN_BG = intArrayOf(72, 180)
        val VERY_HARD_LIMIT_TEMP_MAX_BG = intArrayOf(72, 270)
        val VERY_HARD_LIMIT_TEMP_TARGET_BG = intArrayOf(72, 200)
        val MIN_DIA = doubleArrayOf(5.0, 5.0, 5.0, 5.0, 5.0)
        val MAX_DIA = doubleArrayOf(9.0, 9.0, 9.0, 9.0, 10.0)
        val MIN_IC = doubleArrayOf(2.0, 2.0, 2.0, 2.0, 0.3)
        val MAX_IC = doubleArrayOf(100.0, 100.0, 100.0, 100.0, 100.0)
        const val MIN_ISF = 2.0 // mgdl
        const val MAX_ISF = 720.0 // mgdl
        val MAX_IOB_AMA = doubleArrayOf(3.0, 5.0, 7.0, 12.0, 25.0)
        val MAX_IOB_SMB = doubleArrayOf(7.0, 13.0, 22.0, 30.0, 70.0)
        val MAX_BASAL = doubleArrayOf(2.0, 5.0, 10.0, 12.0, 25.0)

        //LGS Hard limits
        //No IOB at all
        const val MAX_IOB_LGS = 0.0

    }

    private fun loadAge(): Int = when (sp.getString(R.string.key_age, "")) {
        rh.gs(R.string.key_child)          -> CHILD
        rh.gs(R.string.key_teenage)        -> TEENAGE
        rh.gs(R.string.key_adult)          -> ADULT
        rh.gs(R.string.key_resistantadult) -> RESISTANT_ADULT
        rh.gs(R.string.key_pregnant)       -> PREGNANT
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
    fun checkHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Boolean =
         value == verifyHardLimits(value, valueName, lowLimit, highLimit)

    fun isInRange(value: Double, lowLimit: Double, highLimit: Double): Boolean =
        value in lowLimit..highLimit

    fun verifyHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Double {
        var newValue = value
        if (newValue < lowLimit || newValue > highLimit) {
            newValue = max(newValue, lowLimit)
            newValue = min(newValue, highLimit)
            var msg = rh.gs(R.string.valueoutofrange, rh.gs(valueName))
            msg += ".\n"
            msg += rh.gs(R.string.valuelimitedto, value, newValue)
            aapsLogger.error(msg)
            disposable += repository.runTransaction(InsertTherapyEventAnnouncementTransaction(msg)).subscribe()
            ToastUtils.showToastInUiThread(context, rxBus, msg, R.raw.error)
        }
        return newValue
    }
}