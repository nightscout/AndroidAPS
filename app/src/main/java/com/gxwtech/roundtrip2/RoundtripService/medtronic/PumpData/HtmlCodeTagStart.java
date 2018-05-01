package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData;

/**
 * Created by geoff on 6/24/16.
 */
public class HtmlCodeTagStart extends HtmlElement {
    String title = "";
    String color = "";
    public HtmlCodeTagStart(String title, String bgColor) {
        this.title = title;
        this.color = bgColor;
    }
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("<code");

        if ((title != null) && (title.length() > 0)) {
            b.append(" title=\"");
            b.append(title);
            b.append("\"");
        }
        if ((color != null) && (color.length() > 0)) {
            b.append(" style=\"background-color:");
            b.append(color);
            b.append(";\"");
        }
        b.append(">");
        return b.toString();
    }
}
