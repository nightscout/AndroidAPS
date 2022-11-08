package info.nightscout.androidaps.plugins.sync.nsclient.data

import android.text.Spanned
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.androidaps.plugins.aps.loop.APSResult
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import info.nightscout.interfaces.utils.HtmlHelper
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.interfaces.utils.Round
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("SpellCheckingInspection")
@Singleton
class ProcessedDeviceStatusData @Inject constructor(
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val sp: SP
) {

    enum class Levels(val level: Int) {

        URGENT(2),
        WARN(1),
        INFO(0);

        fun toColor(): String =
            when (level) {
                INFO.level   -> "white"
                WARN.level   -> "yellow"
                URGENT.level -> "red"
                else         -> "white"
            }
    }

    class PumpData {

        var clock = 0L
        var isPercent = false
        var percent = 0
        var voltage = 0.0
        var status = "N/A"
        var reservoir = 0.0
        var reservoirDisplayOverride = ""
        var extended: Spanned? = null
        var activeProfileName: String? = null
    }

    var pumpData: PumpData? = null

    data class Device(
        val createdAt: Long,
        val device: String?
    )

    var device: Device? = null

    class Uploader {

        var clock = 0L
        var battery = 0
    }

    val uploaderMap = HashMap<String, Uploader>()

    class OpenAPSData {

        var clockSuggested = 0L
        var clockEnacted = 0L
        var suggested: JSONObject? = null
        var enacted: JSONObject? = null
    }

    var openAPSData = OpenAPSData()

    // test warning level // color
    fun pumpStatus(nsSettingsStatus: NSSettingsStatus): Spanned {
            val pumpData = pumpData ?: return HtmlHelper.fromHtml("")

            //String[] ALL_STATUS_FIELDS = {"reservoir", "battery", "clock", "status", "device"};
            val string = StringBuilder()
                .append("<span style=\"color:${rh.gac(R.attr.nsTitleColor)}\">")
                .append(rh.gs(R.string.pump))
                .append(": </span>")

            // test warning level
            val level = when {
                pumpData.clock + nsSettingsStatus.extendedPumpSettings("urgentClock") * 60 * 1000L < dateUtil.now()                    -> Levels.URGENT
                pumpData.reservoir < nsSettingsStatus.extendedPumpSettings("urgentRes")                                                -> Levels.URGENT
                pumpData.isPercent && pumpData.percent < nsSettingsStatus.extendedPumpSettings("urgentBattP")                          -> Levels.URGENT
                !pumpData.isPercent && pumpData.voltage > 0 && pumpData.voltage < nsSettingsStatus.extendedPumpSettings("urgentBattV") -> Levels.URGENT
                pumpData.clock + nsSettingsStatus.extendedPumpSettings("warnClock") * 60 * 1000L < dateUtil.now()                      -> Levels.WARN
                pumpData.reservoir < nsSettingsStatus.extendedPumpSettings("warnRes")                                                  -> Levels.WARN
                pumpData.isPercent && pumpData.percent < nsSettingsStatus.extendedPumpSettings("warnBattP")                            -> Levels.WARN
                !pumpData.isPercent && pumpData.voltage > 0 && pumpData.voltage < nsSettingsStatus.extendedPumpSettings("warnBattV")   -> Levels.WARN
                else                                                                                                                   -> Levels.INFO
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

    val extendedPumpStatus: Spanned get() = pumpData?.extended ?: HtmlHelper.fromHtml("")
    val extendedOpenApsStatus: Spanned
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

    val openApsStatus: Spanned
        get() {
            val string = StringBuilder()
                .append("<span style=\"color:${rh.gac(R.attr.nsTitleColor)}\">")
                .append(rh.gs(R.string.openaps_short))
                .append(": </span>")

            // test warning level
            val level = when {
                openAPSData.clockSuggested + T.mins(sp.getLong(R.string.key_nsalarm_urgent_staledatavalue, 31)).msecs() < dateUtil.now() -> Levels.URGENT
                openAPSData.clockSuggested + T.mins(sp.getLong(R.string.key_nsalarm_staledatavalue, 16)).msecs() < dateUtil.now()        -> Levels.WARN
                else                                                                                                                     -> Levels.INFO
            }
            string.append("<span style=\"color:${level.toColor()}\">")
            if (openAPSData.clockSuggested != 0L) string.append(dateUtil.minAgo(rh, openAPSData.clockSuggested)).append(" ")
            string.append("</span>") // color
            return HtmlHelper.fromHtml(string.toString())
        }

    val openApsTimestamp: Long
        get() = if (openAPSData.clockSuggested != 0L) openAPSData.clockSuggested else -1

    fun getAPSResult(injector: HasAndroidInjector): APSResult {
        val result = APSResult(injector)
        result.json = openAPSData.suggested
        result.date = openAPSData.clockSuggested
        return result
    }
    val uploaderStatus: String
        get() {
            val iterator: Iterator<*> = uploaderMap.entries.iterator()
            var minBattery = 100
            while (iterator.hasNext()) {
                val pair = iterator.next() as Map.Entry<*, *>
                val uploader = pair.value as Uploader
                if (minBattery > uploader.battery) minBattery = uploader.battery
            }
            return "$minBattery%"
        }

    val uploaderStatusSpanned: Spanned
        get() {
            val string = StringBuilder()
            string.append("<span style=\"color:${rh.gac(R.attr.nsTitleColor)}\">")
            string.append(rh.gs(R.string.uploader_short))
            string.append(": </span>")
            val iterator: Iterator<*> = uploaderMap.entries.iterator()
            var minBattery = 100
            while (iterator.hasNext()) {
                val pair = iterator.next() as Map.Entry<*, *>
                val uploader = pair.value as Uploader
                if (minBattery > uploader.battery) minBattery = uploader.battery
            }
            string.append(minBattery)
            string.append("%")
            return HtmlHelper.fromHtml(string.toString())
        }

    val extendedUploaderStatus: Spanned
        get() {
            val string = StringBuilder()
            val iterator: Iterator<*> = uploaderMap.entries.iterator()
            while (iterator.hasNext()) {
                val pair = iterator.next() as Map.Entry<*, *>
                val uploader = pair.value as Uploader
                val device = pair.key as String
                string.append("<b>").append(device).append(":</b> ").append(uploader.battery).append("%<br>")
            }
            return HtmlHelper.fromHtml(string.toString())
        }

}

