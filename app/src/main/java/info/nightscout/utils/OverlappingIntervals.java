package info.nightscout.utils;

import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.interfaces.Interval;

/**
 * Created by mike on 09.05.2017.
 */

public class OverlappingIntervals {

    private Handler sHandler = null;
    private HandlerThread sHandlerThread = null;
    private Object dataLock = new Object();


    private static LongSparseArray<Interval> rawData = new LongSparseArray<>(); // oldest at index 0

    public OverlappingIntervals() {
        if (sHandlerThread == null) {
            sHandlerThread = new HandlerThread(OverlappingIntervals.class.getSimpleName());
            sHandlerThread.start();
            sHandler = new Handler(sHandlerThread.getLooper());
        }
    }

    public OverlappingIntervals resetData() {
        rawData = new LongSparseArray<>();
        return this;
    }

    public void add(Interval newInterval) {
        rawData.put(newInterval.start(), newInterval);
        merge();
    }

    public void add(List<Interval> list) {
        for (Interval interval : list) {
            rawData.put(interval.start(), interval);
        }
        merge();
    }

    private void merge() {
        for (int index = 0; index < rawData.size() - 1; index++) {
            Interval i = rawData.get(index);
            long startOfNewer = rawData.get(index + 1).start();
            if (i.originalEnd() > startOfNewer) {
                i.cutEndTo(startOfNewer);
            }
        }
    }

    @Nullable
    public Interval getValueByInterval(long time) {
        int index = binarySearch(time);
        if (index >= 0) return rawData.get(index);
        return null;
    }

    public List<Interval> getList() {
        List<Interval> list = new ArrayList<>();
        for (int i = 0; i < rawData.size(); i++)
            list.add(rawData.valueAt(i));
        return list;
    }

    private static int binarySearch(long value) {
        int lo = 0;
        int hi = rawData.size() - 1;

        while (lo <= hi) {
            final int mid = (lo + hi) >>> 1;
            final Interval midVal = rawData.valueAt(mid);

            if (midVal.before(value)) {
                lo = mid + 1;
            } else if (midVal.after(value)) {
                hi = mid - 1;
            } else if (midVal.match(value)) {
                return mid;  // value found
            }
        }
        return ~lo;  // value not present
    }

}