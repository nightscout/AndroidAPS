package app.aaps.plugins.insulin

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.kotlin.whenever

class InsulinOrefUltraRapidActingPluginTest : TestBase() {

    private lateinit var sut: InsulinOrefUltraRapidActingPlugin

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var config: Config
    @Mock lateinit var hardLimits: HardLimits
    @Mock lateinit var uiInteraction: UiInteraction

    @BeforeEach
    fun setup() {
        sut = InsulinOrefUltraRapidActingPlugin(rh, profileFunction, rxBus, aapsLogger, config, hardLimits, uiInteraction)
    }

    @Test
    fun `simple peak test`() {
        assertThat(sut.peak).isEqualTo(55)
    }

    @Test
    fun getIdTest() {
        assertThat(sut.id).isEqualTo(Insulin.InsulinType.OREF_ULTRA_RAPID_ACTING)
    }

    @Test
    fun commentStandardTextTest() {
        whenever(rh.gs(eq(R.string.ultra_fast_acting_insulin_comment))).thenReturn("Fiasp")
        assertThat(sut.commentStandardText()).isEqualTo("Fiasp")
    }

    @Test
    fun getFriendlyNameTest() {
        whenever(rh.gs(eq(R.string.ultra_rapid_oref))).thenReturn("Ultra-Rapid Oref")
        assertThat(sut.friendlyName).isEqualTo("Ultra-Rapid Oref")
    }

}
