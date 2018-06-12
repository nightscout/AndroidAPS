package info.nightscout.androidaps.plugins.Source;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;

/**
 * Created by mike on 05.08.2016.
 */
public class SourcePoctechPlugin extends PluginBase implements BgSourceInterface {

    private static SourcePoctechPlugin plugin = null;

    public static SourcePoctechPlugin getPlugin() {
        if (plugin == null)
            plugin = new SourcePoctechPlugin();
        return plugin;
    }

    private SourcePoctechPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.poctech)
                .showInList(!Config.NSCLIENT)
                .preferencesId(R.xml.pref_poctech)
                .description(R.string.description_source_poctech)
        );
    }

    @Override
    public boolean advancedFilteringSupported() {
        return false;
    }

}
