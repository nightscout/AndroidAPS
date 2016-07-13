package info.nightscout.androidaps.plugins.SmsCommunicator;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;

/**
 * A simple {@link Fragment} subclass.
 */
public class SmsCommunicatorFragment extends Fragment implements PluginBase {

    boolean fragmentEnabled = false;
    boolean fragmentVisible = true;

    public SmsCommunicatorFragment() {
        super();
        registerBus();
    }

    public static SmsCommunicatorFragment newInstance() {
        SmsCommunicatorFragment fragment = new SmsCommunicatorFragment();
        return fragment;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.smscommunicator_fragment, container, false);
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    @Override
    public String getName() {
        return MainApp.sResources.getString(R.string.smscommunicator);
    }

    @Override
    public boolean isEnabled(int type) {
        return fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        this.fragmentVisible = fragmentVisible;
    }
}
