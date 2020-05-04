package info.nightscout.androidaps.logging;

import android.os.Bundle;

/**
 * Created by mike on 14.08.2017.
 */

public class BundleLogger {
    public static String log(Bundle bundle) {
        if (bundle == null) {
            return null;
        }
        String string = "Bundle{";
        for (String key : bundle.keySet()) {
            string += " " + key + " => " + bundle.get(key) + ";";
        }
        string += " }Bundle";
        return string;
    }
}
