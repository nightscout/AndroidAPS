package app.aaps.wear.data

import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.interaction.utils.Persistence
import kotlinx.serialization.InternalSerializationApi

/**
 * Holds bunch of data model variables and lists that arrive from phone app and are due to be
 * displayed on watchface and complications. Keeping them together makes code cleaner and allows
 * passing it to complications via persistence layer.
 *
 * Created by dlvoy on 2019-11-12
 * Refactored by MilosKozak 24/04/2022
 *
 */
class RawDisplayData {

    @InternalSerializationApi
    var singleBg = arrayOf<EventData.SingleBg>(
        EventData.SingleBg(
            dataset = 0,
            timeStamp = 0,
            sgvString = "---",
            glucoseUnits = "-",
            slopeArrow = "--",
            delta = "--",
            deltaDetailed = "--",
            avgDelta = "--",
            avgDeltaDetailed = "--",
            sgvLevel = 0,
            sgv = 0.0,
            high = 0.0,
            low = 0.0,
            color = 0,
            deltaMgdl = null,
            avgDeltaMgdl = null
        ), EventData.SingleBg(
            dataset = 1,
            timeStamp = 0,
            sgvString = "---",
            glucoseUnits = "-",
            slopeArrow = "--",
            delta = "--",
            deltaDetailed = "--",
            avgDelta = "--",
            avgDeltaDetailed = "--",
            sgvLevel = 0,
            sgv = 0.0,
            high = 0.0,
            low = 0.0,
            color = 0,
            deltaMgdl = null,
            avgDeltaMgdl = null,
        ), EventData.SingleBg(
            dataset = 2,
            timeStamp = 0,
            sgvString = "---",
            glucoseUnits = "-",
            slopeArrow = "--",
            delta = "--",
            deltaDetailed = "--",
            avgDelta = "--",
            avgDeltaDetailed = "--",
            sgvLevel = 0,
            sgv = 0.0,
            high = 0.0,
            low = 0.0,
            color = 0,
            deltaMgdl = null,
            avgDeltaMgdl = null,
        )
    )

    // status bundle
    @InternalSerializationApi
    var status = arrayOf<EventData.Status>(
        EventData.Status(
            dataset = 0,
            externalStatus = "no status",
            iobSum = "IOB",
            iobDetail = "-.--",
            cob = "--g",
            currentBasal = "-.--U/h",
            battery = "--",
            rigBattery = "--",
            openApsStatus = -1,
            bgi = "--",
            batteryLevel = 1,
            patientName = ""
        ), EventData.Status(
            dataset = 1,
            externalStatus = "no status",
            iobSum = "IOB",
            iobDetail = "-.--",
            cob = "--g",
            currentBasal = "-.--U/h",
            battery = "--",
            rigBattery = "--",
            openApsStatus = -1,
            bgi = "--",
            batteryLevel = 1,
            patientName = "",
        ), EventData.Status(
            dataset = 2,
            externalStatus = "no status",
            iobSum = "IOB",
            iobDetail = "-.--",
            cob = "--g",
            currentBasal = "-.--U/h",
            battery = "--",
            rigBattery = "--",
            openApsStatus = -1,
            bgi = "--",
            batteryLevel = 1,
            patientName = "",
        )
    )

    @InternalSerializationApi
    var graphData = EventData.GraphData(
        entries = ArrayList()
    )

    @InternalSerializationApi
    var treatmentData = EventData.TreatmentData(
        temps = ArrayList(),
        basals = ArrayList(),
        boluses = ArrayList(),
        predictions = ArrayList()
    )

    @InternalSerializationApi
    fun toDebugString(): String =
        "DisplayRawData{singleBg=$singleBg, status=$status, graphData=$graphData, treatmentData=$treatmentData}"

    @InternalSerializationApi
    fun updateFromPersistence(persistence: Persistence) {
        persistence.readSingleBg(singleBg).let { singleBg = it }
        persistence.readGraphData()?.let { graphData = it }
        persistence.readStatus(status).let { status = it }
        persistence.readTreatments()?.let { treatmentData = it }
    }
}