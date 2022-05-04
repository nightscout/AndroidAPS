package info.nightscout.androidaps.interaction.utils;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.data.RawDisplayData;
import info.nightscout.shared.sharedPreferences.SP;

@Singleton
public class DisplayFormat {

    @Inject SP sp;
    @Inject WearUtil wearUtil;

    @Inject DisplayFormat() {
    }

    /**
     * Maximal and minimal lengths of fields/labels shown in complications, in characters
     * For MAX values - above that WearOS and watch faces may start ellipsize (...) contents
     * For MIN values - this is minimal length that can hold legible data
     */
    public final int MAX_FIELD_LEN_LONG = 22; // this is found out empirical, for TYPE_LONG_TEXT
    public final int MAX_FIELD_LEN_SHORT = 7; // according to Wear OS docs for TYPE_SHORT_TEXT
    public final int MIN_FIELD_LEN_COB = 3;   // since carbs are usually 0..99g
    public final int MIN_FIELD_LEN_IOB = 3;   // IoB can range from like .1U to 99U

    private boolean areComplicationsUnicode() {
        return sp.getBoolean("complication_unicode", true);
    }

    public String deltaSymbol() {
        return areComplicationsUnicode() ? "\u0394" : "";
    }

    public String verticalSeparatorSymbol() {
        return areComplicationsUnicode() ? "\u205E" : "|";
    }

    public String basalRateSymbol() {
        return areComplicationsUnicode() ? "\u238D\u2006" : "";
    }

    public String shortTimeSince(final long refTime) {

        long deltaTimeMs = wearUtil.msSince(refTime);

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

    public String shortTrend(final RawDisplayData raw) {
        String minutes = "--";
        if (raw.getSingleBg().getTimeStamp() > 0) {
            minutes = shortTimeSince(raw.getSingleBg().getTimeStamp());
        }

        if (minutes.length() + raw.getSingleBg().getDelta().length() + deltaSymbol().length() + 1 <= MAX_FIELD_LEN_SHORT) {
            return minutes + " " + deltaSymbol() + raw.getSingleBg().getDelta();
        }

        // that only optimizes obvious things like 0 before . or at end, + at beginning
        String delta = (new SmallestDoubleString(raw.getSingleBg().getDelta())).minimise(MAX_FIELD_LEN_SHORT - 1);
        if (minutes.length() + delta.length() + deltaSymbol().length() + 1 <= MAX_FIELD_LEN_SHORT) {
            return minutes + " " + deltaSymbol() + delta;
        }

        String shortDelta = (new SmallestDoubleString(raw.getSingleBg().getDelta())).minimise(MAX_FIELD_LEN_SHORT - (1 + minutes.length()));

        return minutes + " " + shortDelta;
    }

    public String longGlucoseLine(final RawDisplayData raw) {
        return raw.getSingleBg().getSgvString() + raw.getSingleBg().getSlopeArrow() + " " + deltaSymbol() + (new SmallestDoubleString(raw.getSingleBg().getDelta())).minimise(8) + " (" + shortTimeSince(raw.getSingleBg().getTimeStamp()) + ")";
    }

    public String longDetailsLine(final RawDisplayData raw) {

        final String SEP_LONG = "  " + verticalSeparatorSymbol() + "  ";
        final String SEP_SHORT = " " + verticalSeparatorSymbol() + " ";
        final int SEP_SHORT_LEN = SEP_SHORT.length();
        final String SEP_MIN = " ";

        String line =
                raw.getStatus().getCob() + SEP_LONG + raw.getStatus().getIobSum() + SEP_LONG + basalRateSymbol() + raw.getStatus().getCurrentBasal();
        if (line.length() <= MAX_FIELD_LEN_LONG) {
            return line;
        }
        line = raw.getStatus().getCob() + SEP_SHORT + raw.getStatus().getIobSum() + SEP_SHORT + raw.getStatus().getCurrentBasal();
        if (line.length() <= MAX_FIELD_LEN_LONG) {
            return line;
        }

        int remainingMax = MAX_FIELD_LEN_LONG - (raw.getStatus().getCob().length() + raw.getStatus().getCurrentBasal().length() + SEP_SHORT_LEN * 2);
        final String smallestIoB = new SmallestDoubleString(raw.getStatus().getIobSum(), SmallestDoubleString.Units.USE).minimise(Math.max(MIN_FIELD_LEN_IOB, remainingMax));
        line = raw.getStatus().getCob() + SEP_SHORT + smallestIoB + SEP_SHORT + raw.getStatus().getCurrentBasal();
        if (line.length() <= MAX_FIELD_LEN_LONG) {
            return line;
        }

        remainingMax = MAX_FIELD_LEN_LONG - (smallestIoB.length() + raw.getStatus().getCurrentBasal().length() + SEP_SHORT_LEN * 2);
        final String simplifiedCob = new SmallestDoubleString(raw.getStatus().getCob(), SmallestDoubleString.Units.USE).minimise(Math.max(MIN_FIELD_LEN_COB, remainingMax));

        line = simplifiedCob + SEP_SHORT + smallestIoB + SEP_SHORT + raw.getStatus().getCurrentBasal();
        if (line.length() <= MAX_FIELD_LEN_LONG) {
            return line;
        }

        line = simplifiedCob + SEP_MIN + smallestIoB + SEP_MIN + raw.getStatus().getCurrentBasal();

        return line;
    }

    public Pair<String, String> detailedIob(RawDisplayData raw) {
        final String iob1 = new SmallestDoubleString(raw.getStatus().getIobSum(), SmallestDoubleString.Units.USE).minimise(MAX_FIELD_LEN_SHORT);
        String iob2 = "";
        if (raw.getStatus().getIobDetail().contains("|")) {
            String[] iobs = raw.getStatus().getIobDetail().replace("(", "").replace(")", "").split("\\|");

            String iobBolus = new SmallestDoubleString(iobs[0]).minimise(MIN_FIELD_LEN_IOB);
            if (iobBolus.trim().length() == 0) {
                iobBolus = "--";
            }
            String iobBasal = new SmallestDoubleString(iobs[1]).minimise((MAX_FIELD_LEN_SHORT - 1) - Math.max(MIN_FIELD_LEN_IOB, iobBolus.length()));
            if (iobBasal.trim().length() == 0) {
                iobBasal = "--";
            }
            iob2 = iobBolus + " " + iobBasal;
        }
        return Pair.Companion.create(iob1, iob2);
    }

    public Pair<String, String> detailedCob(final RawDisplayData raw) {
        SmallestDoubleString cobMini = new SmallestDoubleString(raw.getStatus().getCob(), SmallestDoubleString.Units.USE);

        String cob2 = "";
        if (cobMini.getExtra().length() > 0) {
            cob2 = cobMini.getExtra() + cobMini.getUnits();
        }
        final String cob1 = cobMini.minimise(MAX_FIELD_LEN_SHORT);
        return Pair.Companion.create(cob1, cob2);
    }
}
