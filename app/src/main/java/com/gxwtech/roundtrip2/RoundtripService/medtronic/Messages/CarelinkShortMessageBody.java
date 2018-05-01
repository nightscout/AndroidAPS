package com.gxwtech.roundtrip2.RoundtripService.medtronic.Messages;

/**
 * Created by geoff on 5/29/16.
 */
public class CarelinkShortMessageBody extends MessageBody {
    byte[] body;

    public int getLength() {
        return body.length;
    }

    public CarelinkShortMessageBody() { init(new byte[] {0}); }

    public CarelinkShortMessageBody(byte[] data) {
        init(data);
    }

    public void init(byte[] rxData) {
        body = rxData;
    }

    public byte[] getRxData() {
        return body;
    }

    public void setRxData(byte[] rxData) {
        init(rxData);
    }

    public byte[] getTxData() {
        return body;
    }

    public void setTxData(byte[] txData) {
        init(txData);
    }
}
