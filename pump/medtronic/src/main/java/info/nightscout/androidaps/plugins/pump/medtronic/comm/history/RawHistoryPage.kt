package info.nightscout.androidaps.plugins.pump.medtronic.comm.history

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.utils.CRC
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.pump.common.utils.ByteUtil
import java.util.Arrays
import java.util.Locale

/**
 * Created by geoff on 6/4/16.
 */
class RawHistoryPage(private val aapsLogger: AAPSLogger) {

    var data = ByteArray(0)
        private set

    fun appendData(newdata: ByteArray?) {
        data = ByteUtil.concat(data, newdata)
    }

    val onlyData: ByteArray
        get() = Arrays.copyOfRange(data, 0, 1022)

    val length: Int
        get() = data.size

    val isChecksumOK: Boolean
        get() {
            if (length != 1024) {
                return false
            }
            val computedCRC = CRC.calculate16CCITT(ByteUtil.substring(data, 0, 1022))
            val crcCalculated = ByteUtil.toInt(computedCRC[0].toInt(), computedCRC[1].toInt())
            val crcStored = ByteUtil.toInt(data[1022].toInt(), data[1023].toInt())
            if (crcCalculated != crcStored) {
                aapsLogger.error(
                    LTag.PUMPBTCOMM, String.format(
                        Locale.ENGLISH, "Stored CRC (%d) is different than calculated (%d), but ignored for now.", crcStored,
                        crcCalculated
                    )
                )
            } else {
                if (MedtronicUtil.isLowLevelDebug) aapsLogger.debug(LTag.PUMPBTCOMM, "CRC ok.")
            }
            return crcCalculated == crcStored
        }

    fun dumpToDebug() {
        val linesize = 80
        var offset = 0
        val sb = StringBuilder()
        while (offset < data.size) {
            var bytesToLog = linesize
            if (offset + linesize > data.size) {
                bytesToLog = data.size - offset
            }
            sb.append(ByteUtil.shortHexString(ByteUtil.substring(data, offset, bytesToLog)) + " ")
            // sb.append("\n");
            offset += linesize
        }
        aapsLogger.info(LTag.PUMPBTCOMM, "History Page Data:\n$sb")
    }

}