package info.nightscout.core.graph.data;

import com.jjoe64.graphview.DefaultLabelFormatter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Created by mike on 09.06.2016.
 */
public class TimeAsXAxisLabelFormatter extends DefaultLabelFormatter {

    protected final String mFormat;

    public TimeAsXAxisLabelFormatter(String format) {
        mFormat = format;
    }

    @Override
    public String formatLabel(double value, boolean isValueX) {
        if (isValueX) {
            // format as date
            DateFormat dateFormat = new SimpleDateFormat(mFormat, Locale.getDefault());
            return dateFormat.format((long) value);
        } else {
            try {
                // unknown reason for crashing on this
                //                Fatal Exception: java.lang.NullPointerException
                //                Attempt to invoke virtual method 'double com.jjoe64.graphview.Viewport.getMaxY(boolean)' on a null object reference
                //                com.jjoe64.graphview.DefaultLabelFormatter.formatLabel (DefaultLabelFormatter.java:89)
                //                info.nightscout.core.graph.data.TimeAsXAxisLabelFormatter.formatLabel (TimeAsXAxisLabelFormatter.java:26)
                //                com.jjoe64.graphview.GridLabelRenderer.drawVerticalSteps (GridLabelRenderer.java:1057)
                //                com.jjoe64.graphview.GridLabelRenderer.draw (GridLabelRenderer.java:866)
                //                com.jjoe64.graphview.GraphView.onDraw (GraphView.java:296)
                //noinspection ConstantConditions
                return super.formatLabel(value, isValueX);
            } catch (Exception ignored) {
                return "";
            }
        }
    }
}
