package info.nightscout.androidaps.plugins.Wear;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import info.nightscout.androidaps.R;

/**
 * Created by adrian on 17/11/16.
 */

public class WearFragment extends Fragment {

    private static WearPlugin wearPlugin;

    public static WearPlugin getPlugin(Context ctx) {

        if (wearPlugin == null) {
            wearPlugin = new WearPlugin(ctx);
        }

        return wearPlugin;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.wear_fragment, container, false);

        view.findViewById(R.id.wear_resend).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getPlugin(getContext()).resendDataToWatch();
            }
        });

        view.findViewById(R.id.wear_opensettings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getPlugin(getContext()).openSettings();
            }
        });

        return view;
    }

}
