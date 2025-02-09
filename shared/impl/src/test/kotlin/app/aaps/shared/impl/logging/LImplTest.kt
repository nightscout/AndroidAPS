package app.aaps.shared.impl.logging

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
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

    @Mock lateinit var preferences: Preferences

    private lateinit var sut: LImpl

    @BeforeEach
    fun setUp() {
        val lazyPreferences: Lazy<Preferences> = Lazy { preferences }
        sut = LImpl(lazyPreferences)
    }

    @Test
    fun findByName() {
        assertThat(sut.findByName("APS").name).isEqualTo(LTag.APS.name)
        assertThat(sut.findByName("APS2").name).isEqualTo("NONEXISTENT")
    }

    @Test
    fun getLogElements() {
        assertThat(sut.logElements()).isNotEmpty()
    }

    @Test
    fun resetToDefaults() {
        val element = sut.findByName("APS")
        element.enabled = false
        sut.resetToDefaults()
        assertThat(element.enabled).isTrue()
    }
}
