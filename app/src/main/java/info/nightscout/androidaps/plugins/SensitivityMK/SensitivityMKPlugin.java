package info.nightscout.androidaps.plugins.SensitivityMK;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.SensitivityInterface;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensResult;

/**
 * Created by mike on 24.06.2017.
 */

public class SensitivityMKPlugin implements PluginBase, SensitivityInterface{
    private static boolean fragmentEnabled = true;
    private static boolean fragmentVisible = false;

    static SensitivityMKPlugin plugin = null;

    public static SensitivityMKPlugin getPlugin() {
        if (plugin == null)
            plugin = new SensitivityMKPlugin();
        return plugin;
    }

    @Override
    public int getType() {
        return INSULIN;
    }

    @Override
    public String getFragmentClass() {
        return null;
    }

    @Override
    public String getName() {
        return MainApp.sResources.getString(R.string.sensitivitymk);
    }

    @Override
    public String getNameShort() {
        return MainApp.sResources.getString(R.string.sensitivity_shortname);
    }

    @Override
    public boolean isEnabled(int type) {
        return type == SENSITIVITY && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == SENSITIVITY && fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return false;
    }

    @Override
    public boolean showInList(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == SENSITIVITY) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == SENSITIVITY) this.fragmentVisible = fragmentVisible;
    }


    @Override
    public AutosensResult detectSensitivity(long fromTime, long toTime) {
        return null;
    }
}
