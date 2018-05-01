package com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE;

import android.util.Log;

import com.gxwtech.roundtrip2.util.ByteUtil;
import com.gxwtech.roundtrip2.util.CRC;

/**
 * Created by geoff on 5/30/16.
 */
public class RadioResponse {
    private static final String TAG = "RadioResponse";
    public boolean decodedOK = false;
    public int rssi;
    public int responseNumber;
    public byte[] decodedPayload = new byte[0];
    public byte receivedCRC;

    public RadioResponse() {
    }

    public RadioResponse(byte[] rxData) {
        init(rxData);
    }

    public boolean isValid() {
        if (!decodedOK) {
            return false;
        }
        if (decodedPayload != null) {
            if (receivedCRC == CRC.crc8(decodedPayload)) {
                return true;
            }
        }
        return false;
    }

    public void init(byte[] rxData) {
        if (rxData == null) {
            return;
        }
        if (rxData.length < 3) {
            // This does not look like something valid heard from a RileyLink device
            return;
        }
        rssi = rxData[0];
        responseNumber = rxData[1];
        byte[] encodedPayload = ByteUtil.substring(rxData, 2, rxData.length - 2);
        try {
            byte[] decodeThis = RFTools.decode4b6b(encodedPayload);
            decodedOK = true;
            decodedPayload = ByteUtil.substring(decodeThis, 0, decodeThis.length - 1);
            byte calculatedCRC = CRC.crc8(decodedPayload);
            receivedCRC = decodeThis[decodeThis.length - 1];
            if (receivedCRC != calculatedCRC) {
                Log.e(TAG, String.format("RadioResponse: CRC mismatch, calculated 0x%02x, received 0x%02x", calculatedCRC, receivedCRC));
            }
        } catch (NumberFormatException e) {
            decodedOK = false;
            Log.e(TAG, "Failed to decode radio data: " + ByteUtil.shortHexString(encodedPayload));
        }
    }

    public byte[] getPayload() {
        return decodedPayload;
    }
}
