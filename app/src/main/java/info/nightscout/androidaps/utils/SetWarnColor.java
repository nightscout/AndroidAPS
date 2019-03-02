package info.nightscout.androidaps.utils;

import android.graphics.Color;
import android.widget.TextView;

/**
 * Created by mike on 08.07.2016.
 */
public class SetWarnColor {
    static final int normalColor = Color.WHITE;
    static final int warnColor = Color.YELLOW;
    static final int urgentColor = Color.RED;

    public static void setColor(TextView view, double value, double warnLevel, double urgentLevel) {
        if (value >= urgentLevel) view.setTextColor(urgentColor);
        else if (value >= warnLevel) view.setTextColor(warnColor);
        else view.setTextColor(normalColor);
    }

    public static void setColorInverse(TextView view, double value, double warnLevel, double urgentLevel) {
        if (value <= urgentLevel) view.setTextColor(urgentColor);
        else if (value <= warnLevel) view.setTextColor(warnColor);
        else view.setTextColor(normalColor);
    }
}
