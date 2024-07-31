package app.aaps.plugins.sync.garmin

import app.aaps.core.data.iob.CobInfo
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.HR
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.OE
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.shared.tests.TestBase
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class LoopHubTest : TestBase() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var constraints: ConstraintsChecker
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var loop: Loop
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var profileUtil: ProfileUtil
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var userEntryLogger: UserEntryLogger
    @Mock lateinit var preferences: Preferences

    private lateinit var loopHub: LoopHubImpl
    private val clock = Clock.fixed(Instant.ofEpochMilli(10_000), ZoneId.of("UTC"))

    @BeforeEach
    fun setup() {
        whenever(profileUtil.convertToMgdl(any(), any())).thenAnswer { i ->
            val v: Double = i.getArgument(0)
            val u: GlucoseUnit = i.getArgument(1)
            if (u == GlucoseUnit.MGDL) v else (18.0 * v)
        }
        loopHub = LoopHubImpl(
            aapsLogger, commandQueue, constraints, iobCobCalculator, loop,
            profileFunction, profileUtil, persistenceLayer, userEntryLogger, preferences
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
        verifyNoMoreInteractions(persistenceLayer)
        verifyNoMoreInteractions(userEntryLogger)
    }

@Test
    fun testCurrentProfile() {
        val profile = mock<Profile>()
        whenever(profileFunction.getProfile()).thenReturn(profile)
        assertEquals(profile, loopHub.currentProfile)
        verify(profileFunction, times(1)).getProfile()
    }

    @Test
    fun testCurrentProfileName() {
        whenever(profileFunction.getProfileName()).thenReturn("pro")
        assertEquals("pro", loopHub.currentProfileName)
        verify(profileFunction, times(1)).getProfileName()
    }

    @Test
    fun testGlucoseRangeMgDl() {
        whenever(preferences.get(StringKey.GeneralUnits)).thenReturn("mg/dl")
        whenever(preferences.get(UnitDoubleKey.OverviewLowMark)).thenReturn(76.0)
        whenever(preferences.get(UnitDoubleKey.OverviewHighMark)).thenReturn(125.0)
        assertEquals(76.0, loopHub.lowGlucoseMark, 1e-6)
        assertEquals(125.0, loopHub.highGlucoseMark, 1e-6)
        verify(preferences, atLeast(1)).get(StringKey.GeneralUnits)
        verify(preferences).get(UnitDoubleKey.OverviewLowMark)
        verify(preferences).get(UnitDoubleKey.OverviewHighMark)
    }

    @Test
    fun testGlucoseRangeMmolL() {
        whenever(preferences.get(StringKey.GeneralUnits)).thenReturn("mmol")
        whenever(preferences.get(UnitDoubleKey.OverviewLowMark)).thenReturn(3.0)
        whenever(preferences.get(UnitDoubleKey.OverviewHighMark)).thenReturn(8.0)
        assertEquals(54.0, loopHub.lowGlucoseMark, 1e-6)
        assertEquals(144.0, loopHub.highGlucoseMark, 1e-6)
        verify(preferences, atLeast(1)).get(StringKey.GeneralUnits)
        verify(preferences).get(UnitDoubleKey.OverviewLowMark)
        verify(preferences).get(UnitDoubleKey.OverviewHighMark)
    }

    @Test
    fun testGlucoseUnit() {
        whenever(preferences.get(StringKey.GeneralUnits)).thenReturn("mg/dl")
        assertEquals(GlucoseUnit.MGDL, loopHub.glucoseUnit)
        whenever(preferences.get(StringKey.GeneralUnits)).thenReturn("mmol")
        assertEquals(GlucoseUnit.MMOL, loopHub.glucoseUnit)
    }

    @Test
    fun testInsulinOnBoard() {
        val iobTotal = IobTotal(time = 0).apply { iob = 23.9 }
        whenever(iobCobCalculator.calculateIobFromBolus()).thenReturn(iobTotal)
        assertEquals(23.9, loopHub.insulinOnboard, 1e-10)
        verify(iobCobCalculator, times(1)).calculateIobFromBolus()
    }

    @Test
    fun testBasalOnBoard() {
        val iobBasal = IobTotal(time = 0).apply { basaliob = 23.9 }
        whenever(iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(iobBasal)
        assertEquals(23.9, loopHub.insulinBasalOnboard, 1e-10)
        verify(iobCobCalculator, times(1)).calculateIobFromTempBasalsIncludingConvertedExtended()
    }

    @Test
    fun testCarbsOnBoard() {
        val cobInfo = CobInfo(0, 12.0, 0.0)
        whenever(iobCobCalculator.getCobInfo(anyString())).thenReturn(cobInfo)
        assertEquals(12.0, loopHub.carbsOnboard)
        verify(iobCobCalculator, times(1)).getCobInfo(anyString())
    }

    @Test
    fun testIsConnected() {
        whenever(loop.isDisconnected).thenReturn(false)
        assertEquals(true, loopHub.isConnected)
        verify(loop, times(1)).isDisconnected
    }

    private fun effectiveProfileSwitch(duration: Long) = EPS(
        timestamp = 100,
        basalBlocks = emptyList(),
        isfBlocks = emptyList(),
        icBlocks = emptyList(),
        targetBlocks = emptyList(),
        glucoseUnit = GlucoseUnit.MGDL,
        originalProfileName = "foo",
        originalCustomizedName = "bar",
        originalTimeshift = 0,
        originalPercentage = 100,
        originalDuration = duration,
        originalEnd = 100 + duration,
        iCfg = ICfg("label", 0, 0)
    )

    @Test
    fun testIsTemporaryProfileTrue() {
        val eps = effectiveProfileSwitch(10)
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(clock.millis())).thenReturn(eps)
        assertEquals(true, loopHub.isTemporaryProfile)
        verify(persistenceLayer, times(1)).getEffectiveProfileSwitchActiveAt(clock.millis())
    }

    @Test
    fun testIsTemporaryProfileFalse() {
        val eps = effectiveProfileSwitch(0)
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(clock.millis())).thenReturn(eps)
        assertEquals(false, loopHub.isTemporaryProfile)
        verify(persistenceLayer).getEffectiveProfileSwitchActiveAt(clock.millis())
    }

    @Test
    fun testTemporaryBasal() {
        val apsResult = mock<APSResult>()
        whenever(apsResult.percent).thenReturn(45)
        val lastRun = Loop.LastRun().apply { constraintsProcessed = apsResult }
        whenever(loop.lastRun).thenReturn(lastRun)
        assertEquals(0.45, loopHub.temporaryBasal, 1e-6)
        verify(loop).lastRun
    }

    @Test
    fun testTemporaryBasalNoRun() {
        whenever(loop.lastRun).thenReturn(null)
        assertTrue(loopHub.temporaryBasal.isNaN())
        verify(loop, times(1)).lastRun
    }

    @Test
    fun testConnectPump() {
        whenever(persistenceLayer.cancelCurrentOfflineEvent(clock.millis(), Action.RECONNECT, Sources.Garmin)).thenReturn(Single.just(PersistenceLayer.TransactionResult()))
        loopHub.connectPump()
        verify(persistenceLayer).cancelCurrentOfflineEvent(clock.millis(), Action.RECONNECT, Sources.Garmin)
        verify(commandQueue).cancelTempBasal(true, null)
    }

    @Test
    fun testDisconnectPump() {
        val profile = mock<Profile>()
        whenever(profileFunction.getProfile()).thenReturn(profile)
        loopHub.disconnectPump(23)
        verify(profileFunction).getProfile()
        verify(loop).goToZeroTemp(
            23, profile, OE.Reason.DISCONNECT_PUMP, Action.DISCONNECT,
            Sources.Garmin,
            listOf(ValueWithUnit.Minute(23))
        )
    }

    @Test
    fun testGetGlucoseValues() {
        val glucoseValues = listOf(
            GV(
                timestamp = 1_000_000L, raw = 90.0, value = 93.0,
                trendArrow = TrendArrow.FLAT, noise = null,
                sourceSensor = SourceSensor.DEXCOM_G5_XDRIP
            )
        )
        whenever(persistenceLayer.getBgReadingsDataFromTime(1001_000, false))
            .thenReturn(Single.just(glucoseValues))
        assertArrayEquals(
            glucoseValues.toTypedArray(),
            loopHub.getGlucoseValues(Instant.ofEpochMilli(1001_000), false).toTypedArray()
        )
        verify(persistenceLayer).getBgReadingsDataFromTime(1001_000, false)
    }

    @Test
    fun testPostCarbs() {
        val constraint = mock<Constraint<Int>> {
            onGeneric { value() }.thenReturn(99)
        }
        whenever(constraints.getMaxCarbsAllowed()).thenReturn(constraint)
        loopHub.postCarbs(100)
        verify(constraints).getMaxCarbsAllowed()
        verify(userEntryLogger).log(
            Action.CARBS,
            Sources.Garmin,
            null,
            listOf(ValueWithUnit.Gram(99))
        )
        verify(commandQueue).bolus(
            argThat { b ->
                b!!.eventType == TE.Type.CARBS_CORRECTION &&
                    b.carbs == 99.0
            } ?: DetailedBolusInfo(),
            isNull()
        )
    }

    @Test
    fun testStoreHeartRate() {
        val samplingStart = Instant.ofEpochMilli(1_001_000)
        val samplingEnd = Instant.ofEpochMilli(1_101_000)
        val hr = HR(
            timestamp = samplingStart.toEpochMilli(),
            duration = samplingEnd.toEpochMilli() - samplingStart.toEpochMilli(),
            dateCreated = clock.millis(),
            beatsPerMinute = 101.0,
            device = "Test Device")
        whenever(persistenceLayer.insertOrUpdateHeartRate(hr)).thenReturn(
            Single.just(PersistenceLayer.TransactionResult())
        )
        loopHub.storeHeartRate(
            samplingStart, samplingEnd, 101, "Test Device"
        )
        verify(persistenceLayer).insertOrUpdateHeartRate(hr)
    }
}
