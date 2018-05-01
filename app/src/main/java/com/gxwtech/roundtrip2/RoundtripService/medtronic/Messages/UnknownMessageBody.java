package com.gxwtech.roundtrip2.RoundtripService.medtronic.Messages;

/**
 * Created by geoff on 5/29/16.
 */
public class UnknownMessageBody extends MessageBody {
    public byte[] rxData;

    public int getLength() {
        return 0;
    }

    public UnknownMessageBody(byte[] data) {
        this.rxData = data;
    }

    public void init(byte[] rxData) {
    }

    public byte[] getRxData() {
        return rxData;
    }

    public void setRxData(byte[] rxData) {
        this.rxData = rxData;
    }

    public byte[] getTxData() {
        return rxData;
    }

    public void setTxData(byte[] txData) {
        this.rxData = txData;
    }
}
