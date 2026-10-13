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

class InsulinLyumjevPluginTest : TestBase() {

    private lateinit var sut: InsulinLyumjevPlugin

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var config: Config
    @Mock lateinit var hardLimits: HardLimits
    @Mock lateinit var uiInteraction: UiInteraction

    @BeforeEach
    fun setup() {
        sut = InsulinLyumjevPlugin(rh, profileFunction, rxBus, aapsLogger, config, hardLimits, uiInteraction)
    }

    @Test
    fun `simple peak test`() {
        assertThat(sut.peak).isEqualTo(45)
    }

    @Test
    fun getIdTest() {
        assertThat(sut.id).isEqualTo(Insulin.InsulinType.OREF_LYUMJEV)
    }

    @Test
    fun commentStandardTextTest() {
        whenever(rh.gs(eq(R.string.lyumjev))).thenReturn("Lyumjev")
        assertThat(sut.commentStandardText()).isEqualTo("Lyumjev")
    }

    @Test
    fun getFriendlyNameTest() {
        whenever(rh.gs(eq(R.string.lyumjev))).thenReturn("Lyumjev")
        assertThat(sut.friendlyName).isEqualTo("Lyumjev")
    }

}
