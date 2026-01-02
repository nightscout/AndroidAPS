package app.aaps.plugins.sync.nsclient.data

import android.text.Spanned
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.HtmlHelper
import app.aaps.plugins.sync.R
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ProcessedDeviceStatusDataImpl @Inject constructor(
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val preferences: Preferences,
    private val apsResultProvider: Provider<APSResult>
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
                .append("Enacted: </br>")
                .append(dateUtil.minAgo(rh, openAPSData.clockEnacted))
                .append("</b> ")
                .append(enacted.reason)
                .append("<br>")
            if (suggested != null) string
                .append("<b>")
                .append("Suggested: </br>")
                .append(dateUtil.minAgo(rh, openAPSData.clockSuggested))
                .append("</b> ")
                .append(suggested.reason)
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
                openAPSData.clockSuggested + T.mins(preferences.get(IntKey.NsClientUrgentAlarmStaleData).toLong()).msecs() < dateUtil.now() -> ProcessedDeviceStatusData.Levels.URGENT

                openAPSData.clockSuggested + T.mins(preferences.get(IntKey.NsClientAlarmStaleData).toLong()).msecs() < dateUtil.now()       -> ProcessedDeviceStatusData.Levels.WARN
                else                                                                                                                        -> ProcessedDeviceStatusData.Levels.INFO
            }
            string.append("<span style=\"color:${level.toColor()}\">")
            if (openAPSData.clockSuggested != 0L) string.append(dateUtil.minOrSecAgo(rh, openAPSData.clockSuggested)).append(" ")
            string.append("</span>") // color
            return HtmlHelper.fromHtml(string.toString())
        }

    override val openApsTimestamp: Long
        get() = if (openAPSData.clockSuggested != 0L) openAPSData.clockSuggested else -1

    override fun getAPSResult(): APSResult? =
        openAPSData.suggested?.let { apsResultProvider.get().with(it) }

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
                    isCharging = uploader.isCharging == true
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

