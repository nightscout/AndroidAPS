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

class InsulinOrefRapidActingPluginTest : TestBase() {

    private lateinit var sut: InsulinOrefRapidActingPlugin

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var config: Config
    @Mock lateinit var hardLimits: HardLimits
    @Mock lateinit var uiInteraction: UiInteraction

    @BeforeEach
    fun setup() {
        sut = InsulinOrefRapidActingPlugin(rh, profileFunction, rxBus, aapsLogger, config, hardLimits, uiInteraction)
    }

    @Test
    fun `simple peak test`() {
        assertThat(sut.peak).isEqualTo(75)
    }

    @Test
    fun getIdTest() {
        assertThat(sut.id).isEqualTo(Insulin.InsulinType.OREF_RAPID_ACTING)
    }

    @Test
    fun commentStandardTextTest() {
        whenever(rh.gs(eq(R.string.fast_acting_insulin_comment))).thenReturn("Novorapid, Novolog, Humalog")
        assertThat(sut.commentStandardText()).isEqualTo("Novorapid, Novolog, Humalog")
    }

    @Test
    fun getFriendlyNameTest() {
        whenever(rh.gs(eq(R.string.rapid_acting_oref))).thenReturn("Rapid-Acting Oref")
        assertThat(sut.friendlyName).isEqualTo("Rapid-Acting Oref")
    }

}
