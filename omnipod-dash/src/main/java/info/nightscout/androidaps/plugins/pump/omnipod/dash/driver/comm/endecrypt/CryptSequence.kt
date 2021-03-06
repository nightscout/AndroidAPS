package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.endecrypt

import java.nio.ByteBuffer

class CryptSequence(var sqn: Long) {

    fun incrementForEnDecrypt(fromPdmToPod: Boolean): ByteArray {
        sqn++
        val ret = ByteBuffer.allocate(8)
            .putLong(sqn)
            .array()
            .copyOfRange(3, 8)
        if (fromPdmToPod) {
            ret[0] = (ret[0].toInt() and 127).toByte()
        } else {
            ret[0] = (ret[0].toInt() or 128).toByte()
        }
        return ret
    }
}
