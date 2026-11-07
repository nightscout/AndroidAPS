package app.aaps.pump.aaruy.services

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
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
    private val pumpSync = mock(PumpSync::class.java) { invocation -> if (invocation.method.name.startsWith("sync") || invocation.method.name.startsWith("insertTherapyEvent")) true else null }
    private val historyDao = mock(HistoryUnitInfoDao::class.java)
    private val dailyDao = mock(HistoryDailyUnitInfoDao::class.java)
    private val rh = mock(ResourceHelper::class.java)
    private val tempBasalSync = mock(AaruyTempBasalSync::class.java)
    private val notificationManager = mock(NotificationManager::class.java)
    private val bolusInfoStorage = mock(DetailedBolusInfoStorage::class.java)
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
            mock(Preferences::class.java), historyDao, dailyDao, bolusInfoStorage, pumpSync, tempBasalSync,
            notificationManager, rh
        )
    }

    @After
    fun tearDown() = transport.shutdown()

    private fun receive(vararg items: LogItem) {
        ble.onHistoryLog(LogInfo(items.size, 1, 0, items.size, items.toList()))
    }

    private fun page(page: Int, total: Int, vararg items: LogItem) {
        ble.onHistoryLog(LogInfo(items.size, total, page, items.size, items.toList()))
    }

    private fun tbrEntry(start: Long, minutes: Int) = LogItem(timestamp = start, type = 1, data = 500, injectTime = minutes, delayBolus = 0, reserved = 0)

    @Test
    fun aTempBasalEntryGoesToTheSyncWithTheStartOfTheNextEntry() {
        val first = tbrEntry(now - 20 * minute, 5)
        val second = tbrEntry(now - 10 * minute, 5)
        receive(first, second)
        runBlocking {
            verify(tempBasalSync, timeout(5000)).onHistoryTempBasal(first, second.timestamp, second, null)
            // The newest entry of the read: nothing after it tells, so the status does.
            verify(tempBasalSync, timeout(5000)).onHistoryTempBasal(second, null, null, null)
        }
    }

    private fun bolusEntry(start: Long) = LogItem(timestamp = start, type = 2, data = 500, injectTime = 0, delayBolus = 0, reserved = 0)
    private fun basalEntry(start: Long) = LogItem(timestamp = start, type = 0, data = 1000, injectTime = 30, delayBolus = 0, reserved = 0)

    /**
     * A bolus says nothing about a temp basal before it, and what does tell can be on the next page. Left to the status now,
     * a temp basal the pump ended during a lost link went on in AAPS when the pump ran another one now.
     */
    @Test
    fun aTempBasalAtTheEndOfAPageWaitsForWhatTheNextPageTells() {
        val tempBasal = tbrEntry(now - 30 * minute, 5)
        val bolus = bolusEntry(now - 25 * minute)
        val basal = basalEntry(now - 24 * minute)
        page(0, 2, tempBasal)
        page(1, 2, bolus, basal)
        runBlocking {
            verify(tempBasalSync, timeout(5000)).onHistoryTempBasal(tempBasal, bolus.timestamp, basal, null)
            verify(tempBasalSync, never()).onHistoryTempBasal(eq(tempBasal), anyOrNull(), eq(null), anyOrNull())
        }
    }

    /** Nothing after it tells in the whole read: it is the newest, and the status now decides, with the exact end. */
    @Test
    fun theNewestTempBasalOfAReadIsLeftToTheStatus() {
        val tempBasal = tbrEntry(now - 30 * minute, 5)
        val bolus = bolusEntry(now - 25 * minute)
        page(0, 2, tempBasal)
        page(1, 2, bolus)
        runBlocking { verify(tempBasalSync, timeout(5000)).onHistoryTempBasal(tempBasal, bolus.timestamp, null, null) }
    }

    /** The read with a hole is asked again, and the entry comes again with it. Decided now, it would be decided without its next page. */
    @Test
    fun aTempBasalWaitingForAPageAfterAHoleIsNotDecided() {
        val tempBasal = tbrEntry(now - 30 * minute, 5)
        page(0, 3, tempBasal)
        page(2, 3, basalEntry(now - 20 * minute))
        verify(historyDao, timeout(5000)).insert(any())
        Thread.sleep(300)
        runBlocking { verify(tempBasalSync, never()).onHistoryTempBasal(eq(tempBasal), anyOrNull(), anyOrNull(), anyOrNull()) }
    }

    /** An entry not yet written must come again if the app stops here. */
    @Test
    fun theLogTimeMovesOnlyAfterTheWrite() {
        `when`(historyDao.insert(any())).thenThrow(IllegalStateException("database full"))
        receive(tbrEntry(now - 20 * minute, 5))
        verify(historyDao, timeout(5000)).insert(any())
        Thread.sleep(300)
        assertEquals(0L, pump.lastHistoryTime)
    }

    @Test
    fun aCompleteReadMovesTheLogTime() {
        page(0, 3, tbrEntry(now - 30 * minute, 5))
        page(1, 3, tbrEntry(now - 20 * minute, 5))
        page(2, 3, tbrEntry(now - 10 * minute, 5))
        verify(historyDao, timeout(5000).times(3)).insert(any())
        Thread.sleep(300)
        assertEquals(now - 10 * minute, pump.lastHistoryTime)
        assertEquals(false, ble.receivingHistory)
    }

    /**
     * The page before the hole is stored. The page after it is left out: the gap fill would take the hole for time with
     * no insulin and write a pump stop that the next read could not remove. The same request brings all of it again.
     */
    @Test
    fun aMissingMiddlePageKeepsTheLogTimeLeavesTheRestOutAndAsksAgain() {
        val before = tbrEntry(now - 30 * minute, 5)
        val after = tbrEntry(now - 10 * minute, 5)
        page(0, 3, before)
        page(2, 3, after)
        verify(historyDao, timeout(5000)).insert(any())
        Thread.sleep(300)
        verify(historyDao, times(1)).insert(any())
        runBlocking {
            verify(tempBasalSync).onHistoryDelivery(eq(before.timestamp), any())
            verify(tempBasalSync, never()).onHistoryDelivery(eq(after.timestamp), any())
        }
        assertEquals(0L, pump.lastHistoryTime)
        assertEquals(true, ble.receivingHistory)
        verify(notificationManager, never()).post(any(), any<String>(), any(), any<Int>(), anyOrNull(), any(), anyOrNull())
    }

    @Test
    fun aMissingFirstPageKeepsTheLogTimeAndWritesNothing() {
        ble.requestHistoryData(0)
        page(1, 2, tbrEntry(now - 10 * minute, 5))
        Thread.sleep(500)
        verify(historyDao, never()).insert(any())
        assertEquals(0L, pump.lastHistoryTime)
    }

    /** A page that could not be written is a hole too: what came after the failed entry must come again. */
    @Test
    fun aPageThatCannotBeWrittenKeepsTheLogTimeAndLeavesTheRestOut() {
        `when`(historyDao.insert(any())).thenThrow(IllegalStateException("database full"))
        page(0, 2, tbrEntry(now - 30 * minute, 5))
        val after = tbrEntry(now - 10 * minute, 5)
        page(1, 2, after)
        verify(historyDao, timeout(5000)).insert(any())
        Thread.sleep(300)
        runBlocking { verify(tempBasalSync, never()).onHistoryDelivery(eq(after.timestamp), any()) }
        assertEquals(0L, pump.lastHistoryTime)
    }

    /** A new read starts at page 0 and is whole again. */
    @Test
    fun aCompleteReadAfterOneWithAHoleMovesTheLogTime() {
        page(0, 3, tbrEntry(now - 30 * minute, 5))
        page(2, 3, tbrEntry(now - 10 * minute, 5))
        page(0, 2, tbrEntry(now - 30 * minute, 5))
        page(1, 2, tbrEntry(now - 10 * minute, 5))
        Thread.sleep(500)
        assertEquals(now - 10 * minute, pump.lastHistoryTime)
    }

    /** A page that always fails would stop the history for good. The user must know. */
    @Test
    fun threeReadsWithAHoleInARowTellTheUser() {
        repeat(3) {
            page(0, 3, tbrEntry(now - 30 * minute, 5))
            page(2, 3, tbrEntry(now - 10 * minute, 5))
        }
        verify(notificationManager, timeout(5000)).post(eq(NotificationId.PUMP_SYNC_ERROR), any<String>(), any(), any<Int>(), anyOrNull(), any(), anyOrNull())
        assertEquals(0L, pump.lastHistoryTime)
    }

    /** The bolus info was found, but no record has its temporary id. The pump gave this bolus, so it must still be written, with its type. */
    @Test
    fun aBolusWithInfoButNoTemporaryRecordIsWrittenByPumpId() {
        val start = now - 20 * minute
        `when`(bolusInfoStorage.findDetailedBolusInfo(start, 3.0)).thenReturn(DetailedBolusInfo().apply { timestamp = start - 3000; insulin = 3.0; bolusType = BS.Type.SMB })
        runBlocking { `when`(pumpSync.syncBolusWithTempId(any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())).thenReturn(false) }
        receive(LogItem(timestamp = start, type = 2, data = 3000, injectTime = 0, delayBolus = 0, reserved = 0))
        runBlocking { verify(pumpSync, timeout(5000)).syncBolusWithPumpId(eq(start), any(), eq(BS.Type.SMB), eq(start), any(), any()) }
    }

    /** It was synced when it first came. Synced again with no type, a PRIMING record changed its amount by the concentration. */
    @Test
    fun aBolusThatComesAgainIsNotSyncedAgain() {
        val entry = LogItem(timestamp = now - 30 * minute, type = 2, data = 2000, injectTime = 0, delayBolus = 0, reserved = 4)
        `when`(historyDao.count(entry.timestamp, 2, 4)).thenReturn(1)
        receive(entry)
        Thread.sleep(300)
        runBlocking {
            verify(pumpSync, never()).syncBolusWithPumpId(any(), any(), anyOrNull(), any(), any(), any())
            verify(pumpSync, never()).syncBolusWithTempId(any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())
        }
    }

    /** Seen on a real pump: a read from an earlier time brought an older bolus, and the pump screen showed it as the last one. */
    @Test
    fun anOlderBolusReadAgainIsNotTheLastBolus() {
        val newer = LogItem(timestamp = now - 10 * minute, type = 2, data = 500, injectTime = 0, delayBolus = 0, reserved = 0)
        val older = LogItem(timestamp = now - 60 * minute, type = 2, data = 10_000, injectTime = 0, delayBolus = 0, reserved = 0)
        receive(newer)
        page(0, 2, older)
        page(1, 2, newer)
        receive(older)
        Thread.sleep(500)
        assertEquals(newer.timestamp, pump.lastBolusTime)
        assertEquals(0.5, pump.lastBolusAmount, 0.0001)
    }

    /** A bolus takes one step every 2 seconds, and no basal entry runs then, so this time is no gap. */
    @Test
    fun aBolusCountsAsDelivery() {
        pump.bolusStep = 2
        val start = now - 20 * minute
        receive(LogItem(timestamp = start, type = 2, data = 1000, injectTime = 0, delayBolus = 0, reserved = 0))
        runBlocking { verify(tempBasalSync, timeout(5000)).onHistoryDelivery(start, start + 40_000) }
    }

    /**
     * Seen on a real pump: the history keeps only the low byte, a blocked motor (0x8105) is 5. The full code was read and dropped it.
     * It is written at the time of the alarm, by a call that writes nothing when that record exists: the next read brings the entry again.
     */
    @Test
    fun anOldAlarmIsRecordedOnceAtTheTimeOfTheAlarm() {
        receive(LogItem(timestamp = now - 30 * minute, type = 3, data = 5, injectTime = 0, delayBolus = 0, reserved = 0))
        runBlocking {
            verify(pumpSync, timeout(5000)).insertTherapyEventIfNewWithTimestamp(eq(now - 30 * minute), eq(TE.Type.ANNOUNCEMENT), eq("alarm"), eq(now - 30 * minute), any(), any())
            verify(pumpSync, never()).insertAnnouncement(any(), anyOrNull(), any(), any())
        }
    }

    @Test
    fun anAlarmJustSeenLiveIsNotRecordedAgain() {
        receive(LogItem(timestamp = now - 10_000, type = 3, data = 5, injectTime = 0, delayBolus = 0, reserved = 0))
        Thread.sleep(300)
        runBlocking { verify(pumpSync, never()).insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any()) }
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
