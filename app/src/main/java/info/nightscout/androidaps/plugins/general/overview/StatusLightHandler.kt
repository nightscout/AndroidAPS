package info.nightscout.androidaps.plugins.general.overview

import android.graphics.Color
import android.widget.TextView
import androidx.annotation.StringRes
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.WarnColors
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusLightHandler @Inject constructor(
    private val resourceHelper: ResourceHelper,
    private val sp: SP,
    private val activePlugin: ActivePluginProvider,
    private val warnColors: WarnColors,
    private val config: Config
) {

    /**
     * applies the extended statusLight subview on the overview fragment
     */
    fun updateStatusLights(careportal_cannula_age: TextView?, careportal_insulin_age: TextView?, careportal_reservoir_level: TextView?, careportal_sensor_age: TextView?, careportal_sensor_battery_level: TextView?, careportal_pb_age: TextView?, careportal_battery_level: TextView?, ColorNormal: Int, ColorWarning: Int, ColorAlarm: Int) {
        val pump = activePlugin.activePump
        val bgSource = activePlugin.activeBgSource
        handleAge(careportal_cannula_age, CareportalEvent.SITECHANGE, R.string.key_statuslights_cage_warning, 48.0, R.string.key_statuslights_cage_critical, 72.0, ColorNormal, ColorWarning, ColorAlarm)
        handleAge(careportal_insulin_age, CareportalEvent.INSULINCHANGE, R.string.key_statuslights_iage_warning, 72.0, R.string.key_statuslights_iage_critical, 144.0, ColorNormal, ColorWarning, ColorAlarm)
        handleAge(careportal_sensor_age, CareportalEvent.SENSORCHANGE, R.string.key_statuslights_sage_warning, 216.0, R.string.key_statuslights_sage_critical, 240.0, ColorNormal, ColorWarning, ColorAlarm)
        handleAge(careportal_pb_age, CareportalEvent.PUMPBATTERYCHANGE, R.string.key_statuslights_bage_warning, 216.0, R.string.key_statuslights_bage_critical, 240.0, ColorNormal, ColorWarning, ColorAlarm)
        if (!config.NSCLIENT) {
            if (pump.model() == PumpType.Insulet_Omnipod) {
                handleOmnipodReservoirLevel(careportal_reservoir_level, R.string.key_statuslights_res_critical, 10.0, R.string.key_statuslights_res_warning, 80.0, pump.reservoirLevel, "U" ,ColorNormal, ColorWarning, ColorAlarm)
            } else {
            handleLevel(careportal_reservoir_level, R.string.key_statuslights_res_critical, 10.0, R.string.key_statuslights_res_warning, 80.0, pump.reservoirLevel, "U", ColorNormal, ColorWarning, ColorAlarm)
            }
            if (bgSource.sensorBatteryLevel != -1)
                handleLevel(careportal_sensor_battery_level, R.string.key_statuslights_sbat_critical, 5.0, R.string.key_statuslights_sbat_warning, 20.0, bgSource.sensorBatteryLevel.toDouble(), "%",  ColorNormal, ColorWarning, ColorAlarm)
            else
                careportal_sensor_battery_level?.text = ""
        }

        if (!config.NSCLIENT) {
            if (pump.model() == PumpType.Insulet_Omnipod && pump is OmnipodPumpPlugin) { // instanceof check is needed because at startup, pump can still be VirtualPumpPlugin and that will cause a crash because of the class cast below
                handleOmnipodBatteryLevel(careportal_battery_level, R.string.key_statuslights_bat_critical, 26.0, R.string.key_statuslights_bat_warning, 51.0, pump.batteryLevel.toDouble(), "%", pump.isUseRileyLinkBatteryLevel ,ColorNormal, ColorWarning, ColorAlarm)
            } else if (pump.model() != PumpType.AccuChekCombo) {
                handleLevel(careportal_battery_level, R.string.key_statuslights_bat_critical, 26.0, R.string.key_statuslights_bat_warning, 51.0, pump.batteryLevel.toDouble(), "%",ColorNormal, ColorWarning, ColorAlarm)
            }
        }
    }

    private fun handleAge(view: TextView?, eventName: String, @StringRes warnSettings: Int, defaultWarnThreshold: Double, @StringRes urgentSettings: Int, defaultUrgentThreshold: Double, ColorNormal: Int, ColorWarning: Int, ColorAlarm: Int) {
        val warn = sp.getDouble(warnSettings, defaultWarnThreshold)
        val urgent = sp.getDouble(urgentSettings, defaultUrgentThreshold)
        val careportalEvent = MainApp.getDbHelper().getLastCareportalEvent(eventName)
        if (careportalEvent != null) {
            warnColors.setColorByAge(view, careportalEvent, warn, urgent, ColorNormal, ColorWarning, ColorAlarm)
            view?.text = careportalEvent.age(resourceHelper.shortTextMode(), resourceHelper)
        } else {
            view?.text = if (resourceHelper.shortTextMode()) "-" else resourceHelper.gs(R.string.notavailable)
        }
    }

    private fun handleLevel(view: TextView?, criticalSetting: Int, criticalDefaultValue: Double, warnSetting: Int, warnDefaultValue: Double, level: Double, units: String, ColorNormal: Int, ColorWarning: Int, ColorAlarm: Int) {
        val resUrgent = sp.getDouble(criticalSetting, criticalDefaultValue)
        val resWarn = sp.getDouble(warnSetting, warnDefaultValue)
        @Suppress("SetTextI18n")
        view?.text = " " + DecimalFormatter.to0Decimal(level) + units
        warnColors.setColorInverse(view, level, resWarn, resUrgent, ColorNormal, ColorWarning, ColorAlarm)
    }

    // Omnipod only reports reservoir level when it's 50 units or less, so we display "50+U" for any value > 50
   @Suppress("SameParameterValue")
    private fun handleOmnipodReservoirLevel(view: TextView?, criticalSetting: Int, criticalDefaultValue: Double, warnSetting: Int, warnDefaultValue: Double, level: Double, units: String, ColorNormal: Int, ColorWarning: Int, ColorAlarm: Int) {
        if (level > OmnipodConstants.MAX_RESERVOIR_READING) {
            @Suppress("SetTextI18n")
            view?.text = " 50+$units"
            view?.setTextColor(Color.WHITE)
        } else {
            handleLevel(view, criticalSetting, criticalDefaultValue, warnSetting, warnDefaultValue, level, units, ColorNormal, ColorWarning, ColorAlarm)
        }
    }

     @Suppress("SameParameterValue")
    private fun handleOmnipodBatteryLevel(view: TextView?, criticalSetting: Int, criticalDefaultValue: Double, warnSetting: Int, warnDefaultValue: Double, level: Double, units: String, useRileyLinkBatteryLevel: Boolean, ColorNormal: Int, ColorWarning: Int, ColorAlarm: Int) {
        if (useRileyLinkBatteryLevel) {
            handleLevel(view, criticalSetting, criticalDefaultValue, warnSetting, warnDefaultValue, level, units, ColorNormal, ColorWarning, ColorAlarm)
        } else {
            view?.text = resourceHelper.gs(R.string.notavailable)
            view?.setTextColor(Color.WHITE)
        }
    }
}