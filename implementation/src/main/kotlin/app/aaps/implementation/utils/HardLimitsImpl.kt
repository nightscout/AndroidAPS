package app.aaps.implementation.utils

import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.asAnnouncement
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@Reusable
class HardLimitsImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val notificationManager: NotificationManager,
    private val preferences: Preferences,
    private val rh: ResourceHelper,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val rxBus: RxBus,
    @ApplicationScope private val appScope: CoroutineScope
) : HardLimits {

    private fun loadAge(): HardLimits.AgeType {
        // Read the preference and build the value list once. This runs on every limit lookup, so it
        // must not do the work per candidate age.
        val stored = preferences.get(StringKey.SafetyAge)
        val values = ageEntryValues()
        return HardLimits.AgeType.entries.firstOrNull { values[it.ordinal] == stored } ?: HardLimits.AgeType.ADULT
    }

    // The maps below hold an entry for every AgeType and loadAge() always returns one, so getValue cannot fail.
    override fun maxBolus(): Double = HardLimits.MAX_BOLUS.getValue(loadAge())
    override fun maxIobAMA(): Double = HardLimits.MAX_IOB_AMA.getValue(loadAge())
    override fun maxIobSMB(): Double = HardLimits.MAX_IOB_SMB.getValue(loadAge())
    override fun maxBasal(): Double = HardLimits.MAX_BASAL.getValue(loadAge())
    override fun diaRange(): ClosedFloatingPointRange<Double> = HardLimits.LIMIT_DIA.getValue(loadAge())
    override fun peakRange(): IntRange = HardLimits.LIMIT_PEAK
    override fun icRange(): ClosedFloatingPointRange<Double> = HardLimits.LIMIT_IC.getValue(loadAge())

    // safety checks
    override fun checkHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Boolean =
        value == verifyHardLimits(value, valueName, lowLimit, highLimit)

    override fun checkHardLimits(value: Double, valueName: TextRef, lowLimit: Double, highLimit: Double): Boolean =
        value == verifyHardLimits(value, valueName, lowLimit, highLimit)

    override fun verifyHardLimits(value: Double, valueName: TextRef, lowLimit: Double, highLimit: Double): Double =
        verifyHardLimits(value, rh.gs(valueName), lowLimit, highLimit)

    override fun verifyHardLimits(value: Double, valueName: Int, lowLimit: Double, highLimit: Double): Double =
        verifyHardLimits(value, rh.gs(valueName), lowLimit, highLimit)

    /** Both public forms resolve their name first and share this, so the behaviour cannot drift. */
    private fun verifyHardLimits(value: Double, valueName: String, lowLimit: Double, highLimit: Double): Double {
        var newValue = value
        if (newValue !in lowLimit..highLimit) {
            newValue = max(newValue, lowLimit)
            newValue = min(newValue, highLimit)
            var msg = rh.gs(app.aaps.core.ui.R.string.valueoutofrange, valueName)
            msg += ".\n"
            msg += rh.gs(app.aaps.core.ui.R.string.valuelimitedto, value, newValue)
            aapsLogger.error(msg)
            appScope.launch {
                persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                    therapyEvent = TE.asAnnouncement(msg),
                    timestamp = dateUtil.now(),
                    action = Action.CAREPORTAL,
                    source = Sources.Aaps,
                    note = msg,
                    listValues = listOf(ValueWithUnit.TEType(TE.Type.ANNOUNCEMENT))
                )
            }
            rxBus.send(EventShowSnackbar(msg, EventShowSnackbar.Type.Warning))
            notificationManager.post(NotificationId.TOAST_ALARM, msg, sound = AlarmSound.ERROR)
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