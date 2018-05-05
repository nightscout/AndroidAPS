package info.nightscout.androidaps.plugins.PumpInsight.history;

import info.nightscout.utils.SP;

/**
 * Created by jamorham on 01/02/2018.
 */

public class PumpIdCache {

    private static final String INSIGHT_PUMP_ID_PREF = "insight-pump-id";
    private static long cachedPumpSerialNumber = -1;

    private static void log(String msg) {
        android.util.Log.e("PumpIdCache", msg);
    }

    static void updatePumpSerialNumber(long pump_serial_number) {
        if (pump_serial_number != cachedPumpSerialNumber) {
            cachedPumpSerialNumber = pump_serial_number;
            log("Updating pump serial number: " + pump_serial_number);
            SP.putLong(INSIGHT_PUMP_ID_PREF, cachedPumpSerialNumber);
        }
    }

    public static long getRecordUniqueID(long record_id) {
        if (cachedPumpSerialNumber == -1) {
            cachedPumpSerialNumber = SP.getLong(INSIGHT_PUMP_ID_PREF, 0L);
        }
        return HistoryIntentAdapter.getRecordUniqueID(cachedPumpSerialNumber, record_id);
    }

}
