package info.nightscout.androidaps.data;


import androidx.annotation.Nullable;

import info.nightscout.androidaps.interfaces.Interval;

/**
 * Created by adrian on 15/07/17.
 */

public class OverlappingIntervals<T extends Interval> extends Intervals<T> {

    public OverlappingIntervals() {
        super();
    }

    public OverlappingIntervals(Intervals<T> other) {
        rawData = other.rawData.clone();
    }

    protected synchronized void merge() {
        boolean needToCut = false;
        long cutTime = 0;

        for (int index = rawData.size() - 1; index >= 0; index--) { //begin with newest
            Interval cur = rawData.valueAt(index);
            if (cur.isEndingEvent()) {
                needToCut = true;
                cutTime = cur.start();
            } else {
                //event that is no EndingEvent might need to be stopped by an ending event
                if (needToCut && cur.end() > cutTime) {
                    cur.cutEndTo(cutTime);
                }
            }
        }
    }

    @Nullable
    public synchronized T getValueByInterval(long time) {
        for (int index = rawData.size() - 1; index >= 0; index--) { //begin with newest
            T cur = rawData.valueAt(index);
            if (cur.match(time)) {
                return cur;
            }
        }
        return null;
    }

}
