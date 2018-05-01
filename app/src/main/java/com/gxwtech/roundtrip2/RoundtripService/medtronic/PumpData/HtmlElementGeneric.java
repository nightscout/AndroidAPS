package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData;

/**
 * Created by geoff on 6/24/16.
 */
public class HtmlElementGeneric extends HtmlElement {
    String s = "";
    public HtmlElementGeneric() {}
    public HtmlElementGeneric(String s) {
        this.s = s;
    }
    public String toString() {
        return s;
    }
}
