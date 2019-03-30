package info.nightscout.androidaps.utils;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;

/**
 * Created by mike on 20.02.2017.
 */

public class BatteryLevel {
    private static Logger log = LoggerFactory.getLogger(BatteryLevel.class);
    static public int lastUploadedLevel = 0;

    static public int getBatteryLevel() {
        int batteryLevel = 0;
        Intent batteryIntent = MainApp.instance().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level != -1 && scale != -1) {
                batteryLevel = (int) (((float) level / (float) scale) * 100.0f);
            }
        }
        log.debug("Battery level: " + batteryLevel);
        return batteryLevel;
    }

}
