package info.nightscout.androidaps.plugins.general.nsclient

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.DeviceStatus
import info.nightscout.androidaps.database.transactions.*
import info.nightscout.androidaps.interfaces.DataSyncSelector
import info.nightscout.androidaps.interfaces.DataSyncSelector.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.acks.NSAddAck
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientNewLog
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import javax.inject.Inject

class NSClientAddAckWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var dataWorker: DataWorker
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var dataSyncSelector: DataSyncSelector
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    override fun doWork(): Result {
        var ret = Result.success()

        val ack = dataWorker.pickupObject(inputData.getLong(DataWorker.STORE_KEY, -1)) as NSAddAck?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        when (ack.originalObject) {
            is PairTemporaryTarget       -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                repository.runTransactionForResult(UpdateNsIdTemporaryTargetTransaction(pair.value))
                    .doOnError { error ->
                        aapsLogger.error(LTag.DATABASE, "Updated ns id of TemporaryTarget failed", error)
                        ret = Result.failure((workDataOf("Error" to error.toString())))
                    }
                    .doOnSuccess {
                        ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
                        aapsLogger.debug(LTag.DATABASE, "Updated ns id of TemporaryTarget " + pair.value)
                        dataSyncSelector.confirmLastTempTargetsIdIfGreater(pair.updateRecordId)
                    }
                    .blockingGet()
                rxBus.send(EventNSClientNewLog("DBADD", "Acked TemporaryTarget " + pair.value.interfaceIDs.nightscoutId))
                // Send new if waiting
                dataSyncSelector.processChangedTempTargetsCompat()
            }

            is PairGlucoseValue          -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                repository.runTransactionForResult(UpdateNsIdGlucoseValueTransaction(pair.value))
                    .doOnError { error ->
                        aapsLogger.error(LTag.DATABASE, "Updated ns id of GlucoseValue failed", error)
                        ret = Result.failure((workDataOf("Error" to error.toString())))
                    }
                    .doOnSuccess {
                        ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
                        aapsLogger.debug(LTag.DATABASE, "Updated ns id of GlucoseValue " + pair.value)
                        dataSyncSelector.confirmLastGlucoseValueIdIfGreater(pair.updateRecordId)
                    }
                    .blockingGet()
                rxBus.send(EventNSClientNewLog("DBADD", "Acked GlucoseValue " + pair.value.interfaceIDs.nightscoutId))
                // Send new if waiting
                dataSyncSelector.processChangedGlucoseValuesCompat()
            }

            is PairFood                  -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                repository.runTransactionForResult(UpdateNsIdFoodTransaction(pair.value))
                    .doOnError { error ->
                        aapsLogger.error(LTag.DATABASE, "Updated ns id of Food failed", error)
                        ret = Result.failure((workDataOf("Error" to error.toString())))
                    }
                    .doOnSuccess {
                        ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
                        aapsLogger.debug(LTag.DATABASE, "Updated ns id of Food " + pair.value)
                        dataSyncSelector.confirmLastFoodIdIfGreater(pair.updateRecordId)
                    }
                    .blockingGet()
                rxBus.send(EventNSClientNewLog("DBADD", "Acked Food " + pair.value.interfaceIDs.nightscoutId))
                // Send new if waiting
                dataSyncSelector.processChangedFoodsCompat()
            }

            is PairTherapyEvent          -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                repository.runTransactionForResult(UpdateNsIdTherapyEventTransaction(pair.value))
                    .doOnError { error ->
                        aapsLogger.error(LTag.DATABASE, "Updated ns id of TherapyEvent failed", error)
                        ret = Result.failure((workDataOf("Error" to error.toString())))
                    }
                    .doOnSuccess {
                        ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
                        aapsLogger.debug(LTag.DATABASE, "Updated ns id of TherapyEvent " + pair.value)
                        dataSyncSelector.confirmLastTherapyEventIdIfGreater(pair.updateRecordId)
                    }
                    .blockingGet()
                rxBus.send(EventNSClientNewLog("DBADD", "Acked TherapyEvent " + pair.value.interfaceIDs.nightscoutId))
                // Send new if waiting
                dataSyncSelector.processChangedTherapyEventsCompat()
            }

            is PairBolus                 -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                repository.runTransactionForResult(UpdateNsIdBolusTransaction(pair.value))
                    .doOnError { error ->
                        aapsLogger.error(LTag.DATABASE, "Updated ns id of Bolus failed", error)
                        ret = Result.failure((workDataOf("Error" to error.toString())))
                    }
                    .doOnSuccess {
                        ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
                        aapsLogger.debug(LTag.DATABASE, "Updated ns id of Bolus " + pair.value)
                        dataSyncSelector.confirmLastBolusIdIfGreater(pair.updateRecordId)
                    }
                    .blockingGet()
                rxBus.send(EventNSClientNewLog("DBADD", "Acked Bolus " + pair.value.interfaceIDs.nightscoutId))
                // Send new if waiting
                dataSyncSelector.processChangedBolusesCompat()
            }

            is PairCarbs                 -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                repository.runTransactionForResult(UpdateNsIdCarbsTransaction(pair.value))
                    .doOnError { error ->
                        aapsLogger.error(LTag.DATABASE, "Updated ns id of Carbs failed", error)
                        ret = Result.failure((workDataOf("Error" to error.toString())))
                    }
                    .doOnSuccess {
                        ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
                        aapsLogger.debug(LTag.DATABASE, "Updated ns id of Carbs " + pair.value)
                        dataSyncSelector.confirmLastCarbsIdIfGreater(pair.updateRecordId)
                    }
                    .blockingGet()
                rxBus.send(EventNSClientNewLog("DBADD", "Acked Carbs " + pair.value.interfaceIDs.nightscoutId))
                // Send new if waiting
                dataSyncSelector.processChangedCarbsCompat()
            }

            is PairBolusCalculatorResult -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                repository.runTransactionForResult(UpdateNsIdBolusCalculatorResultTransaction(pair.value))
                    .doOnError { error ->
                        aapsLogger.error(LTag.DATABASE, "Updated ns id of BolusCalculatorResult failed", error)
                        ret = Result.failure((workDataOf("Error" to error.toString())))
                    }
                    .doOnSuccess {
                        ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
                        aapsLogger.debug(LTag.DATABASE, "Updated ns id of BolusCalculatorResult " + pair.value)
                        dataSyncSelector.confirmLastBolusCalculatorResultsIdIfGreater(pair.updateRecordId)
                    }
                    .blockingGet()
                rxBus.send(EventNSClientNewLog("DBADD", "Acked BolusCalculatorResult " + pair.value.interfaceIDs.nightscoutId))
                // Send new if waiting
                dataSyncSelector.processChangedBolusCalculatorResultsCompat()
            }

            is PairTemporaryBasal        -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                repository.runTransactionForResult(UpdateNsIdTemporaryBasalTransaction(pair.value))
                    .doOnError { error ->
                        aapsLogger.error(LTag.DATABASE, "Updated ns id of TemporaryBasal failed", error)
                        ret = Result.failure((workDataOf("Error" to error.toString())))
                    }
                    .doOnSuccess {
                        ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
                        aapsLogger.debug(LTag.DATABASE, "Updated ns id of TemporaryBasal " + pair.value)
                        dataSyncSelector.confirmLastTemporaryBasalIdIfGreater(pair.updateRecordId)
                    }
                    .blockingGet()
                rxBus.send(EventNSClientNewLog("DBADD", "Acked TemporaryBasal " + pair.value.interfaceIDs.nightscoutId))
                // Send new if waiting
                dataSyncSelector.processChangedTemporaryBasalsCompat()
            }

            is PairExtendedBolus         -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                repository.runTransactionForResult(UpdateNsIdExtendedBolusTransaction(pair.value))
                    .doOnError { error ->
                        aapsLogger.error(LTag.DATABASE, "Updated ns id of ExtendedBolus failed", error)
                        ret = Result.failure((workDataOf("Error" to error.toString())))
                    }
                    .doOnSuccess {
                        ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
                        aapsLogger.debug(LTag.DATABASE, "Updated ns id of ExtendedBolus " + pair.value)
                        dataSyncSelector.confirmLastExtendedBolusIdIfGreater(pair.updateRecordId)
                    }
                    .blockingGet()
                rxBus.send(EventNSClientNewLog("DBADD", "Acked ExtendedBolus " + pair.value.interfaceIDs.nightscoutId))
                // Send new if waiting
                dataSyncSelector.processChangedExtendedBolusesCompat()
            }

            is PairProfileSwitch         -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                repository.runTransactionForResult(UpdateNsIdProfileSwitchTransaction(pair.value))
                    .doOnError { error ->
                        aapsLogger.error(LTag.DATABASE, "Updated ns id of ProfileSwitch failed", error)
                        ret = Result.failure((workDataOf("Error" to error.toString())))
                    }
                    .doOnSuccess {
                        ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
                        aapsLogger.debug(LTag.DATABASE, "Updated ns id of ProfileSwitch " + pair.value)
                        dataSyncSelector.confirmLastProfileSwitchIdIfGreater(pair.updateRecordId)
                    }
                    .blockingGet()
                rxBus.send(EventNSClientNewLog("DBADD", "Acked ProfileSwitch " + pair.value.interfaceIDs.nightscoutId))
                // Send new if waiting
                dataSyncSelector.processChangedProfileSwitchesCompat()
            }

            is DeviceStatus              -> {
                val deviceStatus = ack.originalObject
                deviceStatus.interfaceIDs.nightscoutId = ack.id
                repository.runTransactionForResult(UpdateNsIdDeviceStatusTransaction(deviceStatus))
                    .doOnError { error ->
                        aapsLogger.error(LTag.DATABASE, "Updated ns id of DeviceStatus failed", error)
                        ret = Result.failure((workDataOf("Error" to error.toString())))
                    }
                    .doOnSuccess {
                        ret = Result.success(workDataOf("ProcessedData" to deviceStatus.toString()))
                        aapsLogger.debug(LTag.DATABASE, "Updated ns id of DeviceStatus $deviceStatus")
                        dataSyncSelector.confirmLastDeviceStatusIdIfGreater(deviceStatus.id)
                    }
                    .blockingGet()
                rxBus.send(EventNSClientNewLog("DBADD", "Acked DeviceStatus " + deviceStatus.interfaceIDs.nightscoutId))
                // Send new if waiting
                dataSyncSelector.processChangedDeviceStatusesCompat()
            }

            is PairProfileStore              -> {
                dataSyncSelector.confirmLastProfileStore(ack.originalObject.timestampSync)
                rxBus.send(EventNSClientNewLog("DBADD", "Acked ProfileStore " + ack.id))
            }

            is PairOfflineEvent       -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                repository.runTransactionForResult(UpdateNsIdOfflineEventTransaction(pair.value))
                    .doOnError { error ->
                        aapsLogger.error(LTag.DATABASE, "Updated ns id of OfflineEvent failed", error)
                        ret = Result.failure((workDataOf("Error" to error.toString())))
                    }
                    .doOnSuccess {
                        ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
                        aapsLogger.debug(LTag.DATABASE, "Updated ns id of OfflineEvent " + pair.value)
                        dataSyncSelector.confirmLastOfflineEventIdIfGreater(pair.updateRecordId)
                    }
                    .blockingGet()
                rxBus.send(EventNSClientNewLog("DBADD", "Acked OfflineEvent " + pair.value.interfaceIDs.nightscoutId))
                // Send new if waiting
                dataSyncSelector.processChangedOfflineEventsCompat()
            }

        }
        return ret
    }

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }
}