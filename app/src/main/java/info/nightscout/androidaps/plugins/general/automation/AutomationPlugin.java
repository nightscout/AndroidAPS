package info.nightscout.androidaps.plugins.general.automation;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;

public class AutomationPlugin extends PluginBase {

    static AutomationPlugin plugin = null;

    public static AutomationPlugin getPlugin() {
        if (plugin == null)
            plugin = new AutomationPlugin();
        return plugin;
    }

    private AutomationPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(AutomationFragment.class.getName())
                .pluginName(R.string.automation)
                .shortName(R.string.automation_short)
                .preferencesId(R.xml.pref_safety)
                .description(R.string.automation_description)
        );
    }
}
