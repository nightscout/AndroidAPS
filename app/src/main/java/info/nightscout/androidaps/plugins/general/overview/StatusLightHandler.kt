package info.nightscout.androidaps.plugins.general.overview

import android.graphics.Color
import android.view.View
import android.widget.TextView
import androidx.arch.core.util.Function
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSettingsStatus
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.SetWarnColor
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusLightHandler @Inject constructor(
    private val nsSettingsStatus: NSSettingsStatus,
    private val resourceHelper: ResourceHelper,
    private val sp: SP,
    private val activePlugin: ActivePluginProvider
) {

    /**
     * applies the statusLight subview on the overview fragment
     */
    fun statusLight(cageView: TextView?, iAgeView: TextView?, reservoirView: TextView?,
                    sageView: TextView?, batteryView: TextView?) {
        val pump = activePlugin.activePump
        applyStatusLight("cage", CareportalEvent.SITECHANGE, cageView, "CAN", 48, 72)
        applyStatusLight("iage", CareportalEvent.INSULINCHANGE, iAgeView, "INS", 72, 96)
        val reservoirLevel = if (pump.isInitialized) pump.reservoirLevel else (-1).toDouble()
        applyStatusLightLevel(R.string.key_statuslights_res_critical, 10.0,
            R.string.key_statuslights_res_warning, 80.0, reservoirView, "RES", reservoirLevel)
        applyStatusLight("sage", CareportalEvent.SENSORCHANGE, sageView, "SEN", 164, 166)
        if (pump.model() != PumpType.AccuChekCombo) {
            val batteryLevel = if (pump.isInitialized) pump.batteryLevel.toDouble() else -1.0
            applyStatusLightLevel(R.string.key_statuslights_bat_critical, 5.0,
                R.string.key_statuslights_bat_warning, 22.0,
                batteryView, "BAT", batteryLevel)
        } else {
            applyStatusLight("bage", CareportalEvent.PUMPBATTERYCHANGE, batteryView, "BAT", 224, 336)
        }
    }

    private fun applyStatusLight(nsSettingPlugin: String?, eventName: String?, view: TextView?, text: String?,
                                 defaultWarnThreshold: Int, defaultUrgentThreshold: Int) {
        if (view != null) {
            val urgent = nsSettingsStatus.getExtendedWarnValue(nsSettingPlugin!!, "urgent", defaultUrgentThreshold.toDouble())
            val warn = nsSettingsStatus.getExtendedWarnValue(nsSettingPlugin, "warn", defaultWarnThreshold.toDouble())
            val event = MainApp.getDbHelper().getLastCareportalEvent(eventName)
            val age = event?.hoursFromStart ?: Double.MAX_VALUE
            applyStatusLight(view, text, age, warn, urgent, Double.MAX_VALUE, true)
        }
    }

    private fun applyStatusLightLevel(criticalSetting: Int, criticalDefaultValue: Double,
                                      warnSetting: Int, warnDefaultValue: Double,
                                      view: TextView?, text: String?, level: Double) {
        if (view != null) {
            val resUrgent = sp.getDouble(criticalSetting, criticalDefaultValue)
            val resWarn = sp.getDouble(warnSetting, warnDefaultValue)
            applyStatusLight(view, text, level, resWarn, resUrgent, -1.0, false)
        }
    }

    private fun applyStatusLight(view: TextView, text: String?, value: Double, warnThreshold: Double,
                                 urgentThreshold: Double, invalid: Double, checkAscending: Boolean) {
        val check =
            if (checkAscending) Function { threshold: Double -> value >= threshold }
            else Function { threshold: Double -> value <= threshold }
        if (value != invalid) {
            view.text = text
            when {
                check.apply(urgentThreshold) -> view.setTextColor(resourceHelper.gc(R.color.ribbonCritical))
                check.apply(warnThreshold)   -> view.setTextColor(resourceHelper.gc(R.color.ribbonWarning))
                else                         -> view.setTextColor(resourceHelper.gc(R.color.ribbonDefault))
            }
            view.visibility = View.VISIBLE
        } else {
            view.visibility = View.GONE
        }
    }

    /**
     * applies the extended statusLight subview on the overview fragment
     */
    fun extendedStatusLight(cageView: TextView?, iAgeView: TextView?,
                            reservoirView: TextView?, sageView: TextView?,
                            batteryView: TextView?) {
        val pump = activePlugin.activePump
        handleAge("cage", CareportalEvent.SITECHANGE, cageView, "CAN ",
            48, 72)
        handleAge("iage", CareportalEvent.INSULINCHANGE, iAgeView, "INS ",
            72, 96)
        handleLevel(R.string.key_statuslights_res_critical, 10.0,
            R.string.key_statuslights_res_warning, 80.0,
            reservoirView, "RES ", pump.reservoirLevel)
        handleAge("sage", CareportalEvent.SENSORCHANGE, sageView, "SEN ",
            164, 166)
        if (pump.model() != PumpType.AccuChekCombo) {
            handleLevel(R.string.key_statuslights_bat_critical, 26.0,
                R.string.key_statuslights_bat_warning, 51.0,
                batteryView, "BAT ", pump.batteryLevel.toDouble())
        } else {
            handleAge("bage", CareportalEvent.PUMPBATTERYCHANGE, batteryView, "BAT ",
                224, 336)
        }
    }

    private fun handleAge(nsSettingPlugin: String, eventName: String, view: TextView?, text: String,
                          defaultWarnThreshold: Int, defaultUrgentThreshold: Int) {
        val urgent = nsSettingsStatus.getExtendedWarnValue(nsSettingPlugin, "urgent", defaultUrgentThreshold.toDouble())
        val warn = nsSettingsStatus.getExtendedWarnValue(nsSettingPlugin, "warn", defaultWarnThreshold.toDouble())
        handleAge(view, text, eventName, warn, urgent)
    }

    private fun handleLevel(criticalSetting: Int, criticalDefaultValue: Double,
                            warnSetting: Int, warnDefaultValue: Double,
                            view: TextView?, text: String, level: Double) {
        if (view != null) {
            val resUrgent = sp.getDouble(criticalSetting, criticalDefaultValue)
            val resWarn = sp.getDouble(warnSetting, warnDefaultValue)
            @Suppress("SetTextI18n")
            view.text = text + DecimalFormatter.to0Decimal(level)
            SetWarnColor.setColorInverse(view, level, resWarn, resUrgent)
        }
    }

    private fun handleAge(age: TextView?, eventType: String, warnThreshold: Double, urgentThreshold: Double) =
        handleAge(age, "", eventType, warnThreshold, urgentThreshold)

    fun handleAge(age: TextView?, prefix: String, eventType: String, warnThreshold: Double, urgentThreshold: Double) {
        val notavailable = if (resourceHelper.shortTextMode()) "-" else resourceHelper.gs(R.string.notavailable)
        val careportalEvent = MainApp.getDbHelper().getLastCareportalEvent(eventType)
        if (careportalEvent != null) {
            age?.setTextColor(determineTextColor(careportalEvent, warnThreshold, urgentThreshold))
            age?.text = prefix + careportalEvent.age(resourceHelper.shortTextMode())
        } else {
            age?.text = notavailable
        }
    }

    fun updateAge(sage: TextView?, iage: TextView?, cage: TextView?, pbage: TextView?) {
        val iageUrgent = nsSettingsStatus.getExtendedWarnValue("iage", "urgent", 96.0)
        val iageWarn = nsSettingsStatus.getExtendedWarnValue("iage", "warn", 72.0)
        handleAge(iage, CareportalEvent.INSULINCHANGE, iageWarn, iageUrgent)
        val cageUrgent = nsSettingsStatus.getExtendedWarnValue("cage", "urgent", 72.0)
        val cageWarn = nsSettingsStatus.getExtendedWarnValue("cage", "warn", 48.0)
        handleAge(cage, CareportalEvent.SITECHANGE, cageWarn, cageUrgent)
        val sageUrgent = nsSettingsStatus.getExtendedWarnValue("sage", "urgent", 166.0)
        val sageWarn = nsSettingsStatus.getExtendedWarnValue("sage", "warn", 164.0)
        handleAge(sage, CareportalEvent.SENSORCHANGE, sageWarn, sageUrgent)
        val pbageUrgent = nsSettingsStatus.getExtendedWarnValue("bage", "urgent", 360.0)
        val pbageWarn = nsSettingsStatus.getExtendedWarnValue("bage", "warn", 240.0)
        handleAge(pbage, CareportalEvent.PUMPBATTERYCHANGE, pbageWarn, pbageUrgent)
    }

    fun determineTextColor(careportalEvent: CareportalEvent, warnThreshold: Double, urgentThreshold: Double): Int {
        return if (careportalEvent.isOlderThan(urgentThreshold)) {
            resourceHelper.gc(R.color.low)
        } else if (careportalEvent.isOlderThan(warnThreshold)) {
            resourceHelper.gc(R.color.high)
        } else {
            Color.WHITE
        }
    }
}