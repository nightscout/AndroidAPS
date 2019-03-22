package info.nightscout.androidaps.plugins.general.food;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;

/**
 * Created by mike on 05.08.2016.
 */
public class FoodPlugin extends PluginBase {

    private static FoodPlugin plugin = null;

    public static FoodPlugin getPlugin() {
        if (plugin == null)
            plugin = new FoodPlugin();
        return plugin;
    }

    private FoodService service;

    private FoodPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(FoodFragment.class.getName())
                .pluginName(R.string.food)
                .shortName(R.string.food_short)
                .description(R.string.description_food)
        );
        this.service = new FoodService();
    }

    public FoodService getService() {
        return this.service;
    }

}
