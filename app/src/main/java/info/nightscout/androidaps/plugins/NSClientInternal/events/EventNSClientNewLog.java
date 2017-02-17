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

    public Spanned toHtml() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        Spanned line = Html.fromHtml(timeFormat.format(date) + " <b>" + action + "</b> " + logText + "<br>");
        return line;
    }
}
