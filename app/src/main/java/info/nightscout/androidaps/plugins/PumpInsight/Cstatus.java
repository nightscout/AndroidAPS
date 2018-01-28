package info.nightscout.androidaps.plugins.PumpInsight;

/**
 * Created by jamorham on 25/01/2018.
 *
 * Async command status
 *
 */
enum Cstatus {
    UNKNOWN,
    PENDING,
    SUCCESS,
    FAILURE,
    TIMEOUT;

    boolean success() {
       return this == SUCCESS;
    }

}
