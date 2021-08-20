package info.nightscout.androidaps.interaction.utils;

import info.nightscout.androidaps.Aaps;
import info.nightscout.androidaps.data.RawDisplayData;

public class DisplayFormat {

    /**
     * Maximal and minimal lengths of fields/labels shown in complications, in characters
     * For MAX values - above that WearOS and watch faces may start ellipsize (...) contents
     * For MIN values - this is minimal length that can hold legible data
     */
    public static final int MAX_FIELD_LEN_LONG = 22; // this is found out empirical, for TYPE_LONG_TEXT
    public static final int MAX_FIELD_LEN_SHORT = 7; // according to Wear OS docs for TYPE_SHORT_TEXT
    public static final int MIN_FIELD_LEN_COB = 3;   // since carbs are usually 0..99g
    public static final int MIN_FIELD_LEN_IOB = 3;   // IoB can range from like .1U to 99U

    public static String deltaSymbol() {
        return Aaps.areComplicationsUnicode() ? "\u0394" : "";
    }

    public static String verticalSeparatorSymbol() {
        return Aaps.areComplicationsUnicode() ? "\u205E" : "|";
    }

    public static String basalRateSymbol() {
        return Aaps.areComplicationsUnicode() ? "\u238D\u2006" : "";
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

    public static String shortTrend(final RawDisplayData raw) {
        String minutes = "--";
        if (raw.datetime > 0) {
            minutes = shortTimeSince(raw.datetime);
        }

        if (minutes.length() + raw.sDelta.length() + deltaSymbol().length() + 1 <= MAX_FIELD_LEN_SHORT) {
            return minutes + " " + deltaSymbol() + raw.sDelta;
        }

        // that only optimizes obvious things like 0 before . or at end, + at beginning
        String delta = (new SmallestDoubleString(raw.sDelta)).minimise(MAX_FIELD_LEN_SHORT -1);
        if (minutes.length() + delta.length() + deltaSymbol().length() + 1 <= MAX_FIELD_LEN_SHORT) {
            return minutes + " " + deltaSymbol() + delta;
        }

        String shortDelta = (new SmallestDoubleString(raw.sDelta)).minimise(MAX_FIELD_LEN_SHORT -(1+minutes.length()));

        return minutes + " " + shortDelta;
    }

    public static String longGlucoseLine(final RawDisplayData raw) {
        return raw.sSgv + raw.sDirection + " " + deltaSymbol() + (new SmallestDoubleString(raw.sDelta)).minimise(8) + " (" + shortTimeSince(raw.datetime) + ")";
    }

    public static String longDetailsLine(final RawDisplayData raw) {

        final String SEP_LONG = "  " + verticalSeparatorSymbol() + "  ";
        final String SEP_SHORT = " " + verticalSeparatorSymbol() + " ";
        final int SEP_SHORT_LEN = SEP_SHORT.length();
        final String SEP_MIN = " ";

        String line = raw.sCOB2 + SEP_LONG + raw.sIOB1 + SEP_LONG + basalRateSymbol()+raw.sBasalRate;
        if (line.length() <= MAX_FIELD_LEN_LONG) {
            return line;
        }
        line = raw.sCOB2 + SEP_SHORT + raw.sIOB1 + SEP_SHORT + raw.sBasalRate;
        if (line.length() <= MAX_FIELD_LEN_LONG) {
            return line;
        }

        int remainingMax = MAX_FIELD_LEN_LONG - (raw.sCOB2.length() + raw.sBasalRate.length() + SEP_SHORT_LEN*2);
        final String smallestIoB = new SmallestDoubleString(raw.sIOB1, SmallestDoubleString.Units.USE).minimise(Math.max(MIN_FIELD_LEN_IOB, remainingMax));
        line = raw.sCOB2 + SEP_SHORT + smallestIoB + SEP_SHORT + raw.sBasalRate;
        if (line.length() <= MAX_FIELD_LEN_LONG) {
            return line;
        }

        remainingMax = MAX_FIELD_LEN_LONG - (smallestIoB.length() + raw.sBasalRate.length() + SEP_SHORT_LEN*2);
        final String simplifiedCob = new SmallestDoubleString(raw.sCOB2, SmallestDoubleString.Units.USE).minimise(Math.max(MIN_FIELD_LEN_COB, remainingMax));

        line = simplifiedCob + SEP_SHORT + smallestIoB + SEP_SHORT + raw.sBasalRate;
        if (line.length() <= MAX_FIELD_LEN_LONG) {
            return line;
        }

        line = simplifiedCob + SEP_MIN + smallestIoB + SEP_MIN + raw.sBasalRate;

        return line;
    }

    public static Pair<String, String> detailedIob(RawDisplayData raw) {
        final String iob1 = new SmallestDoubleString(raw.sIOB1, SmallestDoubleString.Units.USE).minimise(MAX_FIELD_LEN_SHORT);
        String iob2 = "";
        if (raw.sIOB2.contains("|")) {
            String[] iobs = raw.sIOB2.replace("(", "").replace(")", "").split("\\|");

            String iobBolus = new SmallestDoubleString(iobs[0]).minimise(MIN_FIELD_LEN_IOB);
            if (iobBolus.trim().length() == 0) {
                iobBolus = "--";
            }
            String iobBasal = new SmallestDoubleString(iobs[1]).minimise((MAX_FIELD_LEN_SHORT -1) - Math.max(MIN_FIELD_LEN_IOB, iobBolus.length()));
            if (iobBasal.trim().length() == 0) {
                iobBasal = "--";
            }
            iob2 = iobBolus+" "+iobBasal;
        }
        return Pair.create(iob1, iob2);
    }

    public static Pair<String, String> detailedCob(final RawDisplayData raw) {
        SmallestDoubleString cobMini = new SmallestDoubleString(raw.sCOB2, SmallestDoubleString.Units.USE);

        String cob2 = "";
        if (cobMini.getExtra().length() > 0) {
            cob2 = cobMini.getExtra() + cobMini.getUnits();
        }
        final String cob1 = cobMini.minimise(MAX_FIELD_LEN_SHORT);
        return Pair.create(cob1, cob2);
    }
}
