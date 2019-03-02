package info.nightscout.androidaps.plugins.insulin;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by adrian on 14/08/17.
 */

public class InsulinOrefUltraRapidActingPlugin extends InsulinOrefBasePlugin {

    private static InsulinOrefUltraRapidActingPlugin plugin = null;

    public static InsulinOrefUltraRapidActingPlugin getPlugin() {
        if (plugin == null)
            plugin = new InsulinOrefUltraRapidActingPlugin();
        return plugin;
    }

    private static final int PEAK = 55;

    private InsulinOrefUltraRapidActingPlugin() {
        super();
        pluginDescription
                .pluginName(R.string.ultrarapid_oref)
                .description(R.string.description_insulin_ultra_rapid);
    }

    @Override
    public int getId() {
        return OREF_ULTRA_RAPID_ACTING;
    }

    @Override
    public String getName() {
        return MainApp.gs(R.string.ultrarapid_oref);
    }

    @Override
    public String getFriendlyName() {
        return MainApp.gs(R.string.ultrarapid_oref);
    }

    @Override
    public String commentStandardText() {
        return MainApp.gs(R.string.ultrafastactinginsulincomment);
    }

    @Override
    int getPeak() {
        return PEAK;
    }
}
