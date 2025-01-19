package app.aaps.pump.common.hw.rileylink.ble.data.encoding

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.utils.pump.ByteUtil
import app.aaps.core.utils.pump.ByteUtil.concat
import app.aaps.core.utils.pump.ByteUtil.getListFromByteArray
import app.aaps.core.utils.pump.ByteUtil.shortHexString
import app.aaps.pump.common.hw.rileylink.ble.RileyLinkCommunicationException
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkBLEError
import java.lang.StringBuilder
import java.util.ArrayList
import java.util.Locale

/**
 * Created by andy on 11/24/18.
 */
class Encoding4b6bGeoff(private val aapsLogger: AAPSLogger) : Encoding4b6bAbstract() {

    override fun encode4b6b(data: ByteArray): ByteArray {
        // if ((data.length % 2) != 0) {
        // LOG.error("Warning: data is odd number of bytes");
        // }
        // use arraylists because byte[] is annoying.
        val inData: List<Byte> = getListFromByteArray(data)
        val outData: MutableList<Byte> = ArrayList<Byte>()

        var acc = 0
        var bitcount = 0
        var i: Int = 0
        while (i < inData.size) {
            acc = acc shl 6
            acc = acc or encode4b6bList[(inData.get(i).toInt() shr 4) and 0x0f].toInt()
            bitcount += 6

            acc = acc shl 6
            acc = acc or encode4b6bList[inData.get(i).toInt() and 0x0f].toInt()
            bitcount += 6

            while (bitcount >= 8) {
                val outByte = (acc shr (bitcount - 8) and 0xff).toByte()
                outData.add(outByte)
                bitcount -= 8
                acc = acc and (0xffff shr (16 - bitcount))
            }
            i++
        }
        if (bitcount > 0) {
            acc = acc shl 6
            acc = acc or 0x14 // marks uneven packet boundary.
            bitcount += 6
            if (bitcount >= 8) {
                val outByte = ((acc shr (bitcount - 8)) and 0xff).toByte()
                outData.add(outByte)
                bitcount -= 8
                // acc &= (0xffff >> (16 - bitcount));
            }
            while (bitcount >= 8) {
                outData.add(0.toByte())
                bitcount -= 8
            }
        }

        // convert back to byte[]
        val rval = ByteUtil.getByteArrayFromList(outData)

        return rval
    }

    /**
     * Decode by Geoff
     *
     * @param raw
     * @return
     * @throws NumberFormatException
     */
    @Throws(RileyLinkCommunicationException::class)
    override fun decode4b6b(raw: ByteArray): ByteArray {
        val errorMessageBuilder = StringBuilder()

        errorMessageBuilder.append("Input data: " + shortHexString(raw) + "\n")

        if ((raw.size % 2) != 0) {
            errorMessageBuilder.append("Warn: odd number of bytes.\n")
        }

        var rval = byteArrayOf()
        var availableBits = 0
        var codingErrors = 0
        var x = 0
        // Log.w(TAG,"decode4b6b: untested code");
        // Log.w(TAG,String.format("Decoding %d bytes: %s",raw.length,ByteUtil.INSTANCE.shortHexString(raw)));
        for (i in raw.indices) {
            var unsignedValue = raw[i].toInt()
            if (unsignedValue < 0) {
                unsignedValue += 256
            }
            x = (x shl 8) + unsignedValue
            availableBits += 8
            if (availableBits >= 12) {
                // take top six
                val highcode = (x shr (availableBits - 6)) and 0x3F
                val highIndex = encode4b6bListIndex((highcode).toByte())
                // take bottom six
                val lowcode = (x shr (availableBits - 12)) and 0x3F
                val lowIndex = encode4b6bListIndex((lowcode).toByte())
                // special case at end of transmission on uneven boundaries:
                if ((highIndex >= 0) && (lowIndex >= 0)) {
                    val decoded = ((highIndex shl 4) + lowIndex).toByte()
                    rval = concat(rval, decoded)
                    /*
                     * LOG.debug(String.format(
                     * "i=%d,x=0x%08X,0x%02X->0x%02X, 0x%02X->0x%02X, result: 0x%02X, %d bits remaining, errors %d, bytes remaining: %s"
                     * ,
                     * i,x,highcode,highIndex, lowcode,
                     * lowIndex,decoded,availableBits,codingErrors,ByteUtil.INSTANCE.shortHexString
                     * (ByteUtil.INSTANCE.substring(raw,i+1,raw.length-i-1))));
                     */
                } else {
                    // LOG.debug(String.format("i=%d,x=%08X, coding error: highcode=0x%02X, lowcode=0x%02X, %d bits remaining",i,x,highcode,lowcode,availableBits));
                    errorMessageBuilder.append(
                        String.format(
                            Locale.ENGLISH,
                            "decode4b6b: i=%d,x=%08X, coding error: highcode=0x%02X, lowcode=0x%02X, %d bits remaining.\n",
                            i, x, highcode, lowcode, availableBits
                        )
                    )
                    codingErrors++
                }

                availableBits -= 12
                x = x and (0x0000ffff shr (16 - availableBits))
            }
        }

        if (availableBits != 0) {
            if ((availableBits == 4) && (x == 0x05)) {
                // normal end
            } else {
                // LOG.error("decode4b6b: failed clean decode -- extra bits available (not marker)(" + availableBits +
                // ")");
                errorMessageBuilder.append(
                    ("decode4b6b: failed clean decode -- extra bits available (not marker)("
                        + availableBits + ")\n")
                )
                codingErrors++
            }
        } else {
            // also normal end.
        }

        if (codingErrors > 0) {
            // LOG.error("decode4b6b: " + codingErrors + " coding errors encountered.");
            errorMessageBuilder.append("decode4b6b: " + codingErrors + " coding errors encountered.")
            writeError(aapsLogger, raw, errorMessageBuilder.toString())
            throw RileyLinkCommunicationException(RileyLinkBLEError.CodingErrors, errorMessageBuilder.toString())
        }
        return rval
    }
}
