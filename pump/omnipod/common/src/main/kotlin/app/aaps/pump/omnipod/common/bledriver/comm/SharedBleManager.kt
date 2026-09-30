package app.aaps.pump.omnipod.common.bledriver.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.BusyException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.CouldNotSendCommandException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.FailedToConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.MessageIOException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.NotConnectedException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.SessionEstablishmentException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandAckError
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandReceiveError
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandReceiveSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandSendErrorConfirming
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.session.Connected
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionState
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition
import app.aaps.pump.omnipod.common.bledriver.comm.session.NotConnected
import app.aaps.pump.omnipod.common.bledriver.event.PodEvent
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.Command
import app.aaps.pump.omnipod.common.bledriver.pod.response.Response
import io.reactivex.rxjava3.core.Observable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

abstract class SharedBleManager(
    protected val aapsLogger: AAPSLogger,
    protected val bleDeviceManager: BleDeviceManager
) : OmnipodBleManager {

    private val busy = AtomicBoolean(false)
    protected var connection: BleConnection? = null

    protected abstract val bluetoothAddress: String?
    protected abstract val ltk: ByteArray?
    protected abstract val ids: Ids
    protected abstract fun createConnection(podAddress: String): BleConnection
    protected abstract fun increaseEapAkaSequenceNumber(): ByteArray
    protected abstract fun updateEapAkaSequenceNumber(sequenceNumber: Long)
    protected abstract fun commitEapAkaSequenceNumber()
    protected abstract fun recordSuccessfulConnection()
    /**
     * Called on the subscription thread after a command write succeeds or its confirmation fails.
     * It is not called if sending the command fails.
     *
     * Exceptions are handled by the enclosing operation: the connection is disconnected and the
     * observable emits the error.
     */
    protected open fun onCommandWriteCompleted() = Unit

    /**
     * Called on the subscription thread before [PodEvent.ResponseReceived] is emitted.
     *
     * Exceptions are handled by the enclosing operation: the connection is disconnected and the
     * observable emits the error.
     */
    protected open fun onResponse(response: Response) = Unit

    /**
     * Called on the subscription thread after [PodEvent.ResponseReceived] is emitted.
     *
     * Exceptions are handled by the enclosing operation: the connection is disconnected and the
     * observable emits the error.
     */
    protected open fun onResponseRead() = Unit
    protected open val releaseBusyBeforeCompletion = false
    protected open val connectionName = "pod"

    final override fun sendCommand(cmd: Command, responseType: KClass<out Response>): Observable<PodEvent> =
        Observable.create { emitter ->
            acquireBusy()
            var busyReleasedBeforeCompletion = false
            try {
                val session = assertSessionEstablished()
                emitter.onNext(PodEvent.CommandSending(cmd))
                when (session.sendCommand(cmd)) {
                    is CommandSendErrorSending    -> {
                        emitter.tryOnError(CouldNotSendCommandException())
                        return@create
                    }

                    is CommandSendSuccess         -> {
                        emitter.onNext(PodEvent.CommandSent(cmd))
                        onCommandWriteCompleted()
                    }

                    is CommandSendErrorConfirming -> {
                        emitter.onNext(PodEvent.CommandSendNotConfirmed(cmd))
                        onCommandWriteCompleted()
                    }
                }
                when (val readResult = session.readAndAckResponse()) {
                    is CommandReceiveSuccess -> {
                        onResponse(readResult.result)
                        emitter.onNext(PodEvent.ResponseReceived(cmd, readResult.result))
                        onResponseRead()
                    }

                    is CommandAckError       -> {
                        onResponse(readResult.result)
                        emitter.onNext(PodEvent.ResponseReceived(cmd, readResult.result))
                        onResponseRead()
                    }

                    is CommandReceiveError   -> {
                        emitter.tryOnError(MessageIOException("Could not read response: $readResult"))
                        return@create
                    }
                }
                complete(emitter::onComplete) { busyReleasedBeforeCompletion = true }
            } catch (ex: Exception) {
                disconnect(false)
                emitter.tryOnError(ex)
            } finally {
                if (!busyReleasedBeforeCompletion) busy.set(false)
            }
        }

    final override fun getStatus(): ConnectionState = connection?.connectionState() ?: NotConnected

    final override fun connect(timeoutMs: Long): Observable<PodEvent> =
        connect(ConnectionWaitCondition(timeoutMs = timeoutMs))

    final override fun connect(stopConnectionLatch: CountDownLatch): Observable<PodEvent> =
        connect(ConnectionWaitCondition(stopConnection = stopConnectionLatch))

    private fun connect(connectionWaitCond: ConnectionWaitCondition): Observable<PodEvent> =
        Observable.create { emitter ->
            acquireBusy()
            var busyReleasedBeforeCompletion = false
            try {
                emitter.onNext(PodEvent.BluetoothConnecting)
                val address = bluetoothAddress
                    ?: throw FailedToConnectException("Missing bluetoothAddress, activate the pod first")
                if (!bleDeviceManager.isBluetoothAvailable()) throw ConnectException("Bluetooth not available")
                if (!bleDeviceManager.ensureBondedIfRequired(address)) {
                    throw ConnectException("Bluetooth not available or bonding failed")
                }
                val conn = connection ?: createConnection(address).also { connection = it }
                if (conn.connectionState() is Connected && conn.session != null) {
                    emitter.onNext(PodEvent.AlreadyConnected(address))
                    complete(emitter::onComplete) { busyReleasedBeforeCompletion = true }
                    return@create
                }
                conn.connect(connectionWaitCond)
                emitter.onNext(PodEvent.BluetoothConnected(address))
                emitter.onNext(PodEvent.EstablishingSession)
                establishSession(1.toByte())
                emitter.onNext(PodEvent.Connected)
                complete(emitter::onComplete) { busyReleasedBeforeCompletion = true }
            } catch (ex: Exception) {
                disconnect(false)
                emitter.tryOnError(ex)
            } finally {
                if (!busyReleasedBeforeCompletion) busy.set(false)
            }
        }

    protected fun establishSession(msgSeq: Byte) {
        val conn = assertConnected()
        val pairedLtk = ltk ?: throw FailedToConnectException("Missing LTK, activate the $connectionName first")
        var newSequenceNumber = conn.establishSession(pairedLtk, msgSeq, ids, increaseEapAkaSequenceNumber())
        if (newSequenceNumber != null) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Updating $connectionName EAP SQN to: $newSequenceNumber")
            updateEapAkaSequenceNumber(newSequenceNumber.toLong())
            newSequenceNumber = conn.establishSession(pairedLtk, msgSeq, ids, increaseEapAkaSequenceNumber())
            if (newSequenceNumber != null) {
                throw SessionEstablishmentException("Received resynchronization SQN for the second time")
            }
        }
        recordSuccessfulConnection()
        commitEapAkaSequenceNumber()
    }

    protected fun acquireBusy() {
        if (!busy.compareAndSet(false, true)) throw BusyException()
    }

    protected fun releaseBusy() {
        busy.set(false)
    }

    private fun complete(onComplete: () -> Unit, onBusyReleased: () -> Unit) {
        if (releaseBusyBeforeCompletion) {
            busy.set(false)
            onBusyReleased()
        }
        onComplete()
    }

    protected fun assertSessionEstablished() =
        assertConnected().session ?: throw NotConnectedException("Missing session")

    protected fun assertConnected(): BleConnection =
        connection ?: throw FailedToConnectException("connection lost")

    final override fun disconnect(closeGatt: Boolean) {
        connection?.disconnect(closeGatt)
            ?: aapsLogger.info(LTag.PUMPBTCOMM, "Trying to disconnect a null $connectionName connection")
    }

    final override fun removeBond() {
        val address = bluetoothAddress
        if (address == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "removeBond ($connectionName): MAC address not found")
            return
        }
        bleDeviceManager.removeBond(address)
    }
}
