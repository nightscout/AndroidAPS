package app.aaps.pump.carelevo.emulator

import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.PairingStep
import app.aaps.pump.carelevo.ble.commands.InfusionInfoCommand
import app.aaps.pump.carelevo.ble.commands.PatchDiscardCommand
import app.aaps.pump.carelevo.ble.commands.PatchInfoCommand
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Transport tests for [CarelevoEmulatorBleTransport] — the seam between the driver's `BleTransport`
 * contract and [CarelevoPumpEmulator].
 *
 * Scope is deliberately the *transport*, not the protocol: [CarelevoPumpEmulatorTest] already proves
 * every opcode round-trips through the production codec, so nothing here re-asserts frame contents
 * beyond the response opcode needed to tell one notification from the next. What this suite pins down
 * is the wiring the emulator adds on top:
 * - which listener callback each `gatt` call fans out to, and **in what order** — `onCharacteristicWritten`
 *   must precede the `onCharacteristicChanged` frames, because [app.aaps.core.interfaces.pump.ble.BleGatt]
 *   answers writes synchronously and a client that saw the reply before the ack would be reading a
 *   reply to a write it does not yet believe it made;
 * - the fault-injection switches (`connectRefused`, `reportConnected`, `silentOpcodes`) that exist
 *   purely to drive the driver's failure paths;
 * - the register → close → register cycle the driver performs once per operation.
 *
 * Pure JVM: the transport takes a nullable logger and touches no Android API, so no Robolectric.
 */
class CarelevoEmulatorTransportTest {

    private lateinit var state: CarelevoPumpState
    private lateinit var emulator: CarelevoPumpEmulator
    private lateinit var transport: CarelevoEmulatorBleTransport
    private lateinit var listener: RecordingListener

    @BeforeEach
    fun setUp() {
        state = CarelevoPumpState()
        emulator = CarelevoPumpEmulator(state)
        transport = CarelevoEmulatorBleTransport(emulator)
        listener = RecordingListener()
        transport.setListener(listener)
    }

    // ---- Listener fan-out --------------------------------------------------------------------

    @Test
    fun `the connect handshake fans out to each listener callback in order`() {
        transport.gatt.connect(ADDRESS)
        transport.gatt.discoverServices()
        val found = transport.gatt.findCharacteristics()
        transport.gatt.enableNotifications()

        // The driver's session drives exactly this sequence; each step must land on its own callback.
        assertThat(listener.events).containsExactly(CONNECTED, SERVICES_OK, DESCRIPTOR).inOrder()
        // findCharacteristics is synchronous — it answers by return value, not by a callback.
        assertThat(found).isTrue()
    }

    @Test
    fun `a write acks before it delivers the notification`() {
        transport.gatt.connect(ADDRESS)
        listener.clear()

        transport.gatt.writeCharacteristic(PatchDiscardCommand().encode())

        // Order is the point: the ack must come first, or a client would see a reply to a write it
        // does not yet believe completed.
        assertThat(listener.events).containsExactly(WRITTEN, CHANGED).inOrder()
        assertThat(listener.notifications.single()[0]).isEqualTo(PatchDiscardCommand.RESPONSE_OPCODE)
    }

    @Test
    fun `a multi-frame command delivers one notification per emulator frame in order`() {
        transport.gatt.connect(ADDRESS)
        listener.clear()

        transport.gatt.writeCharacteristic(PatchInfoCommand().encode())

        // 0x33 is answered by two report frames; both must arrive as separate notifications, in order,
        // or the client's multi-frame correlation never completes.
        assertThat(listener.events).containsExactly(WRITTEN, CHANGED, CHANGED).inOrder()
        assertThat(listener.notifications.map { it[0] })
            .containsExactly(PatchInfoCommand.RPT1_OPCODE, PatchInfoCommand.RPT2_OPCODE)
            .inOrder()
    }

    @Test
    fun `a silent opcode still acks the write but delivers no notification`() {
        state.silentOpcodes += InfusionInfoCommand.REQUEST_OPCODE
        transport.gatt.connect(ADDRESS)
        listener.clear()

        transport.gatt.writeCharacteristic(InfusionInfoCommand().encode())

        // The write itself still completes at the GATT layer — only the patch goes quiet, which is
        // what leaves the caller waiting on its timeout.
        assertThat(listener.events).containsExactly(WRITTEN)
        assertThat(listener.notifications).isEmpty()
        // ...and the frame is still recorded, so a test can assert what was sent into the silence.
        assertThat(transport.writes).hasSize(1)
    }

    @Test
    fun `writes records every frame in order`() {
        val patchInfo = PatchInfoCommand().encode()
        val infusionInfo = InfusionInfoCommand().encode()
        val discard = PatchDiscardCommand().encode()

        transport.gatt.connect(ADDRESS)
        transport.gatt.writeCharacteristic(patchInfo)
        transport.gatt.writeCharacteristic(infusionInfo)
        transport.gatt.writeCharacteristic(discard)

        assertThat(transport.writes.map { it.toList() })
            .containsExactly(patchInfo.toList(), infusionInfo.toList(), discard.toList())
            .inOrder()
    }

    // ---- Connect / disconnect ----------------------------------------------------------------

    @Test
    fun `connect refused returns false and fires no connection callback`() {
        transport.connectRefused = true

        assertThat(transport.gatt.connect(ADDRESS)).isFalse()

        // The BLE stack refused to even start — the driver must not be told it connected.
        assertThat(listener.events).isEmpty()
    }

    @Test
    fun `connect without a connected report returns true but fires no callback`() {
        transport.reportConnected = false

        // The stack accepted the request; the patch simply never answers — this is the connect-timeout
        // path, and it is the absence of the callback that drives it.
        assertThat(transport.gatt.connect(ADDRESS)).isTrue()
        assertThat(listener.events).isEmpty()
    }

    @Test
    fun `disconnect before connect fires nothing`() {
        transport.gatt.disconnect()

        assertThat(listener.events).isEmpty()
    }

    @Test
    fun `disconnect fires a single disconnected callback and is idempotent`() {
        transport.gatt.connect(ADDRESS)
        listener.clear()

        transport.gatt.disconnect()
        transport.gatt.disconnect()

        // A second disconnect must stay silent — a duplicate lost-connection event would tear the
        // session down twice.
        assertThat(listener.connectionStates).containsExactly(false)
    }

    // ---- Bonding -----------------------------------------------------------------------------

    @Test
    fun `adapter reports the emulated bond state`() {
        assertThat(transport.adapter.isDeviceBonded(ADDRESS)).isTrue()

        transport.bonded = false

        assertThat(transport.adapter.isDeviceBonded(ADDRESS)).isFalse()
    }

    @Test
    fun `createBond counts the call and bonds the patch`() {
        transport.bonded = false

        assertThat(transport.adapter.createBond(ADDRESS)).isTrue()

        assertThat(transport.bonded).isTrue()
        assertThat(transport.adapter.isDeviceBonded(ADDRESS)).isTrue()
        assertThat(transport.bondCalls).isEqualTo(1)

        transport.adapter.createBond(ADDRESS)

        assertThat(transport.bondCalls).isEqualTo(2)
    }

    @Test
    fun `removeBond unbonds the patch`() {
        transport.bonded = true

        transport.adapter.removeBond(ADDRESS)

        assertThat(transport.bonded).isFalse()
        assertThat(transport.adapter.isDeviceBonded(ADDRESS)).isFalse()
    }

    // ---- Scanner -----------------------------------------------------------------------------

    @Test
    fun `a discovery scan asks the serial provider and reports the patch mac`() {
        val discovering = CarelevoEmulatorBleTransport(emulator, serialNumberProvider = { PROVIDED_SERIAL })

        discovering.scanner.startScan()
        val device = runBlocking { discovering.scanner.scannedDevices.first() }

        // No address filter means the pairing wizard is hunting for a fresh patch — that is where the
        // serial comes from, so the provider decides what the patch now calls itself.
        assertThat(discovering.pumpState.serialNumber).isEqualTo(PROVIDED_SERIAL)
        assertThat(device.address).isEqualTo(DEFAULT_MAC)
        assertThat(device.name).isEqualTo("CareLevo-$PROVIDED_SERIAL")
    }

    @Test
    fun `a filtered scan reports the scan address and leaves the serial alone`() {
        var providerCalls = 0
        val reconnecting = CarelevoEmulatorBleTransport(
            emulator,
            serialNumberProvider = {
                providerCalls++
                PROVIDED_SERIAL
            }
        )
        reconnecting.scanAddress = FILTER_ADDRESS

        reconnecting.scanner.startScan()
        val device = runBlocking { reconnecting.scanner.scannedDevices.first() }

        // A filtered scan is a reconnect to a patch already known — re-deriving its serial would
        // overwrite the identity the driver is reconnecting to.
        assertThat(providerCalls).isEqualTo(0)
        assertThat(reconnecting.pumpState.serialNumber).isEqualTo(DEFAULT_SERIAL)
        assertThat(device.address).isEqualTo(FILTER_ADDRESS)
    }

    // ---- Adapter identity --------------------------------------------------------------------

    @Test
    fun `getDeviceName follows the patch serial`() {
        assertThat(transport.adapter.getDeviceName(ADDRESS)).isEqualTo("CareLevo-$DEFAULT_SERIAL")

        state.serialNumber = PROVIDED_SERIAL

        // The name is derived, not cached — a re-serialled patch renames itself.
        assertThat(transport.adapter.getDeviceName(ADDRESS)).isEqualTo("CareLevo-$PROVIDED_SERIAL")
    }

    // ---- Listener lifecycle ------------------------------------------------------------------

    @Test
    fun `setListener null detaches and a later write delivers nothing without throwing`() {
        transport.gatt.connect(ADDRESS)
        listener.clear()

        transport.setListener(null)
        transport.gatt.writeCharacteristic(PatchInfoCommand().encode())

        assertThat(listener.events).isEmpty()
        // The frame still reached the emulator — only the delivery path is detached.
        assertThat(transport.writes).hasSize(1)
    }

    @Test
    fun `re-registering a listener routes callbacks to the new listener only`() {
        val firstSession = RecordingListener()
        val secondSession = RecordingListener()

        transport.setListener(firstSession)
        transport.gatt.connect(ADDRESS)
        transport.gatt.close()
        firstSession.clear()

        transport.setListener(secondSession)
        transport.gatt.connect(ADDRESS)
        transport.gatt.writeCharacteristic(PatchInfoCommand().encode())

        // The driver opens a session per operation, so this cycle is the norm, not an edge case: a
        // frame leaking to the closed session's listener would resolve a waiter that is already gone.
        assertThat(secondSession.connectionStates).containsExactly(true)
        assertThat(secondSession.notifications.map { it[0] })
            .containsExactly(PatchInfoCommand.RPT1_OPCODE, PatchInfoCommand.RPT2_OPCODE)
            .inOrder()
        assertThat(firstSession.events).isEmpty()
    }

    // ---- Pairing state -----------------------------------------------------------------------

    @Test
    fun `pairing state round-trips through updatePairingState`() {
        assertThat(transport.pairingState.value).isEqualTo(PairingState())
        assertThat(transport.pairingState.value.step).isEqualTo(PairingStep.IDLE)

        val pairing = PairingState(step = PairingStep.WAITING_FOR_PAIRING_CONFIRM, errorMessage = "confirm on patch")
        transport.updatePairingState(pairing)

        assertThat(transport.pairingState.value).isEqualTo(pairing)
    }

    // ---- State delegation --------------------------------------------------------------------

    @Test
    fun `pumpState is the emulator state instance`() {
        // Not a copy: a test mutates `transport.pumpState` and the emulator must answer from it.
        assertThat(transport.pumpState).isSameInstanceAs(emulator.state)
        assertThat(transport.pumpState).isSameInstanceAs(state)
    }

    // ---- Helpers -----------------------------------------------------------------------------

    /** Records every callback as a flat, ordered event log — the assertion surface for fan-out order. */
    private class RecordingListener : BleTransportListener {

        val events: MutableList<String> = mutableListOf()
        val connectionStates: MutableList<Boolean> = mutableListOf()
        val notifications: MutableList<ByteArray> = mutableListOf()

        override fun onConnectionStateChanged(connected: Boolean) {
            connectionStates += connected
            events += if (connected) CONNECTED else DISCONNECTED
        }

        override fun onServicesDiscovered(success: Boolean) {
            events += if (success) SERVICES_OK else SERVICES_FAILED
        }

        override fun onDescriptorWritten() {
            events += DESCRIPTOR
        }

        override fun onCharacteristicChanged(data: ByteArray) {
            notifications += data.copyOf()
            events += CHANGED
        }

        override fun onCharacteristicWritten() {
            events += WRITTEN
        }

        fun clear() {
            events.clear()
            connectionStates.clear()
            notifications.clear()
        }
    }

    private companion object {

        /** [CarelevoPumpState.macAddress] rendered the way the scanner reports it. */
        const val DEFAULT_MAC = "94:B2:16:1D:2F:6D"

        /** The address the driver hands to `gatt`/`adapter` calls — the emulator ignores it. */
        const val ADDRESS = DEFAULT_MAC
        const val FILTER_ADDRESS = "AA:BB:CC:DD:EE:FF"

        const val DEFAULT_SERIAL = "CL24000000001"
        const val PROVIDED_SERIAL = "CL24000000042"

        const val CONNECTED = "connected"
        const val DISCONNECTED = "disconnected"
        const val SERVICES_OK = "services:true"
        const val SERVICES_FAILED = "services:false"
        const val DESCRIPTOR = "descriptor"
        const val WRITTEN = "written"
        const val CHANGED = "changed"
    }
}
