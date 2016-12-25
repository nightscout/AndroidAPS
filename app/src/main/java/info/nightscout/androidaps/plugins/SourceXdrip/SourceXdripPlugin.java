package info.nightscout.androidaps.plugins.SourceXdrip;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.SourceNSClient.SourceNSClientFragment;

/**
 * Created by mike on 05.08.2016.
 */
public class SourceXdripPlugin implements PluginBase, BgSourceInterface {

    @Override
    public String getFragmentClass() {
        return SourceNSClientFragment.class.getName();
    }

    private static boolean fragmentEnabled = true;

    @Override
    public int getType() {
        return PluginBase.BGSOURCE;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.xdrip);
    }

    @Override
    public boolean isEnabled(int type) {
        return type == BGSOURCE && fragmentEnabled;
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
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == BGSOURCE) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
    }


}
