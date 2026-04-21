package info.nightscout.androidaps.plugins.pump.carelevo.coordinator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.CarelevoBleController
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.Connect
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.DiscoveryService
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.EnableNotifications
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.CommandResult
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isAbnormalBondingFailed
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isDiscoverCleared
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.isReInitialized
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.shouldBeConnected
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.shouldBeDiscovered
import info.nightscout.androidaps.plugins.pump.carelevo.common.CarelevoPatch
import info.nightscout.androidaps.plugins.pump.carelevo.common.model.PatchState
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch.CarelevoRequestPatchInfusionInfoUseCase
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull

@Singleton
class CarelevoConnectionCoordinator @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val commandQueue: CommandQueue,
    private val carelevoPatch: CarelevoPatch,
    private val bleController: CarelevoBleController,
    private val requestPatchInfusionInfoUseCase: CarelevoRequestPatchInfusionInfoUseCase
) {

    private var queueStuckSince: Long? = null
    private var reconnectDisposable = CompositeDisposable()

    fun onStop() {
        aapsLogger.debug(LTag.PUMPCOMM, "onStop.clearReconnectDisposable")
        reconnectDisposable.clear()
    }

    fun isInitialized(): Boolean {
        val patchInfo = carelevoPatch.patchInfo.value?.getOrNull() ?: return false
        val address = patchInfo.address.uppercase()
        val hasOperationalState =
            patchInfo.mode != null ||
                patchInfo.runningMinutes != null ||
                patchInfo.pumpState != null

        aapsLogger.debug(
            LTag.PUMPCOMM,
            "isInitialized.check " +
                "address=$address, " +
                "mode=${patchInfo.mode}, " +
                "runningMinutes=${patchInfo.runningMinutes}, " +
                "pumpState=${patchInfo.pumpState}, " +
                "hasOperationalState=$hasOperationalState"
        )

        return hasOperationalState && carelevoPatch.isBleConnectedNow(address)
    }

    fun isConnected(): Boolean {
        val address = carelevoPatch.patchInfo.value?.getOrNull()?.address?.uppercase()
        aapsLogger.debug(LTag.PUMPCOMM, "isConnected.check address=$address")
        if (address == null) {
            return true // Keep the command loop from spinning when no address is available yet.
        }
        return carelevoPatch.isBleConnectedNow(address)
    }

    fun connect(reason: String, txUuid: UUID, onLastDataUpdated: () -> Unit) {
        aapsLogger.debug(LTag.PUMPCOMM, "connect.start reason=$reason")

        val patchState = carelevoPatch.resolvePatchState()
        aapsLogger.debug(LTag.PUMPCOMM, "connect.state reason=$reason patchState=$patchState")

        if (reason == "Connection needed" && patchState == PatchState.NotConnectedBooted) {
            onLastDataUpdated()
            startReconnection(txUuid)
        }
    }

    fun disconnect(reason: String) {
        val patchState = carelevoPatch.patchState.value?.getOrNull()
        aapsLogger.debug(LTag.PUMPCOMM, "disconnect.start reason=$reason patchState=$patchState")
    }

    fun stopConnecting() {
        aapsLogger.debug(LTag.PUMPCOMM, "stopConnecting.called")
    }

    fun refreshPumpStatus(
        pluginDisposable: CompositeDisposable,
        onLastDataUpdated: () -> Unit
    ) {
        if (!carelevoPatch.isBluetoothEnabled()) return
        if (!carelevoPatch.isCarelevoConnected()) return

        pluginDisposable += requestPatchInfusionInfoUseCase.execute()
            .observeOn(aapsSchedulers.main)
            .subscribeOn(aapsSchedulers.io)
            .timeout(3000L, TimeUnit.MILLISECONDS)
            .subscribe { response ->
                when (response) {
                    is ResponseResult.Success -> {
                        onLastDataUpdated()
                        aapsLogger.debug(LTag.PUMPCOMM, "getPumpStatus.success")
                    }

                    is ResponseResult.Error -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "getPumpStatus.responseError error=${response.e}")
                    }

                    else -> {
                        aapsLogger.debug(LTag.PUMPCOMM, "getPumpStatus.failure")
                    }
                }
            }
    }

    fun startReconnection(txUuid: UUID) {
        reconnectDisposable.clear()
        aapsLogger.debug(LTag.PUMPCOMM, "reconnect.start")

        if (!carelevoPatch.isBluetoothEnabled()) {
            aapsLogger.debug(LTag.PUMPCOMM, "reconnect.skip reason=bluetoothDisabled")
            return
        }

        val address = carelevoPatch.patchInfo.value?.getOrNull()?.address?.uppercase() ?: return
        aapsLogger.debug(LTag.PUMPCOMM, "reconnect.target address=$address")

        reconnectDisposable.add(
            bleController.execute(Connect(address))
                .subscribeOn(aapsSchedulers.io)
                .observeOn(aapsSchedulers.io)
                .subscribe(
                    { result ->
                        when (result) {
                            is CommandResult.Success -> {
                                aapsLogger.debug(LTag.PUMPCOMM, "reconnect.connect.success")
                            }

                            else -> {
                                aapsLogger.error(LTag.PUMPCOMM, "reconnect.connect.failure result=$result")
                                stopReconnection()
                            }
                        }
                    },
                    { e ->
                        aapsLogger.error(LTag.PUMPCOMM, "reconnect.connect.error error=$e")
                        stopReconnection()
                    }
                )
        )

        reconnectDisposable.add(
            carelevoPatch.btState
                .subscribeOn(aapsSchedulers.io)
                .observeOn(aapsSchedulers.io)
                .distinctUntilChanged()
                .timeout(10, TimeUnit.SECONDS)
                .subscribe(
                    { btState ->
                        btState.getOrNull()?.let { state ->
                            when {
                                state.shouldBeConnected() -> {
                                    aapsLogger.debug(LTag.PUMPCOMM, "reconnect.state connected")

                                    reconnectDisposable.add(
                                        bleController.execute(DiscoveryService(address))
                                            .subscribeOn(aapsSchedulers.io)
                                            .observeOn(aapsSchedulers.io)
                                            .subscribe { result ->
                                                if (result !is CommandResult.Success) {
                                                    aapsLogger.error(LTag.PUMPCOMM, "reconnect.discovery.failure result=$result")
                                                    stopReconnection()
                                                }
                                            }
                                    )
                                }

                                state.shouldBeDiscovered() -> {
                                    aapsLogger.debug(LTag.PUMPCOMM, "reconnect.state discovered")
                                    reconnectDisposable.add(
                                        bleController.execute(EnableNotifications(address, txUuid))
                                            .subscribeOn(aapsSchedulers.io)
                                            .observeOn(aapsSchedulers.io)
                                            .subscribe { result ->
                                                aapsLogger.debug(LTag.PUMPCOMM, "reconnect.enableNotifications result=$result")
                                                if (result !is CommandResult.Success) {
                                                    stopReconnection()
                                                } else {
                                                    aapsLogger.debug(LTag.PUMPCOMM, "reconnect.finished")
                                                    stopReconnection()
                                                }
                                            }
                                    )
                                }

                                state.isDiscoverCleared() ||
                                    state.isAbnormalBondingFailed() ||
                                    state.isReInitialized() -> {
                                    aapsLogger.error(LTag.PUMPCOMM, "reconnect.abnormalState state=$state")
                                    stopReconnection()
                                }
                            }
                        }
                    },
                    { e ->
                        aapsLogger.error(LTag.PUMPCOMM, "reconnect.observe.error error=$e")
                        stopReconnection()
                    }
                )
        )
    }

    private fun stopReconnection() {
        aapsLogger.debug(LTag.PUMPCOMM, "reconnect.stop")
        reconnectDisposable.clear()
    }

    private fun forceQueueClear() {
        val running = Command.CommandType.entries
            .filter { commandQueue.isRunning(it) }

        if (running.isEmpty()) {
            queueStuckSince = null
            return
        }

        if (running.any { it == Command.CommandType.BOLUS || it == Command.CommandType.SMB_BOLUS }) {
            queueStuckSince = null
            return
        }

        val now = System.currentTimeMillis()
        if (queueStuckSince == null) {
            queueStuckSince = now
            return
        }

        val elapsed = now - queueStuckSince!!
        val isOnlyBasalProfileRunning = running.size == 1 && running[0] == Command.CommandType.BASAL_PROFILE
        val timeoutMs = if (isOnlyBasalProfileRunning) 30_000L else 5 * 60 * 1000L
        if (elapsed > timeoutMs) {
            aapsLogger.error(
                LTag.PUMPCOMM,
                "queue.forceReset elapsedSec=${elapsed / 1000} timeoutMs=$timeoutMs running=${running.joinToString { it.name }}"
            )
            commandQueue.resetPerforming()
            commandQueue.clear()
            queueStuckSince = null
        }
    }
}
