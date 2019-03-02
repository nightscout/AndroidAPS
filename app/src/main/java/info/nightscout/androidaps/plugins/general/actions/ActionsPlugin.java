package info.nightscout.androidaps.plugins.general.actions;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;

/**
 * Created by mike on 05.11.2016.
 */

public class ActionsPlugin extends PluginBase {

    public ActionsPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(ActionsFragment.class.getName())
                .pluginName(R.string.actions)
                .shortName(R.string.actions_shortname)
                .description(R.string.description_actions)
        );
    }
}
