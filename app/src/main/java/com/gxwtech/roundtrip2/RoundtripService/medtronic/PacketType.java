package com.gxwtech.roundtrip2.RoundtripService.medtronic;

/**
 * Created by geoff on 5/29/16.
 */
public class PacketType {
    public static final short Invalid = 0x00;
    public static final short MySentry = 0xa2;
    public static final short Meter = 0xa5;
    public static final short Carelink = 0xa7;
    public static final short Sensor = 0xa8;

    public short value = 0;
    public PacketType() {
    }
    public PacketType(short value) {
        this.value = value;
    }

}
