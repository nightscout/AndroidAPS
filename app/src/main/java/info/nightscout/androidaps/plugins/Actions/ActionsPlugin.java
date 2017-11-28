package info.nightscout.androidaps.plugins.Actions;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;

/**
 * Created by mike on 05.11.2016.
 */

public class ActionsPlugin implements PluginBase {

    private boolean fragmentEnabled = true;
    private boolean fragmentVisible = true;

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    @Override
    public String getFragmentClass() {
        return ActionsFragment.class.getName();
    }

    @Override
    public String getName() {
        return MainApp.sResources.getString(R.string.actions);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.actions_shortname);
        if (!name.trim().isEmpty()){
            //only if translation exists
            return name;
        }
        // use long name as fallback
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
