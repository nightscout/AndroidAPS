package info.nightscout.androidaps.plugins.pump.medtronic.comm.history;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.CRC;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;

/**
 * Created by geoff on 6/4/16.
 */
public class RawHistoryPage {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPBTCOMM);

    private byte[] data = new byte[0];


    public RawHistoryPage() {
    }


    public void appendData(byte[] newdata) {
        data = ByteUtil.concat(data, newdata);
    }


    public byte[] getData() {
        return data;
    }


    public byte[] getOnlyData() {
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
            LOG.error("Stored CRC ({}) is different than calculated ({}), but ignored for now.", crcStored,
                crcCalculated);
        } else {
            if (MedtronicUtil.isLowLevelDebug())
                LOG.debug("CRC ok.");
        }

        return crcCalculated == crcStored;
    }


    public void dumpToDebug() {
        int linesize = 80;
        int offset = 0;

        StringBuffer sb = new StringBuffer();

        while (offset < data.length) {
            int bytesToLog = linesize;
            if (offset + linesize > data.length) {
                bytesToLog = data.length - offset;
            }
            sb.append(ByteUtil.shortHexString(ByteUtil.substring(data, offset, bytesToLog)) + " ");
            // sb.append("\n");

            offset += linesize;
        }

        LOG.debug("History Page Data:\n{}", sb.toString());
    }

}
