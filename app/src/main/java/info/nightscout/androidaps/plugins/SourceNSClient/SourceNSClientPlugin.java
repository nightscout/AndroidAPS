package info.nightscout.androidaps.plugins.SourceNSClient;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;

/**
 * Created by mike on 05.08.2016.
 */
public class SourceNSClientPlugin implements PluginBase, BgSourceInterface {
    private boolean fragmentEnabled = true;

    private static SourceNSClientPlugin plugin = null;

    public static SourceNSClientPlugin getPlugin() {
        if (plugin == null)
            plugin = new SourceNSClientPlugin();
        return plugin;
    }

    @Override
    public String getFragmentClass() {
        return null;
    }

    @Override
    public int getType() {
        return PluginBase.BGSOURCE;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.nsclient);
    }

    @Override
    public String getNameShort() {
        // use long name as fallback (not visible in tabs)
        return getName();
    }


    @Override
    public boolean isEnabled(int type) {
        return Config.NSCLIENT || type == BGSOURCE && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return false;
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
        return !Config.NSCLIENT;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == BGSOURCE) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {

    }


}
