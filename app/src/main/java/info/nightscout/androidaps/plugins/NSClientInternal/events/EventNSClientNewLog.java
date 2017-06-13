package info.nightscout.androidaps.plugins.NSClientInternal.events;

import android.text.Html;
import android.text.Spanned;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by mike on 15.02.2017.
 */

public class EventNSClientNewLog {
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
