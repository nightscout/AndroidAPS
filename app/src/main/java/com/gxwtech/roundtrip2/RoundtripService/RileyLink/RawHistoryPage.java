package com.gxwtech.roundtrip2.RoundtripService.RileyLink;

import android.util.Log;

import com.gxwtech.roundtrip2.util.ByteUtil;
import com.gxwtech.roundtrip2.util.CRC;

/**
 * Created by geoff on 6/4/16.
 */
public class RawHistoryPage {
    private static final String TAG = "RawHistoryPage";
    byte[] data = new byte[0];
    public RawHistoryPage() {
    }
    public void appendData(byte[] newdata) {
        data = ByteUtil.concat(data,newdata);
    }
    public byte[] getData() {
        return data;
    }
    public int getLength() {
        return data.length;
    }
    public boolean isChecksumOK() {
        if (getLength() != 1024) {
            return false;
        }
        byte[] computedCRC = CRC.calculate16CCITT(ByteUtil.substring(data,0,1022));
        return ((computedCRC[0] == data[1022]) && (computedCRC[1] == data[1023]));
    }

    public void dumpToDebug() {
        int linesize = 80;
        int offset = 0;
        while (offset < data.length) {
            int bytesToLog = linesize;
            if (offset + linesize > data.length) {
                bytesToLog = data.length - offset;
            }
            Log.d(TAG,ByteUtil.shortHexString(ByteUtil.substring(data,offset,bytesToLog)));
            offset += linesize;
        }
    }
}
