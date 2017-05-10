package info.nightscout.utils;

import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mike on 09.05.2017.
 */

public class OverlappingIntervals {

    private Handler sHandler = null;
    private HandlerThread sHandlerThread = null;
    private Object dataLock = new Object();


    public abstract class Interval {
        Long start = null;
        Long duration = null;
        Long cuttedEnd = null;

        public Interval(long start, long duration) {
            this.start = start;
            this.duration = duration;
        }

        long durationInMsec() {
            return duration;
        }

        long start() {
            return start;
        }

        // planned end time at time of creation
        long originalEnd() {
            return start + duration;
        }

        // end time after cut
        long end() {
            if (cuttedEnd != null)
                return cuttedEnd;
            return originalEnd();
        }

        void cutEndTo(long end) {
            cuttedEnd = end;
        }

        boolean match(long time) {
            if (start() <= time && end() >= time)
                return true;
            return false;
        }

        boolean before(long time) {
            if (end() < time)
                return true;
            return false;
        }

        boolean after(long time) {
            if (start() > time)
                return true;
            return false;
        }
    }

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
