package app.aaps.implementation.overview

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.ui.CoreUiStrings
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The sensitivity lines as the Overview chip dialog and the watch's Loop Status show them. The
 * rules moved here from the chips ViewModel unchanged; these tests pin the standard autosens
 * branch, which is what most users run.
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
            profileUtil, activePlugin, rh, decimalFormatter, dateUtil, aapsLogger, preferences
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
        whenever(rh.gs(eq(CoreUiStrings.autosens_short), anyVararg())).thenReturn("120%")
        whenever(rh.gs(eq(CoreUiStrings.autosens_long), anyVararg())).thenReturn("Autosens Value: 120%")
        whenever(rh.gs(eq(CoreUiStrings.isf_profile), anyVararg())).thenReturn("ISF (profile): 50")
        whenever(rh.gs(eq(CoreUiStrings.isf_effective), anyVararg())).thenReturn("ISF (effective): 42")
    }

    private fun autosensRatio(ratio: Double) {
        val data = mock<AutosensData>().also { whenever(it.autosensResult).thenReturn(AutosensResult(ratio = ratio)) }
        whenever(ads.getLastAutosensData(any(), any(), any())).thenReturn(data)
    }

    @Test
    fun `without an autosens result only the profile ISF is known`() = runBlocking {
        whenever(ads.getLastAutosensData(any(), any(), any())).thenReturn(null)

        val data = sut.build()

        assertThat(data.hasData).isFalse()
        assertThat(data.ratio).isEqualTo(1.0)
        assertThat(data.asText).isEmpty()
        assertThat(data.lines).containsExactly("ISF (profile): 50")
        assertThat(data.isEnabled).isTrue()
    }

    @Test
    fun `an autosens ratio gives the value, the profile ISF and the effective ISF, in that order`() = runBlocking {
        autosensRatio(1.2)

        val data = sut.build()

        assertThat(data.hasData).isTrue()
        assertThat(data.ratio).isEqualTo(1.2)
        assertThat(data.asText).isEqualTo("120%")
        assertThat(data.lines).containsExactly("Autosens Value: 120%", "ISF (profile): 50", "ISF (effective): 42").inOrder()
    }

    @Test
    fun `a ratio of exactly 100 percent stays out of the chip but in the lines`() = runBlocking {
        autosensRatio(1.0)
        whenever(rh.gs(eq(CoreUiStrings.autosens_long), anyVararg())).thenReturn("Autosens Value: 100%")

        val data = sut.build()

        assertThat(data.asText).isEmpty()
        assertThat(data.lines.first()).isEqualTo("Autosens Value: 100%")
    }
}
