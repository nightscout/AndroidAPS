package app.aaps.implementation.pump

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PumpWithConcentrationImplTest : TestBase() {

    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var constraintsChecker: ConstraintsChecker
    @Mock lateinit var insulin: Insulin
    @Mock lateinit var pump: Pump
    @Mock lateinit var pumpEnactResult: PumpEnactResult
    @Mock lateinit var effectiveProfile: EffectiveProfile
    @Mock lateinit var pumpProfile: PumpProfile

    private lateinit var sut: PumpWithConcentrationImpl

    @BeforeEach
    fun setup() {
        whenever(activePlugin.activePumpInternal).thenReturn(pump)
        sut = PumpWithConcentrationImpl(aapsLogger, activePlugin, profileFunction, constraintsChecker, insulin)
    }

    private fun setupConcentration(concentration: Double) {
        whenever(insulin.iCfg).thenReturn(ICfg("Test", 0L, 0L, concentration))
        runBlocking { whenever(profileFunction.getProfile()).thenReturn(effectiveProfile) }
    }

    private fun setupU100() {
        whenever(insulin.iCfg).thenReturn(ICfg("Test", 0L, 0L, 1.0))
    }

    // --- deliverTreatment tests ---

    @Test
    fun `deliverTreatment with U100 passes insulin unchanged`() {
        setupU100()
        val dbi = DetailedBolusInfo().apply { insulin = 5.0 }
        whenever(pump.deliverTreatment(any())).thenReturn(pumpEnactResult)

        sut.deliverTreatment(dbi)

        verify(pump).deliverTreatment(argThat { insulin == 5.0 })
    }

    @Test
    fun `deliverTreatment with U200 halves insulin for normal bolus`() {
        setupConcentration(2.0)
        val dbi = DetailedBolusInfo().apply { insulin = 6.0; bolusType = BS.Type.NORMAL }
        whenever(pump.deliverTreatment(any())).thenReturn(pumpEnactResult)

        sut.deliverTreatment(dbi)

        verify(pump).deliverTreatment(argThat { insulin == 3.0 })
    }

    @Test
    fun `deliverTreatment with U200 does not modify priming bolus`() {
        setupConcentration(2.0)
        val dbi = DetailedBolusInfo().apply { insulin = 4.0; bolusType = BS.Type.PRIMING }
        whenever(pump.deliverTreatment(any())).thenReturn(pumpEnactResult)

        sut.deliverTreatment(dbi)

        verify(pump).deliverTreatment(argThat { insulin == 4.0 })
    }

    @Test
    fun `deliverTreatment with U50 doubles insulin for normal bolus`() {
        setupConcentration(0.5)
        val dbi = DetailedBolusInfo().apply { insulin = 2.0; bolusType = BS.Type.NORMAL }
        whenever(pump.deliverTreatment(any())).thenReturn(pumpEnactResult)

        sut.deliverTreatment(dbi)

        verify(pump).deliverTreatment(argThat { insulin == 4.0 })
    }

    // --- setTempBasalAbsolute tests ---

    @Test
    fun `setTempBasalAbsolute with U200 halves rate sent to pump`() {
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
    fun `setTempBasalAbsolute with U100 passes rate unchanged`() {
        setupU100()
        runBlocking { whenever(profileFunction.getProfile()).thenReturn(effectiveProfile) }
        val constraintResult: Constraint<Double> = mock()
        whenever(constraintResult.value()).thenReturn(1.5)
        whenever(constraintsChecker.applyBasalConstraints(any(), eq(effectiveProfile))).thenReturn(constraintResult)
        whenever(pump.setTempBasalAbsolute(any(), any(), any(), any())).thenReturn(pumpEnactResult)

        sut.setTempBasalAbsolute(1.5, 60, true, PumpSync.TemporaryBasalType.NORMAL)

        verify(pump).setTempBasalAbsolute(eq(1.5), eq(60), eq(true), eq(PumpSync.TemporaryBasalType.NORMAL))
    }

    // --- setExtendedBolus tests ---

    @Test
    fun `setExtendedBolus with U200 halves insulin`() {
        setupConcentration(2.0)
        whenever(pump.setExtendedBolus(any(), any())).thenReturn(pumpEnactResult)

        sut.setExtendedBolus(4.0, 60)

        verify(pump).setExtendedBolus(eq(2.0), eq(60))
    }

    @Test
    fun `setExtendedBolus with U100 passes insulin unchanged`() {
        setupU100()
        whenever(pump.setExtendedBolus(any(), any())).thenReturn(pumpEnactResult)

        sut.setExtendedBolus(4.0, 60)

        verify(pump).setExtendedBolus(eq(4.0), eq(60))
    }

    @Test
    fun `setExtendedBolus with U50 doubles insulin`() {
        setupConcentration(0.5)
        whenever(pump.setExtendedBolus(any(), any())).thenReturn(pumpEnactResult)

        sut.setExtendedBolus(2.0, 30)

        // 2.0 / 0.5 = 4.0
        verify(pump).setExtendedBolus(eq(4.0), eq(30))
    }

    // --- pumpDescription tests ---

    @Test
    fun `pumpDescription scales values for U200`() {
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
    fun `pumpDescription scales for U50`() {
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
    fun `setNewBasalProfile converts EffectiveProfile to PumpProfile`() {
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
    fun `setTempBasalAbsolute throws when no profile running`() {
        setupConcentration(2.0)
        runBlocking { whenever(profileFunction.getProfile()).thenReturn(null) }

        try {
            sut.setTempBasalAbsolute(1.0, 30, false, PumpSync.TemporaryBasalType.NORMAL)
            assertThat(false).isTrue() // should not reach here
        } catch (e: IllegalStateException) {
            assertThat(e.message).isEqualTo("No profile running")
        }
    }
}
