package info.nightscout.androidaps.plugins.TempTargetRange;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.TempTargetRange.events.EventNewTempTargetRange;

/**
 * Created by mike on 13/01/17.
 */

public class TempTargetRangePlugin implements PluginBase {

    static boolean fragmentEnabled = true;
    static boolean fragmentVisible = true;

    TempTargetRangePlugin() {
        MainApp.bus().register(this);
    }

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    @Override
    public String getFragmentClass() {
        return TempTargetRangeFragment.class.getName();
    }

    @Override
    public String getName() {
        return MainApp.sResources.getString(R.string.temptargetrange);
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
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == GENERAL) {
            this.fragmentEnabled = fragmentEnabled;
        }
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == GENERAL) this.fragmentVisible = fragmentVisible;
    }

    public static boolean isEnabled() {
        return fragmentEnabled;
    }

    @Subscribe
    public void onStatusEvent(final EventNewTempTargetRange ev) {

    }


}
