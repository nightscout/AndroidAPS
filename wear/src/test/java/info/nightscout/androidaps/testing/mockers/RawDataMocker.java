package info.nightscout.androidaps.testing.mockers;

import info.nightscout.androidaps.data.DisplayRawData;
import info.nightscout.androidaps.interaction.utils.SafeParse;

import static info.nightscout.androidaps.testing.mockers.WearUtilMocker.backInTime;

public class RawDataMocker {

    public static DisplayRawData rawSgv(String sgv, int m, String deltaString) {
        DisplayRawData raw = new DisplayRawData();
        raw.datetime = backInTime(0, 0, m, 0);
        raw.sDelta = deltaString;
        raw.sSgv = sgv;

        double delta = SafeParse.stringToDouble(deltaString);

        if (delta <= (-3.5 * 5)) {
            raw.sDirection = "\u21ca";
        } else if (delta <= (-2 * 5)) {
            raw.sDirection = "\u2193";
        } else if (delta <= (-1 * 5)) {
            raw.sDirection = "\u2198";
        } else if (delta <= (1 * 5)) {
            raw.sDirection = "\u2192";
        } else if (delta <= (2 * 5)) {
            raw.sDirection = "\u2197";
        } else if (delta <= (3.5 * 5)) {
            raw.sDirection = "\u2191";
        } else {
            raw.sDirection = "\u21c8";
        }

        return raw;
    }

    public static DisplayRawData rawDelta(int m, String delta) {
        DisplayRawData raw = new DisplayRawData();
        raw.datetime = backInTime(0, 0, m, 0);
        raw.sDelta = delta;
        return raw;
    }

    public static DisplayRawData rawCobIobBr(String cob, String iob, String br) {
        DisplayRawData raw = new DisplayRawData();
        raw.sCOB2 = cob;
        raw.sIOB1 = iob;
        raw.sBasalRate = br;
        return raw;
    }

    public static DisplayRawData rawIob(String iob, String iob2) {
        DisplayRawData raw = new DisplayRawData();
        raw.sIOB1 = iob;
        raw.sIOB2 = iob2;
        return raw;
    }

    public static DisplayRawData rawCob(String cob) {
        DisplayRawData raw = new DisplayRawData();
        raw.sCOB2 = cob;
        return raw;
    }

}
