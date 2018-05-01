package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData;

/**
 * Created by geoff on 6/24/16.
 */
public class HtmlHistoryPageStart extends HtmlElement {
    int pagenum = 0;
    public HtmlHistoryPageStart() {}
    public HtmlHistoryPageStart(int num) {
        pagenum = num;
    }
    public String toString() {
        return "<h2>History Page " + pagenum + "</h2>\n";
    }
}
