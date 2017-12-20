package info.nightscout.androidaps.plugins.SourceMM640g;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.SourceDexcomG5.BGSourceFragment;

/**
 * Created by mike on 05.08.2016.
 */
public class SourceMM640gPlugin implements PluginBase, BgSourceInterface {
    private boolean fragmentEnabled = false;
    private boolean fragmentVisible = false;

    private static SourceMM640gPlugin plugin = null;

    public static SourceMM640gPlugin getPlugin() {
        if (plugin == null)
            plugin = new SourceMM640gPlugin();
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
        return MainApp.instance().getString(R.string.MM640g);
    }

    @Override
    public String getNameShort() {
        // use long name as fallback (no tabs)
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return type == BGSOURCE && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == BGSOURCE && fragmentVisible;
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
        if (type == BGSOURCE) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == BGSOURCE) this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getPreferencesId() {
        return -1;
    }


}
