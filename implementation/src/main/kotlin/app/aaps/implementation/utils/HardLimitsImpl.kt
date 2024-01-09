package app.aaps.implementation.utils

import android.content.Context
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.StringKey
import app.aaps.core.objects.extensions.asAnnouncement
import dagger.Reusable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@Reusable
class HardLimitsImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val uiInteraction: UiInteraction,
    private val preferences: Preferences,
    private val rh: ResourceHelper,
    private val context: Context,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil
) : HardLimits {

    private val disposable = CompositeDisposable()

    companion object {

        private const val CHILD = 0
        private const val TEENAGE = 1
        private const val ADULT = 2
        private const val RESISTANT_ADULT = 3
        private const val PREGNANT = 4
    }

    private fun loadAge(): Int = when (preferences.get(StringKey.SafetyAge)) {
        rh.gs(app.aaps.core.utils.R.string.key_child)          -> CHILD
        rh.gs(app.aaps.core.utils.R.string.key_teenage)        -> TEENAGE
        rh.gs(app.aaps.core.utils.R.string.key_adult)          -> ADULT
        rh.gs(app.aaps.core.utils.R.string.key_resistantadult) -> RESISTANT_ADULT
        rh.gs(app.aaps.core.utils.R.string.key_pregnant)       -> PREGNANT
        else                                                   -> ADULT
    }

    override fun maxBolus(): Double = HardLimits.MAX_BOLUS[loadAge()]
    override fun maxIobAMA(): Double = HardLimits.MAX_IOB_AMA[loadAge()]
    override fun maxIobSMB(): Double = HardLimits.MAX_IOB_SMB[loadAge()]
    override fun maxBasal(): Double = HardLimits.MAX_BASAL[loadAge()]
    override fun minDia(): Double = HardLimits.MIN_DIA[loadAge()]
    override fun maxDia(): Double = HardLimits.MAX_DIA[loadAge()]
    override fun minIC(): Double = HardLimits.MIN_IC[loadAge()]
    override fun maxIC(): Double = HardLimits.MAX_IC[loadAge()]

    // safety checks
    override fun checkHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Boolean =
        value == verifyHardLimits(value, valueName, lowLimit, highLimit)

    override fun isInRange(value: Double, lowLimit: Double, highLimit: Double): Boolean =
        value in lowLimit..highLimit

    override fun verifyHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Double {
        var newValue = value
        if (newValue < lowLimit || newValue > highLimit) {
            newValue = max(newValue, lowLimit)
            newValue = min(newValue, highLimit)
            var msg = rh.gs(app.aaps.core.ui.R.string.valueoutofrange, rh.gs(valueName))
            msg += ".\n"
            msg += rh.gs(app.aaps.core.ui.R.string.valuelimitedto, value, newValue)
            aapsLogger.error(msg)
            disposable += persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                therapyEvent = TE.asAnnouncement(msg),
                timestamp = dateUtil.now(),
                action = Action.CAREPORTAL,
                source = Sources.Aaps,
                note = msg,
                listValues = listOf(ValueWithUnit.TEType(TE.Type.ANNOUNCEMENT))
            ).subscribe()
            uiInteraction.showToastAndNotification(context, msg, app.aaps.core.ui.R.raw.error)
        }
        return newValue
    }
}