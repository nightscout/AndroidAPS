package info.nightscout.androidaps.testing.mockers;

import info.nightscout.androidaps.data.RawDisplayData;
import info.nightscout.androidaps.interaction.utils.WearUtil;
import info.nightscout.shared.SafeParse;

@SuppressWarnings("PointlessArithmeticExpression")
public class RawDataMocker {

    private final WearUtil wearUtil;
    private final WearUtilMocker wearUtilMocker;

    public RawDataMocker(WearUtil wearUtil) {
        this.wearUtil = wearUtil;
        wearUtilMocker = new WearUtilMocker(wearUtil);
    }

    public RawDisplayData rawSgv(String sgv, int m, String deltaString) {
        RawDisplayData raw = new RawDisplayData(wearUtil);
        raw.datetime = wearUtilMocker.backInTime(0, 0, m, 0);
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

    public RawDisplayData rawDelta(int m, String delta) {
        RawDisplayData raw = new RawDisplayData(wearUtil);
        raw.datetime = wearUtilMocker.backInTime(0, 0, m, 0);
        raw.sDelta = delta;
        return raw;
    }

    public RawDisplayData rawCobIobBr(String cob, String iob, String br) {
        RawDisplayData raw = new RawDisplayData(wearUtil);
        raw.sCOB2 = cob;
        raw.sIOB1 = iob;
        raw.sBasalRate = br;
        return raw;
    }

    public RawDisplayData rawIob(String iob, String iob2) {
        RawDisplayData raw = new RawDisplayData(wearUtil);
        raw.sIOB1 = iob;
        raw.sIOB2 = iob2;
        return raw;
    }

    public RawDisplayData rawCob(String cob) {
        RawDisplayData raw = new RawDisplayData(wearUtil);
        raw.sCOB2 = cob;
        return raw;
    }

}
