package info.nightscout.androidaps.plugins.Source;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;

/**
 * Created by mike on 05.08.2016.
 */
public class SourceGlimpPlugin extends PluginBase implements BgSourceInterface {

    private static SourceGlimpPlugin plugin = null;

    public static SourceGlimpPlugin getPlugin() {
        if (plugin == null)
            plugin = new SourceGlimpPlugin();
        return plugin;
    }

    private SourceGlimpPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.Glimp)
        );
    }

    @Override
    public boolean advancedFilteringSupported() {
        return false;
    }
}
