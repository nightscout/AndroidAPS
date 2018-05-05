package info.nightscout.androidaps.plugins.Source;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;

/**
 * Created by mike on 05.08.2016.
 */
public class SourceMM640gPlugin extends PluginBase implements BgSourceInterface {
    private static SourceMM640gPlugin plugin = null;

    public static SourceMM640gPlugin getPlugin() {
        if (plugin == null)
            plugin = new SourceMM640gPlugin();
        return plugin;
    }

    private SourceMM640gPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.MM640g)
        );
    }

    @Override
    public boolean advancedFilteringSupported() {
        return false;
    }
}
