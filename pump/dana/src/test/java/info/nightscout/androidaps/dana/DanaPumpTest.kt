package info.nightscout.androidaps.dana

import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

class DanaPumpTest : TestBaseWithProfile() {

    @Mock lateinit var sp: SP

    private lateinit var sut: DanaPump

    @Before
    fun setup() {
        sut = DanaPump(aapsLogger, sp, dateUtil, profileInjector)
    }

    @Test
    fun detectDanaRS() {
        sut.hwModel = 0x05
        Assert.assertTrue(sut.modelFriendlyName().contains("DanaRS"))
    }
}