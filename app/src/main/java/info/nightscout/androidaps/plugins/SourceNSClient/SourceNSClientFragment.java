package info.nightscout.androidaps.plugins.SourceNSClient;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;

public class SourceNSClientFragment extends Fragment implements PluginBase, BgSourceInterface {

    boolean fragmentEnabled = true;

    public SourceNSClientFragment() {
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
    public boolean isEnabled() {
        return fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs() {
        return false;
    }

    @Override
    public boolean canBeHidden() {
        return true;
    }

    @Override
    public void setFragmentEnabled(boolean fragmentEnabled) {
        this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(boolean fragmentVisible) {

    }

    public static SourceNSClientFragment newInstance() {
        SourceNSClientFragment fragment = new SourceNSClientFragment();
        return fragment;
    }

}
