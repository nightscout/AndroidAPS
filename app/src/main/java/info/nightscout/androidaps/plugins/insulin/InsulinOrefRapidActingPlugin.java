package info.nightscout.androidaps.plugins.insulin;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by adrian on 14/08/17.
 */

public class InsulinOrefRapidActingPlugin extends InsulinOrefBasePlugin {

    private static InsulinOrefRapidActingPlugin plugin = null;

    public static InsulinOrefRapidActingPlugin getPlugin() {
        if (plugin == null)
            plugin = new InsulinOrefRapidActingPlugin();
        return plugin;
    }

    private static final int PEAK = 75;

    private InsulinOrefRapidActingPlugin() {
        super();
        pluginDescription
                .pluginName(R.string.rapid_acting_oref)
                .description(R.string.description_insulin_rapid);
    }

    @Override
    public int getId() {
        return OREF_RAPID_ACTING;
    }

    @Override
    public String getFriendlyName() {
        return MainApp.gs(R.string.rapid_acting_oref);
    }

    @Override
    public String commentStandardText() {
        return MainApp.gs(R.string.fastactinginsulincomment);
    }

    @Override
    int getPeak() {
        return PEAK;
    }
}
