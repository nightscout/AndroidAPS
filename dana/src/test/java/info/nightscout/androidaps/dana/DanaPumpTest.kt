package info.nightscout.androidaps.dana

import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
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