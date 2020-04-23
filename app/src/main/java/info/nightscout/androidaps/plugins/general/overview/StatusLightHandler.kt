package info.nightscout.androidaps.plugins.general.overview

import android.widget.TextView
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSettingsStatus
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.WarnColors
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusLightHandler @Inject constructor(
    private val nsSettingsStatus: NSSettingsStatus,
    private val resourceHelper: ResourceHelper,
    private val sp: SP,
    private val activePlugin: ActivePluginProvider,
    private val warnColors: WarnColors
) {

    /**
     * applies the extended statusLight subview on the overview fragment
     */
    fun updateStatusLights(careportal_canulaage: TextView?, careportal_insulinage: TextView?, careportal_reservoirlevel: TextView?, careportal_sensorage: TextView?, careportal_pbage: TextView?, careportal_batterylevel: TextView?) {
        val pump = activePlugin.activePump
        handleAge(careportal_canulaage, "cage", CareportalEvent.SITECHANGE, 48.0, 72.0)
        handleAge(careportal_insulinage, "iage", CareportalEvent.INSULINCHANGE, 72.0, 96.0)
        handleAge(careportal_sensorage, "sage", CareportalEvent.SENSORCHANGE, 164.0, 166.0)
        handleAge(careportal_pbage, "bage", CareportalEvent.PUMPBATTERYCHANGE, 224.0, 336.0)
        if (!Config.NSCLIENT)
            handleLevel(careportal_reservoirlevel, R.string.key_statuslights_res_critical, 10.0, R.string.key_statuslights_res_warning, 80.0, pump.reservoirLevel)
        if (!Config.NSCLIENT && pump.model() != PumpType.AccuChekCombo)
            handleLevel(careportal_batterylevel, R.string.key_statuslights_bat_critical, 26.0, R.string.key_statuslights_bat_warning, 51.0, pump.batteryLevel.toDouble())
    }

    private fun handleAge(view: TextView?, nsSettingPlugin: String, eventName: String, defaultWarnThreshold: Double, defaultUrgentThreshold: Double) {
        val urgent = nsSettingsStatus.getExtendedWarnValue(nsSettingPlugin, "urgent", defaultUrgentThreshold)
        val warn = nsSettingsStatus.getExtendedWarnValue(nsSettingPlugin, "warn", defaultWarnThreshold)
        val careportalEvent = MainApp.getDbHelper().getLastCareportalEvent(eventName)
        if (careportalEvent != null) {
            warnColors.setColorByAge(view, careportalEvent, warn, urgent)
            view?.text = careportalEvent.age(resourceHelper.shortTextMode())
        } else {
            view?.text = if (resourceHelper.shortTextMode()) "-" else resourceHelper.gs(R.string.notavailable)
        }
    }

    private fun handleLevel(view: TextView?, criticalSetting: Int, criticalDefaultValue: Double, warnSetting: Int, warnDefaultValue: Double, level: Double) {
        val resUrgent = sp.getDouble(criticalSetting, criticalDefaultValue)
        val resWarn = sp.getDouble(warnSetting, warnDefaultValue)
        @Suppress("SetTextI18n")
        view?.text = DecimalFormatter.to0Decimal(level)
        warnColors.setColorInverse(view, level, resWarn, resUrgent)
    }
}