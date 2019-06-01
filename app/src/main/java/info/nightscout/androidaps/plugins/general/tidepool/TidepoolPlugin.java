package info.nightscout.androidaps.plugins.general.tidepool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.source.BGSourceFragment;

/**
 * Created by mike on 28.11.2017.
 */

public class TidepoolPlugin extends PluginBase {
    private static Logger log = LoggerFactory.getLogger(L.DATABASE);

    private static TidepoolPlugin plugin = null;

    public static TidepoolPlugin getPlugin() {
        if (plugin == null)
            plugin = new TidepoolPlugin();
        return plugin;
    }

    private TidepoolPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.BGSOURCE)
                .fragmentClass(BGSourceFragment.class.getName())
                .pluginName(R.string.tidepool)
                .shortName(R.string.tidepool_shortname)
                .preferencesId(R.xml.pref_tidepool)
                .description(R.string.description_tidepool)
        );
    }

}
