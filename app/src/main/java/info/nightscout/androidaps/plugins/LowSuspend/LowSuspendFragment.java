package info.nightscout.androidaps.plugins.LowSuspend;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.PluginBase;

public class LowSuspendFragment extends Fragment implements PluginBase {

    @Override
    public int getType() {
        return PluginBase.APS;
    }

    @Override
    public boolean isFragmentVisible() {
        return true;
    }

    public static LowSuspendFragment newInstance() {
        LowSuspendFragment fragment = new LowSuspendFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerBus();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.lowsuspend_fragment, container, false);
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }
}
