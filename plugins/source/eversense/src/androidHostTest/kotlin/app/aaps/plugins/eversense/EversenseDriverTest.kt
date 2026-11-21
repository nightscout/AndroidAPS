package app.aaps.plugins.eversense

import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.BleResult
import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.plugins.eversense.DriverHarness.Companion.ADDRESS
import app.aaps.plugins.eversense.DriverHarness.Companion.NAME
import app.aaps.plugins.eversense.DriverHarness.Companion.START
import app.aaps.plugins.eversense.EversenseDriver.ConnectionState
import app.aaps.plugins.eversense.connection.EversenseLink
import app.aaps.plugins.eversense.packets.e3.EnterDiagnosticModePacket
import app.aaps.plugins.eversense.packets.e3.EversenseE3Packets
import app.aaps.plugins.eversense.packets.e3.PingPacket
import app.aaps.plugins.eversense.util.StorageKeys
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.time.Duration

/**
 * The connection rules of the driver, in virtual time: when it connects, how long it waits after each kind of end,
 * and what connect, disconnect and stop do. The transmitter is a [FakeBleLink]; an E3 that takes the bonding is ready.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EversenseDriverTest {

    private val harness = DriverHarness()
    private val central get() = harness.central
    private val watcher get() = harness.watcher

    private val minute = 60_000L

    // ----- the waits after a link ends -----

    @Test fun aLostLinkIsTriedAgainAfterABackoffThatDoublesUpToAMinute(): Unit = harness.run { driver ->
        harness.pairedE3()
        central.refuseConnects(status = 133)
        driver.start()
        runCurrent()
        assertThat(central.connects).hasSize(1)
        for (wait in listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L)) {
            val before = central.connects.size
            advance(wait - 1)
            assertThat(central.connects).hasSize(before)
            advance(1)
            assertThat(central.connects).hasSize(before + 1)
        }

        // A ready link starts the count again
        central.acceptConnects()
        advance(minute)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
        central.links.last().lose(status = 8)
        runCurrent()
        val before = central.connects.size
        advance(4_999)
        assertThat(central.connects).hasSize(before)
        advance(1)
        assertThat(central.connects).hasSize(before + 1)
    }

    /** An E3 that closed the link itself is lifted off the sensor: tried again after 30 s, not at once. */
    @Test fun anE3ClosedByTheTransmitterIsTriedAgainAfter30Seconds(): Unit = harness.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
        central.links[0].lose(status = 19)
        runCurrent()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.DISCONNECTED)
        advance(29_999)
        assertThat(central.connects).hasSize(1)
        advance(1)
        assertThat(central.connects).hasSize(2)
        assertThat(central.connects[1].androidAutoConnect).isFalse()
    }

    @Test fun threeE3ClosesInARowTellTheUserTheTransmitterIsNotPlaced(): Unit = harness.run { driver ->
        harness.pairedE3()
        central.refuseConnects(status = 19)
        driver.start()
        runCurrent()
        advance(30_000)
        assertThat(watcher.notPlaced).isEqualTo(0)
        advance(30_000)
        assertThat(central.connects).hasSize(3)
        assertThat(watcher.notPlaced).isEqualTo(1)

        // A ready link starts the count again: two closes after it are not three in a row
        central.acceptConnects()
        advance(30_000)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
        central.links.last().lose(status = 19)
        runCurrent()
        central.refuseConnects(status = 19)
        advance(30_000)
        assertThat(watcher.notPlaced).isEqualTo(1)
        advance(30_000)
        assertThat(watcher.notPlaced).isEqualTo(2)
    }

    /** A 365 closes the link as its usual cycle: the next connect waits in the background for it to come back. */
    @Test fun a365ClosedByTheTransmitterReconnectsWithAutoConnect(): Unit = harness.run { driver ->
        harness.paired365()
        central.newLink = { device -> FakeBleLink(device, is365 = true) }
        driver.start()
        runCurrent()
        assertThat(central.connects.single().androidAutoConnect).isFalse()
        assertThat(central.connects.single().timeout).isEqualTo(EversenseDriver.CONNECT_TIMEOUT)

        central.links[0].lose(status = 19)
        runCurrent()
        advance(4_999)
        assertThat(central.connects).hasSize(1)
        advance(1)
        assertThat(central.connects).hasSize(2)
        assertThat(central.connects[1].androidAutoConnect).isTrue()
        assertThat(central.connects[1].timeout).isEqualTo(EversenseDriver.ATTEMPT_DEADLINE)

        // Lost by the radio: the next connect is a direct one again
        central.links[1].lose(status = 8)
        runCurrent()
        advance(10_000)
        assertThat(central.connects).hasSize(3)
        assertThat(central.connects[2].androidAutoConnect).isFalse()
        // A 365 that closes the link is never "not placed"
        assertThat(watcher.notPlaced).isEqualTo(0)
    }

    /** No Bluetooth: nothing is opened, and the driver looks again after a minute, also without being told. */
    @Test fun aLinkThatCannotOpenIsTriedAgainAfterAMinute(): Unit = harness.run { driver ->
        harness.pairedE3()
        central.adapterState.value = AdapterState.OFF
        driver.start()
        runCurrent()
        assertThat(central.connects).isEmpty()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.DISCONNECTED)
        central.adapterState.value = AdapterState.ON
        advance(minute - 1)
        assertThat(central.connects).isEmpty()
        advance(1)
        assertThat(central.connects).hasSize(1)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
    }

    /** A transmitter that never signs in does not leave the driver connecting for ever. */
    @Test fun anAttemptThatIsNotReadyIn180SecondsIsEnded(): Unit = harness.run { driver ->
        harness.pairedE3()
        central.newLink = { device -> FakeBleLink(device, is365 = false).also { it.actAsE3(acceptBonding = false) } }
        driver.start()
        runCurrent()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.CONNECTING)
        advance(EversenseDriver.ATTEMPT_DEADLINE.inWholeMilliseconds - 1)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.CONNECTING)
        assertThat(central.links[0].isUp).isTrue()
        advance(1)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.DISCONNECTED)
        assertThat(central.links[0].isUp).isFalse()
        advance(5_000)
        assertThat(central.connects).hasSize(2)
    }

    /** Only a ready link that brings nothing is replaced: a keep alive counts as data. */
    @Test fun aReadyLinkWithNoDataFor7MinutesIsReplaced(): Unit = harness.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
        advance(6 * minute)
        central.links[0].send(e3Frame(EversenseE3Packets.KeepAlivePush))
        runCurrent()
        advance(7 * minute)
        assertThat(central.connects).hasSize(1)
        assertThat(central.links[0].isUp).isTrue()
        advance(minute)
        assertThat(central.connects).hasSize(2)
        assertThat(central.links[0].isUp).isFalse()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
    }

    @Test fun aLostLinkIsReplacedOnceAndTheOldOneIsClosed(): Unit = harness.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        central.links[0].lose(status = 133)
        runCurrent()
        assertThat(central.links[0].closes).isEqualTo(1)
        assertThat(watcher.connections).containsExactly(true, false).inOrder()
        advance(5_000)
        assertThat(central.connects).hasSize(2)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
        advance(6 * minute)
        assertThat(central.connects).hasSize(2)
        assertThat(watcher.ready).isEqualTo(2)
    }

    // ----- start, connect, disconnect, stop -----

    /** Unpaired, a start waits for the pairing instead of trying every minute to reach a transmitter it does not have. */
    @Test fun aStartWithoutATransmitterWaitsForThePairing(): Unit = harness.run { driver ->
        harness.storeLogin(model = "E3")
        driver.start()
        runCurrent()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.PAUSED)
        driver.onBluetoothAvailable()
        advance(1_000 * minute)
        assertThat(central.connects).isEmpty()

        assertThat(driver.pair(ADDRESS, NAME)).isTrue()
        runCurrent()
        assertThat(central.connects).hasSize(1)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
    }

    /** A 365 registers this phone with the Eversense server: with no login it is not connected at all. */
    @Test fun a365WithoutALoginDoesNotConnect(): Unit = harness.run { driver ->
        harness.storeLogin(model = "E365_USA")
        harness.storeTransmitter()
        driver.start()
        runCurrent()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.PAUSED)
        assertThat(driver.connect()).isFalse()
        advance(10 * minute)
        assertThat(central.connects).isEmpty()
    }

    @Test fun stopClosesTheLinkAndNothingRunsAfterIt(): Unit = harness.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        driver.stop()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.STOPPED)
        assertThat(central.links[0].isUp).isFalse()
        driver.onBluetoothAvailable()
        advance(1_000 * minute)
        assertThat(central.connects).hasSize(1)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.STOPPED)
        assertThat(driver.connect()).isFalse()
        assertThat(driver.cloudStore()).isNull()
    }

    /** A stop while the driver waits to try again: the wait ends, and the state stays stopped. */
    @Test fun aStopDuringARetryWaitStaysStopped(): Unit = harness.run { driver ->
        harness.pairedE3()
        central.refuseConnects(status = 133)
        driver.start()
        runCurrent()
        advance(2_000)
        driver.stop()
        advance(10 * minute)
        assertThat(central.connects).hasSize(1)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.STOPPED)
    }

    @Test fun disconnectStaysPausedUntilConnect(): Unit = harness.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        driver.disconnect()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.PAUSED)
        assertThat(central.links[0].isUp).isFalse()
        driver.onBluetoothAvailable()
        advance(1_000 * minute)
        assertThat(central.connects).hasSize(1)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.PAUSED)

        assertThat(driver.connect()).isTrue()
        runCurrent()
        assertThat(central.connects).hasSize(2)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
    }

    @Test fun connectOpensNoSecondLink(): Unit = harness.run { driver ->
        harness.pairedE3()
        central.newLink = { device -> FakeBleLink(device, is365 = false).also { it.actAsE3(acceptBonding = false) } }
        driver.start()
        runCurrent()
        // On its way: not ready yet, and not opened again
        assertThat(driver.connect()).isFalse()
        runCurrent()
        assertThat(central.connects).hasSize(1)

        central.newLink = { device -> FakeBleLink(device, is365 = false).also { it.actAsE3() } }
        advance(EversenseDriver.ATTEMPT_DEADLINE.inWholeMilliseconds + 5_000)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
        val connects = central.connects.size
        // Ready: nothing to do
        assertThat(driver.connect()).isTrue()
        runCurrent()
        assertThat(central.connects).hasSize(connects)
    }

    /** Bluetooth on again: Android can leave out the disconnect while it was off, so the driver connects at once. */
    @Test fun bluetoothBackOnConnectsAtOnce(): Unit = harness.run { driver ->
        harness.pairedE3()
        central.refuseConnects(status = 133)
        driver.start()
        runCurrent()
        central.acceptConnects()
        driver.onBluetoothAvailable()
        runCurrent()
        assertThat(central.connects).hasSize(2)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
    }

    // ----- the cloud store -----

    /**
     * The cloud upload's store outlives a lost link and the reconnect after it, but not a disconnect, a new login or a
     * stop: an upload in flight cannot bring back what a sign-out removed.
     */
    @Test fun theCloudStoreEndsWithADisconnectANewLoginAndAStop(): Unit = harness.run { driver ->
        harness.pairedE3()
        assertThat(driver.cloudStore()).isNull()
        driver.start()
        runCurrent()
        val cloud = driver.cloudStore()!!
        cloud.edit().putString("token", "valid").apply()
        central.links[0].lose(status = 133)
        runCurrent()
        advance(5_000)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
        cloud.edit().putString("token", "still valid").apply()
        assertThat(harness.store.getString("token", null)).isEqualTo("still valid")

        driver.disconnect()
        assertThrows(CancellationException::class.java) { cloud.edit() }
        assertThat(driver.cloudStore()).isNull()

        driver.connect()
        runCurrent()
        val next = driver.cloudStore()!!
        assertThat(next.getString("token", null)).isEqualTo("still valid")
        driver.setCredentials("other@example.com", "other", europeanRegion = false)
        assertThrows(CancellationException::class.java) { next.edit() }

        val last = driver.cloudStore()!!
        driver.stop()
        assertThrows(CancellationException::class.java) { last.edit() }
        assertThat(driver.cloudStore()).isNull()
    }

    // ----- work on the ready link -----

    /** The transmitter refusing a calibration is a failed calibration, not a lost link. */
    @Test fun aRefusedCalibrationKeepsTheLink(): Unit = harness.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        // The fake E3 refuses every command but the bonding
        assertThat(driver.sendCalibration(100)).isFalse()
        runCurrent()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
        assertThat(central.links[0].isUp).isTrue()
        // And the driver still reconnects after a loss
        central.links[0].lose(status = 133)
        runCurrent()
        advance(5_000)
        assertThat(central.connects).hasSize(2)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
    }

    /** Work that needs a ready link is refused while the transmitter signs in: nothing is sent for it. */
    @Test fun aCalibrationIsRefusedWhileTheTransmitterSignsIn(): Unit = harness.run { driver ->
        harness.pairedE3()
        central.newLink = { device -> FakeBleLink(device, is365 = false).also { it.actAsE3(acceptBonding = false) } }
        driver.start()
        runCurrent()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.CONNECTING)
        val written = central.links[0].writes.size
        assertThat(driver.sendCalibration(100)).isFalse()
        assertThat(driver.readSignalStrength()).isNull()
        runCurrent()
        assertThat(central.links[0].writes).hasSize(written)
    }

    @Test fun aSignalThatCannotBeReadKeepsTheLink(): Unit = harness.run { driver ->
        harness.pairedE3()
        assertThat(driver.readSignalStrength()).isNull()
        driver.start()
        runCurrent()
        assertThat(driver.readSignalStrength()).isNull()
        runCurrent()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
        assertThat(central.links[0].isUp).isTrue()
    }

    @Test fun aSyncRunsOnlyOnAReadyLink(): Unit = harness.run { driver ->
        harness.pairedE3()
        driver.triggerFullSync(force = true)
        runCurrent()
        assertThat(central.connects).isEmpty()
        driver.start()
        runCurrent()
        val written = central.links[0].writes.size
        driver.triggerFullSync(force = true)
        runCurrent()
        assertThat(central.links[0].writes.drop(written).first()[0]).isEqualTo(PingPacket().spec.requestId)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
    }

    /** The placement guide keeps the transmitter in diagnostic mode, also over a reconnect. */
    @Test fun thePlacementGuideKeepsDiagnosticModeOverAReconnect(): Unit = harness.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        val enter = EnterDiagnosticModePacket().spec.requestId
        driver.enterPositioningMode()
        assertThat(driver.isPositioningMode).isTrue()
        assertThat(central.links[0].writes.map { it[0] }).contains(enter)

        central.links[0].lose(status = 133)
        runCurrent()
        advance(5_000)
        assertThat(central.links[1].writes.map { it[0] }).contains(enter)

        advance(1_000)
        driver.exitPositioningMode()
        assertThat(driver.isPositioningMode).isFalse()
        assertThat(driver.lastPositioningModeExitAt).isEqualTo(START + currentTime)
    }

    @Test fun pairStoresTheTransmitterAndConnectsToIt(): Unit = harness.run { driver ->
        harness.storeLogin(model = "E3")
        driver.start()
        assertThat(driver.pair("AA:BB:CC:DD:EE:FF", "T654321")).isTrue()
        runCurrent()
        assertThat(harness.store.getString(StorageKeys.REMOTE_DEVICE_KEY, null)).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(harness.store.getString(StorageKeys.REMOTE_DEVICE_NAME, null)).isEqualTo("T654321")
        assertThat(central.links.single().device).isEqualTo(DeviceId("AA:BB:CC:DD:EE:FF"))
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
    }

    // ----- iOS: no timers in the background -----

    /** A suspended iOS app runs no timer: a lost link waits for the transmitter in a pending connect, started at once. */
    @Test fun withoutTimersALostLinkWaitsForTheTransmitterAtOnce(): Unit = harness.also { it.reconnectWithoutTimers = true }.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        assertThat(central.connects.single().timeout).isEqualTo(EversenseDriver.CONNECT_TIMEOUT)

        central.links[0].lose(status = 8)
        runCurrent()
        assertThat(central.connects).hasSize(2)
        assertThat(central.connects[1].androidAutoConnect).isTrue()
        assertThat(central.connects[1].timeout).isEqualTo(Duration.INFINITE)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)
    }

    /**
     * A transmitter that stops answering is waited for in the same way. On a locked iPhone the backoff timer here never
     * ran, and the readings stopped until the user opened the app.
     */
    @Test fun withoutTimersATransmitterThatStopsAnsweringIsWaitedForAtOnce(): Unit = harness.also { it.reconnectWithoutTimers = true }.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)

        central.links[0].onWrite = {}
        driver.triggerFullSync(force = true)
        runCurrent()
        advance(EversenseLink.WRITE_TIMEOUT_MS)
        assertThat(central.connects).hasSize(2)
        assertThat(central.connects[1].timeout).isEqualTo(Duration.INFINITE)
    }

    /** Where timers run, a transmitter that stops answering is asked again on the backoff, as before. */
    @Test fun aTransmitterThatStopsAnsweringIsAskedAgainOnTheBackoff(): Unit = harness.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.READY)

        central.links[0].onWrite = {}
        driver.triggerFullSync(force = true)
        runCurrent()
        advance(EversenseLink.WRITE_TIMEOUT_MS)
        assertThat(central.connects).hasSize(1)
        advance(5_000)
        assertThat(central.connects).hasSize(2)
        assertThat(central.connects[1].timeout).isEqualTo(EversenseDriver.CONNECT_TIMEOUT)
    }

    /** A link the session gave up on is not a radio loss: at once it would only fail again, so it waits on the backoff. */
    @Test fun withoutTimersALinkThatNeverGotReadyStillWaitsOnTheBackoff(): Unit = harness.also { it.reconnectWithoutTimers = true }.run { driver ->
        harness.pairedE3()
        central.newLink = { device -> FakeBleLink(device, is365 = false).also { it.actAsE3(acceptBonding = false) } }
        driver.start()
        runCurrent()
        advance(EversenseDriver.ATTEMPT_DEADLINE.inWholeMilliseconds)
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.DISCONNECTED)
        advance(4_999)
        assertThat(central.connects).hasSize(1)
        advance(1)
        assertThat(central.connects).hasSize(2)
        assertThat(central.connects[1].timeout).isEqualTo(EversenseDriver.CONNECT_TIMEOUT)
    }

    /** A connect that waited long for the transmitter does not eat the time the sign-in has. */
    @Test fun afterALongWaitTheSignInGetsItsFullTime(): Unit = harness.also { it.reconnectWithoutTimers = true }.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        // The transmitter comes back after 10 minutes, and this time it does not take the bonding
        central.answer = { device ->
            delay(10 * minute)
            BleResult.Ok(FakeBleLink(device, is365 = false).also { it.actAsE3(acceptBonding = false); central.links += it })
        }
        central.links[0].lose(status = 8)
        runCurrent()
        advance(10 * minute)
        assertThat(central.links).hasSize(2)
        advance(EversenseDriver.ATTEMPT_DEADLINE.inWholeMilliseconds - 1)
        assertThat(central.links[1].isUp).isTrue()
        assertThat(driver.connectionState.value).isEqualTo(ConnectionState.CONNECTING)
        advance(1)
        assertThat(central.links[1].isUp).isFalse()
    }
}
