package info.nightscout.androidaps.utils;

import org.slf4j.Logger;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;

/**
 * Created by mike on 29.01.2017.
 */

public class Profiler {
    public Profiler() {
    }

    @Deprecated
    static public void log(Logger log, String function, long start) {
        long msec = System.currentTimeMillis() - start;
        log.debug(">>> " + function + " <<< executed in " + msec + " miliseconds");
    }

    static public void log(AAPSLogger log, LTag lTag, String function, long start) {
        long msec = System.currentTimeMillis() - start;
        log.debug(lTag, ">>> " + function + " <<< executed in " + msec + " miliseconds");
    }
}
