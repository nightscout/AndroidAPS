package info.nightscout.androidaps.utils;

/**
 * class contains useful String functions
 */
public class StringUtils {

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
}
