package info.nightscout.utils;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import info.nightscout.androidaps.MainApp;

/**
 * Created by mike on 20.02.2017.
 */

public class BatteryLevel {
    static public int lastUploadedLevel = 0;

    static public int getBatteryLevel() {
        Intent batteryIntent = MainApp.instance().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level == -1 || scale == -1) {
                return 50;
            }
            return (int) (((float) level / (float) scale) * 100.0f);
        } else return 50;
    }

}
