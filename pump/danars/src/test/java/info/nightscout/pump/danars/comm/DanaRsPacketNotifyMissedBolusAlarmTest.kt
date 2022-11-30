package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class DanaRsPacketNotifyMissedBolusAlarmTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketNotifyMissedBolusAlarm) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketNotifyMissedBolusAlarm(packetInjector)
        // test params
        Assert.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        packet.handleMessage(createArray(6, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        // everything ok :)
        packet.handleMessage(createArray(6, 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("NOTIFY__MISSED_BOLUS_ALARM", packet.friendlyName)
    }
}