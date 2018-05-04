package info.nightscout.androidaps.plugins.Source;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;

/**
 * Created by mike on 28.11.2017.
 */

public class SourceDexcomG5Plugin extends PluginBase implements BgSourceInterface {

    private static SourceDexcomG5Plugin plugin = null;

    public static SourceDexcomG5Plugin getPlugin() {
        if (plugin == null)
            plugin = new SourceDexcomG5Plugin();
        return plugin;
    }

    private SourceDexcomG5Plugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.DexcomG5)
                .shortName(R.string.dexcomG5_shortname)
                .showInList(!Config.NSCLIENT)
                .preferencesId(R.xml.pref_dexcomg5)
        );
    }

    @Override
    public boolean advancedFilteringSupported() {
        return true;
    }
}
