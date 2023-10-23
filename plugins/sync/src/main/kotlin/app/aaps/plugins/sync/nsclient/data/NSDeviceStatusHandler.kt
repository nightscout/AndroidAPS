package app.aaps.plugins.sync.nsclient.data

import app.aaps.annotations.OpenForTesting
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.nssdk.localmodel.devicestatus.NSDeviceStatus
import app.aaps.core.utils.HtmlHelper
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.sync.R
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
            "sensitivityRatio": 0.81,
            "variable_sens": 137.3,
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
@OpenForTesting
class NSDeviceStatusHandler @Inject constructor(
    private val sp: SP,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val runningConfiguration: RunningConfiguration,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val uiInteraction: UiInteraction,
    private val rh: ResourceHelper
) {

    fun handleNewData(deviceStatuses: Array<NSDeviceStatus>) {
        var configurationDetected = false
        for (i in deviceStatuses.size - 1 downTo 0) {
            val nsDeviceStatus = deviceStatuses[i]
            if (config.NSCLIENT) {
                updatePumpData(nsDeviceStatus)
                updateDeviceData(nsDeviceStatus)
                updateOpenApsData(nsDeviceStatus)
                updateUploaderData(nsDeviceStatus)
            }
            if (config.NSCLIENT && !configurationDetected)
                nsDeviceStatus.configuration?.let {
                    // copy configuration of Insulin and Sensitivity from main AAPS
                    runningConfiguration.apply(it)
                    configurationDetected = true // pick only newest

                }
            if (config.APS) {
                nsDeviceStatus.pump?.let { sp.putBoolean(app.aaps.core.utils.R.string.key_objectives_pump_status_is_available_in_ns, true) }  // Objective 0
            }
        }
    }

    private fun updateDeviceData(deviceStatus: NSDeviceStatus) {
        val createdAt = deviceStatus.createdAt?.let { dateUtil.fromISODateString(it) } ?: return
        processedDeviceStatusData.device?.let { if (createdAt < it.createdAt) return } // take only newer record
        deviceStatus.device?.let {
            if (it.startsWith("openaps://")) processedDeviceStatusData.device = ProcessedDeviceStatusData.Device(createdAt, it.substring(10))
        }
    }

    private fun updatePumpData(nsDeviceStatus: NSDeviceStatus) {
        val pump = nsDeviceStatus.pump ?: return
        val clock = pump.clock?.let { dateUtil.fromISODateString(it) } ?: return
        processedDeviceStatusData.pumpData?.let { if (clock < it.clock) return } // take only newer record

        // create new status and process data
        processedDeviceStatusData.pumpData = ProcessedDeviceStatusData.PumpData().also { deviceStatusPumpData ->
            deviceStatusPumpData.clock = clock
            pump.status?.status?.let { deviceStatusPumpData.status = it }
            pump.reservoir?.let { deviceStatusPumpData.reservoir = it }
            pump.reservoirDisplayOverride?.let { deviceStatusPumpData.reservoirDisplayOverride = it }
            pump.battery?.percent?.let {
                deviceStatusPumpData.isPercent = true
                deviceStatusPumpData.percent = it
            }
            pump.battery?.voltage?.let {
                deviceStatusPumpData.isPercent = false
                deviceStatusPumpData.voltage = it
            }
            pump.extended?.let {
                val extended = StringBuilder()
                val keys: Iterator<*> = it.keys()
                while (keys.hasNext()) {
                    val key = keys.next() as String
                    val value = it.getString(key)
                    extended.append("<b>").append(key).append(":</b> ").append(value).append("<br>")
                }
                deviceStatusPumpData.extended = HtmlHelper.fromHtml(extended.toString())
                deviceStatusPumpData.activeProfileName = JsonHelper.safeGetStringAllowNull(it, "ActiveProfile", null)
            }
        }
    }

    private fun updateOpenApsData(nsDeviceStatus: NSDeviceStatus) {
        nsDeviceStatus.openaps?.suggested?.let {
            JsonHelper.safeGetString(it, "timestamp")?.let { timestamp ->
                val clock = dateUtil.fromISODateString(timestamp)
                // check if this is new data
                if (clock > processedDeviceStatusData.openAPSData.clockSuggested) {
                    processedDeviceStatusData.openAPSData.suggested = it
                    processedDeviceStatusData.openAPSData.clockSuggested = clock
                }
            }
        }
        nsDeviceStatus.openaps?.enacted?.let {
            JsonHelper.safeGetString(it, "timestamp")?.let { timestamp ->
                val clock = dateUtil.fromISODateString(timestamp)
                // check if this is new data
                if (clock > processedDeviceStatusData.openAPSData.clockEnacted) {
                    processedDeviceStatusData.openAPSData.enacted = it
                    processedDeviceStatusData.openAPSData.clockEnacted = clock
                }
            }
        }
    }

    private fun updateUploaderData(nsDeviceStatus: NSDeviceStatus) {
        val clock = nsDeviceStatus.createdAt?.let { dateUtil.fromISODateString(it) } ?: return
        val device = nsDeviceStatus.device ?: return
        val battery = nsDeviceStatus.uploaderBattery ?: nsDeviceStatus.uploader?.battery ?: return
        val isCharging = nsDeviceStatus.isCharging

        var uploader = processedDeviceStatusData.uploaderMap[device]
        // check if this is new data
        if (uploader == null || clock > uploader.clock) {
            if (uploader == null) uploader = ProcessedDeviceStatusData.Uploader()
            uploader.battery = battery
            uploader.clock = clock
            uploader.isCharging = isCharging
            processedDeviceStatusData.uploaderMap[device] = uploader
        }
    }
}