package app.aaps.plugins.automation.triggers

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorHeart
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.utils.lenientDouble
import app.aaps.core.utils.lenientStringOrNull
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDouble
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TriggerHeartRate(deps: TriggerDeps) : Trigger(deps) {

    // Public only so the test can name the window it feeds samples into. The androidx
    // @VisibleForTesting annotation is Android only, and it was advisory anyway.
    val averageHeartRateDurationMillis = 330 * 1000L
    private val minValue = 30
    private val maxValue = 250
    var heartRate: InputDouble = InputDouble(80.0, minValue.toDouble(), maxValue.toDouble(), 10.0, NumberFormat.INTEGER)
    var comparator: Comparator = Comparator(rh).apply {
        value = Comparator.Compare.IS_EQUAL_OR_GREATER
    }

    override suspend fun shouldRun(): Boolean {
        if (comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
            aapsLogger.info(LTag.AUTOMATION, "HR ready, no limit set ${friendlyDescription()}")
            return true
        }
        val start = dateUtil.now() - averageHeartRateDurationMillis
        val hrs = persistenceLayer.getHeartRatesFromTime(start)
        val duration = hrs.takeUnless { it.isEmpty() }?.sumOf { hr -> hr.duration } ?: 0L
        if (duration == 0L) {
            aapsLogger.info(LTag.AUTOMATION, "HR not ready, no heart rate measured for ${friendlyDescription()}")
            return false
        }
        val hr = hrs.sumOf { hr -> hr.beatsPerMinute * hr.duration } / duration.toDouble()
        return comparator.value.check(hr, heartRate.value).also {
            aapsLogger.info(LTag.AUTOMATION, "HR ${if (it) "" else "not "}ready for $hr for ${friendlyDescription()}")
        }
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("heartRate", heartRate.value)
            put("comparator", comparator.value.toString())
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        heartRate.setValue(d.lenientDouble("heartRate"))
        comparator.setValue(Comparator.Compare.valueOf(d.lenientStringOrNull("comparator")!!))
        return this
    }

    override fun friendlyName(): TextRef = AutomationStrings.triggerHeartRate

    override fun friendlyDescription(): String =
        rh.gs(AutomationStrings.triggerHeartRateDesc, rh.gs(comparator.value.stringRes), heartRate.value)

    override fun composeIcon() = Icons.Filled.MonitorHeart
    override fun elementType() = ElementType.BG_CHECK

    override fun duplicate(): Trigger {
        return TriggerHeartRate(deps).also { o ->
            o.heartRate.setValue(heartRate.value)
            o.comparator.setValue(comparator.value)
        }
    }

}
