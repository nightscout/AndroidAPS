package app.aaps.shared.impl.logging

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.SP
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LImplTest {

    @Mock lateinit var sp: SP

    private lateinit var sut: LImpl

    @BeforeEach
    fun setUp() {
        sut = LImpl(sp)
    }

    @Test
    fun findByName() {
        assertThat(sut.findByName("APS").name).isEqualTo(LTag.APS.name)
        assertThat(sut.findByName("APS2").name).isEqualTo("NONEXISTENT")
    }

    @Test
    fun getLogElements() {
        assertThat(sut.getLogElements()).isNotEmpty()
    }

    @Test
    fun resetToDefaults() {
        val element = sut.findByName("APS")
        element.enabled = false
        sut.resetToDefaults()
        assertThat(element.enabled).isTrue()
    }
}
