package app.aaps.pump.aaruy.services

import android.content.Context
import android.os.Looper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.comm.BasicParam
import app.aaps.pump.aaruy.comm.aaruyFrameKey
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfoDao
import app.aaps.pump.aaruy.database.HistoryUnitInfo
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import app.aaps.pump.aaruy.emulator.AaruyEmulatorBleTransport
import app.aaps.pump.aaruy.emulator.AaruyPumpEmulator
import app.aaps.pump.aaruy.keys.AaruyDoublePreferenceKey
import app.aaps.pump.aaruy.keys.AaruyIntPreferenceKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.RETURNS_SMART_NULLS
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.concurrent.thread

/**
 * A bolus start that no status answered, so the pump may never have had it. Its record counts the whole amount, and the
 * pump log decides once the bolus would have ended. On the emulator, so the status and the log come as from a pump.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AaruyUnconfirmedBolusTest {

    private val minute = 60_000L
    private val dateUtil = mock(DateUtil::class.java, RETURNS_SMART_NULLS)
    private val pumpSync = mock(PumpSync::class.java) { invocation -> if (invocation.method.name.startsWith("sync") || invocation.method.name.startsWith("insert")) true else null }
    private val historyDao = mock(HistoryUnitInfoDao::class.java)
    private val preferences = mock(Preferences::class.java).also { preferences ->
        // A mock gives 0 for a number, and that would be a limit of 0 U for a bolus.
        AaruyDoublePreferenceKey.entries.forEach { `when`(preferences.get(it)).thenReturn(it.defaultValue) }
        AaruyIntPreferenceKey.entries.forEach { `when`(preferences.get(it)).thenReturn(it.defaultValue) }
    }
    private val logger = mock(AAPSLogger::class.java)
    private val pump = AaruyPump(
        logger, mock(ResourceHelper::class.java), preferences, dateUtil, pumpSync,
        mock(TemporaryBasalStorage::class.java), mock(BolusProgressData::class.java)
    )
    private lateinit var ble: AaruyBLE
    private lateinit var transport: AaruyEmulatorBleTransport

    @After
    fun tearDown() = transport.shutdown()

    private fun waitFor(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!condition()) {
            assertTrue("Timeout waiting for: $what", System.currentTimeMillis() < end)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(20)
        }
    }

    private fun connect(unconfirmed: AaruyPump.UnconfirmedBolus) {
        `when`(dateUtil.now()).thenAnswer { System.currentTimeMillis() }
        pump.canSaveHistory = true
        pump.bolusStep = 2
        pump.unconfirmedBolus = unconfirmed
        val emulator = AaruyPumpEmulator(keyProvider = { aaruyFrameKey() })
        emulator.addBasalHistory(3)
        ble = AaruyBLE(
            AaruyEmulatorBleTransport(emulator), logger, RuntimeEnvironment.getApplication() as Context, pump, dateUtil,
            mock(RxBus::class.java), preferences, historyDao, mock(HistoryDailyUnitInfoDao::class.java),
            mock(DetailedBolusInfoStorage::class.java), pumpSync, mock(AaruyTempBasalSync::class.java),
            mock(NotificationManager::class.java), mock(ResourceHelper::class.java, RETURNS_SMART_NULLS)
        )
        ble.startEmulatorIfActive()
        transport = ble.emulatorTransport!!
        ble.connect("test", AaruyEmulatorBleTransport.DEVICE_ADDRESS)
        waitFor("connected") { pump.connectionState == ConnectionState.CONNECTED }
        waitFor("status read at connect") { pump.pumpLastState == AaruyPumpState.BASALRATE_RUNNING }
    }

    /** Reads the log again and again, as the status reads of the queue do, until [done]. */
    private fun readLogUntil(what: String, done: () -> Boolean) {
        waitFor(what) {
            if (!ble.receivingHistory) ble.requestHistoryData(pump.lastHistoryTime)
            done()
        }
    }

    private fun zeroed(start: Long) {
        runBlocking { verify(pumpSync, timeout(5_000)).syncBolusWithTempId(eq(start), argThat<PumpInsulin> { cU == 0.0 }, eq(start), anyOrNull(), eq(start), any(), any()) }
    }

    private fun neverZeroed() {
        runBlocking { verify(pumpSync, never()).syncBolusWithTempId(any(), argThat<PumpInsulin> { cU == 0.0 }, any(), anyOrNull(), anyOrNull(), any(), any()) }
    }

    @Test
    fun aStartThePumpNeverHadCountsNothingOnceTheLogHasNoBolus() {
        val start = System.currentTimeMillis() - 3 * minute
        connect(AaruyPump.UnconfirmedBolus(start, 0.5, decideAfter = start + minute, logIdBefore = 0))
        readLogUntil("the log decided") { pump.unconfirmedBolus == null }
        zeroed(start)
        runBlocking { verify(pumpSync, timeout(5_000)).insertAnnouncement(any(), anyOrNull(), any(), any()) }
    }

    /** The pump gave it: its entry already set the record to what the pump gave. */
    @Test
    fun aBolusInTheLogKeepsWhatItsEntryWrote() {
        val start = System.currentTimeMillis() - 3 * minute
        `when`(historyDao.querySince(any())).thenReturn(listOf(HistoryUnitInfo().apply { id = 8; type = 2; time = start - 5_000 }))
        connect(AaruyPump.UnconfirmedBolus(start, 0.5, decideAfter = start + minute, logIdBefore = 7))
        readLogUntil("the log decided") { pump.unconfirmedBolus == null }
        Thread.sleep(300)
        neverZeroed()
    }

    /**
     * Found by the scenario tests: a bolus 30 seconds before this start, stored before this start was sent, is an earlier
     * bolus. Taken for this one, a start the pump never had kept its whole amount.
     */
    @Test
    fun anEarlierBolusStoredBeforeTheStartIsNotThisOne() {
        val start = System.currentTimeMillis() - 3 * minute
        `when`(historyDao.querySince(any())).thenReturn(listOf(HistoryUnitInfo().apply { id = 7; type = 2; time = start - 30_000 }))
        connect(AaruyPump.UnconfirmedBolus(start, 0.5, decideAfter = start + minute, logIdBefore = 7))
        readLogUntil("the log decided") { pump.unconfirmedBolus == null }
        zeroed(start)
    }

    /** A bolus is logged when it ends. Before it would have ended, no entry is no answer. */
    @Test
    fun nothingIsDecidedBeforeTheBolusWouldHaveEnded() {
        val start = System.currentTimeMillis()
        connect(AaruyPump.UnconfirmedBolus(start, 0.5, decideAfter = start + 10 * minute, logIdBefore = 0))
        waitFor("a whole read") { pump.lastHistoryTime > 0 }
        Thread.sleep(300)
        assertNotNull(pump.unconfirmedBolus)
        neverZeroed()
    }

    /** The pump runs a bolus, so it had the start. The log gives the amount when it ends. */
    @Test
    fun aStatusThatShowsABolusEndsTheQuestion() {
        val start = System.currentTimeMillis()
        connect(AaruyPump.UnconfirmedBolus(start, 0.5, decideAfter = start + 10 * minute, logIdBefore = 0))
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
        thread { ble.setBolus(0.1, false) }
        waitFor("the bolus status ended the question") { pump.unconfirmedBolus == null }
        neverZeroed()
    }
}
