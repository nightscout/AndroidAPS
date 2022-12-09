package info.nightscout.androidaps.dana

import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.interfaces.profile.Instantiator
import info.nightscout.pump.dana.DanaPump
import info.nightscout.shared.sharedPreferences.SP
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class DanaPumpTest : TestBaseWithProfile() {

    @Mock lateinit var sp: SP
    @Mock lateinit var instantiator: Instantiator

    private lateinit var sut: DanaPump

    @BeforeEach
    fun setup() {
        sut = DanaPump(aapsLogger, sp, dateUtil, instantiator)
    }

    @Test
    fun detectDanaRS() {
        sut.hwModel = 0x05
        Assertions.assertTrue(sut.modelFriendlyName().contains("DanaRS"))
    }
}