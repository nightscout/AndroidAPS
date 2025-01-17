package app.aaps.plugins.insulin

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.HardLimits
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InsulinOrefRapidActingPluginTest {

    private lateinit var sut: InsulinOrefRapidActingPlugin

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var rxBus: RxBus
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var aapsLogger: AAPSLogger
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
        `when`(rh.gs(eq(R.string.fast_acting_insulin_comment))).thenReturn("Novorapid, Novolog, Humalog")
        assertThat(sut.commentStandardText()).isEqualTo("Novorapid, Novolog, Humalog")
    }

    @Test
    fun getFriendlyNameTest() {
        `when`(rh.gs(eq(R.string.rapid_acting_oref))).thenReturn("Rapid-Acting Oref")
        assertThat(sut.friendlyName).isEqualTo("Rapid-Acting Oref")
    }

}
