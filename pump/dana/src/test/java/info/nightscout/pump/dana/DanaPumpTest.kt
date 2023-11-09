package info.nightscout.pump.dana

import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class DanaPumpTest : TestBaseWithProfile() {

    @Mock lateinit var instantiator: Instantiator

    private lateinit var sut: DanaPump

    @BeforeEach
    fun setup() {
        sut = DanaPump(aapsLogger, sp, dateUtil, instantiator, decimalFormatter)
    }

    @Test
    fun detectDanaRS() {
        sut.hwModel = 0x05
        assertThat(sut.modelFriendlyName()).contains("DanaRS")
    }
}
