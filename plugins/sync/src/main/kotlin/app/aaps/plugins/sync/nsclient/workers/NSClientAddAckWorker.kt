package app.aaps.plugins.sync.nsclient.workers

import android.content.Context
import android.os.SystemClock
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import app.aaps.core.interfaces.sync.DataSyncSelector
import app.aaps.core.interfaces.sync.DataSyncSelector.PairBolus
import app.aaps.core.interfaces.sync.DataSyncSelector.PairBolusCalculatorResult
import app.aaps.core.interfaces.sync.DataSyncSelector.PairCarbs
import app.aaps.core.interfaces.sync.DataSyncSelector.PairEffectiveProfileSwitch
import app.aaps.core.interfaces.sync.DataSyncSelector.PairExtendedBolus
import app.aaps.core.interfaces.sync.DataSyncSelector.PairFood
import app.aaps.core.interfaces.sync.DataSyncSelector.PairGlucoseValue
import app.aaps.core.interfaces.sync.DataSyncSelector.PairProfileStore
import app.aaps.core.interfaces.sync.DataSyncSelector.PairProfileSwitch
import app.aaps.core.interfaces.sync.DataSyncSelector.PairTemporaryBasal
import app.aaps.core.interfaces.sync.DataSyncSelector.PairTemporaryTarget
import app.aaps.core.interfaces.sync.DataSyncSelector.PairTherapyEvent
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.utils.notifyAll
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.plugins.sync.nsclient.acks.NSAddAck
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class NSClientAddAckWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var storeDataForDb: StoreDataForDb

    override suspend fun doWorkAndLog(): Result {
        val ack = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as NSAddAck?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        if (preferences.get(BooleanKey.NsClientSlowSync)) SystemClock.sleep(1000)
        val ret = try {
            Result.success(workDataOf("ProcessedData" to ack.originalObject.toString()))
        } catch (_: Exception) {
            Result.success(workDataOf("ProcessedData" to "huge record"))
        }

        when (ack.originalObject) {
            is PairTemporaryTarget               -> {
                val pair = ack.originalObject
                pair.value.ids.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.addToNsIdTemporaryTargets(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked TemporaryTarget " + pair.value.ids.nightscoutId))
            }

            is PairGlucoseValue                  -> {
                val pair = ack.originalObject
                pair.value.ids.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.addToNsIdGlucoseValues(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked GlucoseValue " + pair.value.ids.nightscoutId))
            }

            is PairFood                          -> {
                val pair = ack.originalObject
                pair.value.ids.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.addToNsIdFoods(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked Food " + pair.value.ids.nightscoutId))
                // Send new if waiting
            }

            is PairTherapyEvent                  -> {
                val pair = ack.originalObject
                pair.value.ids.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.addToNsIdTherapyEvents(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked TherapyEvent " + pair.value.ids.nightscoutId))
            }

            is PairBolus                         -> {
                val pair = ack.originalObject
                pair.value.ids.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.addToNsIdBoluses(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked Bolus " + pair.value.ids.nightscoutId))
            }

            is PairCarbs                         -> {
                val pair = ack.originalObject
                pair.value.ids.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.addToNsIdCarbs(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked Carbs " + pair.value.ids.nightscoutId))
            }

            is PairBolusCalculatorResult         -> {
                val pair = ack.originalObject
                pair.value.ids.nightscoutId = ack.id
                storeDataForDb.addToNsIdBolusCalculatorResults(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked BolusCalculatorResult " + pair.value.ids.nightscoutId))
            }

            is PairTemporaryBasal                -> {
                val pair = ack.originalObject
                pair.value.ids.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.addToNsIdTemporaryBasals(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked TemporaryBasal " + pair.value.ids.nightscoutId))
            }

            is PairExtendedBolus                 -> {
                val pair = ack.originalObject
                pair.value.ids.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.addToNsIdExtendedBoluses(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked ExtendedBolus " + pair.value.ids.nightscoutId))
            }

            is PairProfileSwitch                 -> {
                val pair = ack.originalObject
                pair.value.ids.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.addToNsIdProfileSwitches(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked ProfileSwitch " + pair.value.ids.nightscoutId))
            }

            is PairEffectiveProfileSwitch        -> {
                val pair = ack.originalObject
                pair.value.ids.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.addToNsIdEffectiveProfileSwitches(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked EffectiveProfileSwitch " + pair.value.ids.nightscoutId))
            }

            is DataSyncSelector.PairDeviceStatus -> {
                val pair = ack.originalObject
                pair.value.ids.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.addToNsIdDeviceStatuses(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked DeviceStatus " + pair.value.ids.nightscoutId))
            }

            is PairProfileStore                  -> {
                val pair = ack.originalObject
                pair.confirmed = true
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked ProfileStore " + ack.id))
            }

            is DataSyncSelector.PairRunningMode -> {
                val pair = ack.originalObject
                pair.value.ids.nightscoutId = ack.id
                pair.confirmed = true
                storeDataForDb.addToNsIdRunningModes(pair.value)
                storeDataForDb.scheduleNsIdUpdate()
                rxBus.send(EventNSClientNewLog("◄ DBADD", "Acked RunningMode " + pair.value.ids.nightscoutId))
            }

        }
        ack.originalObject?.let { synchronized(it) { it.notifyAll() } }
        return ret
    }
}