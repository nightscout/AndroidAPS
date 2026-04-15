package app.aaps.plugins.automation.triggers

import androidx.annotation.VisibleForTesting
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorHeart
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.IconTint
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDouble
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.text.DecimalFormat

class TriggerHeartRate(injector: HasAndroidInjector) : Trigger(injector) {

    @VisibleForTesting val averageHeartRateDurationMillis = 330 * 1000L
    private val minValue = 30
    private val maxValue = 250
    var heartRate: InputDouble = InputDouble(80.0, minValue.toDouble(), maxValue.toDouble(), 10.0, DecimalFormat("1"))
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

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("heartRate", heartRate.value)
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        heartRate.setValue(JsonHelper.safeGetDouble(d, "heartRate"))
        comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!))
        return this
    }

    override fun friendlyName(): Int = R.string.triggerHeartRate

    override fun friendlyDescription(): String =
        rh.gs(R.string.triggerHeartRateDesc, rh.gs(comparator.value.stringRes), heartRate.value)

    override fun composeIcon() = Icons.Filled.MonitorHeart
    override fun composeIconTint() = IconTint.Heart

    override fun duplicate(): Trigger {
        return TriggerHeartRate(injector).also { o ->
            o.heartRate.setValue(heartRate.value)
            o.comparator.setValue(comparator.value)
        }
    }

}
