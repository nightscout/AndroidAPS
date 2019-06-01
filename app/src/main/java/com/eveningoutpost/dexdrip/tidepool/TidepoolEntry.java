package com.eveningoutpost.dexdrip.tidepool;

// jamorham

// lightweight class entry point

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.receivers.ChargingStateReceiver;
import info.nightscout.androidaps.utils.SP;

import static com.eveningoutpost.dexdrip.Models.JoH.isLANConnected;

public class TidepoolEntry {


    public static boolean enabled() {
        return SP.getBoolean(R.string.key_cloud_storage_tidepool_enable, false);
    }

    public static void newData() {
        if (enabled()
                && (!SP.getBoolean(R.string.key_tidepool_only_while_charging, false) || ChargingStateReceiver.isCharging())
                && (!SP.getBoolean(R.string.key_tidepool_only_while_unmetered, false) || isLANConnected())
            //        && JoH.pratelimit("tidepool-new-data-upload", 1200)
        ) {
            TidepoolUploader.doLogin(false);
        }
    }
}
