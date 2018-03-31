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
public class SourceNSClientPlugin extends PluginBase implements BgSourceInterface {

    private static SourceNSClientPlugin plugin = null;

    public static SourceNSClientPlugin getPlugin() {
        if (plugin == null)
            plugin = new SourceNSClientPlugin();
        return plugin;
    }

    private SourceNSClientPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.nsclientbg)
                .showInList(!Config.NSCLIENT)
                .alwaysEnabled(Config.NSCLIENT)
        );
    }

    @Override
    public boolean advancedFilteringSupported() {
        return true;
    }
}
