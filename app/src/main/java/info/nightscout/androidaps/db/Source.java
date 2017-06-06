package info.nightscout.androidaps.db;

/**
 * Created by mike on 21.05.2017.
 */

public class Source {
    public final static int NONE = 0;
    public final static int PUMP = 1;       // Pump history
    public final static int NIGHTSCOUT = 2; // created in NS
    public final static int USER = 3;       // created by user or driver not using history

    public static String getString(int source) {
        switch (source) {
            case PUMP:
                return "PUMP";
            case NIGHTSCOUT:
                return "NIGHTSCOUT";
            case USER:
                return "USER";
        }
        return "NONE";
    }
}
