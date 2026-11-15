package app.aaps.pump.carelevo.ble.gatt

import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleGatt
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.pump.carelevo.ble.BleClientImpl
import app.aaps.pump.carelevo.ble.BleCommand
import app.aaps.pump.carelevo.ble.BleResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

/**
 * Verifies [BleTransportGattConnection] honours the [GattConnection] contract when driven by the
 * shared [BleTransport] listener callbacks, and — the real Phase-1 goal — that the unchanged
 * [BleClientImpl] correlates a request/response end-to-end over the adapter.
 *
 * Uses `runTest` virtual time (no real waits), matching [app.aaps.pump.carelevo.ble.BleClientContractTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BleTransportGattConnectionTest {

    private val writeUuid: UUID = UUID.fromString("e1b40002-ffc4-4daa-a49b-1c92f99072ab")
    private val notifyUuid: UUID = UUID.fromString("e1b40003-ffc4-4daa-a49b-1c92f99072ab")

    private lateinit var transport: FakeBleTransport

    @BeforeEach
    fun setUp() {
        transport = FakeBleTransport()
    }

    private fun TestScope.newAdapter(): BleTransportGattConnection =
        BleTransportGattConnection(transport, writeUuid, notifyUuid, backgroundScope)

    @Test
    fun `registers itself as the transport listener on construction`() = runTest {
        val adapter = newAdapter()
        assertThat(transport.capturedListener).isSameInstanceAs(adapter)
    }

    @Test
    fun `writeCharacteristic returns once the transport acks the write`() = runTest {
        val adapter = newAdapter()
        transport.onWrite = { transport.capturedListener?.onCharacteristicWritten() }

        adapter.writeCharacteristic(writeUuid, byteArrayOf(0x24, 0x01))

        assertThat(transport.writes).hasSize(1)
        assertThat(transport.writes.single()).isEqualTo(byteArrayOf(0x24, 0x01))
    }

    @Test
    fun `writeCharacteristic on a not-connected transport fast-fails with GattWriteException`() = runTest {
        val adapter = newAdapter()
        // Mirrors CarelevoBleTransportImpl: a write with no connection synchronously reports disconnect.
        transport.onWrite = { transport.capturedListener?.onConnectionStateChanged(false) }

        assertFailsWith<GattWriteException> {
            adapter.writeCharacteristic(writeUuid, byteArrayOf(0x24))
        }
    }

    @Test
    fun `discoverServices completes on success`() = runTest {
        val adapter = newAdapter()
        transport.onDiscover = { transport.capturedListener?.onServicesDiscovered(true) }
        adapter.discoverServices() // does not throw
    }

    @Test
    fun `discoverServices throws GattDiscoveryException on failure`() = runTest {
        val adapter = newAdapter()
        transport.onDiscover = { transport.capturedListener?.onServicesDiscovered(false) }
        assertFailsWith<GattDiscoveryException> { adapter.discoverServices() }
    }

    @Test
    fun `enableNotifications completes when the descriptor write is acked`() = runTest {
        val adapter = newAdapter()
        transport.onEnableNotifications = { transport.capturedListener?.onDescriptorWritten() }
        adapter.enableNotifications(notifyUuid) // does not throw
    }

    @Test
    fun `characteristic notifications surface on events as GattEvent Notification`() = runTest {
        val adapter = newAdapter()
        val events = mutableListOf<GattEvent>()
        val collector = launch { adapter.events.collect { events += it } }
        runCurrent()

        transport.capturedListener?.onCharacteristicChanged(byteArrayOf(0x84.toByte(), 0x00))
        runCurrent()

        assertThat(events).hasSize(1)
        val notification = events.single() as GattEvent.Notification
        assertThat(notification.uuid).isEqualTo(notifyUuid)
        assertThat(notification.payload).isEqualTo(byteArrayOf(0x84.toByte(), 0x00))
        collector.cancel()
    }

    @Test
    fun `disconnect aborts an in-flight write`() = runTest {
        val adapter = newAdapter()
        // Write is submitted but never acked; a mid-flight disconnect must abort it.
        transport.onWrite = { transport.capturedListener?.onConnectionStateChanged(false) }

        assertFailsWith<GattWriteException> {
            adapter.writeCharacteristic(writeUuid, byteArrayOf(0x24))
        }
    }

    @Test
    fun `disconnect emits ConnectionStateChanged DISCONNECTED on events`() = runTest {
        val adapter = newAdapter()
        val events = mutableListOf<GattEvent>()
        val collector = launch { adapter.events.collect { events += it } }
        runCurrent()

        transport.capturedListener?.onConnectionStateChanged(false)
        runCurrent()

        val state = events.filterIsInstance<GattEvent.ConnectionStateChanged>().single()
        assertThat(state.state).isEqualTo(GattConnState.DISCONNECTED)
        collector.cancel()
    }

    @Test
    fun `BleClient correlates a request and response end-to-end over the adapter`() = runTest {
        val adapter = newAdapter()
        val client = BleClientImpl(adapter, writeUuid, notifyUuid, backgroundScope)
        runCurrent()

        transport.onWrite = {
            transport.capturedListener?.onCharacteristicWritten()
            transport.capturedListener?.onCharacteristicChanged(byteArrayOf(0x84.toByte(), 0x00))
        }

        val response = client.request(EchoCommand(requestOpcode = 0x24, expectedResponseOpcode = 0x84.toByte()))

        assertThat(response.raw[0]).isEqualTo(0x84.toByte())
        assertThat(transport.writes.single()[0]).isEqualTo(0x24.toByte())
    }

    @Test
    fun `writeCharacteristic after close fast-fails instead of hanging`() = runTest {
        val adapter = newAdapter()
        adapter.close()
        // After close() the transport listener is null, so the not-connected callback can't abort;
        // the closed-guard must fail fast rather than hang until the caller's withTimeout.
        assertFailsWith<GattWriteException> {
            adapter.writeCharacteristic(writeUuid, byteArrayOf(0x24))
        }
    }

    @Test
    fun `discoverServices after close fast-fails`() = runTest {
        val adapter = newAdapter()
        adapter.close()
        assertFailsWith<GattDiscoveryException> { adapter.discoverServices() }
    }

    @Test
    fun `close is idempotent`() = runTest {
        val adapter = newAdapter()
        adapter.close()
        adapter.close() // must not throw
    }

    @Test
    fun `writeCharacteristic with a foreign characteristic uuid fails loudly`() = runTest {
        val adapter = newAdapter()
        val foreignUuid = UUID.fromString("00000000-0000-0000-0000-000000000000")
        assertFailsWith<IllegalArgumentException> {
            adapter.writeCharacteristic(foreignUuid, byteArrayOf(0x24))
        }
    }

    // ===== Fixtures =====

    private data class EchoResponse(val raw: ByteArray) : BleResponse

    private class EchoCommand(
        override val requestOpcode: Byte,
        override val expectedResponseOpcode: Byte
    ) : BleCommand<EchoResponse> {

        override fun encode(): ByteArray = byteArrayOf(requestOpcode)
        override fun decode(responsePayload: ByteArray): EchoResponse = EchoResponse(responsePayload)
    }

    /** Minimal in-memory [BleTransport]; the test drives async responses via [listener]. */
    private class FakeBleTransport : BleTransport {

        var capturedListener: BleTransportListener? = null
        val writes = mutableListOf<ByteArray>()

        /** Invoked synchronously inside `gatt.writeCharacteristic` to simulate the pump's reply. */
        var onWrite: (() -> Unit)? = null

        /** Invoked synchronously inside `gatt.discoverServices` to simulate the discovery result. */
        var onDiscover: (() -> Unit)? = null

        /** Invoked synchronously inside `gatt.enableNotifications` to simulate the CCCD-write ack. */
        var onEnableNotifications: (() -> Unit)? = null

        override val adapter: BleAdapter = object : BleAdapter {
            override fun enable() {}
            override fun getDeviceName(address: String): String? = null
            override fun isDeviceBonded(address: String): Boolean = false
            override fun createBond(address: String): Boolean = true
            override fun removeBond(address: String) {}
        }

        override val scanner: BleScanner = object : BleScanner {
            override val scannedDevices = MutableSharedFlow<ScannedDevice>()
            override fun startScan() {}
            override fun stopScan() {}
        }

        override val gatt: BleGatt = object : BleGatt {
            override fun connect(address: String): Boolean = true
            override fun disconnect() {}
            override fun close() {}
            override fun discoverServices() {
                onDiscover?.invoke()
            }
            override fun findCharacteristics(): Boolean = true
            override fun enableNotifications() {
                onEnableNotifications?.invoke()
            }
            override fun writeCharacteristic(data: ByteArray) {
                writes += data
                onWrite?.invoke()
            }
        }

        private val _pairingState = MutableStateFlow(PairingState())
        override val pairingState = _pairingState

        override fun updatePairingState(state: PairingState) {
            _pairingState.value = state
        }

        override fun setListener(listener: BleTransportListener?) {
            this.capturedListener = listener
        }
    }
}
