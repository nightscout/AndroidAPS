@file:OptIn(ExperimentalUuidApi::class)

package app.aaps.pump.carelevo.ble.gatt

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.BleLink
import app.aaps.core.interfaces.pump.ble.BleResult
import app.aaps.core.interfaces.pump.ble.CharacteristicProperty
import app.aaps.core.interfaces.pump.ble.WriteType
import app.aaps.pump.carelevo.ble.CarelevoGatt
import app.aaps.pump.carelevo.ext.convertBytesToHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * [GattConnection] over a Bluetooth v2 [BleLink]: the seam `BleClientImpl` works on, now on the link every platform
 * shares. The link runs one operation at a time, ends each one with its result and keeps incoming data, so this class
 * only translates.
 *
 * [events] starts to forward when its first collector is there (the client's router). Data that comes before waits
 * in the link, so nothing is lost the way a hot flow with no collector loses it. The link's data and its end are
 * forwarded by one coroutine, in order: everything the patch sent comes before the disconnect.
 *
 * A write uses the type Android takes by default for the write characteristic, as the transport before did: without
 * response when the characteristic offers it, else with response.
 */
class BleLinkGattConnection(
    private val link: BleLink,
    private val scope: CoroutineScope,
    private val aapsLogger: AAPSLogger
) : GattConnection {

    private val _events = MutableSharedFlow<GattEvent>(extraBufferCapacity = EVENT_BUFFER)
    override val events: SharedFlow<GattEvent> = _events.asSharedFlow()

    private val writeType: WriteType =
        if (CharacteristicProperty.WRITE_WITHOUT_RESPONSE in link.services.characteristics[CarelevoGatt.WRITE].orEmpty()) WriteType.WITHOUT_RESPONSE
        else WriteType.WITH_RESPONSE

    init {
        scope.launch {
            _events.subscriptionCount.first { it > 0 }
            // Ends when the link is dead: the link closes its incoming data then.
            link.incoming.collect { incoming ->
                if (incoming.characteristic != CarelevoGatt.NOTIFY) return@collect
                aapsLogger.debug(LTag.PUMPBTCOMM, "incoming: ${incoming.data.convertBytesToHex()}")
                _events.emit(GattEvent.Notification(NOTIFY_UUID, incoming.data))
            }
            _events.emit(GattEvent.ConnectionStateChanged(GattConnState.DISCONNECTED))
        }
    }

    override suspend fun writeCharacteristic(uuid: Uuid, payload: ByteArray, withResponse: Boolean) {
        require(uuid == WRITE_UUID) { "unexpected write characteristic $uuid; the patch has a single write characteristic $WRITE_UUID" }
        val result = link.write(CarelevoGatt.WRITE, payload, writeType)
        if (result is BleResult.Failed) throw GattWriteException("write failed: ${result.failure}")
    }

    /** The link discovered the services when it was made; this checks the patch has its two characteristics. */
    override suspend fun discoverServices() {
        if (!link.services.has(CarelevoGatt.WRITE) || !link.services.has(CarelevoGatt.NOTIFY))
            throw GattDiscoveryException("not a CareLevo patch: its service is missing")
    }

    override suspend fun enableNotifications(uuid: Uuid) {
        require(uuid == NOTIFY_UUID) { "unexpected notify characteristic $uuid; the patch has a single notify characteristic $NOTIFY_UUID" }
        val result = link.subscribe(CarelevoGatt.NOTIFY)
        if (result is BleResult.Failed) throw GattDiscoveryException("notifications not enabled: ${result.failure}")
    }

    /**
     * Ends the link. Safe to call more than once; an operation waiting on the link fails with it.
     * The close runs also when [scope] is already cancelled: it starts in any case and is not cut once started.
     */
    override fun close() {
        scope.launch(start = CoroutineStart.ATOMIC) { withContext(NonCancellable) { link.close() } }
    }

    /** Closes the link before the caller goes on, as a session ends: the next connect must find it closed. */
    suspend fun closeNow() = withContext(NonCancellable) { link.close() }

    companion object {

        val WRITE_UUID: Uuid = Uuid.parse(CarelevoGatt.WRITE.characteristic.value)
        val NOTIFY_UUID: Uuid = Uuid.parse(CarelevoGatt.NOTIFY.characteristic.value)

        private const val EVENT_BUFFER = 64
    }
}
