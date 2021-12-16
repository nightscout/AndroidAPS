package info.nightscout.androidaps.plugins.general.nsclient.data

import android.text.Spanned
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.aps.loop.APSResult
import info.nightscout.androidaps.plugins.configBuilder.RunningConfiguration
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.HtmlHelper.fromHtml
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/*
{
    "_id": "594fdcec327b83c81b6b8c0f",
    "device": "openaps://Sony D5803",
    "pump": {
        "battery": {
            "percent": 100
        },
        "status": {
            "status": "normal",
            "timestamp": "2017-06-25T15:50:14Z"
        },
        "extended": {
            "Version": "1.5-ac98852-2017.06.25",
            "PumpIOB": 1.13,
            "LastBolus": "25. 6. 2017 17:25:00",
            "LastBolusAmount": 0.3,
            "BaseBasalRate": 0.4,
            "ActiveProfile": "2016 +30%"
        },
        "reservoir": 109,
        "clock": "2017-06-25T15:55:10Z"
    },
    "openaps": {
        "suggested": {
            "temp": "absolute",
            "bg": 115.9,
            "tick": "+5",
            "eventualBG": 105,
            "snoozeBG": 105,
            "predBGs": {
                "IOB": [116, 114, 112, 110, 109, 107, 106, 105, 105, 104, 104, 104, 104, 104, 104, 104, 104, 105, 105, 105, 105, 105, 106, 106, 106, 106, 106, 107]
            },
            "COB": 0,
            "IOB": -0.035,
            "reason": "COB: 0, Dev: -18, BGI: 0.43, ISF: 216, Target: 99; Eventual BG 105 > 99 but Min. Delta -2.60 < Exp. Delta 0.1; setting current basal of 0.4 as temp. Suggested rate is same as profile rate, no temp basal is active, doing nothing",
            "timestamp": "2017-06-25T15:55:10Z"
        },
        "iob": {
            "iob": -0.035,
            "basaliob": -0.035,
            "activity": -0.0004,
            "time": "2017-06-25T15:55:10Z"
        }
    },
    "uploaderBattery": 93,
    "created_at": "2017-06-25T15:55:10Z",
    "NSCLIENT_ID": 1498406118857
}
 */
@Suppress("SpellCheckingInspection")
@Singleton
class NSDeviceStatus @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val rh: ResourceHelper,
    private val nsSettingsStatus: NSSettingsStatus,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val runningConfiguration: RunningConfiguration,
    private val deviceStatusData: DeviceStatusData
) {

    private var data: JSONObject? = null
    fun handleNewData(deviceStatuses: JSONArray) {
        aapsLogger.debug(LTag.NSCLIENT, "Got NS deviceStatus: \$deviceStatuses")
        try {
            for (i in deviceStatuses.length() -1 downTo 0) {
                val devicestatusJson = deviceStatuses.getJSONObject(i)
                if (devicestatusJson != null) {
                    setData(devicestatusJson)
                    if (devicestatusJson.has("pump")) {
                        // Objectives 0
                        sp.putBoolean(R.string.key_ObjectivespumpStatusIsAvailableInNS, true)
                    }
                    if (devicestatusJson.has("configuration") && config.NSCLIENT) {
                        // copy configuration of Insulin and Sensitivity from main AAPS
                        runningConfiguration.apply(devicestatusJson.getJSONObject("configuration"))
                        break
                    }
                }
            }
        } catch (jsonException: JSONException) {
            jsonException.printStackTrace()
        }
    }

    private fun setData(obj: JSONObject): NSDeviceStatus {
        data = obj
        updatePumpData()
        updateOpenApsData(obj)
        updateUploaderData(obj)
        return this
    }

    val device: String
        get() {
            try {
                if (data!!.has("device")) {
                    var device = data!!.getString("device")
                    if (device.startsWith("openaps://")) {
                        device = device.substring(10)
                        return device
                    }
                }
            } catch (e: JSONException) {
                aapsLogger.error("Unhandled exception", e)
            }
            return ""
        }

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

    val extendedPumpStatus: Spanned
        get() = deviceStatusData.pumpData?.extended ?: fromHtml("")

    val pumpStatus: Spanned
        // test warning level // color
        get() {
            val pumpData = deviceStatusData.pumpData ?: return fromHtml("")

            //String[] ALL_STATUS_FIELDS = {"reservoir", "battery", "clock", "status", "device"};
            val string = StringBuilder()
                .append("<span style=\"color:${rh.gcs(R.color.defaulttext)}\">")
                .append(rh.gs(R.string.pump))
                .append(": </span>")

            // test warning level
            val level = when {
                pumpData.clock + nsSettingsStatus.extendedPumpSettings("urgentClock") * 60 * 1000L < dateUtil.now() -> Levels.URGENT
                pumpData.reservoir < nsSettingsStatus.extendedPumpSettings("urgentRes")                             -> Levels.URGENT
                pumpData.isPercent && pumpData.percent < nsSettingsStatus.extendedPumpSettings("urgentBattP")        -> Levels.URGENT
                !pumpData.isPercent && pumpData.voltage < nsSettingsStatus.extendedPumpSettings("urgentBattV")      -> Levels.URGENT
                pumpData.clock + nsSettingsStatus.extendedPumpSettings("warnClock") * 60 * 1000L < dateUtil.now()   -> Levels.WARN
                pumpData.reservoir < nsSettingsStatus.extendedPumpSettings("warnRes")                               -> Levels.WARN
                pumpData.isPercent && pumpData.percent < nsSettingsStatus.extendedPumpSettings("warnBattP")          -> Levels.WARN
                !pumpData.isPercent && pumpData.voltage < nsSettingsStatus.extendedPumpSettings("warnBattV")         -> Levels.WARN
                else                                                                                                 -> Levels.INFO
            }
            string.append("<span style=\"color:${level.toColor()}\">")
            val fields = nsSettingsStatus.pumpExtendedSettingsFields()
            if (fields.contains("reservoir")) string.append(pumpData.reservoir.toInt()).append("U ")
            if (fields.contains("battery") && pumpData.isPercent) string.append(pumpData.percent).append("% ")
            if (fields.contains("battery") && !pumpData.isPercent) string.append(Round.roundTo(pumpData.voltage, 0.001)).append(" ")
            if (fields.contains("clock")) string.append(dateUtil.minAgo(rh, pumpData.clock)).append(" ")
            if (fields.contains("status")) string.append(pumpData.status).append(" ")
            if (fields.contains("device")) string.append(device).append(" ")
            string.append("</span>") // color
            return fromHtml(string.toString())
        }

    private fun updatePumpData() {
        try {
            val data = this.data ?: return
            val pump = if (data.has("pump")) data.getJSONObject("pump") else JSONObject()
            val clock = if (pump.has("clock")) dateUtil.fromISODateString(pump.getString("clock")) else 0L
            // check if this is new data
            if (clock == 0L || deviceStatusData.pumpData != null && clock < deviceStatusData.pumpData!!.clock) return

            // create new status and process data
            val deviceStatusPumpData = DeviceStatusData.PumpData()
            deviceStatusPumpData.clock = clock
            if (pump.has("status") && pump.getJSONObject("status").has("status")) deviceStatusPumpData.status = pump.getJSONObject("status").getString("status")
            if (pump.has("reservoir")) deviceStatusPumpData.reservoir = pump.getDouble("reservoir")
            if (pump.has("battery") && pump.getJSONObject("battery").has("percent")) {
                deviceStatusPumpData.isPercent = true
                deviceStatusPumpData.percent = pump.getJSONObject("battery").getInt("percent")
            } else if (pump.has("battery") && pump.getJSONObject("battery").has("voltage")) {
                deviceStatusPumpData.isPercent = false
                deviceStatusPumpData.voltage = pump.getJSONObject("battery").getDouble("voltage")
            }
            if (pump.has("extended")) {
                val extendedJson = pump.getJSONObject("extended")
                val extended = StringBuilder()
                val keys: Iterator<*> = extendedJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next() as String
                    val value = extendedJson.getString(key)
                    extended.append("<b>").append(key).append(":</b> ").append(value).append("<br>")
                }
                deviceStatusPumpData.extended = fromHtml(extended.toString())
                deviceStatusPumpData.activeProfileName = JsonHelper.safeGetStringAllowNull(extendedJson, "ActiveProfile", null)
            }
            deviceStatusData.pumpData = deviceStatusPumpData
        } catch (e: Exception) {
            aapsLogger.error("Unhandled exception", e)
        }
    }

    private fun updateOpenApsData(jsonObject: JSONObject) {
        try {
            val openAps = if (jsonObject.has("openaps")) jsonObject.getJSONObject("openaps") else JSONObject()
            val suggested = if (openAps.has("suggested")) openAps.getJSONObject("suggested") else JSONObject()
            val enacted = if (openAps.has("enacted")) openAps.getJSONObject("enacted") else JSONObject()
            var clock = if (suggested.has("timestamp")) dateUtil.fromISODateString(suggested.getString("timestamp")) else 0L
            // check if this is new data
            if (clock != 0L && clock > deviceStatusData.openAPSData.clockSuggested) {
                deviceStatusData.openAPSData.suggested = suggested
                deviceStatusData.openAPSData.clockSuggested = clock
            }
            clock = if (enacted.has("timestamp")) dateUtil.fromISODateString(enacted.getString("timestamp")) else 0L
            // check if this is new data
            if (clock != 0L && clock > deviceStatusData.openAPSData.clockEnacted) {
                deviceStatusData.openAPSData.enacted = enacted
                deviceStatusData.openAPSData.clockEnacted = clock
            }
        } catch (e: Exception) {
            aapsLogger.error("Unhandled exception", e)
        }
    }

    val openApsStatus: Spanned
        get() {
            val string = StringBuilder()
                .append("<span style=\"color:${rh.gcs(R.color.defaulttext)}\">")
                .append(rh.gs(R.string.openaps_short))
                .append(": </span>")

            // test warning level
            val level = when {
                deviceStatusData.openAPSData.clockSuggested + T.mins(sp.getLong(R.string.key_nsalarm_urgent_staledatavalue, 31)).msecs() < dateUtil.now() -> Levels.URGENT
                deviceStatusData.openAPSData.clockSuggested + T.mins(sp.getLong(R.string.key_nsalarm_staledatavalue, 16)).msecs() < dateUtil.now()        -> Levels.WARN
                else                                                                                                                                 -> Levels.INFO
            }
            string.append("<span style=\"color:${level.toColor()}\">")
            if (deviceStatusData.openAPSData.clockSuggested != 0L) string.append(dateUtil.minAgo(rh, deviceStatusData.openAPSData.clockSuggested)).append(" ")
            string.append("</span>") // color
            return fromHtml(string.toString())
        }

    val extendedOpenApsStatus: Spanned
        get() {
            val string = StringBuilder()
            try {
                if (deviceStatusData.openAPSData.enacted != null && deviceStatusData.openAPSData.clockEnacted != deviceStatusData.openAPSData.clockSuggested) string.append("<b>").append(dateUtil.minAgo(rh, deviceStatusData.openAPSData.clockEnacted)).append("</b> ").append(deviceStatusData.openAPSData.enacted!!.getString("reason")).append("<br>")
                if (deviceStatusData.openAPSData.suggested != null) string.append("<b>").append(dateUtil.minAgo(rh, deviceStatusData.openAPSData.clockSuggested)).append("</b> ").append(deviceStatusData.openAPSData.suggested!!.getString("reason")).append("<br>")
                return fromHtml(string.toString())
            } catch (e: JSONException) {
                aapsLogger.error("Unhandled exception", e)
            }
            return fromHtml("")
        }

    private fun updateUploaderData(jsonObject: JSONObject) {
        try {
            val clock =
                when {
                    jsonObject.has("mills")      -> jsonObject.getLong("mills")
                    jsonObject.has("created_at") -> dateUtil.fromISODateString(jsonObject.getString("created_at"))
                    else                         -> 0L
                }
            val device = device
            val battery: Int =
                when {
                    jsonObject.has("uploaderBattery")                                                 -> jsonObject.getInt("uploaderBattery")
                    jsonObject.has("uploader") && jsonObject.getJSONObject("uploader").has("battery") -> jsonObject.getJSONObject("uploader").getInt("battery")
                    else                                                                              -> 0
                }

            var uploader = deviceStatusData.uploaderMap[device]
            // check if this is new data
            if (clock != 0L && battery != 0 && (uploader == null || clock > uploader.clock)) {
                if (uploader == null) uploader = DeviceStatusData.Uploader()
                uploader.battery = battery
                uploader.clock = clock
                deviceStatusData.uploaderMap[device] = uploader
            }
        } catch (e: Exception) {
            aapsLogger.error("Unhandled exception", e)
        }
    }

    val uploaderStatus: String
        get() {
            val iterator: Iterator<*> = deviceStatusData.uploaderMap.entries.iterator()
            var minBattery = 100
            while (iterator.hasNext()) {
                val pair = iterator.next() as Map.Entry<*, *>
                val uploader = pair.value as DeviceStatusData.Uploader
                if (minBattery > uploader.battery) minBattery = uploader.battery
            }
            return "$minBattery%"
        }

    val uploaderStatusSpanned: Spanned
        get() {
            val string = StringBuilder()
            string.append("<span style=\"color:${rh.gcs(R.color.defaulttext)}\">")
            string.append(rh.gs(R.string.uploader_short))
            string.append(": </span>")
            val iterator: Iterator<*> = deviceStatusData.uploaderMap.entries.iterator()
            var minBattery = 100
            while (iterator.hasNext()) {
                val pair = iterator.next() as Map.Entry<*, *>
                val uploader = pair.value as DeviceStatusData.Uploader
                if (minBattery > uploader.battery) minBattery = uploader.battery
            }
            string.append(minBattery)
            string.append("%")
            return fromHtml(string.toString())
        }

    val extendedUploaderStatus: Spanned
        get() {
            val string = StringBuilder()
            val iterator: Iterator<*> = deviceStatusData.uploaderMap.entries.iterator()
            while (iterator.hasNext()) {
                val pair = iterator.next() as Map.Entry<*, *>
                val uploader = pair.value as DeviceStatusData.Uploader
                val device = pair.key as String
                string.append("<b>").append(device).append(":</b> ").append(uploader.battery).append("%<br>")
            }
            return fromHtml(string.toString())
        }

    val openApsTimestamp: Long
        get() =
            if (deviceStatusData.openAPSData.clockSuggested != 0L) {
                deviceStatusData.openAPSData.clockSuggested
            } else {
                -1
            }

    fun getAPSResult(injector: HasAndroidInjector): APSResult {
        val result = APSResult(injector)
        result.json = deviceStatusData.openAPSData.suggested
        result.date = deviceStatusData.openAPSData.clockSuggested
        return result
    }
}