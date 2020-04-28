package info.nightscout.androidaps.plugins.pump.medtronic.comm.history;

import java.util.Arrays;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.CRC;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;

/**
 * Created by geoff on 6/4/16.
 */
public class RawHistoryPage {

    private final AAPSLogger aapsLogger;

    private byte[] data = new byte[0];


    public RawHistoryPage(AAPSLogger aapsLogger) {
        this.aapsLogger = aapsLogger;
    }


    public void appendData(byte[] newdata) {
        data = ByteUtil.concat(data, newdata);
    }


    public byte[] getData() {
        return data;
    }


    byte[] getOnlyData() {
        return Arrays.copyOfRange(data, 0, 1022);
    }


    public int getLength() {
        return data.length;
    }


    public boolean isChecksumOK() {
        if (getLength() != 1024) {
            return false;
        }
        byte[] computedCRC = CRC.calculate16CCITT(ByteUtil.substring(data, 0, 1022));

        int crcCalculated = ByteUtil.toInt(computedCRC[0], computedCRC[1]);
        int crcStored = ByteUtil.toInt(data[1022], data[1023]);

        if (crcCalculated != crcStored) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Stored CRC ({}) is different than calculated ({}), but ignored for now.", crcStored,
                    crcCalculated);
        } else {
            if (MedtronicUtil.isLowLevelDebug())
                aapsLogger.debug(LTag.PUMPBTCOMM, "CRC ok.");
        }

        return crcCalculated == crcStored;
    }


    public void dumpToDebug() {
        int linesize = 80;
        int offset = 0;

        StringBuilder sb = new StringBuilder();

        while (offset < data.length) {
            int bytesToLog = linesize;
            if (offset + linesize > data.length) {
                bytesToLog = data.length - offset;
            }
            sb.append(ByteUtil.shortHexString(ByteUtil.substring(data, offset, bytesToLog)) + " ");
            // sb.append("\n");

            offset += linesize;
        }

        aapsLogger.debug(LTag.PUMPBTCOMM, "History Page Data:\n{}", sb.toString());
    }
}
