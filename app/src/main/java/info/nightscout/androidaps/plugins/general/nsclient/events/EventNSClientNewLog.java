package info.nightscout.androidaps.plugins.general.nsclient.events;

import java.text.SimpleDateFormat;
import java.util.Date;

import info.nightscout.androidaps.events.Event;

/**
 * Created by mike on 15.02.2017.
 */

public class EventNSClientNewLog extends Event {
    public Date date = new Date();
    public String action;
    public String logText;
    public EventNSClientNewLog(String action, String logText) {
        this.action = action;
        this.logText = logText;
    }

    SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public StringBuilder toPreparedHtml() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(timeFormat.format(date));
        stringBuilder.append(" <b>");
        stringBuilder.append(action);
        stringBuilder.append("</b> ");
        stringBuilder.append(logText);
        stringBuilder.append("<br>");
        return stringBuilder;
    }
}
