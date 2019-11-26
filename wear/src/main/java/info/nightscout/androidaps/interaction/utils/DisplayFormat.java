package info.nightscout.androidaps.interaction.utils;

import info.nightscout.androidaps.aaps;
import info.nightscout.androidaps.data.DisplayRawData;

public class DisplayFormat {

    /**
     * Maximal lengths of fields/labels shown in complications
     */
    public static final int MAX_LONG_FIELD = 22; // this is empirical, above that many watch faces start to ellipsize
    public static final int MAX_SHORT_FIELD = 7; // according to Wear OS docs for TYPE_SHORT_TEXT
    public static final int MIN_COB_FIELD = 3;   // since carbs are 0..99g
    public static final int MIN_IOB_FIELD = 3;   // IoB can range from like .1U to 99U

    public static String deltaSymbol() {
        return aaps.areComplicationsUnicode() ? "\u0394" : "";
    }

    public static String verticalSeparatorSymbol() {
        return aaps.areComplicationsUnicode() ? "\u205E" : "|";
    }

    public static String basalRateSymbol() {
        return aaps.areComplicationsUnicode() ? "\u238D\u2006" : "";
    }

    public static String shortTimeSince(final long refTime) {

        long deltaTimeMs = WearUtil.msSince(refTime);

        if (deltaTimeMs < Constants.MINUTE_IN_MS) {
            return "0'";
        } else if (deltaTimeMs < Constants.HOUR_IN_MS) {
            int minutes = (int) (deltaTimeMs / Constants.MINUTE_IN_MS);
            return minutes + "'";
        } else if (deltaTimeMs < Constants.DAY_IN_MS) {
            int hours = (int) (deltaTimeMs / Constants.HOUR_IN_MS);
            return hours + "h";
        } else {
            int days = (int) (deltaTimeMs / Constants.DAY_IN_MS);
            if (days < 7) {
                return days + "d";
            } else {
                int weeks = days / 7;
                return weeks + "w";
            }
        }
    }

    public static String shortTrend(final DisplayRawData raw) {
        String minutes = "--";
        if (raw.datetime > 0) {
            minutes = shortTimeSince(raw.datetime);
        }

        if (minutes.length() + raw.sDelta.length() + deltaSymbol().length() + 1 <= MAX_SHORT_FIELD) {
            return minutes + " " + deltaSymbol() + raw.sDelta;
        }

        // that only optimizes obvious things like 0 before . or at end, + at beginning
        String delta = (new SmallestDoubleString(raw.sDelta)).minimise(MAX_SHORT_FIELD-1);
        if (minutes.length() + delta.length() + deltaSymbol().length() + 1 <= MAX_SHORT_FIELD) {
            return minutes + " " + deltaSymbol() + delta;
        }

        String shortDelta = (new SmallestDoubleString(raw.sDelta)).minimise(MAX_SHORT_FIELD-(1+minutes.length()));

        return minutes + " " + shortDelta;
    }

    public static String longGlucoseLine(final DisplayRawData raw) {
        return raw.sSgv + raw.sDirection + " " + deltaSymbol() + (new SmallestDoubleString(raw.sDelta)).minimise(8) + " (" + shortTimeSince(raw.datetime) + ")";
    }

    public static String longDetailsLine(final DisplayRawData raw) {

        final String SEP_LONG = "  " + verticalSeparatorSymbol() + "  ";
        final String SEP_SHORT = " " + verticalSeparatorSymbol() + " ";
        final int SEP_SHORT_LEN = SEP_SHORT.length();
        final String SEP_MIN = " ";

        String line = raw.sCOB2 + SEP_LONG + raw.sIOB1 + SEP_LONG + basalRateSymbol()+raw.sBasalRate;
        if (line.length() <= MAX_LONG_FIELD) {
            return line;
        }
        line = raw.sCOB2 + SEP_SHORT + raw.sIOB1 + SEP_SHORT + raw.sBasalRate;
        if (line.length() <= MAX_LONG_FIELD) {
            return line;
        }

        int remainingMax = MAX_LONG_FIELD - (raw.sCOB2.length() + raw.sBasalRate.length() + SEP_SHORT_LEN*2);
        final String smallestIoB = new SmallestDoubleString(raw.sIOB1, SmallestDoubleString.Units.USE).minimise(Math.max(MIN_IOB_FIELD, remainingMax));
        line = raw.sCOB2 + SEP_SHORT + smallestIoB + SEP_SHORT + raw.sBasalRate;
        if (line.length() <= MAX_LONG_FIELD) {
            return line;
        }

        remainingMax = MAX_LONG_FIELD - (smallestIoB.length() + raw.sBasalRate.length() + SEP_SHORT_LEN*2);
        final String simplifiedCob = new SmallestDoubleString(raw.sCOB2, SmallestDoubleString.Units.USE).minimise(Math.max(MIN_COB_FIELD, remainingMax));

        line = simplifiedCob + SEP_SHORT + smallestIoB + SEP_SHORT + raw.sBasalRate;
        if (line.length() <= MAX_LONG_FIELD) {
            return line;
        }

        line = simplifiedCob + SEP_MIN + smallestIoB + SEP_MIN + raw.sBasalRate;

        return line;
    }

    public static Pair<String, String> detailedIob(DisplayRawData raw) {
        final String iob1 = new SmallestDoubleString(raw.sIOB1, SmallestDoubleString.Units.USE).minimise(MAX_SHORT_FIELD);
        String iob2 = "";
        if (raw.sIOB2.contains("|")) {
            String[] iobs = raw.sIOB2.replace("(", "").replace(")", "").split("\\|");

            String iobBolus = new SmallestDoubleString(iobs[0]).minimise(MIN_IOB_FIELD);
            if (iobBolus.trim().length() == 0) {
                iobBolus = "--";
            }
            String iobBasal = new SmallestDoubleString(iobs[1]).minimise((MAX_SHORT_FIELD-1) - Math.max(MIN_IOB_FIELD, iobBolus.length()));
            if (iobBasal.trim().length() == 0) {
                iobBasal = "--";
            }
            iob2 = iobBolus+" "+iobBasal;
        }
        return Pair.create(iob1, iob2);
    }

    public static Pair<String, String> detailedCob(final DisplayRawData raw) {
        SmallestDoubleString cobMini = new SmallestDoubleString(raw.sCOB2, SmallestDoubleString.Units.USE);

        String cob2 = "";
        if (cobMini.getExtra().length() > 0) {
            cob2 = cobMini.getExtra() + cobMini.getUnits();
        }
        final String cob1 = cobMini.minimise(MAX_SHORT_FIELD);
        return Pair.create(cob1, cob2);
    }
}
