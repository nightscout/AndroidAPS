package info.nightscout.androidaps.plugins.PumpCommon.dialog;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import info.nightscout.androidaps.R;

/**
 * Created by andy on 5/19/18.
 */

public class RileyLinkSettingsTab2 extends Fragment implements RefreshableInterface {


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.rileylink_settings_tab2, container, false);

        return rootView;
    }

    @Override
    public void refreshData() {

    }
}
