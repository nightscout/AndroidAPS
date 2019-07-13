package info.nightscout.androidaps.utils;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * class contains useful String functions
 */
public class StringUtils {

    private StringUtils() {
        // this constructor is private, since this class should not get instantiated
    }

    public static String removeSurroundingQuotes(String string) {
        if (string.length() >= 2 && string.charAt(0) == '"'
                && string.charAt(string.length() - 1) == '"') {
            string = string.substring(1, string.length() - 1);
        }

        return string;
    }

    public static boolean emptyString(final String str) {
        return str == null || str.length() == 0;
    }

    public static String formatInsulin(double insulin) {
        return String.format(MainApp.gs(R.string.formatinsulinunits), insulin);
    }
}
