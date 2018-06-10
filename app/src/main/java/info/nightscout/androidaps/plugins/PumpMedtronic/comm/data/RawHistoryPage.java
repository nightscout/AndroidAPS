package info.nightscout.androidaps.plugins.PumpMedtronic.comm.data;

import android.util.Log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import info.nightscout.androidaps.plugins.PumpCommon.utils.ByteUtil;
import info.nightscout.androidaps.plugins.PumpCommon.utils.CRC;
import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicUtil;

/**
 * Created by geoff on 6/4/16.
 */
public class RawHistoryPage {
    private static final String TAG = "RawHistoryPage";
    private static final Logger LOG = LoggerFactory.getLogger(RawHistoryPage.class);
    byte[] data = new byte[0];


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
            LOG.error("Stored CRC ({}) is different than calculated ({}), but ignored for now.", crcStored, crcCalculated);
        } else {
            if (MedtronicUtil.isLowLevelDebug()) LOG.debug("CRC ok.");
        }

        return crcCalculated == crcStored;
    }


    public void dumpToDebug() {
        int linesize = 80;
        int offset = 0;
        while (offset < data.length) {
            int bytesToLog = linesize;
            if (offset + linesize > data.length) {
                bytesToLog = data.length - offset;
            }
            Log.d(TAG, ByteUtil.shortHexString(ByteUtil.substring(data, offset, bytesToLog)));
            offset += linesize;
        }
    }


}
