package app.aaps.pump.danar.comm

import app.aaps.core.data.time.T
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MsgStatusTempBasalTest : DanaRTestBase() {

    @Test fun runTest() {
        val packet = MsgStatusTempBasal(injector)
        // test message decoding
        packet.handleMessage(createArray(34, 1.toByte()))
        Assertions.assertEquals(true, packet.isTempBasalInProgress)
        // passing an bigger number
        packet.handleMessage(createArray(34, 2.toByte()))
        Assertions.assertEquals(false, packet.isTempBasalInProgress)
    }

    /**
     * data byte0=in-progress flag, byte1=percent, byte2=duration code, bytes 3-5=running seconds (left 0).
     * intFromBuff applies a +6 header offset, so logical data byte N lives at physical index N+6.
     */
    private fun tbrMessage(durationCode: Int): ByteArray = ByteArray(34).apply {
        this[6] = 0x01           // temp basal in progress
        this[7] = 100.toByte()   // 100 %
        this[8] = durationCode.toByte()
    }

    @Test fun decodesDurationCodes() {
        // 150/160 are the pump's short-duration codes (15 / 30 min). Regression: these were compared after
        // multiplying by 60, so they never matched and decoded as 9000/9600 min.
        MsgStatusTempBasal(injector).handleMessage(tbrMessage(150))
        Assertions.assertEquals(T.mins(15).msecs(), danaPump.tempBasalDuration)

        danaPump.tempBasalStart = 0 // reset so the next message counts as a new TBR (start differs > 3 s)
        MsgStatusTempBasal(injector).handleMessage(tbrMessage(160))
        Assertions.assertEquals(T.mins(30).msecs(), danaPump.tempBasalDuration)

        // any other value is a whole number of hours: raw byte * 60 min
        danaPump.tempBasalStart = 0
        MsgStatusTempBasal(injector).handleMessage(tbrMessage(2))
        Assertions.assertEquals(T.mins(120).msecs(), danaPump.tempBasalDuration)
    }
}