package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData;

/**
 * Created by geoff on 6/24/16.
 */
public class HtmlByte extends HtmlElement {
    private byte data = 0;
    public HtmlByte() {}
    public HtmlByte(byte b) { data = b; }
    public String toString() {return String.format("%02x",data);}
}
