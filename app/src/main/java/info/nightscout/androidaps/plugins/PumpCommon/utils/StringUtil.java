package info.nightscout.androidaps.plugins.PumpCommon.utils;

import org.joda.time.LocalDateTime;

import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.ArrayList;

/**
 * Created by geoff on 4/28/15.
 */
public class StringUtil {


    public static DecimalFormat DecimalFormaters[] = {new DecimalFormat("#0"), new DecimalFormat("#0.0"), new DecimalFormat("#0.00"), new DecimalFormat("#0.000")};


    public static String fromBytes(byte[] ra) {
        return new String(ra, Charset.forName("UTF-8"));
    }


    // these should go in some project-wide string utils package
    public static String join(ArrayList<String> ra, String joiner) {
        int sz = ra.size();
        String rval = "";
        int n;
        for(n = 0; n < sz; n++) {
            rval = rval + ra.get(n);
            if (n < sz - 1) {
                rval = rval + joiner;
            }
        }
        return rval;
    }


    public static String testJoin() {
        ArrayList<String> ra = new ArrayList<String>();
        ra.add("one");
        ra.add("two");
        ra.add("three");
        return join(ra, "+");
    }


    /**
     * Append To StringBuilder
     *
     * @param stringBuilder
     * @param stringToAdd
     * @param delimiter
     * @return
     */
    public static void appendToStringBuilder(StringBuilder stringBuilder, String stringToAdd, String delimiter) {
        if (stringBuilder.length() > 0) {
            stringBuilder.append(delimiter + stringToAdd);
        } else {
            stringBuilder.append(stringToAdd);
        }
    }


    public static String getFormatedValueUS(Number value, int decimals) {
        return DecimalFormaters[decimals].format(value).replace(",", ".");
    }


    public static String getLeadingZero(int number, int places) {
        String nn = "" + number;

        while (nn.length() < places) {
            nn = "0" + nn;
        }

        return nn;
    }


    public static String toDateTimeString(LocalDateTime localDateTime) {
        return localDateTime.toString("dd.MM.yyyy HH:mm:ss");
    }
}
