package app.aaps.pump.aaruy.services

import app.aaps.core.data.model.TB
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.comm.LogItem
import app.aaps.pump.aaruy.keys.AaruyLongKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import org.mockito.invocation.Invocation
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Status frames and log entries as captured from a real pump. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AaruyTempBasalSyncTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val second = 1_000L
    private val dateUtil = mock(DateUtil::class.java)
    /** A suspend function of a plain mock returns null, also where it promises a Boolean. */
    private val pumpSync = mock(PumpSync::class.java) { invocation -> if (invocation.method.name.startsWith("sync")) true else null }
    private val preferences = mock(Preferences::class.java)
    private val storage = mock(TemporaryBasalStorage::class.java)
    private val persistence = mock(PersistenceLayer::class.java)
    private var concentration = 1.0
    private val ch = mock(ConcentrationHelper::class.java) { invocation ->
        when (invocation.method.returnType) {
            String::class.java        -> "0.50 U/h"
            Double::class.javaPrimitiveType -> concentration
            else                      -> null
        }
    }
    private val pump = AaruyPump(
        mock(AAPSLogger::class.java), mock(ResourceHelper::class.java).also { `when`(it.gs(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any())).thenReturn("text") },
        preferences, dateUtil, pumpSync, storage, mock(BolusProgressData::class.java)
    )
    private val sync = AaruyTempBasalSync(mock(AAPSLogger::class.java), dateUtil, pump, pumpSync, persistence, ch)

    private fun running(start: Long, rate: Double = 0.5, type: PumpSync.TemporaryBasalType = PumpSync.TemporaryBasalType.NORMAL, duration: Long = 30 * minute) =
        PumpSync.PumpState.TemporaryBasal(start, duration, rate, true, type, 0L, 77L)

    private fun pumpExpects(tbr: PumpSync.PumpState.TemporaryBasal?) = runBlocking {
        whenever(pumpSync.expectedPumpState()).thenReturn(PumpSync.PumpState(temporaryBasal = tbr, extendedBolus = null, bolus = null, profile = null, serialNumber = ""))
    }

    private fun databaseHolds(start: Long, duration: Long = 30 * minute) = runBlocking {
        val record = TB(timestamp = start, duration = duration, rate = 0.5, isAbsolute = true, type = TB.Type.NORMAL)
        whenever(persistence.getTemporaryBasalActiveAt(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as Long).takeIf { it in start..start + duration }?.let { record }
        }
    }

    private fun calls(name: String): List<Invocation> = mockingDetails(pumpSync).invocations.filter { it.method.name.startsWith(name) }
    private val writes get() = calls("syncTemporaryBasalWithPumpId")
    private val stops get() = calls("syncStopTemporaryBasalWithPumpId")
    private val announcements get() = calls("insertAnnouncement")

    private fun entry(start: Long, minutes: Int, rate: Long = 500) = LogItem(timestamp = start, type = 1, data = rate, injectTime = minutes, delayBolus = 0, reserved = 0)
    private fun tempBasalFrame(rate: Double = 0.5, remaining: Long = 1766, runningFor: Long = 34) = AaruyStatusSnapshot(2, rate, remaining, runningFor)

    @Before
    fun setUp() {
        `when`(dateUtil.now()).thenReturn(now)
        `when`(dateUtil.dateAndTimeString(org.mockito.ArgumentMatchers.anyLong())).thenReturn("time")
        pumpExpects(null)
        runBlocking { whenever(persistence.getTemporaryBasalsStartingFromTimeToTime(any(), any(), any())).thenReturn(emptyList()) }
    }

    @Test
    fun aTempBasalTheDatabaseDoesNotHaveIsWrittenFromTheStatus() = runBlocking {
        sync.onStatus(tempBasalFrame(), live = true)
        assertEquals(1, writes.size)
        assertEquals(now - 34 * second, writes.single().arguments[0])
        assertEquals(1800 * second, writes.single().arguments[2])
    }

    /** The answer to the command was lost, but the request is known. That is normal with a loop. */
    @Test
    fun aLostAnswerGivesNoAnnouncement() = runBlocking {
        whenever(storage.findTemporaryBasal(any(), any())).thenReturn(running(now))
        sync.onStatus(tempBasalFrame(), live = false)
        assertEquals(1, writes.size)
        assertEquals(0, announcements.size)
    }

    @Test
    fun aTempBasalAapsDidNotAskForIsAnnounced() = runBlocking {
        sync.onStatus(tempBasalFrame(), live = true)
        assertEquals(1, announcements.size)
    }

    @Test
    fun aKnownTempBasalWritesNothing() = runBlocking {
        pumpExpects(running(now - 34 * second))
        sync.onStatus(tempBasalFrame(), live = true)
        assertEquals(0, writes.size)
    }

    /** The database holds insulin units, the pump its own. With U200 the same temp basal has two numbers. */
    @Test
    fun theSameTempBasalWithAnotherInsulinWritesNothing() = runBlocking {
        concentration = 2.0
        pumpExpects(running(now - 34 * second, rate = 1.0))
        sync.onStatus(tempBasalFrame(rate = 0.5), live = true)
        assertEquals(0, writes.size)
    }

    @Test
    fun anotherRateIsAnotherTempBasal() = runBlocking {
        pumpExpects(running(now - 10 * minute, rate = 1.0))
        sync.onStatus(tempBasalFrame(rate = 0.5), live = true)
        assertEquals(1, writes.size)
    }

    /** Seen on a real pump: the first frame of a read has old counters. The next one corrects the start in the same record. */
    @Test
    fun aLaterFrameCorrectsALateStart() = runBlocking {
        pumpExpects(running(now - 34 * second))
        sync.onStatus(tempBasalFrame(remaining = 1596, runningFor = 204), live = true)
        assertEquals(now - 204 * second, writes.single().arguments[0])
        assertEquals(77L, writes.single().arguments[5])
    }

    /** Seen on a real pump: after a split the counters start again. The start of the record stays. */
    @Test
    fun aSplitDoesNotMoveTheStart() = runBlocking {
        pumpExpects(running(now - 10 * minute))
        sync.onStatus(tempBasalFrame(remaining = 1200, runningFor = 6), live = true)
        assertEquals(0, writes.size)
    }

    /** After a bolus the pump goes back to the temp basal, so a bolus must not end it. */
    @Test
    fun aBolusDoesNotEndTheTempBasal() = runBlocking {
        pumpExpects(running(now - minute))
        sync.onStatus(AaruyStatusSnapshot(3), live = true)
        assertEquals(0, stops.size)
    }

    @Test
    fun onALiveLinkTheEndIsNow() = runBlocking {
        pumpExpects(running(now - minute))
        sync.onStatus(AaruyStatusSnapshot(1), live = true)
        assertEquals(now, stops.single().arguments[0])
    }

    /** It could have ended an hour ago, and the database takes one end only. */
    @Test
    fun afterAReconnectTheStatusWritesNoEnd() = runBlocking {
        pumpExpects(running(now - minute))
        sync.onStatus(AaruyStatusSnapshot(1), live = false)
        assertEquals(0, stops.size)
    }

    @Test
    fun aStoppedPumpIsAZeroTempBasal() = runBlocking {
        sync.onStatus(AaruyStatusSnapshot(8), live = true)
        assertEquals(PumpSync.TemporaryBasalType.PUMP_SUSPEND, writes.single().arguments[4])
        assertEquals(0, announcements.size)
    }

    @Test
    fun aStopIsWrittenOnce() = runBlocking {
        pumpExpects(running(now - minute, rate = 0.0, type = PumpSync.TemporaryBasalType.PUMP_SUSPEND))
        sync.onStatus(AaruyStatusSnapshot(7), live = true)
        assertEquals(0, writes.size)
    }

    @Test
    fun aResumedPumpEndsTheStop() = runBlocking {
        pumpExpects(running(now - minute, rate = 0.0, type = PumpSync.TemporaryBasalType.PUMP_SUSPEND))
        sync.onStatus(AaruyStatusSnapshot(1), live = true)
        assertEquals(now, stops.single().arguments[0])
    }

    /** Seen on a real pump: a bolus attempt logs 1 minute while the temp basal goes on. This stopped the record in AAPS. */
    @Test
    fun aSplitEntryDoesNotEndTheTempBasal() = runBlocking {
        val start = now - 2 * minute
        databaseHolds(start)
        sync.onHistoryTempBasal(entry(start, 1), null, statusNow = 2)
        sync.onHistoryTempBasal(entry(start, 1), null, statusNow = 3)
        assertEquals(0, stops.size)
        assertEquals(0, writes.size)
    }

    @Test
    fun anEntryEndsATempBasalThePumpEndedDuringALostLink() = runBlocking {
        val start = now - 20 * minute
        databaseHolds(start)
        sync.onHistoryTempBasal(entry(start, 5), null, statusNow = 1)
        assertEquals(start + 5 * minute, stops.single().arguments[0])
    }

    /** An entry counts whole minutes, and the next entry knows the exact time. */
    @Test
    fun theNextEntryGivesTheExactEnd() = runBlocking {
        val start = now - 20 * minute
        databaseHolds(start)
        sync.onHistoryTempBasal(entry(start, 0), start + 22 * second, statusNow = 1)
        assertEquals(start + 22 * second, stops.single().arguments[0])
    }

    /** The clocks differ, so the record can start some seconds after the entry. */
    @Test
    fun aRecordSomeSecondsLaterIsTheSameTempBasal() = runBlocking {
        val start = now - 20 * minute
        databaseHolds(start + 2 * second)
        sync.onHistoryTempBasal(entry(start, 5), null, statusNow = 1)
        assertEquals(0, writes.size)
    }

    @Test
    fun anEntryInsideAFinishedRecordChangesNothing() = runBlocking {
        val start = now - 60 * minute
        databaseHolds(start, duration = 10 * minute)
        sync.onHistoryTempBasal(entry(start + 2 * minute, 3), null, statusNow = 1)
        assertEquals(0, stops.size)
        assertEquals(0, writes.size)
    }

    /** It started and ended while the link was down. */
    @Test
    fun anEntryTheDatabaseDoesNotHaveIsAdded() = runBlocking {
        val start = now - 60 * minute
        sync.onHistoryTempBasal(entry(start, 30), null, statusNow = 1)
        assertEquals(start, writes.single().arguments[0])
        assertEquals(30 * minute, writes.single().arguments[2])
        assertEquals(start, writes.single().arguments[5])
    }

    @Test
    fun aGapInTheLogIsAZeroTempBasal() = runBlocking {
        `when`(preferences.get(AaruyLongKey.PumpLastDeliveryEnd)).thenReturn(now - 60 * minute)
        sync.onHistoryDelivery(now - 40 * minute, now - 30 * minute)
        assertEquals(now - 60 * minute, writes.single().arguments[0])
        assertEquals(20 * minute, writes.single().arguments[2])
        assertEquals(PumpSync.TemporaryBasalType.PUMP_SUSPEND, writes.single().arguments[4])
    }

    /** Whole minutes are lost at each end of an entry. */
    @Test
    fun aShortBreakIsNoGap() = runBlocking {
        `when`(preferences.get(AaruyLongKey.PumpLastDeliveryEnd)).thenReturn(now - 60 * minute)
        sync.onHistoryDelivery(now - 58 * minute, now - 30 * minute)
        assertEquals(0, writes.size)
    }

    @Test
    fun aStopSeenLiveIsNotWrittenAgain() = runBlocking {
        `when`(preferences.get(AaruyLongKey.PumpLastDeliveryEnd)).thenReturn(now - 60 * minute)
        databaseHolds(now - 60 * minute, duration = 20 * minute)
        sync.onHistoryDelivery(now - 40 * minute, now - 30 * minute)
        assertEquals(0, writes.size)
    }

    @Test
    fun theFirstEntryHasNoGapBeforeIt() = runBlocking {
        `when`(preferences.get(AaruyLongKey.PumpLastDeliveryEnd)).thenReturn(0L)
        sync.onHistoryDelivery(now - 40 * minute, now - 30 * minute)
        assertEquals(0, writes.size)
    }
}
