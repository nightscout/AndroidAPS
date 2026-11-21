package app.aaps.plugins.eversense.emulator

import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.EmulatedBleCentral
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.plugins.eversense.EversenseDriver
import app.aaps.plugins.eversense.EversenseDriver.ConnectionState
import app.aaps.plugins.eversense.MemoryStore
import app.aaps.plugins.eversense.RecordingWatcher
import app.aaps.plugins.eversense.advance
import app.aaps.plugins.eversense.models.EversenseSecureState
import app.aaps.plugins.eversense.protocol.BinaryFields
import app.aaps.plugins.eversense.protocol.PacketTime
import app.aaps.plugins.eversense.util.StorageKeys
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The real driver against the emulated E3, over the emulated Bluetooth of the pump emulators, in virtual time: the
 * same frames, encoders and queue as with a transmitter, with no radio. What the emulator gets right is what the
 * driver understands; that both are right is for a real transmitter to show (see [EversenseTransmitterEmulator]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmulatedE3Test {

    private val store = MemoryStore()
    private val watcher = RecordingWatcher()
    private val network: ReceiverStatusStore = mock<ReceiverStatusStore>().also { whenever(it.isConnected).thenReturn(true) }

    private class Rig(val driver: EversenseDriver, val transmitter: EversenseTransmitterEmulator, val peripheral: EversenseE3Peripheral, val central: EmulatedBleCentral)

    /** An E3 paired and used without a login, as the wizard leaves it; the phone clock and the transmitter's in virtual time */
    private fun run(body: suspend TestScope.(Rig) -> Unit) {
        val dispatcher = StandardTestDispatcher()
        PacketTime.withClock({ START + dispatcher.scheduler.currentTime }) {
            runTest(dispatcher) {
                val transmitter = EversenseTransmitterEmulator(now = { START + testScheduler.currentTime })
                val peripheral = EversenseE3Peripheral(transmitter, scope = backgroundScope, keepAliveInterval = 5.minutes, firstPushDelay = 2.seconds)
                val central = EmulatedBleCentral(scope = backgroundScope).also { it.add(peripheral) }
                store.values[StorageKeys.SECURE_STATE] = Json.encodeToString(EversenseSecureState().apply { model = "E3" })
                store.values[StorageKeys.REMOTE_DEVICE_KEY] = transmitter.address
                store.values[StorageKeys.REMOTE_DEVICE_NAME] = transmitter.name
                val driver = EversenseDriver(central, store, AAPSLoggerTest(), network, dispatcher)
                driver.addWatcher(watcher)
                try {
                    body(Rig(driver, transmitter, peripheral, central))
                } finally {
                    driver.stop()
                }
            }
        }
    }

    private fun readings() = watcher.reads.flatten()

    @Test fun signsInSyncsAndDeliversTheFirstReadingAfterThePush() = run { rig ->
        rig.driver.start()
        advance(5_000)

        assertThat(rig.driver.connectionState.value).isEqualTo(ConnectionState.READY)
        assertThat(readings().map { it.glucoseInMgDl }).containsExactly(rig.transmitter.latestReading().glucoseMgDl)
        assertThat(readings().single().datetime).isEqualTo(rig.transmitter.latestReading().timestamp)
        // The full sync read what the transmitter knows
        val state = rig.driver.getCurrentState()
        assertThat(state.batteryPercentage).isEqualTo(85)
        assertThat(state.firmwareVersion).isNotEmpty()
    }

    @Test fun aNewReadingEveryFiveMinutes() = run { rig ->
        rig.driver.start()
        advance(5_000)
        advance(5.minutes.inWholeMilliseconds)
        advance(5.minutes.inWholeMilliseconds)

        val times = readings().map { it.datetime }
        assertThat(times).hasSize(3)
        assertThat(times).isInStrictOrder()
        assertThat(rig.driver.connectionState.value).isEqualTo(ConnectionState.READY)
    }

    /** The full sync sets the transmitter's clock and zone when they differ from the phone's, as with a real E3. */
    @Test fun theFullSyncSetsTheClockAndZone() = run { rig ->
        rig.driver.start()
        advance(5_000)

        assertThat(rig.transmitter.zoneOffsetMillis()).isEqualTo(BinaryFields.zoneOffsetMillis(PacketTime.nowMillis()).toLong())
        assertThat(rig.transmitter.clock()).isWithin(2_000L).of(PacketTime.nowMillis())
    }

    @Test fun aCalibrationReachesTheTransmitter() = run { rig ->
        rig.driver.start()
        advance(5_000)

        assertThat(rig.driver.sendCalibration(105)).isTrue()
        assertThat(rig.transmitter.calibrations).containsExactly(105)
    }

    @Test fun theSignalIsReadForThePlacementGuide() = run { rig ->
        rig.driver.start()
        advance(5_000)

        assertThat(rig.driver.readSignalStrength()).isNotNull()
    }

    /** Bluetooth off drops the link; on again, the driver connects and signs in by itself. */
    @Test fun bluetoothOffAndOnAgain() = run { rig ->
        rig.driver.start()
        advance(5_000)

        rig.central.setAdapterState(AdapterState.OFF)
        advance(1_000)
        assertThat(rig.driver.connectionState.value).isNotEqualTo(ConnectionState.READY)

        rig.central.setAdapterState(AdapterState.ON)
        rig.driver.onBluetoothAvailable()
        advance(2.minutes.inWholeMilliseconds)
        assertThat(rig.driver.connectionState.value).isEqualTo(ConnectionState.READY)
    }

    private companion object {

        /** The wall clock at virtual time 0 */
        const val START = 1_800_000_000_000L
    }
}
