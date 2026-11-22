package app.aaps.plugins.eversense.emulator

import app.aaps.core.interfaces.pump.ble.EmulatedBleCentral
import app.aaps.plugins.eversense.EversenseDriver
import app.aaps.plugins.eversense.EversenseDriver.ConnectionState
import app.aaps.plugins.eversense.FakeNetwork
import app.aaps.plugins.eversense.MemoryStore
import app.aaps.plugins.eversense.RecordingWatcher
import app.aaps.plugins.eversense.advance
import app.aaps.plugins.eversense.enums.EversenseAlarm
import app.aaps.plugins.eversense.models.EversenseSecureState
import app.aaps.plugins.eversense.protocol.PacketTime
import app.aaps.plugins.eversense.util.EversenseCrypto365Util
import app.aaps.plugins.eversense.util.StorageKeys
import app.aaps.shared.tests.AAPSLoggerTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The real driver against the emulated 365, over the emulated Bluetooth, in virtual time. The phone is registered with
 * the transmitter already (the sign-in shortcut), so no Eversense server is needed: the sign-in is the start alone,
 * and everything after it is encrypted with the session key both sides agreed. On iOS this runs the platform's own
 * crypto (ECDH, HKDF, AES) against the emulator's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Emulated365Test {

    private val store = MemoryStore()
    private val watcher = RecordingWatcher()

    private class Rig(val driver: EversenseDriver, val transmitter: EversenseTransmitterEmulator, val peripheral: Eversense365Peripheral)

    private fun run(prepare: (EversenseTransmitterEmulator) -> Unit = {}, body: suspend TestScope.(Rig) -> Unit) {
        val dispatcher = StandardTestDispatcher()
        PacketTime.withClock({ START + dispatcher.scheduler.currentTime }) {
            runTest(dispatcher) {
                val transmitter = EversenseTransmitterEmulator(name = "D3650001", address = "00:EE:00:36:50:01", now = { START + testScheduler.currentTime })
                prepare(transmitter)
                val peripheral = Eversense365Peripheral(transmitter, scope = backgroundScope, keepAliveInterval = 5.minutes)
                val central = EmulatedBleCentral(scope = backgroundScope).also { it.add(peripheral) }
                registeredPhone(transmitter)
                val driver = EversenseDriver(central, store, AAPSLoggerTest(), FakeNetwork(), dispatcher)
                driver.addWatcher(watcher)
                try {
                    body(Rig(driver, transmitter, peripheral))
                } finally {
                    driver.stop()
                }
            }
        }
    }

    /** A 365 paired with a login, and this phone registered with it: its keys made, the shortcut allowed */
    private fun registeredPhone(transmitter: EversenseTransmitterEmulator) {
        store.values[StorageKeys.SECURE_STATE] = Json.encodeToString(EversenseSecureState().apply {
            username = "user@example.com"
            password = "secret"
            model = "E365_OTHER"
        })
        EversenseCrypto365Util(store, AAPSLoggerTest()).generateKeyPairIfNotExists()
        EversenseCrypto365Util(store, AAPSLoggerTest()).allowUseShortcut()
        store.values[StorageKeys.REMOTE_DEVICE_KEY] = transmitter.address
        store.values[StorageKeys.REMOTE_DEVICE_NAME] = transmitter.name
    }

    private fun readings() = watcher.reads.flatten()

    @Test fun signsInWithTheShortcutAndDeliversTheReading() = run { rig ->
        rig.driver.start()
        advance(10_000)

        assertEquals(ConnectionState.READY, rig.driver.connectionState.value)
        assertTrue(rig.peripheral.signedIn)
        val newest = readings().maxBy { it.datetime }
        assertEquals(rig.transmitter.latestReading().timestamp, newest.datetime)
        assertEquals(rig.transmitter.latestReading().glucoseMgDl, newest.glucoseInMgDl)
        val state = rig.driver.getCurrentState()
        assertEquals(85, state.batteryPercentage)
        assertEquals("D3650001", state.transmitterName)
    }

    /** The keep-alive the transmitter pushes every five minutes brings the next reading. */
    @Test fun theKeepAliveBringsTheNextReading() = run { rig ->
        rig.driver.start()
        advance(10_000)
        val first = readings().maxOf { it.datetime }

        advance(5.minutes.inWholeMilliseconds)

        assertTrue(readings().maxOf { it.datetime } > first)
        assertEquals(ConnectionState.READY, rig.driver.connectionState.value)
    }

    /** After half an hour without a link, the readings of the gap come from the history log, each once. */
    @Test fun aGapIsFilledFromTheHistory() = run { rig ->
        rig.driver.start()
        advance(10_000)
        val before = readings().maxOf { it.datetime }

        rig.driver.stop()
        advance(30.minutes.inWholeMilliseconds)
        rig.driver.start()
        advance(10_000)

        val gap = readings().map { it.datetime }.filter { it > before }
        assertTrue(gap.size >= 5, "only ${gap.size} readings of the gap")
        assertEquals(gap.size, gap.toSet().size, "duplicates in $gap")
    }

    /** A transmitter clock a minute off is set at the full sync, to the second the 365 keeps. */
    @Test fun theFullSyncSetsTheClock() = run(prepare = { it.setClock(START + 60_000, 0) }) { rig ->
        rig.driver.start()
        advance(10_000)

        assertTrue(abs(rig.transmitter.clock() - PacketTime.nowMillis()) <= 1_000L, "clock ${rig.transmitter.clock()}, phone ${PacketTime.nowMillis()}")
    }

    @Test fun aCalibrationReachesTheTransmitter() = run { rig ->
        rig.driver.start()
        advance(10_000)

        assertTrue(rig.driver.sendCalibration(110))
        assertEquals(listOf(110), rig.transmitter.calibrations)
    }

    @Test fun theSignalIsReadForThePlacementGuide() = run { rig ->
        rig.driver.start()
        advance(10_000)

        assertEquals(60, rig.driver.readSignalStrength())
    }

    @Test fun anAlarmTheTransmitterPushesReachesThePlugin() = run { rig ->
        rig.driver.start()
        advance(10_000)

        rig.peripheral.pushAlarm(rig.peripheral.link!!, EversenseAlarm.LOW_GLUCOSE.code)
        advance(1_000)

        assertTrue(EversenseAlarm.LOW_GLUCOSE in watcher.alarms.map { it.code })
    }

    private companion object {

        /** The wall clock at virtual time 0 */
        const val START = 1_800_000_000_000L
    }
}
