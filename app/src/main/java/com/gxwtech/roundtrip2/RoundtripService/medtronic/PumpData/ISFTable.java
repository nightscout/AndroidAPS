package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData;

import com.gxwtech.roundtrip2.util.ByteUtil;

import org.joda.time.LocalDateTime;

/**
 * Created by geoff on 7/2/16.
 *
 * This class exists to map the Minimed byte response to "Get sensitivity factors"
 * into a more usable form.
 */
public class ISFTable {
    boolean mIsValid = false;

    LocalDateTime validDate;
    byte[] header;
    int[] times;
    float[] rates;
    public ISFTable() {}
    public boolean parseFrom(byte[] responseBytes) {
        // example format: { 7, 1, 0, 45, 12, 30, 42, 50, 0, 0 }
        // means value pairs: {0, 45}, {12, 30}, {42, 50}
        // means: at midnight, the amount is 45,
        // at 6am, the about is 30
        // at 9pm, the amount is 50
        if (responseBytes == null) return false;
        // minimum of two bytes in header
        if (responseBytes.length < 2) {
            return false;
        }
        // Must be an even number of times and rates
        if (responseBytes.length % 2 != 0) {
            return false;
        }
        mIsValid = true;
        header = ByteUtil.substring(responseBytes,0,2);
        // find end of list
        int index = 2;
        while (true) {
            if (index + 1 > responseBytes.length) {
                break;
            }
            if ((responseBytes[index]==0) && (responseBytes[index+1]==0)) {
                break;
            }
            index += 2;
        }
        if (index == 2) {
            // no entries.
            times = new int[]{};
            rates = new float[]{};
        } else {
            int numEntries = (index - 2) / 2;
            times = new int[numEntries];
            rates = new float[numEntries];
            for (int i=0; i<numEntries; i++) {
                times[i] = responseBytes[i*2+2];
                rates[i] = responseBytes[i*2+2];
            }
        }
        validDate = new LocalDateTime();
        return true;
    }

    public byte[] getHeader() {
        return header;
    }

    public void setHeader(byte[] header) {
        this.header = header;
    }

    public int[] getTimes() {
        return times;
    }

    public void setTimes(int[] times) {
        this.times = times;
    }

    public float[] getRates() {
        return rates;
    }

    public void setRates(float[] rates) {
        this.rates = rates;
    }

    public LocalDateTime getValidDate() {
        return validDate;
    }

    public void setValidDate(LocalDateTime validDate) {
        this.validDate = validDate;
    }

    public boolean isValid() {
        return mIsValid;
    }

}
