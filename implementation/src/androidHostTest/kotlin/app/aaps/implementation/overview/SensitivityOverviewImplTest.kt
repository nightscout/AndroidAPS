package app.aaps.implementation.overview

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.IsfStep
import app.aaps.core.ui.CoreUiStrings
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The sensitivity lines as the Overview chip dialog and the watch's Loop Status show them. The
 * rules moved here from the chips ViewModel unchanged; these tests pin the standard autosens
 * branch, which is what most users run.
 *
 * The strings resolve to their real English text through [baseText], so nothing is stubbed and the
 * asserts check what the user reads.
 *
 * The expected ISF lines are built from the profile, not written as numbers. The test profile has a
 * different ISF before and after 02:00, and the profile reads the real clock, not the base's `now`.
 */
class SensitivityOverviewImplTest : TestBaseWithProfile() {

    @Mock lateinit var loop: Loop
    @Mock lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Mock lateinit var ads: AutosensDataStore

    private lateinit var sut: SensitivityOverviewImpl

    @BeforeEach
    fun prepare() {
        sut = SensitivityOverviewImpl(
            iobCobCalculator, loop, config, constraintsChecker, profileFunction, processedDeviceStatusData,
            profileUtil, activePlugin, baseText, decimalFormatter, dateUtil, aapsLogger, preferences
        )
        whenever(config.APS).thenReturn(true)
        whenever(iobCobCalculator.ads).thenReturn(ads)
        val autosensEnabled: Constraint<Boolean> = mock()
        whenever(autosensEnabled.value()).thenReturn(true)
        whenever(constraintsChecker.isAutosensModeEnabled()).thenReturn(autosensEnabled)
        runBlocking {
            whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
            whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        }
    }

    private fun autosensRatio(ratio: Double) {
        val data = mock<AutosensData>().also { whenever(it.autosensResult).thenReturn(AutosensResult(ratio = ratio)) }
        whenever(ads.getLastAutosensData(any(), any(), any())).thenReturn(data)
    }

    /** What the last loop run divided the profile ISF by. Null stands for "the loop has not run yet". */
    private fun ratioUsedByLoop(ratio: Double?) {
        val request = mock<APSResult>().also {
            whenever(it.rawData()).thenReturn(RT(runningDynamicIsf = false, sensitivityRatio = ratio))
        }
        whenever(loop.lastRun).thenReturn(Loop.LastRun().apply { this.request = request })
    }

    private fun profileIsfLine(): String =
        baseText.gs(CoreUiStrings.isf_profile, effectiveProfile.getProfileIsfMgdl())

    /** The algorithm divides, so a ratio below 1.0 raises the ISF - see `DetermineBasalSMB`. */
    private fun effectiveIsfLine(ratio: Double): String =
        baseText.gs(CoreUiStrings.isf_effective, effectiveProfile.getProfileIsfMgdl() / ratio)

    @Test
    fun `without an autosens result only the profile ISF is known`() = runBlocking {
        whenever(ads.getLastAutosensData(any(), any(), any())).thenReturn(null)

        val data = sut.build()

        assertThat(data.hasData).isFalse()
        assertThat(data.ratio).isEqualTo(1.0)
        assertThat(data.asText).isEmpty()
        assertThat(data.lines).containsExactly(profileIsfLine())
        assertThat(data.isEnabled).isTrue()
        assertThat(data.isfFrom).isEmpty()
        assertThat(data.isfTo).isEmpty()
        assertThat(data.isfStep).isEqualTo(IsfStep.SAME)
    }

    @Test
    fun `an autosens ratio gives the value, the profile ISF and the effective ISF, in that order`() = runBlocking {
        autosensRatio(1.2)
        ratioUsedByLoop(1.2)

        val data = sut.build()

        assertThat(data.hasData).isTrue()
        assertThat(data.ratio).isEqualTo(1.2)
        assertThat(data.asText).isEqualTo("120%")
        assertThat(data.lines).containsExactly("Autosens Value: 120%", profileIsfLine(), effectiveIsfLine(1.2)).inOrder()
    }

    /**
     * Issue 5080: the effective ISF must move the other way than the ratio. A user who is more
     * sensitive (ratio below 100%) needs a higher ISF, because one unit moves the glucose further.
     */
    @Test
    fun `a ratio below 100 percent raises the effective ISF above the profile ISF`() = runBlocking {
        autosensRatio(0.8)
        ratioUsedByLoop(0.8)

        val data = sut.build()

        assertThat(data.lines).containsExactly("Autosens Value: 80%", profileIsfLine(), effectiveIsfLine(0.8)).inOrder()
        assertThat(effectiveProfile.getProfileIsfMgdl() / 0.8).isGreaterThan(effectiveProfile.getProfileIsfMgdl())
        // The chip shows the same step, pointing up, not only the percentage.
        assertThat(data.isfFrom).isEqualTo(decimalFormatter.to1Decimal(effectiveProfile.getProfileIsfMgdl()))
        assertThat(data.isfTo).isEqualTo(decimalFormatter.to1Decimal(effectiveProfile.getProfileIsfMgdl() / 0.8))
        assertThat(data.isfStep).isEqualTo(IsfStep.UP)
    }

    @Test
    fun `a ratio above 100 percent points the chip step down`() = runBlocking {
        autosensRatio(1.2)
        ratioUsedByLoop(1.2)

        val data = sut.build()

        assertThat(data.isfStep).isEqualTo(IsfStep.DOWN)
    }

    /**
     * The step is worked out from the numbers, but a step the chip cannot show must not get an
     * arrow: two values that round to the same text would read "5.5 up 5.5".
     */
    @Test
    fun `a ratio too small to change the shown value gives no step`() = runBlocking {
        autosensRatio(1.0)
        ratioUsedByLoop(0.9999)

        val data = sut.build()

        assertThat(data.isfFrom).isEqualTo(data.isfTo)
        assertThat(data.isfStep).isEqualTo(IsfStep.SAME)
    }

    @Test
    fun `an autosens value the loop did not use leaves the chip step empty`() = runBlocking {
        autosensRatio(0.8)
        ratioUsedByLoop(1.0)

        val data = sut.build()

        assertThat(data.isfFrom).isEmpty()
        assertThat(data.isfTo).isEmpty()
        assertThat(data.isfStep).isEqualTo(IsfStep.SAME)
    }

    @Test
    fun `a ratio of exactly 100 percent stays out of the chip but in the lines`() = runBlocking {
        autosensRatio(1.0)
        ratioUsedByLoop(1.0)

        val data = sut.build()

        assertThat(data.asText).isEmpty()
        assertThat(data.lines.first()).isEqualTo("Autosens Value: 100%")
    }

    /**
     * Autosens keeps running even when it is switched off, so the computed value is still shown,
     * but the algorithm used the plain profile ISF and no effective ISF may be offered.
     */
    @Test
    fun `an autosens value the loop did not use gives no effective ISF`() = runBlocking {
        autosensRatio(0.8)
        ratioUsedByLoop(1.0)

        val data = sut.build()

        assertThat(data.lines).containsExactly("Autosens Value: 80%", profileIsfLine()).inOrder()
    }

    @Test
    fun `before the first loop run only the profile ISF is offered`() = runBlocking {
        autosensRatio(0.8)
        whenever(loop.lastRun).thenReturn(null)

        val data = sut.build()

        assertThat(data.lines).containsExactly("Autosens Value: 80%", profileIsfLine()).inOrder()
    }

    /**
     * A temp target can take sensitivity over from autosens, and a client computes its own autosens
     * over Nightscout data. Both show up as a ratio the loop used that is not the one autosens
     * reported, and then the user is told which one the algorithm worked with.
     */
    @Test
    fun `a ratio the loop used that differs from autosens is named and drives the effective ISF`() = runBlocking {
        autosensRatio(1.0)
        ratioUsedByLoop(0.7)

        val data = sut.build()

        assertThat(data.lines).containsExactly(
            "Autosens Value: 100%", profileIsfLine(), effectiveIsfLine(0.7), "Autosens in algorithm: 70%"
        ).inOrder()
    }

    /**
     * Autosens is computed again on every new reading, while the loop ran up to five minutes ago,
     * so the two ratios are rarely the exact same number. Both lines print whole percent, and the
     * same percentage twice tells the user nothing.
     */
    @Test
    fun `a loop ratio that reads as the same percentage is not named again`() = runBlocking {
        autosensRatio(0.8512)
        ratioUsedByLoop(0.8488)

        val data = sut.build()

        assertThat(data.lines).containsExactly("Autosens Value: 85%", profileIsfLine(), effectiveIsfLine(0.8488)).inOrder()
    }

    /** Foreign device status data: a zero would divide by zero, so it counts as no ratio at all. */
    @Test
    fun `a ratio of zero is read as no adjustment`() = runBlocking {
        autosensRatio(1.0)
        ratioUsedByLoop(0.0)

        val data = sut.build()

        assertThat(data.lines).containsExactly("Autosens Value: 100%", profileIsfLine()).inOrder()
    }
}
