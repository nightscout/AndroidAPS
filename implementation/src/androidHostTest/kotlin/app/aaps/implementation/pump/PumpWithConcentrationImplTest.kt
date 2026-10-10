package app.aaps.implementation.pump

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.stream.Stream

class PumpWithConcentrationImplTest : TestBase() {

    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var constraintsChecker: ConstraintsChecker
    @Mock lateinit var pump: Pump
    @Mock lateinit var pumpEnactResult: PumpEnactResult
    @Mock lateinit var effectiveProfile: EffectiveProfile
    @Mock lateinit var pumpProfile: PumpProfile

    private lateinit var sut: PumpWithConcentrationImpl

    @BeforeEach
    fun setup() {
        whenever(activePlugin.activePumpInternal).thenReturn(pump)
        // The concentration boundary floors the converted cU to the pump's native step, so the driver needs a
        // pumpDescription. The pumpDescription-scaling tests below re-stub this with their own description.
        whenever(pump.pumpDescription).thenReturn(PumpDescription().fillFor(PumpType.DANA_RS))
        // Feature-2 last-resort guard queries the overall max; default to no effective cap.
        whenever(constraintsChecker.getMaxBolusAllowed()).thenReturn(ConstraintObject(Double.MAX_VALUE, aapsLogger))
        whenever(constraintsChecker.getMaxExtendedBolusAllowed()).thenReturn(ConstraintObject(Double.MAX_VALUE, aapsLogger))
        sut = PumpWithConcentrationImpl(aapsLogger, activePlugin, profileFunction, constraintsChecker, { pumpEnactResult })
    }

    private suspend fun setupConcentration(concentration: Double) {
        whenever(profileFunction.runningICfg).thenReturn(MutableStateFlow(ICfg("Test", 0L, 0L, concentration)))
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
    }

    private fun setupU100() {
        whenever(profileFunction.runningICfg).thenReturn(MutableStateFlow(ICfg("Test", 0L, 0L, 1.0)))
    }

    // Real pump descriptions with mocked drivers: these verify the concentration boundary, not physical delivery.
    companion object {
        @JvmStatic
        fun pumpConcentrations(): Stream<Arguments> =
            listOf(PumpType.DANA_RS, PumpType.OMNIPOD_DASH, PumpType.ACCU_CHEK_INSIGHT).flatMap { type ->
                listOf(0.05, 0.1, 0.2, 0.25, 0.4, 0.5, 1.0, 2.0).map { Arguments.of(type, it) }
            }.stream()
    }

    private suspend fun setupPumpConcentration(type: PumpType, factor: Double): PumpDescription {
        setupConcentration(factor)
        return PumpDescription().fillFor(type).also { whenever(pump.pumpDescription).thenReturn(it) }
    }

    @ParameterizedTest
    @MethodSource("pumpConcentrations")
    fun `bolus is converted once and partial delivery is reported in IU`(type: PumpType, factor: Double) = runBlocking<Unit> {
        setupPumpConcentration(type, factor)
        val dbi = DetailedBolusInfo().apply { insulin = 2.0 * factor; bolusType = BS.Type.NORMAL }
        whenever(pump.deliverTreatment(any())).thenReturn(driverDelivered(1.25))

        val result = sut.deliverTreatment(dbi)

        verify(pump).deliverTreatment(argThat { kotlin.math.abs(insulin - 2.0) < 1e-9 })
        assertThat(result.bolusDelivered).isWithin(1e-9).of(1.25 * factor)
    }

    @ParameterizedTest
    @MethodSource("pumpConcentrations")
    fun `SMB floors to the native bolus step without rounding up`(type: PumpType, factor: Double) = runBlocking<Unit> {
        setupPumpConcentration(type, factor)
        // 0.077 cU -> 0.07 on Insight (0.01 step), 0.05 on Dana/DASH (0.05 step).
        val expected = if (type == PumpType.ACCU_CHEK_INSIGHT) 0.07 else 0.05
        val dbi = DetailedBolusInfo().apply { insulin = 0.077 * factor; bolusType = BS.Type.SMB }
        whenever(pump.deliverTreatment(any())).thenReturn(driverDelivered(expected))

        val result = sut.deliverTreatment(dbi)

        verify(pump).deliverTreatment(argThat { kotlin.math.abs(insulin - expected) < 1e-9 })
        assertThat(result.bolusDelivered).isWithin(1e-9).of(expected * factor)
    }

    @ParameterizedTest
    @MethodSource("pumpConcentrations")
    fun `sub step SMB never reaches the pump`(type: PumpType, factor: Double) = runBlocking<Unit> {
        val description = setupPumpConcentration(type, factor)
        val dbi = DetailedBolusInfo().apply { insulin = description.bolusStep * factor / 2; bolusType = BS.Type.SMB }
        whenever(pumpEnactResult.success(any())).thenReturn(pumpEnactResult)
        whenever(pumpEnactResult.enacted(any())).thenReturn(pumpEnactResult)
        whenever(pumpEnactResult.bolusDelivered(any())).thenReturn(pumpEnactResult)

        sut.deliverTreatment(dbi)

        verify(pump, never()).deliverTreatment(any())
        verify(pumpEnactResult).success(true)
        verify(pumpEnactResult).enacted(false)
        verify(pumpEnactResult).bolusDelivered(0.0)
    }

    @ParameterizedTest
    @MethodSource("pumpConcentrations")
    fun `bolus safety cap is applied in IU before pump conversion`(type: PumpType, factor: Double) = runBlocking<Unit> {
        setupPumpConcentration(type, factor)
        whenever(constraintsChecker.getMaxBolusAllowed()).thenReturn(ConstraintObject(1.5 * factor, aapsLogger))
        val dbi = DetailedBolusInfo().apply { insulin = 2.0 * factor; bolusType = BS.Type.NORMAL }
        whenever(pump.deliverTreatment(any())).thenReturn(driverDelivered(1.5))

        assertThat(sut.deliverTreatment(dbi).bolusDelivered).isWithin(1e-9).of(1.5 * factor)
        verify(pump).deliverTreatment(argThat { kotlin.math.abs(insulin - 1.5) < 1e-9 })
    }

    @ParameterizedTest
    @MethodSource("pumpConcentrations")
    fun `priming remains in pump units in both directions`(type: PumpType, factor: Double) = runBlocking<Unit> {
        setupPumpConcentration(type, factor)
        val dbi = DetailedBolusInfo().apply { insulin = 2.0; bolusType = BS.Type.PRIMING }
        whenever(pump.deliverTreatment(any())).thenReturn(driverDelivered(2.0))

        assertThat(sut.deliverTreatment(dbi).bolusDelivered).isEqualTo(2.0)
        verify(pump).deliverTreatment(argThat { insulin == 2.0 })
    }

    @ParameterizedTest
    @MethodSource("pumpConcentrations")
    fun `extended bolus floors outgoing dose and converts partial delivery back to IU`(type: PumpType, factor: Double) = runBlocking<Unit> {
        setupPumpConcentration(type, factor)
        val expected = if (type == PumpType.ACCU_CHEK_INSIGHT) 2.07 else 2.05
        whenever(pump.setExtendedBolus(any(), any())).thenReturn(driverDelivered(1.25))

        assertThat(sut.setExtendedBolus(2.077 * factor, 60).bolusDelivered).isWithin(1e-9).of(1.25 * factor)
        verify(pump).setExtendedBolus(argThat { kotlin.math.abs(this - expected) < 1e-9 }, eq(60))
    }

    @ParameterizedTest
    @MethodSource("pumpConcentrations")
    fun `effective steps and limits scale without mutating the driver description`(type: PumpType, factor: Double) = runBlocking<Unit> {
        val original = setupPumpConcentration(type, factor)
        val bolusStep = original.bolusStep
        val basalStep = original.basalStep
        val result = sut.pumpDescription

        assertThat(result.bolusStep).isWithin(1e-9).of(bolusStep * factor)
        assertThat(result.extendedBolusStep).isWithin(1e-9).of(original.extendedBolusStep * factor)
        assertThat(result.extendedBolusMinAmount).isWithin(1e-9).of(original.extendedBolusMinAmount * factor)
        assertThat(result.tempAbsoluteStep).isWithin(1e-9).of(original.tempAbsoluteStep * factor)
        assertThat(result.maxTempAbsolute).isWithin(1e-9).of(original.maxTempAbsolute * factor)
        assertThat(result.basalStep).isWithin(1e-9).of(basalStep * factor)
        assertThat(result.basalMinimumRate).isWithin(1e-9).of(original.basalMinimumRate * factor)
        assertThat(result.basalMaximumRate).isWithin(1e-9).of(original.basalMaximumRate * factor)
        assertThat(result.maxReservoirReading).isEqualTo((original.maxReservoirReading * factor).toInt())
        assertThat(original.bolusStep).isEqualTo(bolusStep)
        assertThat(original.basalStep).isEqualTo(basalStep)
    }

    @ParameterizedTest
    @CsvSource("0.05", "0.1", "0.2", "0.25", "0.4", "0.5", "1.0", "2.0")
    fun `DASH absolute temp basal converts IU and floors to native step`(factor: Double) = runBlocking<Unit> {
        setupPumpConcentration(PumpType.OMNIPOD_DASH, factor)
        whenever(constraintsChecker.applyBasalConstraints(any(), eq(effectiveProfile))).thenReturn(ConstraintObject(0.077 * factor, aapsLogger))
        whenever(pump.setTempBasalAbsolute(any(), any(), any(), any())).thenReturn(pumpEnactResult)

        sut.setTempBasalAbsolute(0.077 * factor, 30, false, PumpSync.TemporaryBasalType.NORMAL)

        verify(pump).setTempBasalAbsolute(eq(0.05), eq(30), eq(false), eq(PumpSync.TemporaryBasalType.NORMAL))
    }

    @ParameterizedTest
    @MethodSource("pumpConcentrations")
    fun `percentage temp basal is independent of concentration`(type: PumpType, factor: Double) = runBlocking<Unit> {
        setupPumpConcentration(type, factor)
        whenever(constraintsChecker.applyBasalPercentConstraints(any(), eq(effectiveProfile))).thenReturn(ConstraintObject(70, aapsLogger))
        whenever(pump.setTempBasalPercent(any(), any(), any(), any())).thenReturn(pumpEnactResult)

        sut.setTempBasalPercent(70, 30, false, PumpSync.TemporaryBasalType.NORMAL)

        verify(pump).setTempBasalPercent(eq(70), eq(30), eq(false), eq(PumpSync.TemporaryBasalType.NORMAL))
    }

    // Nothing in force (pre-first-profile-switch): the identity, so the driver receives exactly the requested
    // amount rather than one scaled by an insulin the user never chose.
    @Test
    fun `deliverTreatment with no insulin in force passes the amount through unscaled`() = runBlocking<Unit> {
        whenever(profileFunction.runningICfg).thenReturn(MutableStateFlow(null))
        val dbi = DetailedBolusInfo().apply { insulin = 5.0; bolusType = BS.Type.NORMAL }
        whenever(pump.deliverTreatment(any())).thenReturn(pumpEnactResult)

        sut.deliverTreatment(dbi)

        verify(pump).deliverTreatment(argThat { insulin == 5.0 })
    }

    // --- deliverTreatment tests ---

    @Test
    fun `deliverTreatment with U100 passes insulin unchanged`() = runBlocking<Unit> {
        setupU100()
        val dbi = DetailedBolusInfo().apply { insulin = 5.0 }
        whenever(pump.deliverTreatment(any())).thenReturn(pumpEnactResult)

        sut.deliverTreatment(dbi)

        verify(pump).deliverTreatment(argThat { insulin == 5.0 })
    }

    @Test
    fun `deliverTreatment with U200 halves insulin for normal bolus`() = runBlocking<Unit> {
        setupConcentration(2.0)
        val dbi = DetailedBolusInfo().apply { insulin = 6.0; bolusType = BS.Type.NORMAL }
        whenever(pump.deliverTreatment(any())).thenReturn(pumpEnactResult)

        sut.deliverTreatment(dbi)

        verify(pump).deliverTreatment(argThat { insulin == 3.0 })
    }

    @Test
    fun `deliverTreatment with U200 does not modify priming bolus`() = runBlocking<Unit> {
        setupConcentration(2.0)
        val dbi = DetailedBolusInfo().apply { insulin = 4.0; bolusType = BS.Type.PRIMING }
        whenever(pump.deliverTreatment(any())).thenReturn(pumpEnactResult)

        sut.deliverTreatment(dbi)

        verify(pump).deliverTreatment(argThat { insulin == 4.0 })
    }

    @Test
    fun `deliverTreatment with U50 doubles insulin for normal bolus`() = runBlocking<Unit> {
        setupConcentration(0.5)
        val dbi = DetailedBolusInfo().apply { insulin = 2.0; bolusType = BS.Type.NORMAL }
        whenever(pump.deliverTreatment(any())).thenReturn(pumpEnactResult)

        sut.deliverTreatment(dbi)

        verify(pump).deliverTreatment(argThat { insulin == 4.0 })
    }

    @Test
    fun `deliverTreatment with U200 skips pump when bolus floors to zero`() = runBlocking<Unit> {
        setupConcentration(2.0)
        // 0.05 IU / 2.0 = 0.025 cU -> below DANA_RS bolus step -> floors to 0.0 cU
        val dbi = DetailedBolusInfo().apply { insulin = 0.05; bolusType = BS.Type.SMB }
        whenever(pumpEnactResult.success(any())).thenReturn(pumpEnactResult)
        whenever(pumpEnactResult.enacted(any())).thenReturn(pumpEnactResult)
        whenever(pumpEnactResult.bolusDelivered(any())).thenReturn(pumpEnactResult)

        val result = sut.deliverTreatment(dbi)

        verify(pump, never()).deliverTreatment(any())
        assertThat(result).isSameInstanceAs(pumpEnactResult)
        verify(pumpEnactResult).success(true)
        verify(pumpEnactResult).enacted(false)
        verify(pumpEnactResult).bolusDelivered(0.0)
    }

    @Test
    fun `deliverTreatment with zero priming bolus still calls pump`() = runBlocking<Unit> {
        setupConcentration(2.0)
        val dbi = DetailedBolusInfo().apply { insulin = 0.0; bolusType = BS.Type.PRIMING }
        whenever(pump.deliverTreatment(any())).thenReturn(pumpEnactResult)

        sut.deliverTreatment(dbi)

        verify(pump).deliverTreatment(any())
    }

    // --- bolusDelivered comes back in IU (#5191) ---

    /** What a driver answers: the amount in its own units, the units it was handed. */
    private fun driverDelivered(cU: Double): PumpEnactResult =
        PumpEnactResultObject(mock<TextResolver>()).success(true).enacted(true).bolusDelivered(cU)

    @Test
    fun `deliverTreatment converts bolusDelivered back to IU for U200`() = runBlocking<Unit> {
        setupConcentration(2.0)
        val dbi = DetailedBolusInfo().apply { insulin = 1.0; bolusType = BS.Type.NORMAL }
        whenever(pump.deliverTreatment(any())).thenReturn(driverDelivered(0.5))

        // The SMS reply and Nightscout read this. Half of it was what a caregiver used to see.
        assertThat(sut.deliverTreatment(dbi).bolusDelivered).isEqualTo(1.0)
    }

    @Test
    fun `deliverTreatment converts bolusDelivered back to IU for U50`() = runBlocking<Unit> {
        setupConcentration(0.5)
        val dbi = DetailedBolusInfo().apply { insulin = 1.0; bolusType = BS.Type.SMB }
        whenever(pump.deliverTreatment(any())).thenReturn(driverDelivered(2.0))

        assertThat(sut.deliverTreatment(dbi).bolusDelivered).isEqualTo(1.0)
    }

    @Test
    fun `deliverTreatment reports a partly delivered bolus in IU`() = runBlocking<Unit> {
        // Stopped by the user: 3.0 cU asked, 1.2 cU given.
        setupConcentration(2.0)
        val dbi = DetailedBolusInfo().apply { insulin = 6.0; bolusType = BS.Type.NORMAL }
        whenever(pump.deliverTreatment(any())).thenReturn(driverDelivered(1.2))

        assertThat(sut.deliverTreatment(dbi).bolusDelivered).isWithin(1e-9).of(2.4)
    }

    @Test
    fun `deliverTreatment leaves bolusDelivered unchanged for U100`() = runBlocking<Unit> {
        setupU100()
        val dbi = DetailedBolusInfo().apply { insulin = 1.5; bolusType = BS.Type.NORMAL }
        whenever(pump.deliverTreatment(any())).thenReturn(driverDelivered(1.5))

        assertThat(sut.deliverTreatment(dbi).bolusDelivered).isEqualTo(1.5)
    }

    @Test
    fun `deliverTreatment leaves bolusDelivered of a priming bolus unchanged`() = runBlocking<Unit> {
        // Priming goes in without conversion, so it must come back without one too.
        setupConcentration(2.0)
        val dbi = DetailedBolusInfo().apply { insulin = 4.0; bolusType = BS.Type.PRIMING }
        whenever(pump.deliverTreatment(any())).thenReturn(driverDelivered(4.0))

        assertThat(sut.deliverTreatment(dbi).bolusDelivered).isEqualTo(4.0)
    }

    @Test
    fun `setExtendedBolus converts bolusDelivered back to IU for U200`() = runBlocking<Unit> {
        setupConcentration(2.0)
        whenever(pump.setExtendedBolus(any(), any())).thenReturn(driverDelivered(2.0))

        assertThat(sut.setExtendedBolus(4.0, 60).bolusDelivered).isEqualTo(4.0)
    }

    // --- setTempBasalAbsolute tests ---

    @Test
    fun `setTempBasalAbsolute with U200 halves rate sent to pump`() = runBlocking<Unit> {
        setupConcentration(2.0)
        val constraintResult: Constraint<Double> = mock()
        whenever(constraintResult.value()).thenReturn(4.0)
        whenever(constraintsChecker.applyBasalConstraints(any(), eq(effectiveProfile))).thenReturn(constraintResult)
        whenever(pump.setTempBasalAbsolute(any(), any(), any(), any())).thenReturn(pumpEnactResult)

        sut.setTempBasalAbsolute(4.0, 30, false, PumpSync.TemporaryBasalType.NORMAL)

        // 4.0 / 2.0 = 2.0 sent to the actual pump
        verify(pump).setTempBasalAbsolute(eq(2.0), eq(30), eq(false), eq(PumpSync.TemporaryBasalType.NORMAL))
    }

    @Test
    fun `setTempBasalAbsolute with U100 passes rate unchanged`() = runBlocking<Unit> {
        setupU100()
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        val constraintResult: Constraint<Double> = mock()
        whenever(constraintResult.value()).thenReturn(1.5)
        whenever(constraintsChecker.applyBasalConstraints(any(), eq(effectiveProfile))).thenReturn(constraintResult)
        whenever(pump.setTempBasalAbsolute(any(), any(), any(), any())).thenReturn(pumpEnactResult)

        sut.setTempBasalAbsolute(1.5, 60, true, PumpSync.TemporaryBasalType.NORMAL)

        verify(pump).setTempBasalAbsolute(eq(1.5), eq(60), eq(true), eq(PumpSync.TemporaryBasalType.NORMAL))
    }

    // --- setExtendedBolus tests ---

    @Test
    fun `setExtendedBolus with U200 halves insulin`() = runBlocking<Unit> {
        setupConcentration(2.0)
        whenever(pump.setExtendedBolus(any(), any())).thenReturn(pumpEnactResult)

        sut.setExtendedBolus(4.0, 60)

        verify(pump).setExtendedBolus(eq(2.0), eq(60))
    }

    @Test
    fun `setExtendedBolus with U100 passes insulin unchanged`() = runBlocking<Unit> {
        setupU100()
        whenever(pump.setExtendedBolus(any(), any())).thenReturn(pumpEnactResult)

        sut.setExtendedBolus(4.0, 60)

        verify(pump).setExtendedBolus(eq(4.0), eq(60))
    }

    @Test
    fun `setExtendedBolus with U50 doubles insulin`() = runBlocking<Unit> {
        setupConcentration(0.5)
        whenever(pump.setExtendedBolus(any(), any())).thenReturn(pumpEnactResult)

        sut.setExtendedBolus(2.0, 30)

        // 2.0 / 0.5 = 4.0
        verify(pump).setExtendedBolus(eq(4.0), eq(30))
    }

    // --- pumpDescription tests ---

    @Test
    fun `pumpDescription scales values for U200`() = runBlocking<Unit> {
        setupConcentration(2.0)
        val desc = PumpDescription().apply {
            bolusStep = 0.1
            extendedBolusStep = 0.1
            maxTempAbsolute = 10.0
            tempAbsoluteStep = 0.05
            basalStep = 0.01
            basalMinimumRate = 0.05
            basalMaximumRate = 5.0
            maxReservoirReading = 300
        }
        whenever(pump.pumpDescription).thenReturn(desc)

        val result = sut.pumpDescription

        assertThat(result.bolusStep).isEqualTo(0.2)
        assertThat(result.extendedBolusStep).isEqualTo(0.2)
        assertThat(result.maxTempAbsolute).isEqualTo(20.0)
        assertThat(result.tempAbsoluteStep).isEqualTo(0.1)
        assertThat(result.basalStep).isEqualTo(0.02)
        assertThat(result.basalMinimumRate).isEqualTo(0.1)
        assertThat(result.basalMaximumRate).isEqualTo(10.0)
        assertThat(result.maxReservoirReading).isEqualTo(600)
    }

    @Test
    fun `pumpDescription returns original for U100`() {
        setupU100()
        val desc = PumpDescription().apply {
            bolusStep = 0.1
            basalStep = 0.01
        }
        whenever(pump.pumpDescription).thenReturn(desc)

        val result = sut.pumpDescription

        assertThat(result.bolusStep).isEqualTo(0.1)
        assertThat(result.basalStep).isEqualTo(0.01)
        // Should be the same object, not a clone
        assertThat(result).isSameInstanceAs(desc)
    }

    @Test
    fun `pumpDescription scales for U50`() = runBlocking<Unit> {
        setupConcentration(0.5)
        val desc = PumpDescription().apply {
            bolusStep = 0.1
            basalStep = 0.01
            basalMinimumRate = 0.05
            basalMaximumRate = 5.0
            maxReservoirReading = 300
        }
        whenever(pump.pumpDescription).thenReturn(desc)

        val result = sut.pumpDescription

        assertThat(result.bolusStep).isEqualTo(0.05)
        assertThat(result.basalStep).isEqualTo(0.005)
        assertThat(result.basalMinimumRate).isEqualTo(0.025)
        assertThat(result.basalMaximumRate).isEqualTo(2.5)
        assertThat(result.maxReservoirReading).isEqualTo(150)
    }

    // --- setNewBasalProfile tests ---

    @Test
    fun `setNewBasalProfile converts EffectiveProfile to PumpProfile`() = runBlocking<Unit> {
        whenever(effectiveProfile.toPump()).thenReturn(pumpProfile)
        whenever(pump.setNewBasalProfile(pumpProfile)).thenReturn(pumpEnactResult)

        sut.setNewBasalProfile(effectiveProfile)

        verify(effectiveProfile).toPump()
        verify(pump).setNewBasalProfile(pumpProfile)
    }

    // --- isThisProfileSet tests ---

    @Test
    fun `isThisProfileSet converts EffectiveProfile to PumpProfile`() {
        whenever(effectiveProfile.toPump()).thenReturn(pumpProfile)
        whenever(pump.isThisProfileSet(pumpProfile)).thenReturn(true)

        val result = sut.isThisProfileSet(effectiveProfile)

        assertThat(result).isTrue()
        verify(effectiveProfile).toPump()
        verify(pump).isThisProfileSet(pumpProfile)
    }

    // --- selectedActivePump test ---

    @Test
    fun `selectedActivePump returns internal pump`() {
        assertThat(sut.selectedActivePump()).isSameInstanceAs(pump)
    }

    // --- no profile running ---

    @Test
    fun `setTempBasalAbsolute throws when no profile running`() = runBlocking<Unit> {
        setupConcentration(2.0)
        whenever(profileFunction.getProfile()).thenReturn(null)

        try {
            sut.setTempBasalAbsolute(1.0, 30, false, PumpSync.TemporaryBasalType.NORMAL)
            assertThat(false).isTrue() // should not reach here
        } catch (e: IllegalStateException) {
            assertThat(e.message).isEqualTo("No profile running")
        }
    }

    // --- a driver that failed to start ---

    /**
     * A driver whose own `isInitialized()` is delegated to [pump], so it keeps answering what the hardware
     * state says while [PluginBase.lastStartFailed] is set. That is the real shape: `DanaRSPlugin` reads
     * `danaPump.lastConnection`, `OmnipodDashPumpPlugin` reads `podStateManager.isPodRunning`, and neither
     * is reset by `onStop`.
     */
    private class FailingPumpPlugin(
        aapsLogger: AAPSLogger,
        rh: TextResolver,
        notificationManager: NotificationManager,
        delegate: Pump
    ) : PluginBase(PluginDescription().mainType(PluginType.PUMP), aapsLogger, rh, notificationManager), Pump by delegate {

        override suspend fun onStart() {
            throw IllegalStateException("driver did not come up")
        }
    }

    @Test
    fun `a pump driver whose start failed is reported as not initialized`() = runBlocking {
        val failing = FailingPumpPlugin(aapsLogger, mock<TextResolver>(), mock<NotificationManager>(), pump)
        whenever(pump.isInitialized()).thenReturn(true)
        whenever(activePlugin.activePumpInternal).thenReturn(failing)

        // Same driver, same device state, before and after: only the failed start differs.
        assertThat(sut.isInitialized()).isTrue()

        failing.setPluginEnabledAwaiting(PluginType.PUMP, true)

        assertThat(failing.isInitialized()).isTrue()   // the driver still says yes, as it would after a restart
        assertThat(sut.isInitialized()).isFalse()      // ...and the dosing gates are told no
    }
}
