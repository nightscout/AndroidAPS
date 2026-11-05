package app.aaps.pump.aaruy.services

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.comm.DailyInfo
import app.aaps.pump.aaruy.comm.DailyItem
import app.aaps.pump.aaruy.comm.LogInfo
import app.aaps.pump.aaruy.comm.LogItem
import app.aaps.pump.aaruy.comm.aaruyFrameKey
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfo
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfoDao
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import app.aaps.pump.aaruy.emulator.AaruyEmulatorBleTransport
import app.aaps.pump.aaruy.emulator.AaruyPumpEmulator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** What the pump log says about the time the link was down. Layouts as captured from a real pump. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AaruyHistorySyncTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val logger = mock(AAPSLogger::class.java)
    private val dateUtil = mock(DateUtil::class.java)
    private val pumpSync = mock(PumpSync::class.java)
    private val historyDao = mock(HistoryUnitInfoDao::class.java)
    private val dailyDao = mock(HistoryDailyUnitInfoDao::class.java)
    private val rh = mock(ResourceHelper::class.java)
    private val pump = AaruyPump(
        logger, rh, mock(Preferences::class.java), dateUtil, pumpSync,
        mock(TemporaryBasalStorage::class.java), mock(BolusProgressData::class.java)
    )
    private val transport = AaruyEmulatorBleTransport(AaruyPumpEmulator(keyProvider = { aaruyFrameKey() }))
    private lateinit var ble: AaruyBLE

    @Before
    fun setUp() {
        `when`(dateUtil.now()).thenReturn(now)
        `when`(rh.gs(org.mockito.ArgumentMatchers.anyInt())).thenReturn("alarm")
        pump.canSaveHistory = true
        ble = AaruyBLE(
            transport, logger, RuntimeEnvironment.getApplication() as Context, pump, dateUtil, mock(RxBus::class.java),
            mock(Preferences::class.java), historyDao, dailyDao, mock(DetailedBolusInfoStorage::class.java), pumpSync
        )
    }

    @After
    fun tearDown() = transport.shutdown()

    private fun tbrRunningSince(start: Long) = runBlocking {
        whenever(pumpSync.expectedPumpState()).thenReturn(
            PumpSync.PumpState(
                temporaryBasal = PumpSync.PumpState.TemporaryBasal(start, 30 * minute, 0.5, true, PumpSync.TemporaryBasalType.NORMAL, 0L, 0L),
                extendedBolus = null, bolus = null, profile = null, serialNumber = ""
            )
        )
    }

    private fun receive(vararg items: LogItem) {
        ble.onHistoryLog(LogInfo(items.size, 1, 0, items.size, items.toList()))
    }

    private fun tbrEntry(start: Long, minutes: Int) = LogItem(timestamp = start, type = 1, data = 500, injectTime = minutes, delayBolus = 0, reserved = 0)

    @Test
    fun aTempBasalThePumpEndedWhileTheLinkWasDownIsStopped() {
        val start = now - 20 * minute
        tbrRunningSince(start + 500)
        receive(tbrEntry(start, 5))
        runBlocking { verify(pumpSync, timeout(5000)).syncStopTemporaryBasalWithPumpId(eq(start + 5 * minute), any(), any(), any(), any()) }
    }

    @Test
    fun anOlderTempBasalDoesNotStopTheOneThatRuns() {
        val start = now - 20 * minute
        tbrRunningSince(start)
        receive(tbrEntry(start - 60 * minute, 5))
        Thread.sleep(300)
        runBlocking { verify(pumpSync, never()).syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any()) }
    }

    @Test
    fun anOldAlarmIsRecordedWithThePumpTime() {
        receive(LogItem(timestamp = now - 30 * minute, type = 3, data = 0x8105, injectTime = 0, delayBolus = 0, reserved = 0))
        runBlocking { verify(pumpSync, timeout(5000)).insertAnnouncement(eq("alarm"), eq(now - 30 * minute), any(), any()) }
    }

    @Test
    fun anAlarmJustSeenLiveIsNotRecordedAgain() {
        receive(LogItem(timestamp = now - 10_000, type = 3, data = 0x8105, injectTime = 0, delayBolus = 0, reserved = 0))
        Thread.sleep(300)
        runBlocking { verify(pumpSync, never()).insertAnnouncement(any(), anyOrNull(), any(), any()) }
    }

    /** AAPS never starts one, so something else did. The status comes again and again while it runs. */
    @Test
    fun anExtendedBolusNotFromAapsIsAnnouncedOnce() {
        ble.onExtendedRunning()
        ble.onExtendedRunning()
        runBlocking { verify(pumpSync, timeout(5000)).insertAnnouncement(any(), anyOrNull(), any(), any()) }
        Thread.sleep(300)
        runBlocking { verify(pumpSync, times(1)).insertAnnouncement(any(), anyOrNull(), any(), any()) }
    }

    @Test
    fun aDayThatComesAgainUpdatesItsRow() {
        val row = HistoryDailyUnitInfo().apply { dailyTotal = 10; dailyBolus = 5 }
        `when`(dailyDao.find(eq(now), any())).thenReturn(row)
        ble.saveDailyData(DailyInfo(1, listOf(DailyItem(now, 2.3, 1.1))))
        verify(dailyDao, timeout(5000)).update(row)
        assertEquals(23, row.dailyTotal)
        assertEquals(11, row.dailyBolus)
        verify(dailyDao, never()).insert(any())
    }

    @Test
    fun aNewDayGetsANewRow() {
        ble.saveDailyData(DailyInfo(1, listOf(DailyItem(now, 2.3, 1.1))))
        verify(dailyDao, timeout(5000)).insert(any())
    }

    @Test
    fun theDayOfThePumpIsNotStoredAsATotalDailyDose() {
        ble.saveDailyData(DailyInfo(1, listOf(DailyItem(now, 2.3, 1.1))))
        verify(dailyDao, timeout(5000)).insert(any())
        Thread.sleep(300)
        runBlocking { verify(pumpSync, never()).createOrUpdateTotalDailyDose(any(), any(), any(), any(), anyOrNull(), any(), any()) }
    }

    @Test
    fun theLastEntryThatComesAgainIsNotStoredTwice() {
        val entry = LogItem(timestamp = now - 30 * minute, type = 0, data = 150, injectTime = 3, delayBolus = 0, reserved = 8)
        `when`(historyDao.count(entry.timestamp, 0, 8)).thenReturn(1)
        receive(entry)
        Thread.sleep(300)
        org.mockito.Mockito.verify(historyDao, org.mockito.Mockito.never()).insert(any())
    }
}
