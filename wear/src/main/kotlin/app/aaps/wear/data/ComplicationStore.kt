package app.aaps.wear.data

import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.core.interfaces.rx.weardata.EventData
import kotlinx.serialization.Serializable

/**
 * Data models for DataStore-based persistence
 * Using EventData models directly to avoid duplication
 * Kotlin Serialization with ProtoBuf format for efficient storage
 */

@Serializable
data class ComplicationData(
    val bgData: EventData.SingleBg = EventData.SingleBg(
        dataset = 0,
        timeStamp = 0L,
        sgvString = "---",
        glucoseUnits = "-",
        slopeArrow = "--",
        delta = "--",
        deltaDetailed = "--",
        avgDelta = "--",
        avgDeltaDetailed = "--",
        sgvLevel = 0L,
        sgv = 0.0,
        high = 0.0,
        low = 0.0,
        color = 0
    ),
    val bgData1: EventData.SingleBg = EventData.SingleBg(
        dataset = 1,
        timeStamp = 0L,
        sgvString = "---",
        glucoseUnits = "-",
        slopeArrow = "--",
        delta = "--",
        deltaDetailed = "--",
        avgDelta = "--",
        avgDeltaDetailed = "--",
        sgvLevel = 0L,
        sgv = 0.0,
        high = 0.0,
        low = 0.0,
        color = 0
    ),
    val bgData2: EventData.SingleBg = EventData.SingleBg(
        dataset = 2,
        timeStamp = 0L,
        sgvString = "---",
        glucoseUnits = "-",
        slopeArrow = "--",
        delta = "--",
        deltaDetailed = "--",
        avgDelta = "--",
        avgDeltaDetailed = "--",
        sgvLevel = 0L,
        sgv = 0.0,
        high = 0.0,
        low = 0.0,
        color = 0
    ),
    val statusData: EventData.Status = EventData.Status(
        dataset = 0,
        externalStatus = "no status",
        iobSum = "IOB",
        iobDetail = "-.--",
        cob = "--g",
        currentBasal = "-.--U/h",
        battery = "--",
        rigBattery = "--",
        openApsStatus = -1L,
        bgi = "--",
        batteryLevel = 1,
        patientName = "",
        tempTarget = "--",
        tempTargetLevel = 0,
        reservoirString = "--",
        reservoir = 0.0,
        reservoirLevel = 0
    ),
    val statusData1: EventData.Status = EventData.Status(
        dataset = 1,
        externalStatus = "no status",
        iobSum = "IOB",
        iobDetail = "-.--",
        cob = "--g",
        currentBasal = "-.--U/h",
        battery = "--",
        rigBattery = "--",
        openApsStatus = -1L,
        bgi = "--",
        batteryLevel = 1,
        patientName = "",
        tempTarget = "--",
        tempTargetLevel = 0,
        reservoirString = "--",
        reservoir = 0.0,
        reservoirLevel = 0
    ),
    val statusData2: EventData.Status = EventData.Status(
        dataset = 2,
        externalStatus = "no status",
        iobSum = "IOB",
        iobDetail = "-.--",
        cob = "--g",
        currentBasal = "-.--U/h",
        battery = "--",
        rigBattery = "--",
        openApsStatus = -1L,
        bgi = "--",
        batteryLevel = 1,
        patientName = "",
        tempTarget = "--",
        tempTargetLevel = 0,
        reservoirString = "--",
        reservoir = 0.0,
        reservoirLevel = 0
    ),
    val graphData: EventData.GraphData = EventData.GraphData(
        entries = ArrayList()
    ),
    val treatmentData: EventData.TreatmentData = EventData.TreatmentData(
        temps = ArrayList(),
        basals = ArrayList(),
        boluses = ArrayList(),
        predictions = ArrayList()
    ),
    val customWatchface: CwfData? = null,
    val customWatchfaceDefault: CwfData? = null,
    val customWatchfaceDefaultFull: CwfData? = null,
    val lastUpdateTimestamp: Long = 0L
)
