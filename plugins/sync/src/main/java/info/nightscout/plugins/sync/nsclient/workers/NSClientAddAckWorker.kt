package info.nightscout.plugins.sync.nsclient.workers

import android.content.Context
import android.os.SystemClock
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import info.nightscout.core.utils.notify
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.nsclient.StoreDataForDb
import info.nightscout.interfaces.sync.DataSyncSelector
import info.nightscout.interfaces.sync.DataSyncSelector.PairBolus
import info.nightscout.interfaces.sync.DataSyncSelector.PairBolusCalculatorResult
import info.nightscout.interfaces.sync.DataSyncSelector.PairCarbs
import info.nightscout.interfaces.sync.DataSyncSelector.PairEffectiveProfileSwitch
import info.nightscout.interfaces.sync.DataSyncSelector.PairExtendedBolus
import info.nightscout.interfaces.sync.DataSyncSelector.PairFood
import info.nightscout.interfaces.sync.DataSyncSelector.PairGlucoseValue
import info.nightscout.interfaces.sync.DataSyncSelector.PairOfflineEvent
import info.nightscout.interfaces.sync.DataSyncSelector.PairProfileStore
import info.nightscout.interfaces.sync.DataSyncSelector.PairProfileSwitch
import info.nightscout.interfaces.sync.DataSyncSelector.PairTemporaryBasal
import info.nightscout.interfaces.sync.DataSyncSelector.PairTemporaryTarget
import info.nightscout.interfaces.sync.DataSyncSelector.PairTherapyEvent
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.nsclient.acks.NSAddAck
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.shared.sharedPreferences.SP
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class NSClientAddAckWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var sp: SP
    @Inject lateinit var storeDataForDb: StoreDataForDb

    override suspend fun doWorkAndLog(): Result {
        val ack = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as NSAddAck?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        if (sp.getBoolean(R.string.key_ns_sync_slow, false)) SystemClock.sleep(1000)
        val ret = try {
            Result.success(workDataOf("ProcessedData" to ack.originalObject.toString()))
        } catch (e: Exception) {
            Result.success(workDataOf("ProcessedData" to "huge record"))
        }

        when (ack.originalObject) {
            is PairTemporaryTarget        -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.nsIdTemporaryTargets.add(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked TemporaryTarget " + pair.value.interfaceIDs.nightscoutId))
            }

            is PairGlucoseValue           -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.nsIdGlucoseValues.add(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked GlucoseValue " + pair.value.interfaceIDs.nightscoutId))
            }

            is PairFood                   -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.nsIdFoods.add(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked Food " + pair.value.interfaceIDs.nightscoutId))
                // Send new if waiting
            }

            is PairTherapyEvent           -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.nsIdTherapyEvents.add(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked TherapyEvent " + pair.value.interfaceIDs.nightscoutId))
            }

            is PairBolus                  -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.nsIdBoluses.add(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked Bolus " + pair.value.interfaceIDs.nightscoutId))
            }

            is PairCarbs                  -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.nsIdCarbs.add(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked Carbs " + pair.value.interfaceIDs.nightscoutId))
            }

            is PairBolusCalculatorResult  -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                storeDataForDb.nsIdBolusCalculatorResults.add(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked BolusCalculatorResult " + pair.value.interfaceIDs.nightscoutId))
            }

            is PairTemporaryBasal         -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.nsIdTemporaryBasals.add(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked TemporaryBasal " + pair.value.interfaceIDs.nightscoutId))
            }

            is PairExtendedBolus          -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.nsIdExtendedBoluses.add(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked ExtendedBolus " + pair.value.interfaceIDs.nightscoutId))
            }

            is PairProfileSwitch          -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.nsIdProfileSwitches.add(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked ProfileSwitch " + pair.value.interfaceIDs.nightscoutId))
            }

            is PairEffectiveProfileSwitch -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.nsIdEffectiveProfileSwitches.add(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked EffectiveProfileSwitch " + pair.value.interfaceIDs.nightscoutId))
            }

            is DataSyncSelector.PairDeviceStatus -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.nsIdDeviceStatuses.add(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked DeviceStatus " + pair.value.interfaceIDs.nightscoutId))
            }

            is PairProfileStore           -> {
                val pair = ack.originalObject
                pair.confirmed = true
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked ProfileStore " + ack.id))
            }

            is PairOfflineEvent           -> {
                val pair = ack.originalObject
                pair.value.interfaceIDs.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.nsIdOfflineEvents.add(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked OfflineEvent " + pair.value.interfaceIDs.nightscoutId))
            }

        }
        ack.originalObject?.let { synchronized(it) { it.notify() } }
        return ret
    }
}