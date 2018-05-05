package info.nightscout.androidaps.plugins.Source;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;

/**
 * Created by mike on 05.08.2016.
 */
public class SourceXdripPlugin extends PluginBase implements BgSourceInterface {

    private static SourceXdripPlugin plugin = null;
    
    boolean advancedFiltering;

    public static SourceXdripPlugin getPlugin() {
        if (plugin == null)
            plugin = new SourceXdripPlugin();
        return plugin;
    }

    private SourceXdripPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.xdrip)
        );
    }

    @Override
    public boolean advancedFilteringSupported() {
        return advancedFiltering;
    }

    public void setSource(String source) {
        this.advancedFiltering = source.contains("G5 Native");
    }
}
