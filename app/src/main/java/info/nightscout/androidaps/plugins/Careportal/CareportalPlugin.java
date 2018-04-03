package info.nightscout.androidaps.plugins.Careportal;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;

public class CareportalPlugin extends PluginBase {

    static CareportalPlugin careportalPlugin;

    static public CareportalPlugin getPlugin() {
        if (careportalPlugin == null) {
            careportalPlugin = new CareportalPlugin();
        }
        return careportalPlugin;
    }

    public CareportalPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(CareportalFragment.class.getName())
                .pluginName(R.string.careportal)
                .shortName(R.string.careportal_shortname)
        );
    }

}
