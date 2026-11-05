package app.aaps.pump.aaruy.services

import android.content.Context
import android.os.Looper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.comm.BasicParam
import app.aaps.pump.aaruy.comm.BasalInfo
import app.aaps.pump.aaruy.comm.BasalItem
import app.aaps.pump.aaruy.comm.BasalItems
import app.aaps.pump.aaruy.comm.aaruyFrameKey
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfoDao
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import app.aaps.pump.aaruy.emulator.AaruyEmulatorBleTransport
import app.aaps.pump.aaruy.emulator.AaruyPumpEmulator
import app.aaps.pump.aaruy.keys.AaruyDoublePreferenceKey
import app.aaps.pump.aaruy.keys.AaruyIntPreferenceKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone
import kotlin.concurrent.thread
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** The driver's `AaruyBLE` on the Kotlin client and the emulator. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AaruyBleReconnectTest {

    private fun tempBasalSyncMock() = mock(AaruyTempBasalSync::class.java)

    /** A mock gives 0 for a number, and that would be a limit of 0 U for a bolus. */
    private fun preferencesWithDefaults(): Preferences {
        val preferences = mock(Preferences::class.java)
        AaruyDoublePreferenceKey.entries.forEach { `when`(preferences.get(it)).thenReturn(it.defaultValue) }
        AaruyIntPreferenceKey.entries.forEach { `when`(preferences.get(it)).thenReturn(it.defaultValue) }
        return preferences
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!condition()) {
            assertTrue("Timeout waiting for: $what", System.currentTimeMillis() < end)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(20)
        }
    }

    /** The bolus ends while the link is lost. The status read at reconnect is the only way to learn it. */
    @Test
    fun bolusThatEndedDuringALostLinkIsOverAfterReconnect() {
        val preferences = preferencesWithDefaults()
        val logger = mock(AAPSLogger::class.java)
        val pump = AaruyPump(
            logger,
            mock(ResourceHelper::class.java),
            preferences,
            mock(DateUtil::class.java),
            mock(PumpSync::class.java),
            mock(TemporaryBasalStorage::class.java),
            mock(BolusProgressData::class.java)
        )
        val ble = AaruyBLE(
            AaruyEmulatorBleTransport(AaruyPumpEmulator(keyProvider = { aaruyFrameKey() })),
            logger,
            RuntimeEnvironment.getApplication() as Context,
            pump,
            mock(DateUtil::class.java),
            mock(RxBus::class.java),
            preferences,
            mock(HistoryUnitInfoDao::class.java),
            mock(HistoryDailyUnitInfoDao::class.java),
            mock(DetailedBolusInfoStorage::class.java),
            mock(PumpSync::class.java), tempBasalSyncMock()
        )
        ble.startEmulatorIfActive()
        val transport = ble.emulatorTransport!!
        val emulator = transport.emulator

        ble.connect("first", AaruyEmulatorBleTransport.DEVICE_ADDRESS)
        waitFor("first connection") { pump.connectionState == ConnectionState.CONNECTED }
        waitFor("status read at connect") { pump.pumpLastState == AaruyPumpState.BASALRATE_RUNNING }
        pump.handleLoadBasicParam(BasicParam(2.0, 10.0, 2, 20.0, 0, 3))
        val writes = transport.writes.size
        ble.syncAndLoadBasicParam()
        waitFor("basic param sent") { transport.writes.size > writes }
        // The client refuses a bolus until the answer has told it the limit.
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(20)
        }

        pump.bolusDone = false
        // On its own thread: `setBolus` waits for a status that is handled on the main thread.
        Thread { ble.setBolus(0.1, false) }.start()
        waitFor("the driver saw the bolus") { pump.pumpLastState == AaruyPumpState.LARGEDOSE_RUNNING }
        assertEquals(false, pump.bolusDone)

        transport.outOfRange = true
        transport.loseConnection()
        waitFor("link lost") { !ble.isConnected }
        waitFor("the pump ended the bolus") { emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL }
        assertEquals("nothing can be known yet", false, pump.bolusDone)

        transport.outOfRange = false
        ble.connect("back in range", AaruyEmulatorBleTransport.DEVICE_ADDRESS)
        waitFor("reconnected") { ble.isConnected }
        waitFor("bolus over") { pump.bolusDone }

        assertTrue(pump.bolusEndNotSeen)
        assertEquals(AaruyPumpState.BASALRATE_RUNNING, pump.pumpState)
        transport.shutdown()
    }

    /** A real pump answers a stop by going straight from status 3 to 1. That confirms the stop, and no link was lost. */
    @Test
    fun aStopThatGoesBackToBasalIsConfirmedAndIsNotALostLink() {
        val preferences = preferencesWithDefaults()
        val logger = mock(AAPSLogger::class.java)
        val pump = AaruyPump(
            logger,
            mock(ResourceHelper::class.java),
            preferences,
            mock(DateUtil::class.java),
            mock(PumpSync::class.java),
            mock(TemporaryBasalStorage::class.java),
            mock(BolusProgressData::class.java)
        )
        val ble = AaruyBLE(
            AaruyEmulatorBleTransport(AaruyPumpEmulator(keyProvider = { aaruyFrameKey() })),
            logger,
            RuntimeEnvironment.getApplication() as Context,
            pump,
            mock(DateUtil::class.java),
            mock(RxBus::class.java),
            preferences,
            mock(HistoryUnitInfoDao::class.java),
            mock(HistoryDailyUnitInfoDao::class.java),
            mock(DetailedBolusInfoStorage::class.java),
            mock(PumpSync::class.java), tempBasalSyncMock()
        )
        ble.startEmulatorIfActive()
        val transport = ble.emulatorTransport!!

        ble.connect("first", AaruyEmulatorBleTransport.DEVICE_ADDRESS)
        waitFor("first connection") { pump.connectionState == ConnectionState.CONNECTED }
        waitFor("status read at connect") { pump.pumpLastState == AaruyPumpState.BASALRATE_RUNNING }
        pump.handleLoadBasicParam(BasicParam(2.0, 10.0, 2, 20.0, 0, 3))
        val writes = transport.writes.size
        ble.syncAndLoadBasicParam()
        waitFor("basic param sent") { transport.writes.size > writes }
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(20)
        }

        pump.bolusDone = false
        Thread { ble.setBolus(3.0, false) }.start()
        waitFor("the driver saw the bolus") { pump.pumpLastState == AaruyPumpState.LARGEDOSE_RUNNING }

        pump.bolusStopRequested = true
        var confirmed = false
        // `cancelBolus` sleeps in virtual time, so it can look before the answer is handled. The service repeats it too.
        Thread {
            repeat(50) {
                if (!confirmed) confirmed = ble.cancelBolus()
                Thread.sleep(100)
            }
        }.start()
        waitFor("the stop is confirmed") { confirmed }

        assertEquals(AaruyPumpState.BASALRATE_RUNNING, pump.pumpState)
        assertTrue("the bolus is over", pump.bolusDone)
        assertEquals("the link was never lost", false, pump.bolusEndNotSeen)
        transport.shutdown()
    }

    /**
     * A real pump has an empty history after pairing. If that keeps history off, a stopped bolus stays in the
     * database with the amount that was asked for.
     */
    @Test
    fun emptyHistoryAtPairingStillGivesTheRealAmountOfAStoppedBolus() {
        val preferences = preferencesWithDefaults()
        val logger = mock(AAPSLogger::class.java)
        val pumpSync = mock(PumpSync::class.java)
        val pump = AaruyPump(
            logger,
            mock(ResourceHelper::class.java),
            preferences,
            mock(DateUtil::class.java),
            pumpSync,
            mock(TemporaryBasalStorage::class.java),
            mock(BolusProgressData::class.java)
        )
        val ble = AaruyBLE(
            AaruyEmulatorBleTransport(AaruyPumpEmulator(keyProvider = { aaruyFrameKey() })),
            logger,
            RuntimeEnvironment.getApplication() as Context,
            pump,
            mock(DateUtil::class.java),
            mock(RxBus::class.java),
            preferences,
            mock(HistoryUnitInfoDao::class.java),
            mock(HistoryDailyUnitInfoDao::class.java),
            mock(DetailedBolusInfoStorage::class.java),
            pumpSync, tempBasalSyncMock()
        )
        ble.startEmulatorIfActive()
        val transport = ble.emulatorTransport!!

        ble.connect("first", AaruyEmulatorBleTransport.DEVICE_ADDRESS)
        waitFor("first connection") { pump.connectionState == ConnectionState.CONNECTED }
        waitFor("status read at connect") { pump.pumpLastState == AaruyPumpState.BASALRATE_RUNNING }
        waitFor("history is on after the empty reply") { pump.canSaveHistory }
        pump.handleLoadBasicParam(BasicParam(2.0, 10.0, 2, 20.0, 0, 3))
        val writes = transport.writes.size
        ble.syncAndLoadBasicParam()
        waitFor("basic param sent") { transport.writes.size > writes }
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(20)
        }

        pump.bolusDone = false
        Thread { ble.setBolus(3.0, false) }.start()
        waitFor("the driver saw the bolus") { pump.pumpLastState == AaruyPumpState.LARGEDOSE_RUNNING }
        waitFor("some of the bolus is delivered") { pump.bolusDelivered > 0.0 }
        pump.bolusStopRequested = true
        Thread { ble.cancelBolus() }.start()

        waitFor("the pump history gave the amount") {
            mockingDetails(pumpSync).invocations.any { it.method.name == "syncBolusWithPumpId" }
        }
        val amount = mockingDetails(pumpSync).invocations.first { it.method.name == "syncBolusWithPumpId" }.arguments[1].toString().filter { it.isDigit() || it == '.' }.toDouble()
        assertTrue("delivered, not asked: $amount", amount > 0.0 && amount < 3.0)
        transport.shutdown()
    }

    /**
     * The client is still connected, because `disconnectDevice` does not drop the link. It reports CONNECTED at once
     * when asked to connect, and that must not be overwritten by CONNECTING.
     */
    @Test
    fun connectWhileTheLinkIsStillUpEndsConnected() {
        val preferences = preferencesWithDefaults()
        val logger = mock(AAPSLogger::class.java)
        val pump = AaruyPump(
            logger,
            mock(ResourceHelper::class.java),
            preferences,
            mock(DateUtil::class.java),
            mock(PumpSync::class.java),
            mock(TemporaryBasalStorage::class.java),
            mock(BolusProgressData::class.java)
        )
        val ble = AaruyBLE(
            AaruyEmulatorBleTransport(AaruyPumpEmulator(keyProvider = { aaruyFrameKey() })),
            logger,
            RuntimeEnvironment.getApplication() as Context,
            pump,
            mock(DateUtil::class.java),
            mock(RxBus::class.java),
            preferences,
            mock(HistoryUnitInfoDao::class.java),
            mock(HistoryDailyUnitInfoDao::class.java),
            mock(DetailedBolusInfoStorage::class.java),
            mock(PumpSync::class.java), tempBasalSyncMock()
        )
        ble.startEmulatorIfActive()

        ble.connect("first", AaruyEmulatorBleTransport.DEVICE_ADDRESS)
        waitFor("first connection") { pump.connectionState == ConnectionState.CONNECTED && ble.isConnected }

        // This is what `disconnect` leaves behind: the driver thinks it is off, the client is still on.
        ble.isConnected = false

        ble.connect("retrying connection", AaruyEmulatorBleTransport.DEVICE_ADDRESS)
        waitFor("callbacks") { true }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        assertEquals(ConnectionState.CONNECTED, pump.connectionState)
        assertTrue("driver must know it is connected", ble.isConnected)
        assertEquals("not connecting", false, ble.isConnecting)
        ble.emulatorTransport?.shutdown()
    }

    /** Seen on a real pump: the answer to a new profile comes after a second, and a false failure raised an urgent alarm. */
    @Test
    fun aProfileThePumpAnswersLateIsNotAFailure() {
        val preferences = preferencesWithDefaults()
        val logger = mock(AAPSLogger::class.java)
        val pump = AaruyPump(
            logger,
            mock(ResourceHelper::class.java),
            preferences,
            mock(DateUtil::class.java),
            mock(PumpSync::class.java),
            mock(TemporaryBasalStorage::class.java),
            mock(BolusProgressData::class.java)
        )
        val ble = AaruyBLE(
            AaruyEmulatorBleTransport(AaruyPumpEmulator(keyProvider = { aaruyFrameKey() })),
            logger,
            RuntimeEnvironment.getApplication() as Context,
            pump,
            mock(DateUtil::class.java),
            mock(RxBus::class.java),
            preferences,
            mock(HistoryUnitInfoDao::class.java),
            mock(HistoryDailyUnitInfoDao::class.java),
            mock(DetailedBolusInfoStorage::class.java),
            mock(PumpSync::class.java), tempBasalSyncMock()
        )
        ble.startEmulatorIfActive()
        ble.connect("first", AaruyEmulatorBleTransport.DEVICE_ADDRESS)
        waitFor("first connection") { pump.connectionState == ConnectionState.CONNECTED }
        waitFor("status read at connect") { pump.pumpLastState == AaruyPumpState.BASALRATE_RUNNING }
        val settled = System.currentTimeMillis() + 2000
        waitFor("the settings of the connect have come") { System.currentTimeMillis() > settled }

        var accepted: Boolean? = null
        thread { accepted = ble.updateBasalsInPump(BasalInfo(BasalItems(4.8, 1, listOf(BasalItem(0, 0.2))))) }
        waitFor("the answer") { accepted != null }

        assertEquals(true, accepted)
        assertEquals(0.2, pump.baseBasalRateAt(0), 0.0)
        ble.emulatorTransport?.shutdown()
    }

    /** The case measured on the real pump: 23:06 in UTC+1 is 06:06 for the pump, and it must deliver the rate of 23:00. */
    @Test
    fun thePumpDeliversTheRateOfTheLocalHour() {
        val zoneBefore = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT-1"))
        try {
            val now = 1_793_830_000_000L
            val dateUtil = mock(DateUtil::class.java)
            `when`(dateUtil.now()).thenReturn(now)
            val preferences = preferencesWithDefaults()
            val logger = mock(AAPSLogger::class.java)
            val pump = AaruyPump(
                logger, mock(ResourceHelper::class.java), preferences, dateUtil, mock(PumpSync::class.java),
                mock(TemporaryBasalStorage::class.java), mock(BolusProgressData::class.java)
            )
            val ble = AaruyBLE(
                AaruyEmulatorBleTransport(AaruyPumpEmulator(keyProvider = { aaruyFrameKey() }, clock = { now })),
                logger, RuntimeEnvironment.getApplication() as Context, pump, dateUtil, mock(RxBus::class.java), preferences,
                mock(HistoryUnitInfoDao::class.java), mock(HistoryDailyUnitInfoDao::class.java), mock(DetailedBolusInfoStorage::class.java), mock(PumpSync::class.java), tempBasalSyncMock()
            )
            ble.startEmulatorIfActive()
            ble.connect("first", AaruyEmulatorBleTransport.DEVICE_ADDRESS)
            waitFor("first connection") { pump.connectionState == ConnectionState.CONNECTED }
            waitFor("status read at connect") { pump.pumpLastState == AaruyPumpState.BASALRATE_RUNNING }

            val profile = mock(Profile::class.java)
            `when`(profile.getBasalValues()).thenReturn(Array(24) { hour -> Profile.ProfileValue(hour * 3600, 0.1 + 0.05 * hour) })
            var accepted: Boolean? = null
            thread { accepted = ble.updateBasalsInPump(pump.buildAaruyProfileRecord(profile)) }
            waitFor("the answer") { accepted != null }
            assertEquals(true, accepted)

            ble.syncPumpStatus()
            waitFor("the rate of 23:00, not of 06:00") { pump.lastBasalRate == 1.25 }
            ble.emulatorTransport?.shutdown()
        } finally {
            TimeZone.setDefault(zoneBefore)
        }
    }
}
