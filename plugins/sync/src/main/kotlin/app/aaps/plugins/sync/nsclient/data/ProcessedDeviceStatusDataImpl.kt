package app.aaps.plugins.sync.nsclient.data

import android.text.Spanned
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.interfaces.utils.T
import app.aaps.core.utils.HtmlHelper
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.sync.R
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessedDeviceStatusDataImpl @Inject constructor(
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val sp: SP,
    private val instantiator: Instantiator
) : ProcessedDeviceStatusData {

    override var pumpData: ProcessedDeviceStatusData.PumpData? = null

    override var device: ProcessedDeviceStatusData.Device? = null

    override val uploaderMap = HashMap<String, ProcessedDeviceStatusData.Uploader>()

    override var openAPSData = ProcessedDeviceStatusData.OpenAPSData()

    // test warning level // color
    override fun pumpStatus(nsSettingsStatus: NSSettingsStatus): Spanned {

        //String[] ALL_STATUS_FIELDS = {"reservoir", "battery", "clock", "status", "device"};
        val string = StringBuilder()
            .append("<span style=\"color:${rh.gac(app.aaps.core.ui.R.attr.nsTitleColor)}\">")
            .append(rh.gs(app.aaps.core.ui.R.string.pump))
            .append(": </span>")

        val pumpData = pumpData ?: return HtmlHelper.fromHtml(string.toString())

        // test warning level
        val level = when {
            pumpData.clock + nsSettingsStatus.extendedPumpSettings("urgentClock") * 60 * 1000L < dateUtil.now()                    -> ProcessedDeviceStatusData.Levels.URGENT
            pumpData.reservoir < nsSettingsStatus.extendedPumpSettings("urgentRes")                                                -> ProcessedDeviceStatusData.Levels.URGENT
            pumpData.isPercent && pumpData.percent < nsSettingsStatus.extendedPumpSettings("urgentBattP")                          -> ProcessedDeviceStatusData.Levels.URGENT
            !pumpData.isPercent && pumpData.voltage > 0 && pumpData.voltage < nsSettingsStatus.extendedPumpSettings("urgentBattV") -> ProcessedDeviceStatusData.Levels.URGENT
            pumpData.clock + nsSettingsStatus.extendedPumpSettings("warnClock") * 60 * 1000L < dateUtil.now()                      -> ProcessedDeviceStatusData.Levels.WARN
            pumpData.reservoir < nsSettingsStatus.extendedPumpSettings("warnRes")                                                  -> ProcessedDeviceStatusData.Levels.WARN
            pumpData.isPercent && pumpData.percent < nsSettingsStatus.extendedPumpSettings("warnBattP")                            -> ProcessedDeviceStatusData.Levels.WARN
            !pumpData.isPercent && pumpData.voltage > 0 && pumpData.voltage < nsSettingsStatus.extendedPumpSettings("warnBattV")   -> ProcessedDeviceStatusData.Levels.WARN
            else                                                                                                                   -> ProcessedDeviceStatusData.Levels.INFO
        }
        string.append("<span style=\"color:${level.toColor()}\">")
        // val insulinUnit = rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)
        // val fields = nsSettingsStatus.pumpExtendedSettingsFields()
        // Removed here. Same value is in StatusLights
        // if (pumpData.reservoirDisplayOverride != "") string.append(pumpData.reservoirDisplayOverride).append("$insulinUnit ")
        // else if (fields.contains("reservoir")) string.append(pumpData.reservoir.toInt()).append("$insulinUnit ")
        if (pumpData.isPercent) string.append(pumpData.percent).append("% ")
        if (!pumpData.isPercent && pumpData.voltage > 0) string.append(Round.roundTo(pumpData.voltage, 0.001)).append(" ")
        string.append(dateUtil.minAgo(rh, pumpData.clock)).append(" ")
        string.append(pumpData.status).append(" ")
        //string.append(device).append(" ")
        string.append("</span>") // color
        return HtmlHelper.fromHtml(string.toString())
    }

    override val extendedPumpStatus: Spanned get() = pumpData?.extended ?: HtmlHelper.fromHtml("")
    override val extendedOpenApsStatus: Spanned
        get() {
            val string = StringBuilder()
            val enacted = openAPSData.enacted
            val suggested = openAPSData.suggested
            if (enacted != null && openAPSData.clockEnacted != openAPSData.clockSuggested) string
                .append("<b>")
                .append(dateUtil.minAgo(rh, openAPSData.clockEnacted))
                .append("</b> ")
                .append(JsonHelper.safeGetString(enacted, "reason"))
                .append("<br>")
            if (suggested != null) string
                .append("<b>")
                .append(dateUtil.minAgo(rh, openAPSData.clockSuggested))
                .append("</b> ")
                .append(JsonHelper.safeGetString(suggested, "reason"))
                .append("<br>")
            return HtmlHelper.fromHtml(string.toString())
        }

    override val openApsStatus: Spanned
        get() {
            val string = StringBuilder()
                .append("<span style=\"color:${rh.gac(app.aaps.core.ui.R.attr.nsTitleColor)}\">")
                .append(rh.gs(R.string.openaps_short))
                .append(": </span>")

            // test warning level
            val level = when {
                openAPSData.clockSuggested + T.mins(sp.getLong(app.aaps.core.utils.R.string.key_ns_alarm_urgent_stale_data_value, 31))
                    .msecs() < dateUtil.now()                                                                                                                   -> ProcessedDeviceStatusData.Levels.URGENT

                openAPSData.clockSuggested + T.mins(sp.getLong(app.aaps.core.utils.R.string.key_ns_alarm_stale_data_value, 16)).msecs() < dateUtil.now() -> ProcessedDeviceStatusData.Levels.WARN
                else                                                                                                                                            -> ProcessedDeviceStatusData.Levels.INFO
            }
            string.append("<span style=\"color:${level.toColor()}\">")
            if (openAPSData.clockSuggested != 0L) string.append(dateUtil.minAgo(rh, openAPSData.clockSuggested)).append(" ")
            string.append("</span>") // color
            return HtmlHelper.fromHtml(string.toString())
        }

    override val openApsTimestamp: Long
        get() = if (openAPSData.clockSuggested != 0L) openAPSData.clockSuggested else -1

    override fun getAPSResult(): APSResult =
        instantiator.provideAPSResultObject().also {
            it.json = openAPSData.suggested
            it.date = openAPSData.clockSuggested
        }

    override val uploaderStatus: String
        get() {
            val iterator: Iterator<*> = uploaderMap.entries.iterator()
            var minBattery = 100
            while (iterator.hasNext()) {
                val pair = iterator.next() as Map.Entry<*, *>
                val uploader = pair.value as ProcessedDeviceStatusData.Uploader
                if (minBattery > uploader.battery) minBattery = uploader.battery
            }
            return "$minBattery%"
        }

    override val uploaderStatusSpanned: Spanned
        get() {
            var isCharging = false
            val string = StringBuilder()
            string.append("<span style=\"color:${rh.gac(app.aaps.core.ui.R.attr.nsTitleColor)}\">")
            string.append(rh.gs(R.string.uploader_short))
            string.append(": </span>")
            val iterator: Iterator<*> = uploaderMap.entries.iterator()
            var minBattery = 100
            var found = false
            while (iterator.hasNext()) {
                val pair = iterator.next() as Map.Entry<*, *>
                val uploader = pair.value as ProcessedDeviceStatusData.Uploader
                if (minBattery >= uploader.battery) {
                    minBattery = uploader.battery
                    isCharging = uploader.isCharging ?: false
                    found = true
                }
            }
            if (found) {
                if (isCharging) string.append("ᴪ ")
                string.append(minBattery)
                string.append("%")
            }
            return HtmlHelper.fromHtml(string.toString())
        }

    override val extendedUploaderStatus: Spanned
        get() {
            val string = StringBuilder()
            val iterator: Iterator<*> = uploaderMap.entries.iterator()
            while (iterator.hasNext()) {
                val pair = iterator.next() as Map.Entry<*, *>
                val uploader = pair.value as ProcessedDeviceStatusData.Uploader
                val device = pair.key as String
                string.append("<b>").append(device).append(":</b> ").append(uploader.battery).append("%<br>")
            }
            return HtmlHelper.fromHtml(string.toString())
        }

    private fun ProcessedDeviceStatusData.Levels.toColor(): String =
        when (level) {
            ProcessedDeviceStatusData.Levels.INFO.level   -> "white"
            ProcessedDeviceStatusData.Levels.WARN.level   -> "yellow"
            ProcessedDeviceStatusData.Levels.URGENT.level -> "red"
            else                                          -> "white"
        }

}

