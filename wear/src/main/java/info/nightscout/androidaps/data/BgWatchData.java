package info.nightscout.androidaps.data;

import java.util.Objects;

/**
 * Created by emmablack on 1/7/15.
 */
public class BgWatchData implements Comparable<BgWatchData>{
    public double sgv;
    public double high;
    public double low;
    public long timestamp;
    public int color;

    public BgWatchData(double aSgv, double aHigh, double aLow, long aTimestamp, int aColor) {
        this.sgv = aSgv;
        this.high = aHigh;
        this.low = aLow;
        this.timestamp = aTimestamp;
        this.color = aColor;
    }

    public BgWatchData(){

    }

    @Override
    public boolean equals(Object that){
        if(! (that instanceof BgWatchData)){
            return  false;
        }
        if (this.color != ((BgWatchData) that).color)
            return false;
        return this.timestamp == ((BgWatchData) that).timestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, color);
    }

    @Override
    public int compareTo(BgWatchData that) {
        // reverse order endTime get latest first
        if(this.timestamp < that.timestamp) return 1;
        if(this.timestamp > that.timestamp) return  -1;
        return 0;
    }
}
