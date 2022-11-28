package info.nightscout.plugins.sync.nsclient.data

import android.text.Spanned
import dagger.android.HasAndroidInjector
import info.nightscout.core.aps.APSResultObject
import info.nightscout.interfaces.nsclient.NSSettingsStatus
import info.nightscout.interfaces.nsclient.ProcessedDeviceStatusData
import info.nightscout.interfaces.utils.HtmlHelper
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.interfaces.utils.Round
import info.nightscout.plugins.sync.R
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("SpellCheckingInspection")
@Singleton
class ProcessedDeviceStatusDataImpl @Inject constructor(
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val sp: SP
) : ProcessedDeviceStatusData {

    override var pumpData: ProcessedDeviceStatusData.PumpData? = null

    override var device: ProcessedDeviceStatusData.Device? = null

    override val uploaderMap = HashMap<String, ProcessedDeviceStatusData.Uploader>()

    override var openAPSData = ProcessedDeviceStatusData.OpenAPSData()

    // test warning level // color
    override fun pumpStatus(nsSettingsStatus: NSSettingsStatus): Spanned {
            val pumpData = pumpData ?: return HtmlHelper.fromHtml("")

            //String[] ALL_STATUS_FIELDS = {"reservoir", "battery", "clock", "status", "device"};
            val string = StringBuilder()
                .append("<span style=\"color:${rh.gac(R.attr.nsTitleColor)}\">")
                .append(rh.gs(R.string.pump))
                .append(": </span>")

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
            val insulinUnit = rh.gs(R.string.insulin_unit_shortname)
            val fields = nsSettingsStatus.pumpExtendedSettingsFields()
            if (pumpData.reservoirDisplayOverride != "")
                string.append(pumpData.reservoirDisplayOverride).append("$insulinUnit ")
            else if (fields.contains("reservoir")) string.append(pumpData.reservoir.toInt()).append("$insulinUnit ")
            if (fields.contains("battery") && pumpData.isPercent) string.append(pumpData.percent).append("% ")
            if (fields.contains("battery") && !pumpData.isPercent) string.append(Round.roundTo(pumpData.voltage, 0.001)).append(" ")
            if (fields.contains("clock")) string.append(dateUtil.minAgo(rh, pumpData.clock)).append(" ")
            if (fields.contains("status")) string.append(pumpData.status).append(" ")
            if (fields.contains("device")) string.append(device).append(" ")
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
                .append("<span style=\"color:${rh.gac(R.attr.nsTitleColor)}\">")
                .append(rh.gs(R.string.openaps_short))
                .append(": </span>")

            // test warning level
            val level = when {
                openAPSData.clockSuggested + T.mins(sp.getLong(R.string.key_ns_alarm_urgent_stale_data_value, 31)).msecs() < dateUtil.now() -> ProcessedDeviceStatusData.Levels.URGENT
                openAPSData.clockSuggested + T.mins(sp.getLong(R.string.key_ns_alarm_stale_data_value, 16)).msecs() < dateUtil.now()        -> ProcessedDeviceStatusData.Levels.WARN
                else                                                                                                                        -> ProcessedDeviceStatusData.Levels.INFO
            }
            string.append("<span style=\"color:${level.toColor()}\">")
            if (openAPSData.clockSuggested != 0L) string.append(dateUtil.minAgo(rh, openAPSData.clockSuggested)).append(" ")
            string.append("</span>") // color
            return HtmlHelper.fromHtml(string.toString())
        }

    override val openApsTimestamp: Long
        get() = if (openAPSData.clockSuggested != 0L) openAPSData.clockSuggested else -1

    override fun getAPSResult(injector: HasAndroidInjector): APSResultObject {
        val result = APSResultObject(injector)
        result.json = openAPSData.suggested
        result.date = openAPSData.clockSuggested
        return result
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
            val string = StringBuilder()
            string.append("<span style=\"color:${rh.gac(R.attr.nsTitleColor)}\">")
            string.append(rh.gs(R.string.uploader_short))
            string.append(": </span>")
            val iterator: Iterator<*> = uploaderMap.entries.iterator()
            var minBattery = 100
            while (iterator.hasNext()) {
                val pair = iterator.next() as Map.Entry<*, *>
                val uploader = pair.value as ProcessedDeviceStatusData.Uploader
                if (minBattery > uploader.battery) minBattery = uploader.battery
            }
            string.append(minBattery)
            string.append("%")
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
}

