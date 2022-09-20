package info.nightscout.androidaps.plugins.sync.nsclient

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.interfaces.DataSyncSelector
import info.nightscout.androidaps.interfaces.DataSyncSelector.*
import info.nightscout.androidaps.interfaces.NsClient
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.sync.nsclient.acks.NSUpdateAck
import info.nightscout.androidaps.plugins.sync.nsclient.events.EventNSClientNewLog
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import javax.inject.Inject

class NSClientUpdateRemoveAckWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var dataWorker: DataWorker
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var dataSyncSelector: DataSyncSelector
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    override fun doWork(): Result {
        var ret = Result.success()

        val ack = dataWorker.pickupObject(inputData.getLong(DataWorker.STORE_KEY, -1)) as NSUpdateAck?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        // new room way
        when (ack.originalObject) {
            is PairTemporaryTarget       -> {
                val pair = ack.originalObject
                dataSyncSelector.confirmLastTempTargetsIdIfGreater(pair.updateRecordId)
                rxBus.send(EventNSClientNewLog("DBUPDATE", "Acked TemporaryTarget" + ack._id, NsClient.Version.V1))
                // Send new if waiting
                dataSyncSelector.processChangedTempTargetsCompat()
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairGlucoseValue          -> {
                val pair = ack.originalObject
                dataSyncSelector.confirmLastGlucoseValueIdIfGreater(pair.updateRecordId)
                rxBus.send(EventNSClientNewLog("DBUPDATE", "Acked GlucoseValue " + ack._id, NsClient.Version.V1))
                // Send new if waiting
                dataSyncSelector.processChangedGlucoseValuesCompat()
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairFood                  -> {
                val pair = ack.originalObject
                dataSyncSelector.confirmLastFoodIdIfGreater(pair.updateRecordId)
                rxBus.send(EventNSClientNewLog("DBUPDATE", "Acked Food " + ack._id, NsClient.Version.V1))
                // Send new if waiting
                dataSyncSelector.processChangedFoodsCompat()
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairTherapyEvent          -> {
                val pair = ack.originalObject
                dataSyncSelector.confirmLastTherapyEventIdIfGreater(pair.updateRecordId)
                rxBus.send(EventNSClientNewLog("DBUPDATE", "Acked TherapyEvent " + ack._id, NsClient.Version.V1))
                // Send new if waiting
                dataSyncSelector.processChangedTherapyEventsCompat()
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairBolus                 -> {
                val pair = ack.originalObject
                dataSyncSelector.confirmLastBolusIdIfGreater(pair.updateRecordId)
                rxBus.send(EventNSClientNewLog("DBUPDATE", "Acked Bolus " + ack._id, NsClient.Version.V1))
                // Send new if waiting
                dataSyncSelector.processChangedBolusesCompat()
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairCarbs                 -> {
                val pair = ack.originalObject
                dataSyncSelector.confirmLastCarbsIdIfGreater(pair.updateRecordId)
                rxBus.send(EventNSClientNewLog("DBUPDATE", "Acked Carbs " + ack._id, NsClient.Version.V1))
                // Send new if waiting
                dataSyncSelector.processChangedCarbsCompat()
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairBolusCalculatorResult -> {
                val pair = ack.originalObject
                dataSyncSelector.confirmLastBolusCalculatorResultsIdIfGreater(pair.updateRecordId)
                rxBus.send(EventNSClientNewLog("DBUPDATE", "Acked BolusCalculatorResult " + ack._id, NsClient.Version.V1))
                // Send new if waiting
                dataSyncSelector.processChangedBolusCalculatorResultsCompat()
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairTemporaryBasal        -> {
                val pair = ack.originalObject
                dataSyncSelector.confirmLastTemporaryBasalIdIfGreater(pair.updateRecordId)
                rxBus.send(EventNSClientNewLog("DBUPDATE", "Acked TemporaryBasal " + ack._id, NsClient.Version.V1))
                // Send new if waiting
                dataSyncSelector.processChangedTemporaryBasalsCompat()
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairExtendedBolus         -> {
                val pair = ack.originalObject
                dataSyncSelector.confirmLastExtendedBolusIdIfGreater(pair.updateRecordId)
                rxBus.send(EventNSClientNewLog("DBUPDATE", "Acked ExtendedBolus " + ack._id, NsClient.Version.V1))
                // Send new if waiting
                dataSyncSelector.processChangedExtendedBolusesCompat()
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairProfileSwitch         -> {
                val pair = ack.originalObject
                dataSyncSelector.confirmLastProfileSwitchIdIfGreater(pair.updateRecordId)
                rxBus.send(EventNSClientNewLog("DBUPDATE", "Acked ProfileSwitch " + ack._id, NsClient.Version.V1))
                // Send new if waiting
                dataSyncSelector.processChangedProfileSwitchesCompat()
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairEffectiveProfileSwitch         -> {
                val pair = ack.originalObject
                dataSyncSelector.confirmLastEffectiveProfileSwitchIdIfGreater(pair.updateRecordId)
                rxBus.send(EventNSClientNewLog("DBUPDATE", "Acked EffectiveProfileSwitch " + ack._id, NsClient.Version.V1))
                // Send new if waiting
                dataSyncSelector.processChangedEffectiveProfileSwitchesCompat()
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairOfflineEvent       -> {
                val pair = ack.originalObject
                dataSyncSelector.confirmLastOfflineEventIdIfGreater(pair.updateRecordId)
                rxBus.send(EventNSClientNewLog("DBUPDATE", "Acked OfflineEvent" + ack._id, NsClient.Version.V1))
                // Send new if waiting
                dataSyncSelector.processChangedOfflineEventsCompat()
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }
        }
        return ret
    }

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }
}