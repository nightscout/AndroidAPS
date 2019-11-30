package info.nightscout.androidaps.interaction.utils;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper to minimise various floating point values, with or without unit, to fit into specified
 * and limited size, scarifying precision (rounding up) and extra characters like leading zero,
 * following zero(s) in fractional part, extra plus sign etc.
 *
 * Created by dlvoy on 2019-11-12
 */
public class SmallestDoubleString {

    private String sign = "";
    private String decimal = "";
    private String separator = "";
    private String fractional = "";
    private String extra = "";
    private String units = "";

    private final Units withUnits;

    public enum Units {
        SKIP,
        USE
    }

    private  static Pattern pattern = Pattern.compile("^([+-]?)([0-9]*)([,.]?)([0-9]*)(\\([^)]*\\))?(.*?)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );

    public SmallestDoubleString(String inputString) {
        this(inputString, Units.SKIP);
    }

    public SmallestDoubleString(String inputString, Units withUnits) {
        Matcher matcher = pattern.matcher(inputString);
        matcher.matches();

        sign = matcher.group(1);
        decimal = matcher.group(2);
        separator = matcher.group(3);
        fractional = matcher.group(4);
        units = matcher.group(6);

        if (fractional == null || fractional.length() == 0) {
            separator = "";
            fractional = "";
        }
        if (decimal == null || decimal.length() == 0) {
            decimal = "";
        }
        if (separator == null || separator.length() == 0) {
            separator = "";
        }
        if (sign == null || sign.length() == 0) {
            sign = "";
        }

        final String extraCandidate = matcher.group(5);
        if (extraCandidate != null && extraCandidate.length() > 2) {
            extra = extraCandidate.substring(1, extraCandidate.length()-1);
        }

        if (units != null) {
            units = units.trim();
        }

        this.withUnits = withUnits;
    }

    public String minimise(int maxSize) {
        final String originalSeparator = separator;

        if (Integer.parseInt("0"+fractional) == 0) {
            separator = "";
            fractional = "";
        }
        if (Integer.parseInt("0"+decimal) == 0 && (fractional.length() >0)) {
            decimal = "";
        }
        if (currentLen() <= maxSize)
            return toString();

        if (sign.equals("+")) {
            sign = "";
        }
        if (currentLen() <= maxSize) {
            return toString();
        }

        while ((fractional.length() > 1)&&(fractional.charAt(fractional.length()-1) == '0')) {
            fractional = fractional.substring(0, fractional.length()-1);
        }
        if (currentLen() <= maxSize) {
            return toString();
        }

        if (fractional.length() > 0) {
            int remainingForFraction = maxSize-currentLen()+fractional.length();
            String formatCandidate = "#";
            if (remainingForFraction>=1) {
                formatCandidate = "#."+("#######".substring(0, remainingForFraction));
            }
            DecimalFormat df = new DecimalFormat(formatCandidate);
            df.setRoundingMode(RoundingMode.HALF_UP);

            final String decimalSup = (decimal.length() > 0) ? decimal : "0";
            String result = sign + df.format(Double.parseDouble(decimalSup+"."+fractional)).replace(",", originalSeparator).replace(".", originalSeparator) +
                    ((withUnits == Units.USE) ? units : "");
            return (decimal.length() > 0) ? result : result.substring(1);
        }
        return toString();
    }

    private int currentLen() {
        return sign.length() + decimal.length() + separator.length() + fractional.length() +
                ((withUnits == Units.USE) ? units.length() : 0);
    }

    @Override
    public String toString() {
        return sign+decimal+separator+fractional +
                ((withUnits == Units.USE) ? units : "");
    }

    public String getExtra() {
        return extra;
    }

    public String getUnits() { return units; }


}
