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
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
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

    private fun loadAge(): Int = when (preferences.get(StringKey.SafetyAge)) {
        ageEntryValues()[HardLimits.AgeType.CHILD.ordinal]           -> HardLimits.AgeType.CHILD.ordinal
        ageEntryValues()[HardLimits.AgeType.TEENAGE.ordinal]         -> HardLimits.AgeType.TEENAGE.ordinal
        ageEntryValues()[HardLimits.AgeType.ADULT.ordinal]           -> HardLimits.AgeType.ADULT.ordinal
        ageEntryValues()[HardLimits.AgeType.RESISTANT_ADULT.ordinal] -> HardLimits.AgeType.RESISTANT_ADULT.ordinal
        ageEntryValues()[HardLimits.AgeType.PREGNANT.ordinal]        -> HardLimits.AgeType.PREGNANT.ordinal
        else                                                         -> HardLimits.AgeType.ADULT.ordinal
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

    override fun ageEntries() = arrayOf<CharSequence>(
        rh.gs(app.aaps.core.ui.R.string.child),
        rh.gs(app.aaps.core.ui.R.string.teenage),
        rh.gs(app.aaps.core.ui.R.string.adult),
        rh.gs(app.aaps.core.ui.R.string.resistant_adult),
        rh.gs(app.aaps.core.ui.R.string.pregnant),
    )

    override fun ageEntryValues() = arrayOf<CharSequence>(
        "child",
        "teenage",
        "adult",
        "resistantadult",
        "pregnant"
    )
}