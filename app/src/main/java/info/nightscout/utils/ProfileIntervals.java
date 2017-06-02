package info.nightscout.utils;

import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.interfaces.Interval;

/**
 * Created by mike on 09.05.2017.
 */

// Zero duration means profile is valid until is chaged
// When no interval match the lastest record without duration is used

public class ProfileIntervals<T extends Interval> {

    private LongSparseArray<T> rawData = new LongSparseArray<>(); // oldest at index 0

    public ProfileIntervals reset() {
        rawData = new LongSparseArray<>();
        return this;
    }

    public void add(T newInterval) {
        rawData.put(newInterval.start(), newInterval);
        merge();
    }

    public void add(List<T> list) {
        for (T interval : list) {
            rawData.put(interval.start(), interval);
        }
        merge();
    }

    private void merge() {
        for (int index = 0; index < rawData.size() - 1; index++) {
            Interval i = rawData.valueAt(index);
            long startOfNewer = rawData.valueAt(index + 1).start();
            if (i.originalEnd() > startOfNewer) {
                i.cutEndTo(startOfNewer);
            }
        }
    }

    @Nullable
    public Interval getValueToTime(long time) {
        int index = binarySearch(time);
        if (index >= 0) return rawData.valueAt(index);
        return null;
    }

    public List<T> getList() {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < rawData.size(); i++)
            list.add(rawData.valueAt(i));
        return list;
    }

    public List<T> getReversedList() {
        List<T> list = new ArrayList<>();
        for (int i = rawData.size() -1; i>=0; i--)
            list.add(rawData.valueAt(i));
        return list;
    }

    private int binarySearch(long value) {
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
        // not found, try nearest older with duration 0
        while (lo >= 0) {
            if (rawData.valueAt(lo).isEndingEvent())
                return lo;
            lo--;
        }
        return -1;  // value not present
    }

    public int size() {
        return rawData.size();
    }

    public T get(int index) {
        return rawData.valueAt(index);
    }

    public T getReversed(int index) {
        return rawData.valueAt(size() - 1 - index);
    }
}