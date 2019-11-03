package info.nightscout.androidaps.utils;

import org.slf4j.Logger;

/**
 * Created by mike on 29.01.2017.
 */

public class Profiler {
    public Profiler(){}

    static public void log(Logger log, String function, long start) {
        long msec = System.currentTimeMillis() - start;
        log.debug(">>> " + function + " <<< executed in " + msec + " miliseconds");
    }
}
