package info.nightscout.androidaps.plugins.Food;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;

/**
 * Created by mike on 05.08.2016.
 */
public class FoodPlugin implements PluginBase {
    private boolean fragmentEnabled = true;
    private boolean fragmentVisible = false;

    private static FoodPlugin plugin = null;

    public static FoodPlugin getPlugin() {
        if (plugin == null)
            plugin = new FoodPlugin();
        return plugin;
    }

    @Override
    public String getFragmentClass() {
        return FoodFragment.class.getName();
    }

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.food);
    }

    @Override
    public String getNameShort() {
        // use long name as fallback (not visible in tabs)
        return getName();
    }


    @Override
    public boolean isEnabled(int type) {
        return type == GENERAL && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == GENERAL && fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == GENERAL) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == GENERAL) this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getPreferencesId() {
        return -1;
    }


}
