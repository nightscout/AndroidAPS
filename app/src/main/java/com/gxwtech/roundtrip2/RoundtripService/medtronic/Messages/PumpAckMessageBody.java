package com.gxwtech.roundtrip2.RoundtripService.medtronic.Messages;

/**
 * Created by geoff on 5/29/16.
 */
public class PumpAckMessageBody extends CarelinkShortMessageBody {
    public PumpAckMessageBody() { init(new byte[] {0}); }
    public PumpAckMessageBody(byte[] bodyData) {
        init(bodyData);
    }
}
