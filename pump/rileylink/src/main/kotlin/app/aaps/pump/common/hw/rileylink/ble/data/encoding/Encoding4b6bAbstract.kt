package app.aaps.pump.common.hw.rileylink.ble.data.encoding

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.utils.pump.ByteUtil.getHex
import app.aaps.pump.common.hw.rileylink.ble.RileyLinkCommunicationException

/**
 * Created by andy on 11/24/18.
 */
abstract class Encoding4b6bAbstract : Encoding4b6b {

    abstract override fun encode4b6b(data: ByteArray): ByteArray

    @Throws(RileyLinkCommunicationException::class)
    abstract override fun decode4b6b(raw: ByteArray): ByteArray

    fun writeError(aapsLogger: AAPSLogger, raw: ByteArray, errorData: String?) {
        aapsLogger.error(
            String.format(
                "\n" +
                    "=============================================================================\n" +  //
                    " Decoded payload length is zero.\n" +
                    " encodedPayload: %s\n" +
                    " errors: %s\n" +
                    "=============================================================================",  //
                getHex(raw), errorData
            )
        )

        //FabricUtil.createEvent("MedtronicDecode4b6bError", null);
    }

    companion object {

        /**
         * encode4b6bMap is an ordered list of translations 6bits -> 4 bits, in order from 0x0 to 0xF
         * The 6 bit codes are what is used on the RF side of the RileyLink to communicate
         * with a Medtronic pump.
         */
        val encode4b6bList: ByteArray = byteArrayOf(0x15, 0x31, 0x32, 0x23, 0x34, 0x25, 0x26, 0x16, 0x1a, 0x19, 0x2a, 0x0b, 0x2c, 0x0d, 0x0e, 0x1c)

        // 21, 49, 50, 35, 52, 37, 38, 22, 26, 25, 42, 11, 44, 13, 14, 28
        /* O(n) lookup. Run on an O(n) translation of a byte-stream, gives O(n**2) performance. Sigh. */
        fun encode4b6bListIndex(b: Byte): Int {
            for (i in encode4b6bList.indices) {
                if (b == encode4b6bList[i]) {
                    return i
                }
            }
            return -1
        }
    }
}
