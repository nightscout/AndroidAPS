package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import java.nio.ByteBuffer

class CryptSequence(var sqn: Long) {

    fun incrementForEapAka():ByteArray {
        sqn++
        return ByteBuffer.allocate(8)
            .putLong(sqn)
            .array()
            .copyOfRange(2, 8)
    }

    fun incrementForEnDecrypt(podReceiving: Boolean):ByteArray{
        sqn++
        val ret = ByteBuffer.allocate(8)
            .putLong(sqn)
            .array()
            .copyOfRange(3, 8)
        if (podReceiving) {
            ret[0] = (ret[0].toInt() and 127).toByte()
        } else {
            ret[0] = (ret[0].toInt() or 128).toByte()
        }
        return ret
    }
}