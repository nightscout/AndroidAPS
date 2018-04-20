package info.nightscout.androidaps.interfaces;

/**
 * Created by mike on 21.05.2017.
 */

public interface Interval {
    long durationInMsec();
    long start();

    // planned end time at time of creation
    long originalEnd();

    // end time after cut
    long end();

    void cutEndTo(long end);
    boolean match(long time);
    boolean before(long time);
    boolean after(long time);

    boolean isInProgress();
    boolean isEndingEvent();

    boolean isValid();
}