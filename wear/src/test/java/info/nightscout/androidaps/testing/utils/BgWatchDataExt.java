package info.nightscout.androidaps.testing.utils;

import androidx.annotation.Nullable;

import java.util.Objects;

import info.nightscout.androidaps.data.BgWatchData;

import static info.nightscout.androidaps.testing.utils.ExtUtil.assertClassHaveSameFields;

public class BgWatchDataExt extends BgWatchData {

    private BgWatchDataExt() {
        super();
    }

    public BgWatchDataExt(double aSgv, double aHigh, double aLow, long aTimestamp, int aColor) {
        super(aSgv, aHigh, aLow, aTimestamp, aColor);
    }

    public BgWatchDataExt(BgWatchData ref) {
        super();

        // since we do not want modify BgWatchDataExt - we use this wrapper class
        // but we make sure it has same fields
        assertClassHaveSameFields(BgWatchData.class, "sgv,high,low,timestamp,color");

        this.sgv = ref.sgv;
        this.high = ref.high;
        this.low = ref.low;
        this.timestamp = ref.timestamp;
        this.color = ref.color;
    }

    public static BgWatchDataExt build(double sgv, long timestamp, int color) {
        BgWatchDataExt twd = new BgWatchDataExt();
        twd.sgv = sgv;
        twd.timestamp = timestamp;
        twd.color = color;
        return twd;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if ((obj instanceof BgWatchData)||(obj instanceof BgWatchDataExt)) {
            return (this.sgv == ((BgWatchData) obj).sgv)
                    && (this.high == ((BgWatchData) obj).high)
                    && (this.low == ((BgWatchData) obj).low)
                    && (this.timestamp == ((BgWatchData) obj).timestamp)
                    && (this.color == ((BgWatchData) obj).color);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return sgv+", "+high+", "+low+", "+timestamp+", "+color;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sgv, high, low, timestamp, color);
    }

}
