package info.nightscout.androidaps.plugins.general.automation;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.general.automation.actions.Action;

public class AutomationPlugin extends PluginBase {

    static AutomationPlugin plugin = null;

    public static AutomationPlugin getPlugin() {
        if (plugin == null)
            plugin = new AutomationPlugin();
        return plugin;
    }

    List<AutomationEvent> automationEvents = new ArrayList<>();

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
