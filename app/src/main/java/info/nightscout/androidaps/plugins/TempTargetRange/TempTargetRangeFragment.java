package info.nightscout.androidaps.plugins.TempTargetRange;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.androidaps.plugins.TempTargetRange.events.EventNewTempTargetRange;

/**
 * Created by mike on 13/01/17.
 */

public class TempTargetRangeFragment extends Fragment implements FragmentBase {

    private static TempTargetRangePlugin tempTargetRangePlugin = new TempTargetRangePlugin();

    public static TempTargetRangePlugin getPlugin() {
        return tempTargetRangePlugin;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.temptargetrange_fragment, container, false);

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        MainApp.bus().register(this);
    }

    @Subscribe
    public void onStatusEvent(final EventNewTempTargetRange ev) {
        updateGUI();
    }

    void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                }
            });
    }
}
