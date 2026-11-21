package app.aaps.core.interfaces.nsclient

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.RT

interface ProcessedDeviceStatusData {

    enum class Levels(val level: Int) {

        URGENT(2),
        WARN(1),
        INFO(0);
    }

    class PumpData {

        var clock = 0L
        var isPercent = false
        var percent = 0
        var voltage = 0.0
        var status = "N/A"
        /** Null when the master did not send it: it did not know it yet. That is no empty reservoir. */
        var reservoir: Double? = null
        var reservoirDisplayOverride = ""
        var extended: String? = null
        var activeProfileName: String? = null
    }

    var pumpData: PumpData?

    data class Device(
        val createdAt: Long,
        val device: String?
    )

    var device: Device?

    class Uploader {

        var clock = 0L
        var battery = 0
        var isCharging: Boolean? = null
    }

    /** Every uploader seen, with its newest report. A phone that stopped uploading stays in here. */
    val uploaderMap: HashMap<String, Uploader>

    /**
     * The uploaders that still report: [uploaderMap] without the phones whose last report is much older than
     * the newest report of any phone. Use this, not [uploaderMap], for anything shown to the user (#5547).
     */
    val activeUploaders: Map<String, Uploader>

    class OpenAPSData {

        var clockSuggested = 0L
        var clockEnacted = 0L
        var suggested: RT? = null
        var enacted: RT? = null
    }

    var openAPSData: OpenAPSData

    val openApsTimestamp: Long
    fun getAPSResult(): APSResult?
    val uploaderStatus: String
}