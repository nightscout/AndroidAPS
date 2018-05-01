package com.gxwtech.roundtrip2.RoundtripService.medtronic.Messages;

/**
 * Created by geoff on 5/29/16.
 */
public class GetPumpModelCarelinkMessageBody extends MessageBody {

    public int getLength() {
        return 1;
    }

    public void init(byte[] rxData) {

    }

    public byte[] getRxData() {
        return new byte[] { 0 };
    }

    public void setRxData(byte[] rxData) {

    }

    public byte[] getTxData() {
        return new byte[] { 0 };
    }

    public void setTxData(byte[] txData) {

    }
}
