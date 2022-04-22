package info.nightscout.androidaps.data

import android.content.Intent
import info.nightscout.androidaps.comm.DataLayerListenerServiceWear
import info.nightscout.androidaps.interaction.utils.Persistence
import info.nightscout.shared.weardata.EventData

/**
 * Holds bunch of data model variables and lists that arrive from phone app and are due to be
 * displayed on watchface and complications. Keeping them together makes code cleaner and allows
 * passing it to complications via persistence layer.
 *
 * Created by dlvoy on 2019-11-12
 */
class RawDisplayData {

    // bg data bundle
    var singleBg = EventData.SingleBg(
        timeStamp = 0,
        sgv = 0.0,
        high = 0.0,
        low = 0.0,
        color = 0
    )

    // status bundle
    var status = EventData.Status(
        externalStatus = "no status",
        iobSum = "IOB",
        iobDetail = "-.--",
        detailedIob = false,
        cob = "--g",
        currentBasal = "-.--U/h",
        battery = "--",
        rigBattery = "--",
        openApsStatus = -1,
        bgi = "--",
        showBgi = false,
        batteryLevel = 1
    )

    // basals bundle
    var graphData = EventData.GraphData(
        entries = ArrayList<EventData.SingleBg>()
    )

    var treatmentData = EventData.TreatmentData(
        temps = ArrayList<EventData.TreatmentData.TempBasal>(),
        basals = ArrayList<EventData.TreatmentData.Basal>(),
        boluses = ArrayList<EventData.TreatmentData.Treatment>(),
        predictions = ArrayList<EventData.SingleBg>()
    )

    fun toDebugString(): String =
        "DisplayRawData{singleBg=$singleBg, status=$status, graphData=$graphData, treatmentData=$treatmentData}"

    fun updateFromPersistence(persistence: Persistence) {
        persistence.readSingleBg()?.let { singleBg = it }
        persistence.readGraphData()?.let { graphData = it }
        persistence.readStatus()?.let { status = it }
        persistence.readTreatments()?.let { treatmentData = it }
    }

    /*
     * Since complications do not need Basals, we skip them for performance
     */
    fun updateForComplicationsFromPersistence(persistence: Persistence) {
        persistence.readSingleBg()?.let { singleBg = it }
        persistence.readGraphData()?.let { graphData = it }
        persistence.readStatus()?.let { status = it }
    }

    fun updateFromMessage(intent: Intent) {
        intent.getStringExtra(DataLayerListenerServiceWear.KEY_SINGLE_BG_DATA)?.let{
            singleBg = EventData.deserialize(it) as EventData.SingleBg
        }
        intent.getStringExtra(DataLayerListenerServiceWear.KEY_STATUS_DATA)?.let{
            status = EventData.deserialize(it) as EventData.Status
        }
        intent.getStringExtra(DataLayerListenerServiceWear.KEY_TREATMENTS_DATA)?.let{
            treatmentData = EventData.deserialize(it) as EventData.TreatmentData
        }
        intent.getStringExtra(DataLayerListenerServiceWear.KEY_GRAPH_DATA)?.let{
            graphData = EventData.deserialize(it) as EventData.GraphData
        }
    }

    companion object {

        const val BG_DATA_PERSISTENCE_KEY = "raw_data"
        const val GRAPH_DATA_PERSISTENCE_KEY = "raw_data"
        const val BASALS_PERSISTENCE_KEY = "raw_basals"
        const val STATUS_PERSISTENCE_KEY = "raw_status"
    }
}