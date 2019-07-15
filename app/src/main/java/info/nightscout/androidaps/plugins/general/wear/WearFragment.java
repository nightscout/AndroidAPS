package info.nightscout.androidaps.plugins.general.wear;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import info.nightscout.androidaps.R;

/**
 * Created by adrian on 17/11/16.
 */

public class WearFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.wear_fragment, container, false);

        view.findViewById(R.id.wear_resend).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WearPlugin.getPlugin().resendDataToWatch();
            }
        });

        view.findViewById(R.id.wear_opensettings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WearPlugin.getPlugin().openSettings();
            }
        });

        return view;
    }

}
