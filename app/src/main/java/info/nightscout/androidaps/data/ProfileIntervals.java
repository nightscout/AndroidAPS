package info.nightscout.androidaps.data;

import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.interfaces.Interval;

/**
 * Created by mike on 09.05.2017.
 */

// Zero duration means profile is valid until is chaged
// When no interval match the lastest record without duration is used

public class ProfileIntervals<T extends Interval> {
    private static Logger log = LoggerFactory.getLogger(ProfileIntervals.class);

    private LongSparseArray<T> rawData; // oldest at index 0

    public ProfileIntervals () {
        rawData = new LongSparseArray<>();
    }

    public ProfileIntervals (ProfileIntervals<T> other) {
        rawData = other.rawData.clone();
    }

    public synchronized ProfileIntervals reset() {
        rawData = new LongSparseArray<>();
        return this;
    }

    public synchronized void add(T newInterval) {
        if (newInterval.isValid()) {
            rawData.put(newInterval.start(), newInterval);
            merge();
        }
    }

    public synchronized void add(List<T> list) {
        for (T interval : list) {
            if (interval.isValid())
                rawData.put(interval.start(), interval);
        }
        merge();
    }

    private synchronized void merge() {
        for (int index = 0; index < rawData.size() - 1; index++) {
            Interval i = rawData.valueAt(index);
            long startOfNewer = rawData.valueAt(index + 1).start();
            if (i.originalEnd() > startOfNewer) {
                i.cutEndTo(startOfNewer);
            }
        }
    }

    @Nullable
    public synchronized Interval getValueToTime(long time) {
        int index = binarySearch(time);
        if (index >= 0) return rawData.valueAt(index);
        // if we request data older than first record, use oldest with zero duration instead
        for (index = 0; index < rawData.size(); index++) {
            if (rawData.valueAt(index).durationInMsec() == 0) {
                //log.debug("Requested profile for time: " + DateUtil.dateAndTimeString(time) + ". Providing oldest record: " + rawData.valueAt(0).toString());
                return rawData.valueAt(index);
            }
        }
        return null;
    }

    public synchronized List<T> getList() {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < rawData.size(); i++)
            list.add(rawData.valueAt(i));
        return list;
    }

    public synchronized List<T> getReversedList() {
        List<T> list = new ArrayList<>();
        for (int i = rawData.size() - 1; i >= 0; i--)
            list.add(rawData.valueAt(i));
        return list;
    }

    private synchronized int binarySearch(long value) {
        if (rawData.size() == 0)
            return -1;
        int lo = 0;
        int hi = rawData.size() - 1;

        while (lo <= hi) {
            final int mid = (lo + hi) >>> 1;
            final Interval midVal = rawData.valueAt(mid);

            if (midVal.match(value)) {
                return mid;  // value found
            } else if (midVal.before(value)) {
                lo = mid + 1;
            } else if (midVal.after(value)) {
                hi = mid - 1;
            }
        }
        // not found, try nearest older with duration 0
        lo = lo - 1;
        while (lo >= 0 && lo < rawData.size()) {
            if (rawData.valueAt(lo).isEndingEvent())
                return lo;
            lo--;
        }
        return -1;  // value not present
    }

    public synchronized int size() {
        return rawData.size();
    }

    public synchronized T get(int index) {
        return rawData.valueAt(index);
    }

    public synchronized T getReversed(int index) {
        return rawData.valueAt(size() - 1 - index);
    }

    @Override
    public String toString() {
        return rawData.toString();
    }
}