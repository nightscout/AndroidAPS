package app.aaps.plugins.sync.garmin

import app.aaps.core.data.iob.CobInfo
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.HR
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
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
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
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
    @Mock lateinit var processedTbrEbData: ProcessedTbrEbData

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
            profileFunction, profileUtil, persistenceLayer, userEntryLogger, preferences, processedTbrEbData
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
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
        assertEquals(true, loopHub.isConnected)
        verify(loop, times(1)).runningMode
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
        val profile = mock<Profile>()
        whenever(profileFunction.getProfile()).thenReturn(profile)
        val tb = mock<TB> {
            on { isAbsolute }.thenReturn(false)
            on { rate }.thenReturn(45.0)
        }
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(clock.millis())).thenReturn(tb)
        assertEquals(0.45, loopHub.temporaryBasal, 1e-6)
        verify(profileFunction, times(1)).getProfile()
    }

    @Test
    fun testTemporaryBasalAbsolute() {
        val profile = mock<Profile> {
            onGeneric { getBasal(clock.millis()) }.thenReturn(2.0)
        }
        whenever(profileFunction.getProfile()).thenReturn(profile)
        val tb = mock<TB> {
            on { isAbsolute }.thenReturn(true)
            on { rate }.thenReturn(0.9)
        }
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(clock.millis())).thenReturn(tb)
        assertEquals(0.45, loopHub.temporaryBasal, 1e-6)
        verify(profileFunction, times(1)).getProfile()
    }

    @Test
    fun testTemporaryBasalNoRun() {
        val profile = mock<Profile>()
        whenever(profileFunction.getProfile()).thenReturn(profile)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(clock.millis())).thenReturn(null)
        assertTrue(loopHub.temporaryBasal.isNaN())
        verify(profileFunction, times(1)).getProfile()
    }

    @Test
    fun testConnectPump() {
        whenever(persistenceLayer.cancelCurrentRunningMode(clock.millis(), Action.RECONNECT, Sources.Garmin)).thenReturn(Single.just(PersistenceLayer.TransactionResult()))
        loopHub.connectPump()
        verify(persistenceLayer).cancelCurrentRunningMode(clock.millis(), Action.RECONNECT, Sources.Garmin)
        verify(commandQueue).cancelTempBasal(enforceNew = true, autoForced = false, callback = null)
    }

    @Test
    fun testDisconnectPump() {
        val profile = mock<Profile>()
        whenever(profileFunction.getProfile()).thenReturn(profile)
        loopHub.disconnectPump(23)
        verify(profileFunction).getProfile()
        verify(loop).handleRunningModeChange(
            durationInMinutes = 23, profile = profile, newRM = RM.Mode.DISCONNECTED_PUMP, action = Action.DISCONNECT,
            source = Sources.Garmin, listValues = listOf(ValueWithUnit.Minute(23))
        )
    }

    @Test
    fun testGetGlucoseValues() {
        val glucoseValues = listOf(
            GV(
                timestamp = 1_000_000L, raw = 90.0, value = 93.0,
                trendArrow = TrendArrow.FLAT, noise = null,
                sourceSensor = SourceSensor.DEXCOM_G6_NATIVE_XDRIP
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
            device = "Test Device"
        )
        whenever(persistenceLayer.insertOrUpdateHeartRate(hr)).thenReturn(
            Single.just(PersistenceLayer.TransactionResult())
        )
        loopHub.storeHeartRate(
            samplingStart, samplingEnd, 101, "Test Device"
        )
        verify(persistenceLayer).insertOrUpdateHeartRate(hr)
    }
}
