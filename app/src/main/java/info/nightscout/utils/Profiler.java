package info.nightscout.utils;

import org.slf4j.Logger;

import java.util.Date;

/**
 * Created by mike on 29.01.2017.
 */

public class Profiler {
    public Profiler(){}

    static public void log(Logger log, String function, Date start) {
        long msec = System.currentTimeMillis() - start.getTime();
        log.debug(">>> " + function + " <<< executed in " + msec + " miliseconds");
    }
}
