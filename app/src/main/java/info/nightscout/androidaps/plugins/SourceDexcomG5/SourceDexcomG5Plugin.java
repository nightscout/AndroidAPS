package info.nightscout.androidaps.plugins.SourceDexcomG5;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;

/**
 * Created by mike on 28.11.2017.
 */

public class SourceDexcomG5Plugin implements PluginBase, BgSourceInterface {
    private boolean fragmentEnabled = false;
    private boolean fragmentVisible = false;

    private static SourceDexcomG5Plugin plugin = null;

    public static SourceDexcomG5Plugin getPlugin() {
        if (plugin == null)
            plugin = new SourceDexcomG5Plugin();
        return plugin;
    }

    @Override
    public String getFragmentClass() {
        return BGSourceFragment.class.getName();
    }

    @Override
    public int getType() {
        return PluginBase.BGSOURCE;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.DexcomG5);
    }

    @Override
    public String getNameShort() {
        // use long name as fallback (no tabs)
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return Config.G5UPLOADER || type == BGSOURCE && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return Config.G5UPLOADER || type == BGSOURCE && fragmentVisible;
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
        return !Config.G5UPLOADER;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == BGSOURCE) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == BGSOURCE) this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getPreferencesId() {
        return R.xml.pref_dexcomg5;
    }
}
