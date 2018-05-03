package info.nightscout.androidaps.plugins.Insulin;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.utils.SP;

/**
 * Created by adrian on 14/08/17.
 */

public class InsulinOrefFreePeakPlugin extends InsulinOrefBasePlugin {

    private static InsulinOrefFreePeakPlugin plugin = null;

    public static InsulinOrefFreePeakPlugin getPlugin() {
        if (plugin == null)
            plugin = new InsulinOrefFreePeakPlugin();
        return plugin;
    }

    private static final int DEFAULT_PEAK = 75;

    private InsulinOrefFreePeakPlugin() {
        super();
        pluginDescription
                .pluginName(R.string.free_peak_oref)
                .preferencesId(R.xml.pref_insulinoreffreepeak);
    }

    @Override
    public int getId() {
        return OREF_FREE_PEAK;
    }

    public String getFriendlyName() {
        return MainApp.gs(R.string.free_peak_oref);
    }

    @Override
    public String commentStandardText() {
        return MainApp.gs(R.string.insulin_peak_time) + ": " + getPeak();
    }

    @Override
    int getPeak() {
        return SP.getInt(R.string.key_insulin_oref_peak, DEFAULT_PEAK);
    }
}
