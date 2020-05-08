package info.nightscout.androidaps.plugins.pump.danaRS.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRS_Packet_History_Blood_GlucoseTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_History_Blood_Glucose) {
                it.aapsLogger = aapsLogger
                it.rxBus = rxBus
            }
        }
    }

    @Test fun runTest() {
<<<<<<< HEAD
        val packet = DanaRS_Packet_History_Blood_Glucose(packetInjector, System.currentTimeMillis())
=======
        val packet = DanaRS_Packet_History_Blood_Glucose(aapsLogger, rxBus, dateUtil, System.currentTimeMillis())
>>>>>>> origin/dev
        Assert.assertEquals("REVIEW__BLOOD_GLUCOSE", packet.friendlyName)
    }
}