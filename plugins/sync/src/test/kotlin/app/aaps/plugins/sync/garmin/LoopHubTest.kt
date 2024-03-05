package app.aaps.plugins.sync.garmin

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.iob.CobInfo
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.iob.IobTotal
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.main.graph.OverviewData
import app.aaps.database.ValueWrapper
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.HeartRate
import app.aaps.database.entities.OfflineEvent
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.ValueWithUnit
import app.aaps.database.entities.embedments.InsulinConfiguration
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.CancelCurrentOfflineEventIfAnyTransaction
import app.aaps.database.impl.transactions.InsertOrUpdateHeartRateTransaction
import app.aaps.shared.tests.TestBase
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class LoopHubTest: TestBase() {
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var constraints: ConstraintsChecker
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var loop: Loop
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var repo: AppRepository
    @Mock lateinit var userEntryLogger: UserEntryLogger
    @Mock lateinit var sp: SP
    @Mock lateinit var overviewData: OverviewData

    private lateinit var loopHub: LoopHubImpl
    private val clock = Clock.fixed(Instant.ofEpochMilli(10_000), ZoneId.of("UTC"))

    @BeforeEach
    fun setup() {
        loopHub = LoopHubImpl(
            aapsLogger, commandQueue, constraints, iobCobCalculator, loop,
            profileFunction, repo, userEntryLogger, sp, overviewData
        )
        loopHub.clock = clock
    }

    @AfterEach
    fun verifyNoFurtherInteractions() {
        verifyNoMoreInteractions(commandQueue)
        verifyNoMoreInteractions(constraints)
        verifyNoMoreInteractions(iobCobCalculator)
        verifyNoMoreInteractions(loop)
        verifyNoMoreInteractions(profileFunction)
        verifyNoMoreInteractions(repo)
        verifyNoMoreInteractions(userEntryLogger)
        verifyNoMoreInteractions(overviewData)
    }

@Test
    fun testCurrentProfile() {
        val profile = mock(Profile::class.java)
        `when`(profileFunction.getProfile()).thenReturn(profile)
        assertEquals(profile, loopHub.currentProfile)
        verify(profileFunction, times(1)).getProfile()
    }

    @Test
    fun testCurrentProfileName() {
        `when`(profileFunction.getProfileName()).thenReturn("pro")
        assertEquals("pro", loopHub.currentProfileName)
        verify(profileFunction, times(1)).getProfileName()
    }

    @Test
    fun testGlucoseUnit() {
        `when`(sp.getString(app.aaps.core.utils.R.string.key_units, GlucoseUnit.MGDL.asText)).thenReturn("mg/dl")
        assertEquals(GlucoseUnit.MGDL, loopHub.glucoseUnit)
        `when`(sp.getString(app.aaps.core.utils.R.string.key_units, GlucoseUnit.MGDL.asText)).thenReturn("mmol")
        assertEquals(GlucoseUnit.MMOL, loopHub.glucoseUnit)
    }

    @Test
    fun testInsulinOnBoard() {
        val iobTotal = IobTotal(time = 0).apply { iob = 23.9 }
        `when`(iobCobCalculator.calculateIobFromBolus()).thenReturn(iobTotal)
        assertEquals(23.9, loopHub.insulinOnboard, 1e-10)
        verify(iobCobCalculator, times(1)).calculateIobFromBolus()
    }

    @Test
    fun testBasalOnBoard() {
        val iobBasal = IobTotal(time = 0).apply { basaliob = 23.9 }
        `when`(iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(iobBasal)
        assertEquals(23.9, loopHub.insulinBasalOnboard, 1e-10)
        verify(iobCobCalculator, times(1)).calculateIobFromTempBasalsIncludingConvertedExtended()
    }

    @Test
    fun testCarbsOnBoard() {
        val cobInfo = CobInfo(0, 12.0, 0.0)
        `when`(overviewData.cobInfo(iobCobCalculator)).thenReturn(cobInfo)
        assertEquals(12.0, loopHub.carbsOnboard)
        verify(overviewData, times(1)).cobInfo(iobCobCalculator)
    }

    @Test
    fun testIsConnected() {
        `when`(loop.isDisconnected).thenReturn(false)
        assertEquals(true, loopHub.isConnected)
        verify(loop, times(1)).isDisconnected
    }

    private fun effectiveProfileSwitch(duration: Long) = EffectiveProfileSwitch(
        timestamp = 100,
        basalBlocks = emptyList(),
        isfBlocks = emptyList(),
        icBlocks = emptyList(),
        targetBlocks = emptyList(),
        glucoseUnit = EffectiveProfileSwitch.GlucoseUnit.MGDL,
        originalProfileName = "foo",
        originalCustomizedName = "bar",
        originalTimeshift = 0,
        originalPercentage = 100,
        originalDuration = duration,
        originalEnd = 100 + duration,
        insulinConfiguration = InsulinConfiguration(
            "label", 0, 0
        )
    )

    @Test
    fun testIsTemporaryProfileTrue() {
        val eps = effectiveProfileSwitch(10)
        `when`(repo.getEffectiveProfileSwitchActiveAt(clock.millis())).thenReturn(
            Single.just(ValueWrapper.Existing(eps)))
        assertEquals(true, loopHub.isTemporaryProfile)
        verify(repo, times(1)).getEffectiveProfileSwitchActiveAt(clock.millis())
    }

    @Test
    fun testIsTemporaryProfileFalse() {
        val eps = effectiveProfileSwitch(0)
        `when`(repo.getEffectiveProfileSwitchActiveAt(clock.millis())).thenReturn(
            Single.just(ValueWrapper.Existing(eps)))
        assertEquals(false, loopHub.isTemporaryProfile)
        verify(repo).getEffectiveProfileSwitchActiveAt(clock.millis())
    }

    @Test
    fun testTemporaryBasal() {
        val apsResult = mock(APSResult::class.java)
        `when`(apsResult.percent).thenReturn(45)
        val lastRun = Loop.LastRun().apply { constraintsProcessed = apsResult }
        `when`(loop.lastRun).thenReturn(lastRun)
        assertEquals(0.45, loopHub.temporaryBasal, 1e-6)
        verify(loop).lastRun
    }

    @Test
    fun testTemporaryBasalNoRun() {
        `when`(loop.lastRun).thenReturn(null)
        assertTrue(loopHub.temporaryBasal.isNaN())
        verify(loop, times(1)).lastRun
    }

    @Test
    fun testConnectPump() {
        val c = mock(Completable::class.java)
        val dummy = CancelCurrentOfflineEventIfAnyTransaction(0)
        val matcher = {
            argThat<CancelCurrentOfflineEventIfAnyTransaction> { t -> t.timestamp == clock.millis() }}
        `when`(repo.runTransaction(matcher() ?: dummy)).thenReturn(c)
        loopHub.connectPump()
        verify(repo).runTransaction(matcher() ?: dummy)
        verify(commandQueue).cancelTempBasal(true, null)
        verify(userEntryLogger).log(UserEntry.Action.RECONNECT, UserEntry.Sources.GarminDevice)
    }

    @Test
    fun testDisconnectPump() {
        val profile = mock(Profile::class.java)
        `when`(profileFunction.getProfile()).thenReturn(profile)
        loopHub.disconnectPump(23)
        verify(profileFunction).getProfile()
        verify(loop).goToZeroTemp(23, profile, OfflineEvent.Reason.DISCONNECT_PUMP)
        verify(userEntryLogger).log(
            UserEntry.Action.DISCONNECT,
            UserEntry.Sources.GarminDevice,
            ValueWithUnit.Minute(23))
    }

    @Test
    fun testGetGlucoseValues() {
        val glucoseValues = listOf(
            GlucoseValue(
                timestamp = 1_000_000L, raw = 90.0, value = 93.0,
                trendArrow = GlucoseValue.TrendArrow.FLAT, noise = null,
                sourceSensor = GlucoseValue.SourceSensor.DEXCOM_G5_XDRIP))
        `when`(repo.compatGetBgReadingsDataFromTime(1001_000, false))
            .thenReturn(Single.just(glucoseValues))
        assertArrayEquals(
            glucoseValues.toTypedArray(),
            loopHub.getGlucoseValues(Instant.ofEpochMilli(1001_000), false).toTypedArray())
        verify(repo).compatGetBgReadingsDataFromTime(1001_000, false)
    }

    @Test
    fun testPostCarbs() {
        @Suppress("unchecked_cast")
        val constraint = mock(Constraint::class.java) as Constraint<Int>
        `when`(constraint.value()).thenReturn(99)
        `when`(constraints.getMaxCarbsAllowed()).thenReturn(constraint)
        loopHub.postCarbs(100)
        verify(constraints).getMaxCarbsAllowed()
        verify(userEntryLogger).log(
            UserEntry.Action.CARBS,
            UserEntry.Sources.GarminDevice,
            ValueWithUnit.Gram(99))
        verify(commandQueue).bolus(
            argThat { b ->
                b!!.eventType == DetailedBolusInfo.EventType.CARBS_CORRECTION &&
                b.carbs == 99.0 }?: DetailedBolusInfo() ,
            isNull())
    }

    @Test
    fun testStoreHeartRate() {
        val samplingStart = Instant.ofEpochMilli(1_001_000)
        val samplingEnd = Instant.ofEpochMilli(1_101_000)
        val hr = HeartRate(
            timestamp = samplingStart.toEpochMilli(),
            duration = samplingEnd.toEpochMilli() - samplingStart.toEpochMilli(),
            dateCreated = clock.millis(),
            beatsPerMinute = 101.0,
            device = "Test Device")
        `when`(repo.runTransaction(InsertOrUpdateHeartRateTransaction(hr))).thenReturn(
            Completable.fromCallable {
                InsertOrUpdateHeartRateTransaction.TransactionResult(
                    emptyList(), emptyList())})
        loopHub.storeHeartRate(
            samplingStart, samplingEnd, 101, "Test Device")
        verify(repo).runTransaction(InsertOrUpdateHeartRateTransaction(hr))
    }
}
