package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.DanaRSTestBase
import info.nightscout.androidaps.interfaces.PumpSync
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRsPacketNotifyAlarmTest : DanaRSTestBase() {

    @Mock lateinit var pumpSync: PumpSync

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketNotifyAlarm) {
                it.aapsLogger = aapsLogger
                it.rxBus = rxBus
                it.resourceHelper = resourceHelper
                it.pumpSync = pumpSync
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketNotifyAlarm(packetInjector)
        // test params
        Assert.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        packet.handleMessage(createArray(17, 0x01.toByte()))
        Assert.assertEquals(false, packet.failed)
        // no error
        packet.handleMessage(createArray(17, 0.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("NOTIFY__ALARM", packet.friendlyName)
    }
}