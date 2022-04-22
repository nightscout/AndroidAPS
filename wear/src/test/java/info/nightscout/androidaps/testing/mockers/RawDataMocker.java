package info.nightscout.androidaps.testing.mockers;

import info.nightscout.androidaps.data.RawDisplayData;
import info.nightscout.androidaps.interaction.utils.WearUtil;
import info.nightscout.shared.SafeParse;
import info.nightscout.shared.weardata.EventData;

@SuppressWarnings("PointlessArithmeticExpression")
public class RawDataMocker {

    private final WearUtilMocker wearUtilMocker;

    public RawDataMocker(WearUtil wearUtil) {
        wearUtilMocker = new WearUtilMocker(wearUtil);
    }

    public RawDisplayData rawSgv(String sgv, int m, String deltaString) {
        RawDisplayData raw = new RawDisplayData();
        double delta = SafeParse.stringToDouble(deltaString);
        String d;

        if (delta <= (-3.5 * 5)) {
            d = "\u21ca";
        } else if (delta <= (-2 * 5)) {
            d = "\u2193";
        } else if (delta <= (-1 * 5)) {
            d = "\u2198";
        } else if (delta <= (1 * 5)) {
            d = "\u2192";
        } else if (delta <= (2 * 5)) {
            d = "\u2197";
        } else if (delta <= (3.5 * 5)) {
            d = "\u2191";
        } else {
            d = "\u21c8";
        }
        raw.setSingleBg(
                new EventData.SingleBg(
                        wearUtilMocker.backInTime(0, 0, m, 0),
                        sgv,
                        "",
                        d,
                        deltaString,
                        "",
                        0,
                        0.0,
                        0.0,
                        0.0,
                        0
                )
        );
        return raw;
    }

    public RawDisplayData rawDelta(int m, String delta) {
        RawDisplayData raw = new RawDisplayData();
        raw.setSingleBg(
                new EventData.SingleBg(
                        wearUtilMocker.backInTime(0, 0, m, 0),
                        "",
                        "",
                        "",
                        delta,
                        "",
                        0,
                        0.0,
                        0.0,
                        0.0,
                        0
                )
        );
        return raw;
    }

    public RawDisplayData rawCobIobBr(String cob, String iob, String br) {
        RawDisplayData raw = new RawDisplayData();
        raw.setStatus(
                new EventData.Status(
                        "",
                        iob,
                        "",
                        true,
                        cob,
                        br,
                        "",
                        "",
                        0L,
                        "",
                        true,
                        0

                )
        );
        return raw;
    }

    public RawDisplayData rawIob(String iob, String iob2) {
        RawDisplayData raw = new RawDisplayData();
        raw.setStatus(
                new EventData.Status(
                        "",
                        iob,
                        iob2,
                        true,
                        "",
                        "",
                        "",
                        "",
                        0L,
                        "",
                        true,
                        0

                )
        );
        return raw;
    }

    public RawDisplayData rawCob(String cob) {
        RawDisplayData raw = new RawDisplayData();
        raw.setStatus(
                new EventData.Status(
                        "",
                        "",
                        "",
                        true,
                        cob,
                        "",
                        "",
                        "",
                        0L,
                        "",
                        true,
                        0

                )
        );
        return raw;
    }

}
