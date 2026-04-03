package app.aaps.plugins.sync.nsclient.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.sync.DataSyncSelector
import app.aaps.core.interfaces.sync.DataSyncSelector.PairBolus
import app.aaps.core.interfaces.sync.DataSyncSelector.PairBolusCalculatorResult
import app.aaps.core.interfaces.sync.DataSyncSelector.PairCarbs
import app.aaps.core.interfaces.sync.DataSyncSelector.PairEffectiveProfileSwitch
import app.aaps.core.interfaces.sync.DataSyncSelector.PairExtendedBolus
import app.aaps.core.interfaces.sync.DataSyncSelector.PairFood
import app.aaps.core.interfaces.sync.DataSyncSelector.PairGlucoseValue
import app.aaps.core.interfaces.sync.DataSyncSelector.PairProfileSwitch
import app.aaps.core.interfaces.sync.DataSyncSelector.PairTemporaryBasal
import app.aaps.core.interfaces.sync.DataSyncSelector.PairTemporaryTarget
import app.aaps.core.interfaces.sync.DataSyncSelector.PairTherapyEvent
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.utils.notifyAll
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.plugins.sync.nsclient.acks.NSUpdateAck
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class NSClientUpdateRemoveAckWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var nsClientRepository: NSClientRepository

    override suspend fun doWorkAndLog(): Result {
        var ret = Result.success()

        val ack = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as NSUpdateAck?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        // new room way
        when (ack.originalObject) {
            is PairTemporaryTarget              -> {
                val pair = ack.originalObject
                pair.confirmed = true
                nsClientRepository.addLog("◄ DBUPDATE", "Acked TemporaryTarget" + ack._id)
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairGlucoseValue                 -> {
                val pair = ack.originalObject
                pair.confirmed = true
                nsClientRepository.addLog("◄ DBUPDATE", "Acked GlucoseValue " + ack._id)
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairFood                         -> {
                val pair = ack.originalObject
                pair.confirmed = true
                nsClientRepository.addLog("◄ DBUPDATE", "Acked Food " + ack._id)
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairTherapyEvent                 -> {
                val pair = ack.originalObject
                pair.confirmed = true
                nsClientRepository.addLog("◄ DBUPDATE", "Acked TherapyEvent " + ack._id)
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairBolus                        -> {
                val pair = ack.originalObject
                pair.confirmed = true
                nsClientRepository.addLog("◄ DBUPDATE", "Acked Bolus " + ack._id)
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairCarbs                        -> {
                val pair = ack.originalObject
                pair.confirmed = true
                nsClientRepository.addLog("◄ DBUPDATE", "Acked Carbs " + ack._id)
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairBolusCalculatorResult        -> {
                val pair = ack.originalObject
                pair.confirmed = true
                nsClientRepository.addLog("◄ DBUPDATE", "Acked BolusCalculatorResult " + ack._id)
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairTemporaryBasal               -> {
                val pair = ack.originalObject
                pair.confirmed = true
                nsClientRepository.addLog("◄ DBUPDATE", "Acked TemporaryBasal " + ack._id)
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairExtendedBolus                -> {
                val pair = ack.originalObject
                pair.confirmed = true
                nsClientRepository.addLog("◄ DBUPDATE", "Acked ExtendedBolus " + ack._id)
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairProfileSwitch                -> {
                val pair = ack.originalObject
                pair.confirmed = true
                nsClientRepository.addLog("◄ DBUPDATE", "Acked ProfileSwitch " + ack._id)
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is PairEffectiveProfileSwitch       -> {
                val pair = ack.originalObject
                pair.confirmed = true
                nsClientRepository.addLog("◄ DBUPDATE", "Acked EffectiveProfileSwitch " + ack._id)
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }

            is DataSyncSelector.PairRunningMode -> {
                val pair = ack.originalObject
                pair.confirmed = true
                nsClientRepository.addLog("◄ DBUPDATE", "Acked RunningMode" + ack._id)
                ret = Result.success(workDataOf("ProcessedData" to pair.toString()))
            }
        }
        ack.originalObject?.let { synchronized(it) { it.notifyAll() } }
        return ret
    }
}