package info.nightscout.androidaps.plugins.sync.nsclient

import android.content.Context
import android.os.SystemClock
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.DeviceStatus
import info.nightscout.androidaps.database.transactions.*
import info.nightscout.androidaps.interfaces.DataSyncSelector
import info.nightscout.androidaps.interfaces.DataSyncSelector.*
import info.nightscout.androidaps.interfaces.NsClient
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.sync.nsclient.acks.NSAddAck
import info.nightscout.androidaps.plugins.sync.nsclient.events.EventNSClientNewLog
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject

class NSClientAddAckWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var dataWorker: DataWorker
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var dataSyncSelector: DataSyncSelector
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var sp: SP

    override fun doWork(): Result {
        var ret = Result.success()

        val ack = dataWorker.pickupObject(inputData.getLong(DataWorker.STORE_KEY, -1)) as NSAddAck?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        if (sp.getBoolean(R.string.key_ns_sync_slow, false)) SystemClock.sleep(1000)

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
                rxBus.send(EventNSClientNewLog("DBADD", "Acked TemporaryTarget " + pair.value.interfaceIDs.nightscoutId, NsClient.Version.V1))
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
                rxBus.send(EventNSClientNewLog("DBADD", "Acked GlucoseValue " + pair.value.interfaceIDs.nightscoutId, NsClient.Version.V1))
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
                rxBus.send(EventNSClientNewLog("DBADD", "Acked Food " + pair.value.interfaceIDs.nightscoutId, NsClient.Version.V1))
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
                rxBus.send(EventNSClientNewLog("DBADD", "Acked TherapyEvent " + pair.value.interfaceIDs.nightscoutId, NsClient.Version.V1))
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
                rxBus.send(EventNSClientNewLog("DBADD", "Acked Bolus " + pair.value.interfaceIDs.nightscoutId, NsClient.Version.V1))
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
                rxBus.send(EventNSClientNewLog("DBADD", "Acked Carbs " + pair.value.interfaceIDs.nightscoutId, NsClient.Version.V1))
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
                rxBus.send(EventNSClientNewLog("DBADD", "Acked BolusCalculatorResult " + pair.value.interfaceIDs.nightscoutId, NsClient.Version.V1))
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
                rxBus.send(EventNSClientNewLog("DBADD", "Acked TemporaryBasal " + pair.value.interfaceIDs.nightscoutId, NsClient.Version.V1))
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
                rxBus.send(EventNSClientNewLog("DBADD", "Acked ExtendedBolus " + pair.value.interfaceIDs.nightscoutId, NsClient.Version.V1))
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
                rxBus.send(EventNSClientNewLog("DBADD", "Acked ProfileSwitch " + pair.value.interfaceIDs.nightscoutId, NsClient.Version.V1))
                // Send new if waiting
                dataSyncSelector.processChangedProfileSwitchesCompat()
            }

            is PairEffectiveProfileSwitch         -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                repository.runTransactionForResult(UpdateNsIdEffectiveProfileSwitchTransaction(pair.value))
                    .doOnError { error ->
                        aapsLogger.error(LTag.DATABASE, "Updated ns id of EffectiveProfileSwitch failed", error)
                        ret = Result.failure((workDataOf("Error" to error.toString())))
                    }
                    .doOnSuccess {
                        ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
                        aapsLogger.debug(LTag.DATABASE, "Updated ns id of EffectiveProfileSwitch " + pair.value)
                        dataSyncSelector.confirmLastEffectiveProfileSwitchIdIfGreater(pair.updateRecordId)
                    }
                    .blockingGet()
                rxBus.send(EventNSClientNewLog("DBADD", "Acked EffectiveProfileSwitch " + pair.value.interfaceIDs.nightscoutId, NsClient.Version.V1))
                // Send new if waiting
                dataSyncSelector.processChangedEffectiveProfileSwitchesCompat()
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
                rxBus.send(EventNSClientNewLog("DBADD", "Acked DeviceStatus " + deviceStatus.interfaceIDs.nightscoutId, NsClient.Version.V1))
                // Send new if waiting
                dataSyncSelector.processChangedDeviceStatusesCompat()
            }

            is PairProfileStore              -> {
                dataSyncSelector.confirmLastProfileStore(ack.originalObject.timestampSync)
                rxBus.send(EventNSClientNewLog("DBADD", "Acked ProfileStore " + ack.id, NsClient.Version.V1))
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
                rxBus.send(EventNSClientNewLog("DBADD", "Acked OfflineEvent " + pair.value.interfaceIDs.nightscoutId, NsClient.Version.V1))
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