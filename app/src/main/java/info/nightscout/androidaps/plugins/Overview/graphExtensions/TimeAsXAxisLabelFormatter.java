package info.nightscout.androidaps.plugins.Overview.graphExtensions;

import android.content.Context;

import com.jjoe64.graphview.DefaultLabelFormatter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by mike on 09.06.2016.
 */
public class TimeAsXAxisLabelFormatter extends DefaultLabelFormatter {

    protected final String mFormat;

    public TimeAsXAxisLabelFormatter(Context context, String format) {
        mFormat = format;
    }

    @Override
    public String formatLabel(double value, boolean isValueX) {
        if (isValueX) {
            // format as date
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis((long) value);
            DateFormat dateFormat = new SimpleDateFormat(mFormat);
            return dateFormat.format(calendar.getTimeInMillis());
        } else {
            return super.formatLabel(value, isValueX);
        }
    }
}
