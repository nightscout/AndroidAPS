package app.aaps.plugins.main.general.overview.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.HandlerThread
import android.widget.TextView
import androidx.annotation.StringRes
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.extensions.runOnUiThread
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.WarnColors
import app.aaps.core.interfaces.pump.defs.PumpType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.database.ValueWrapper
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.impl.AppRepository
import app.aaps.plugins.main.R
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusLightHandler @Inject constructor(
    private val rh: ResourceHelper,
    private val sp: SP,
    private val dateUtil: DateUtil,
    private val activePlugin: ActivePlugin,
    private val warnColors: WarnColors,
    private val config: Config,
    private val repository: AppRepository,
    private val tddCalculator: TddCalculator,
    private val decimalFormatter: DecimalFormatter
) {

    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    /**
     * applies the extended statusLight subview on the overview fragment
     */
    fun updateStatusLights(
        cannulaAge: TextView?,
        cannulaUsage: TextView?,
        insulinAge: TextView?,
        reservoirLevel: TextView?,
        sensorAge: TextView?,
        sensorBatteryLevel: TextView?,
        batteryAge: TextView?,
        batteryLevel: TextView?
    ) {
        val pump = activePlugin.activePump
        val bgSource = activePlugin.activeBgSource
        handleAge(
            cannulaAge,
            TherapyEvent.Type.CANNULA_CHANGE,
            app.aaps.core.utils.R.string.key_statuslights_cage_warning,
            48.0,
            app.aaps.core.utils.R.string.key_statuslights_cage_critical,
            72.0
        )
        handleAge(
            insulinAge,
            TherapyEvent.Type.INSULIN_CHANGE,
            app.aaps.core.utils.R.string.key_statuslights_iage_warning,
            72.0,
            app.aaps.core.utils.R.string.key_statuslights_iage_critical,
            144.0
        )
        handleAge(
            sensorAge,
            TherapyEvent.Type.SENSOR_CHANGE,
            app.aaps.core.utils.R.string.key_statuslights_sage_warning,
            216.0,
            app.aaps.core.utils.R.string.key_statuslights_sage_critical,
            240.0
        )
        if (pump.pumpDescription.isBatteryReplaceable || pump.isBatteryChangeLoggingEnabled()) {
            handleAge(
                batteryAge,
                TherapyEvent.Type.PUMP_BATTERY_CHANGE,
                app.aaps.core.utils.R.string.key_statuslights_bage_warning,
                216.0,
                app.aaps.core.utils.R.string.key_statuslights_bage_critical,
                240.0
            )
        }

        val insulinUnit = rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)
        if (pump.pumpDescription.isPatchPump) {
            handlePatchReservoirLevel(
                reservoirLevel,
                R.string.key_statuslights_res_critical,
                10.0,
                R.string.key_statuslights_res_warning,
                80.0,
                pump.reservoirLevel,
                insulinUnit,
                pump.pumpDescription.maxResorvoirReading.toDouble()
            )
        } else {
            if (cannulaUsage != null) handleUsage(cannulaUsage, insulinUnit)
            handleLevel(reservoirLevel, R.string.key_statuslights_res_critical, 10.0, R.string.key_statuslights_res_warning, 80.0, pump.reservoirLevel, insulinUnit)
        }
        if (!config.NSCLIENT) {
            if (bgSource.sensorBatteryLevel != -1)
                handleLevel(sensorBatteryLevel, R.string.key_statuslights_sbat_critical, 5.0, R.string.key_statuslights_sbat_warning, 20.0, bgSource.sensorBatteryLevel.toDouble(), "%")
            else
                sensorBatteryLevel?.text = ""
        }

        if (!config.NSCLIENT) {
            // The Omnipod Eros does not report its battery level. However, some RileyLink alternatives do.
            // Depending on the user's configuration, we will either show the battery level reported by the RileyLink or "n/a"
            // Pump instance check is needed because at startup, the pump can still be VirtualPumpPlugin and that will cause a crash
            val erosBatteryLinkAvailable = pump.model() == PumpType.OMNIPOD_EROS && pump.isUseRileyLinkBatteryLevel()

            if (pump.model().supportBatteryLevel || erosBatteryLinkAvailable) {
                handleLevel(batteryLevel, R.string.key_statuslights_bat_critical, 26.0, R.string.key_statuslights_bat_warning, 51.0, pump.batteryLevel.toDouble(), "%")
            } else {
                batteryLevel?.text = rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)
                batteryLevel?.setTextColor(rh.gac(batteryLevel.context, app.aaps.core.ui.R.attr.defaultTextColor))
            }
        }
    }

    private fun handleAge(view: TextView?, type: TherapyEvent.Type, @StringRes warnSettings: Int, defaultWarnThreshold: Double, @StringRes urgentSettings: Int, defaultUrgentThreshold: Double) {
        val warn = sp.getDouble(warnSettings, defaultWarnThreshold)
        val urgent = sp.getDouble(urgentSettings, defaultUrgentThreshold)
        val therapyEvent = repository.getLastTherapyRecordUpToNow(type).blockingGet()
        if (therapyEvent is ValueWrapper.Existing) {
            warnColors.setColorByAge(view, therapyEvent.value, warn, urgent)
            view?.text = therapyEvent.value.age(rh.shortTextMode(), rh, dateUtil)
        } else {
            view?.text = if (rh.shortTextMode()) "-" else rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun handleLevel(view: TextView?, criticalSetting: Int, criticalDefaultValue: Double, warnSetting: Int, warnDefaultValue: Double, level: Double, units: String) {
        val resUrgent = sp.getDouble(criticalSetting, criticalDefaultValue)
        val resWarn = sp.getDouble(warnSetting, warnDefaultValue)
        if (level > 0) view?.text = " " + decimalFormatter.to0Decimal(level, units)
        else view?.text = ""
        warnColors.setColorInverse(view, level, resWarn, resUrgent)
    }

    // Omnipod only reports reservoir level when it's 50 units or less, so we display "50+U" for any value > 50
    @Suppress("SameParameterValue")
    private fun handlePatchReservoirLevel(
        view: TextView?, criticalSetting: Int, criticalDefaultValue: Double, warnSetting: Int,
        warnDefaultValue: Double, level: Double, units: String, maxReading: Double
    ) {
        if (level >= maxReading) {
            view?.text = decimalFormatter.to0Decimal(maxReading, units)
            view?.setTextColor(rh.gac(view.context, app.aaps.core.ui.R.attr.defaultTextColor))
        } else {
            handleLevel(view, criticalSetting, criticalDefaultValue, warnSetting, warnDefaultValue, level, units)
        }
    }

    private fun handleUsage(view: TextView?, units: String) {
        handler.post {
            val therapyEvent = repository.getLastTherapyRecordUpToNow(TherapyEvent.Type.CANNULA_CHANGE).blockingGet()
            var usage =
                if (therapyEvent is ValueWrapper.Existing) {
                    tddCalculator.calculate(therapyEvent.value.timestamp, dateUtil.now(), allowMissingData = false)?.totalAmount ?: 0.0
                } else 0.0
            runOnUiThread {
                view?.text = decimalFormatter.to0Decimal(usage, units)
            }
        }
    }

    private fun TherapyEvent.age(useShortText: Boolean, rh: ResourceHelper, dateUtil: DateUtil): String {
        val diff = dateUtil.computeDiff(timestamp, System.currentTimeMillis())
        var days = " " + rh.gs(app.aaps.core.interfaces.R.string.days) + " "
        var hours = " " + rh.gs(app.aaps.core.interfaces.R.string.hours) + " "
        if (useShortText) {
            days = rh.gs(app.aaps.core.interfaces.R.string.shortday)
            hours = rh.gs(app.aaps.core.interfaces.R.string.shorthour)
        }
        return diff[TimeUnit.DAYS].toString() + days + diff[TimeUnit.HOURS] + hours
    }
}
