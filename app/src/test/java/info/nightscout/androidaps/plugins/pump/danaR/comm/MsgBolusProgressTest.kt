package info.nightscout.androidaps.plugins.pump.danaR.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.danar.comm.MsgBolusProgress
import info.nightscout.androidaps.db.Treatment
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class MsgBolusProgressTest : DanaRTestBase() {


    @Test fun runTest() {
        `when`(resourceHelper.gs(ArgumentMatchers.eq(R.string.bolusdelivering), ArgumentMatchers.anyDouble())).thenReturn("Delivering %1\$.2fU")
        danaPump.bolusingTreatment = Treatment(HasAndroidInjector { AndroidInjector { } })
        danaPump.bolusAmountToBeDelivered = 3.0
        val packet = MsgBolusProgress(injector)

        // test message decoding
        val array = ByteArray(100)
        putIntToArray(array, 0, 2 * 100)
        packet.handleMessage(array)
        Assert.assertEquals(1.0, danaPump.bolusingTreatment?.insulin!!, 0.0)
    }
}