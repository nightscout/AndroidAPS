package app.aaps.pump.diaconn.emulator

import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.core.interfaces.pump.emulator.EmulatedAlarm
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/** The link: threads, order and delays of what the pump sends, as the driver sees them. */
class DiaconnEmulatorBleTransportTest {

    private lateinit var transport: DiaconnEmulatorBleTransport
    private val packets = CopyOnWriteArrayList<ByteArray>()
    private val links = CopyOnWriteArrayList<Boolean>()
    @Volatile private var notifying = false
    private var seq = 0

    /** Does what the driver does: discovers the services and switches the notifications on as soon as the link is up. */
    private val listener = object : BleTransportListener {
        override fun onConnectionStateChanged(connected: Boolean) {
            links += connected
            if (connected) transport.gatt.discoverServices()
        }

        override fun onServicesDiscovered(success: Boolean) {
            if (success && transport.gatt.findCharacteristics()) transport.gatt.enableNotifications()
        }

        override fun onDescriptorWritten() {
            notifying = true
        }

        override fun onCharacteristicChanged(data: ByteArray) {
            packets += data.copyOf()
        }

        override fun onCharacteristicWritten() {}
    }

    @BeforeEach
    fun setUp() {
        // The real G8 whose answers these tests copy.
        transport = DiaconnEmulatorBleTransport(DiaconnPumpEmulator(DiaconnPumpState().apply { serialNumber = REAL_SERIAL }))
        transport.speed = EmulatorSpeed.FAST
        transport.setListener(listener)
    }

    @AfterEach
    fun tearDown() {
        transport.awaitPendingCallbacks()
    }

    @Test
    fun theLinkComesUpAfterConnectReturned() {
        assertThat(transport.gatt.connect(ADDRESS)).isTrue()
        // The driver marks itself "connecting" after the call; a link that was up inside the call would get lost.
        assertThat(links).isEmpty()

        awaitTrue { links.isNotEmpty() }

        assertThat(links).containsExactly(true)
        assertThat(transport.state().linkUp).isTrue()
    }

    @Test
    fun anAnswerCarriesTheSeqOfItsRequest() {
        connect()

        write(0x6E, seq = 0xF0)

        val answer = awaitPacket(0xAE)
        assertThat(answer.u8(2)).isEqualTo(0xF0)
        assertThat(answer.u16(11)).isEqualTo(64004)
    }

    @Test
    fun theMainInfoComesInOneNotification() {
        connect()

        write(0x73)

        assertThat(awaitPacket(0xB3).size).isEqualTo(182)
    }

    @Test
    fun anAnswerGoesOutBeforeTheReportItCaused() {
        transport.speed = EmulatorSpeed.REAL
        connect()
        val bolusOtp = setting(0x07) { u16(100) }.i32(5)
        write(0x37) { u8(0x07).u32(bolusOtp.toLong()) }
        awaitPacket(0xB7)
        val stopOtp = setting(0x2B) { u8(0x07) }.i32(5)
        packets.clear()

        write(0x37) { u8(0x2B).u32(stopOtp.toLong()) }
        awaitPacket(0xE4)

        // The confirm is answered after 0.1 to 0.4 s; the result report of the stop waits for it.
        assertThat(packets.map { it.u8(1) }).containsExactly(0xB7, 0xE4).inOrder()
        assertThat(packets[1].u8(4)).isEqualTo(1)
    }

    @Test
    fun aBrokenLinkDropsTheAnswerOnItsWay() {
        transport.speed = EmulatorSpeed.REAL
        connect()

        write(0x4F)
        transport.breakLink()
        transport.awaitPendingCallbacks()

        assertThat(packets).isEmpty()
        assertThat(links).containsExactly(true, false).inOrder()
        assertThat(transport.state().linkUp).isFalse()
    }

    @Test
    fun aRequestWithNoLinkIsLost() {
        write(0x4F)
        transport.awaitPendingCallbacks()

        assertThat(packets).isEmpty()
        assertThat(transport.state().commands).isEqualTo(0)
    }

    @Test
    fun aPumpOutOfRangeNeverComesUp() {
        transport.setOutOfRange(true)

        assertThat(transport.gatt.connect(ADDRESS)).isTrue()
        Thread.sleep(200)

        assertThat(links).isEmpty()
        assertThat(transport.state().linkUp).isFalse()
    }

    @Test
    fun anAlarmIsSentNowOrAtTheNextLink() {
        assertThat(transport.raise(EmulatedAlarm.LOW_BATTERY)).isTrue()
        assertThat(packets).isEmpty()

        connect()
        val battery = awaitPacket(0xD7)
        assertThat(battery.u8(6)).isEqualTo(90)

        assertThat(transport.raise(EmulatedAlarm.OCCLUSION)).isTrue()
        assertThat(awaitPacket(0xD8).u8(4)).isEqualTo(3)
    }

    @Test
    fun anAlarmThePumpHasNotIsNotRaised() {
        assertThat(transport.raise(EmulatedAlarm.EMPTY_RESERVOIR)).isFalse()
        assertThat(transport.raise(EmulatedAlarm.STOPPED_TOO_LONG)).isFalse()
    }

    @Test
    fun theScanFindsThePumpByItsName() {
        transport.scanner.startScan()

        val device = runBlocking { withTimeout(3000) { transport.scanner.scannedDevices.first() } }
        transport.scanner.stopScan()

        assertThat(device.name).isEqualTo("DIACONN-64004")
        assertThat(device.address).isEqualTo(ADDRESS)
        assertThat(transport.adapter.isDeviceBonded(device.address)).isTrue()
        assertThat(transport.adapter.createBond(device.address)).isTrue()
    }

    @Test
    fun aBolusOnThePumpNeedsNoLink() {
        assertThat(transport.bolusOnPump(0.5)).isTrue()
        assertThat(transport.state().boluses).containsExactly(0.5)
        assertThat(transport.suspendOnPump(true)).isTrue()
        assertThat(transport.state().suspended).isTrue()
    }

    // --- Helpers ---

    /** As the driver: it writes only once the notifications are on. */
    private fun connect() {
        notifying = false
        transport.gatt.connect(ADDRESS)
        awaitTrue { notifying }
    }

    private fun write(msgType: Int, seq: Int = nextSeq(), fields: PacketData.() -> Unit = {}) {
        transport.gatt.writeCharacteristic(DiaconnPacket.build(msgType, seq, PacketData().apply(fields).bytes()))
    }

    private fun nextSeq(): Int {
        seq = (seq + 1) % 255
        return seq
    }

    /** A setting; its answer, with the OTP. */
    private fun setting(msgType: Int, fields: PacketData.() -> Unit): ByteArray {
        packets.clear()
        write(msgType, fields = fields)
        val answer = awaitPacket((msgType and 0x3F) or 0x80)
        assertThat(answer.u8(4)).isEqualTo(0)
        return answer
    }

    private fun awaitPacket(msgType: Int): ByteArray {
        awaitTrue { packets.any { it.u8(1) == msgType } }
        return packets.first { it.u8(1) == msgType }
    }

    private fun awaitTrue(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "timed out" }
            Thread.sleep(5)
        }
    }

    private companion object {

        /** The serial of the real G8 whose answers these tests copy. */
        const val REAL_SERIAL = 64004

        const val ADDRESS = "00:00:00:00:00:00"
    }
}
