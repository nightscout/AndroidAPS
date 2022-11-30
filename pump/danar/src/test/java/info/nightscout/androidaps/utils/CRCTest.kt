package info.nightscout.androidaps.utils

import org.junit.Assert
import org.junit.jupiter.api.Test

class CRCTest {

    @Test
    fun getCrc16Test() {
        val data = byteArrayOf(1,2,3,4,5,6,7,8,9,0)
        Assert.assertEquals(27649.toShort(), CRC.getCrc16(data, 0, 10))
    }
}