package app.aaps.plugins.eversense

import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.BleResult
import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.plugins.eversense.DriverHarness.Companion.ADDRESS
import app.aaps.plugins.eversense.DriverHarness.Companion.CLOSED_BY_TRANSMITTER
import app.aaps.plugins.eversense.DriverHarness.Companion.NAME
import app.aaps.plugins.eversense.DriverHarness.Companion.START
import app.aaps.plugins.eversense.EversenseDriver.ConnectionState
import app.aaps.plugins.eversense.packets.e3.EnterDiagnosticModePacket
import app.aaps.plugins.eversense.packets.e3.EversenseE3Packets
import app.aaps.plugins.eversense.packets.e3.PingPacket
import app.aaps.plugins.eversense.util.StorageKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
        assertEquals(1, central.connects.size)
        for (wait in listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L)) {
            val before = central.connects.size
            advance(wait - 1)
            assertEquals(before, central.connects.size)
            advance(1)
            assertEquals(before + 1, central.connects.size)
        }

        // A ready link starts the count again
        central.acceptConnects()
        advance(minute)
        assertEquals(ConnectionState.READY, driver.connectionState.value)
        central.links.last().lose(status = 8)
        runCurrent()
        val before = central.connects.size
        advance(4_999)
        assertEquals(before, central.connects.size)
        advance(1)
        assertEquals(before + 1, central.connects.size)
    }

    /** An E3 that closed the link itself is lifted off the sensor: tried again after 30 s, not at once. */
    @Test fun anE3ClosedByTheTransmitterIsTriedAgainAfter30Seconds(): Unit = harness.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        assertEquals(ConnectionState.READY, driver.connectionState.value)
        central.links[0].lose(status = CLOSED_BY_TRANSMITTER)
        runCurrent()
        assertEquals(ConnectionState.DISCONNECTED, driver.connectionState.value)
        advance(29_999)
        assertEquals(1, central.connects.size)
        advance(1)
        assertEquals(2, central.connects.size)
        assertFalse(central.connects[1].androidAutoConnect)
    }

    @Test fun threeE3ClosesInARowTellTheUserTheTransmitterIsNotPlaced(): Unit = harness.run { driver ->
        harness.pairedE3()
        central.refuseConnects(status = CLOSED_BY_TRANSMITTER)
        driver.start()
        runCurrent()
        advance(30_000)
        assertEquals(0, watcher.notPlaced)
        advance(30_000)
        assertEquals(3, central.connects.size)
        assertEquals(1, watcher.notPlaced)

        // A ready link starts the count again: two closes after it are not three in a row
        central.acceptConnects()
        advance(30_000)
        assertEquals(ConnectionState.READY, driver.connectionState.value)
        central.links.last().lose(status = CLOSED_BY_TRANSMITTER)
        runCurrent()
        central.refuseConnects(status = CLOSED_BY_TRANSMITTER)
        advance(30_000)
        assertEquals(1, watcher.notPlaced)
        advance(30_000)
        assertEquals(2, watcher.notPlaced)
    }

    /** A 365 closes the link as its usual cycle: the next connect waits in the background for it to come back. */
    @Test fun a365ClosedByTheTransmitterReconnectsWithAutoConnect(): Unit = harness.run { driver ->
        harness.paired365()
        central.newLink = { device -> FakeBleLink(device, is365 = true) }
        driver.start()
        runCurrent()
        assertFalse(central.connects.single().androidAutoConnect)
        assertEquals(EversenseDriver.CONNECT_TIMEOUT, central.connects.single().timeout)

        central.links[0].lose(status = CLOSED_BY_TRANSMITTER)
        runCurrent()
        advance(4_999)
        assertEquals(1, central.connects.size)
        advance(1)
        assertEquals(2, central.connects.size)
        assertTrue(central.connects[1].androidAutoConnect)
        assertEquals(EversenseDriver.ATTEMPT_DEADLINE, central.connects[1].timeout)

        // Lost by the radio: the next connect is a direct one again
        central.links[1].lose(status = 8)
        runCurrent()
        advance(10_000)
        assertEquals(3, central.connects.size)
        assertFalse(central.connects[2].androidAutoConnect)
        // A 365 that closes the link is never "not placed"
        assertEquals(0, watcher.notPlaced)
    }

    /** No Bluetooth: nothing is opened, and the driver looks again after a minute, also without being told. */
    @Test fun aLinkThatCannotOpenIsTriedAgainAfterAMinute(): Unit = harness.run { driver ->
        harness.pairedE3()
        central.adapterState.value = AdapterState.OFF
        driver.start()
        runCurrent()
        assertEquals(emptyList(), central.connects)
        assertEquals(ConnectionState.DISCONNECTED, driver.connectionState.value)
        central.adapterState.value = AdapterState.ON
        advance(minute - 1)
        assertEquals(emptyList(), central.connects)
        advance(1)
        assertEquals(1, central.connects.size)
        assertEquals(ConnectionState.READY, driver.connectionState.value)
    }

    /** A transmitter that never signs in does not leave the driver connecting for ever. */
    @Test fun anAttemptThatIsNotReadyIn180SecondsIsEnded(): Unit = harness.run { driver ->
        harness.pairedE3()
        central.newLink = { device -> FakeBleLink(device, is365 = false).also { it.actAsE3(acceptBonding = false) } }
        driver.start()
        runCurrent()
        assertEquals(ConnectionState.CONNECTING, driver.connectionState.value)
        advance(EversenseDriver.ATTEMPT_DEADLINE.inWholeMilliseconds - 1)
        assertEquals(ConnectionState.CONNECTING, driver.connectionState.value)
        assertTrue(central.links[0].isUp)
        advance(1)
        assertEquals(ConnectionState.DISCONNECTED, driver.connectionState.value)
        assertFalse(central.links[0].isUp)
        advance(5_000)
        assertEquals(2, central.connects.size)
    }

    /** Only a ready link that brings nothing is replaced: a keep alive counts as data. */
    @Test fun aReadyLinkWithNoDataFor7MinutesIsReplaced(): Unit = harness.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        assertEquals(ConnectionState.READY, driver.connectionState.value)
        advance(6 * minute)
        central.links[0].send(e3Frame(EversenseE3Packets.KeepAlivePush))
        runCurrent()
        advance(7 * minute)
        assertEquals(1, central.connects.size)
        assertTrue(central.links[0].isUp)
        advance(minute)
        assertEquals(2, central.connects.size)
        assertFalse(central.links[0].isUp)
        assertEquals(ConnectionState.READY, driver.connectionState.value)
    }

    @Test fun aLostLinkIsReplacedOnceAndTheOldOneIsClosed(): Unit = harness.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        central.links[0].lose(status = 133)
        runCurrent()
        assertEquals(1, central.links[0].closes)
        assertEquals(listOf(true, false), watcher.connections)
        advance(5_000)
        assertEquals(2, central.connects.size)
        assertEquals(ConnectionState.READY, driver.connectionState.value)
        advance(6 * minute)
        assertEquals(2, central.connects.size)
        assertEquals(2, watcher.ready)
    }

    // ----- start, connect, disconnect, stop -----

    /** Unpaired, a start waits for the pairing instead of trying every minute to reach a transmitter it does not have. */
    @Test fun aStartWithoutATransmitterWaitsForThePairing(): Unit = harness.run { driver ->
        harness.storeLogin(model = "E3")
        driver.start()
        runCurrent()
        assertEquals(ConnectionState.PAUSED, driver.connectionState.value)
        driver.onBluetoothAvailable()
        advance(1_000 * minute)
        assertEquals(emptyList(), central.connects)

        assertTrue(driver.pair(ADDRESS, NAME))
        runCurrent()
        assertEquals(1, central.connects.size)
        assertEquals(ConnectionState.READY, driver.connectionState.value)
    }

    /** A 365 registers this phone with the Eversense server: with no login it is not connected at all. */
    @Test fun a365WithoutALoginDoesNotConnect(): Unit = harness.run { driver ->
        harness.storeLogin(model = "E365_USA")
        harness.storeTransmitter()
        driver.start()
        runCurrent()
        assertEquals(ConnectionState.PAUSED, driver.connectionState.value)
        assertFalse(driver.connect())
        advance(10 * minute)
        assertEquals(emptyList(), central.connects)
    }

    @Test fun stopClosesTheLinkAndNothingRunsAfterIt(): Unit = harness.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        driver.stop()
        assertEquals(ConnectionState.STOPPED, driver.connectionState.value)
        assertFalse(central.links[0].isUp)
        driver.onBluetoothAvailable()
        advance(1_000 * minute)
        assertEquals(1, central.connects.size)
        assertEquals(ConnectionState.STOPPED, driver.connectionState.value)
        assertFalse(driver.connect())
        assertNull(driver.cloudStore())
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
        assertEquals(1, central.connects.size)
        assertEquals(ConnectionState.STOPPED, driver.connectionState.value)
    }

    @Test fun disconnectStaysPausedUntilConnect(): Unit = harness.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        driver.disconnect()
        assertEquals(ConnectionState.PAUSED, driver.connectionState.value)
        assertFalse(central.links[0].isUp)
        driver.onBluetoothAvailable()
        advance(1_000 * minute)
        assertEquals(1, central.connects.size)
        assertEquals(ConnectionState.PAUSED, driver.connectionState.value)

        assertTrue(driver.connect())
        runCurrent()
        assertEquals(2, central.connects.size)
        assertEquals(ConnectionState.READY, driver.connectionState.value)
    }

    @Test fun connectOpensNoSecondLink(): Unit = harness.run { driver ->
        harness.pairedE3()
        central.newLink = { device -> FakeBleLink(device, is365 = false).also { it.actAsE3(acceptBonding = false) } }
        driver.start()
        runCurrent()
        // On its way: not ready yet, and not opened again
        assertFalse(driver.connect())
        runCurrent()
        assertEquals(1, central.connects.size)

        central.newLink = { device -> FakeBleLink(device, is365 = false).also { it.actAsE3() } }
        advance(EversenseDriver.ATTEMPT_DEADLINE.inWholeMilliseconds + 5_000)
        assertEquals(ConnectionState.READY, driver.connectionState.value)
        val connects = central.connects.size
        // Ready: nothing to do
        assertTrue(driver.connect())
        runCurrent()
        assertEquals(connects, central.connects.size)
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
        assertEquals(2, central.connects.size)
        assertEquals(ConnectionState.READY, driver.connectionState.value)
    }

    // ----- the cloud store -----

    /**
     * The cloud upload's store outlives a lost link and the reconnect after it, but not a disconnect, a new login or a
     * stop: an upload in flight cannot bring back what a sign-out removed.
     */
    @Test fun theCloudStoreEndsWithADisconnectANewLoginAndAStop(): Unit = harness.run { driver ->
        harness.pairedE3()
        assertNull(driver.cloudStore())
        driver.start()
        runCurrent()
        val cloud = driver.cloudStore()!!
        cloud.edit().putString("token", "valid").apply()
        central.links[0].lose(status = 133)
        runCurrent()
        advance(5_000)
        assertEquals(ConnectionState.READY, driver.connectionState.value)
        cloud.edit().putString("token", "still valid").apply()
        assertEquals("still valid", harness.store.getString("token", null))

        driver.disconnect()
        assertFailsWith<CancellationException> { cloud.edit() }
        assertNull(driver.cloudStore())

        driver.connect()
        runCurrent()
        val next = driver.cloudStore()!!
        assertEquals("still valid", next.getString("token", null))
        driver.setCredentials("other@example.com", "other", europeanRegion = false)
        assertFailsWith<CancellationException> { next.edit() }

        val last = driver.cloudStore()!!
        driver.stop()
        assertFailsWith<CancellationException> { last.edit() }
        assertNull(driver.cloudStore())
    }

    // ----- work on the ready link -----

    /** The transmitter refusing a calibration is a failed calibration, not a lost link. */
    @Test fun aRefusedCalibrationKeepsTheLink(): Unit = harness.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        // The fake E3 refuses every command but the bonding
        assertFalse(driver.sendCalibration(100))
        runCurrent()
        assertEquals(ConnectionState.READY, driver.connectionState.value)
        assertTrue(central.links[0].isUp)
        // And the driver still reconnects after a loss
        central.links[0].lose(status = 133)
        runCurrent()
        advance(5_000)
        assertEquals(2, central.connects.size)
        assertEquals(ConnectionState.READY, driver.connectionState.value)
    }

    /** Work that needs a ready link is refused while the transmitter signs in: nothing is sent for it. */
    @Test fun aCalibrationIsRefusedWhileTheTransmitterSignsIn(): Unit = harness.run { driver ->
        harness.pairedE3()
        central.newLink = { device -> FakeBleLink(device, is365 = false).also { it.actAsE3(acceptBonding = false) } }
        driver.start()
        runCurrent()
        assertEquals(ConnectionState.CONNECTING, driver.connectionState.value)
        val written = central.links[0].writes.size
        assertFalse(driver.sendCalibration(100))
        assertNull(driver.readSignalStrength())
        runCurrent()
        assertEquals(written, central.links[0].writes.size)
    }

    @Test fun aSignalThatCannotBeReadKeepsTheLink(): Unit = harness.run { driver ->
        harness.pairedE3()
        assertNull(driver.readSignalStrength())
        driver.start()
        runCurrent()
        assertNull(driver.readSignalStrength())
        runCurrent()
        assertEquals(ConnectionState.READY, driver.connectionState.value)
        assertTrue(central.links[0].isUp)
    }

    @Test fun aSyncRunsOnlyOnAReadyLink(): Unit = harness.run { driver ->
        harness.pairedE3()
        driver.triggerFullSync(force = true)
        runCurrent()
        assertEquals(emptyList(), central.connects)
        driver.start()
        runCurrent()
        val written = central.links[0].writes.size
        driver.triggerFullSync(force = true)
        runCurrent()
        assertEquals(PingPacket().spec.requestId, central.links[0].writes.drop(written).first()[0])
        assertEquals(ConnectionState.READY, driver.connectionState.value)
    }

    /** The placement guide keeps the transmitter in diagnostic mode, also over a reconnect. */
    @Test fun thePlacementGuideKeepsDiagnosticModeOverAReconnect(): Unit = harness.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        val enter = EnterDiagnosticModePacket().spec.requestId
        driver.enterPositioningMode()
        assertTrue(driver.isPositioningMode)
        assertContains(central.links[0].writes.map { it[0] }, enter)

        central.links[0].lose(status = 133)
        runCurrent()
        advance(5_000)
        assertContains(central.links[1].writes.map { it[0] }, enter)

        advance(1_000)
        driver.exitPositioningMode()
        assertFalse(driver.isPositioningMode)
        assertEquals(START + currentTime, driver.lastPositioningModeExitAt)
    }

    @Test fun pairStoresTheTransmitterAndConnectsToIt(): Unit = harness.run { driver ->
        harness.storeLogin(model = "E3")
        driver.start()
        assertTrue(driver.pair("AA:BB:CC:DD:EE:FF", "T654321"))
        runCurrent()
        assertEquals("AA:BB:CC:DD:EE:FF", harness.store.getString(StorageKeys.REMOTE_DEVICE_KEY, null))
        assertEquals("T654321", harness.store.getString(StorageKeys.REMOTE_DEVICE_NAME, null))
        assertEquals(DeviceId("AA:BB:CC:DD:EE:FF"), central.links.single().device)
        assertEquals(ConnectionState.READY, driver.connectionState.value)
    }

    // ----- iOS: no timers in the background -----

    /** A suspended iOS app runs no timer: a lost link waits for the transmitter in a pending connect, started at once. */
    @Test fun withoutTimersALostLinkWaitsForTheTransmitterAtOnce(): Unit = harness.also { it.reconnectWithoutTimers = true }.run { driver ->
        harness.pairedE3()
        driver.start()
        runCurrent()
        assertEquals(EversenseDriver.CONNECT_TIMEOUT, central.connects.single().timeout)

        central.links[0].lose(status = 8)
        runCurrent()
        assertEquals(2, central.connects.size)
        assertTrue(central.connects[1].androidAutoConnect)
        assertEquals(Duration.INFINITE, central.connects[1].timeout)
        assertEquals(ConnectionState.READY, driver.connectionState.value)
    }

    /** A link the session gave up on is not a radio loss: at once it would only fail again, so it waits on the backoff. */
    @Test fun withoutTimersALinkThatNeverGotReadyStillWaitsOnTheBackoff(): Unit = harness.also { it.reconnectWithoutTimers = true }.run { driver ->
        harness.pairedE3()
        central.newLink = { device -> FakeBleLink(device, is365 = false).also { it.actAsE3(acceptBonding = false) } }
        driver.start()
        runCurrent()
        advance(EversenseDriver.ATTEMPT_DEADLINE.inWholeMilliseconds)
        assertEquals(ConnectionState.DISCONNECTED, driver.connectionState.value)
        advance(4_999)
        assertEquals(1, central.connects.size)
        advance(1)
        assertEquals(2, central.connects.size)
        assertEquals(EversenseDriver.CONNECT_TIMEOUT, central.connects[1].timeout)
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
        assertEquals(2, central.links.size)
        advance(EversenseDriver.ATTEMPT_DEADLINE.inWholeMilliseconds - 1)
        assertTrue(central.links[1].isUp)
        assertEquals(ConnectionState.CONNECTING, driver.connectionState.value)
        advance(1)
        assertFalse(central.links[1].isUp)
    }
}
