package info.nightscout.androidaps.plugins.Wear;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.FragmentBase;

/**
 * Created by adrian on 17/11/16.
 */

public class WearFragment extends Fragment implements FragmentBase {

    private static WearPlugin wearPlugin;

    public static WearPlugin getPlugin(Context ctx) {

        if (wearPlugin == null){
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
                //TODO: revert after debugging!
                //getPlugin(getContext()).openSettings();
                String title = "CONFIRM"; //TODO: i18n
                String message = "Insulin: 7.0U \n" +
                        "Carbs: 26 \n" +
                        "CONSTRAINTS APPLIED!!!"; //TODO: apply constraints
                String actionstring = "Bolus 7.0 Carbs 26"; //TODO: to be returned by watch if confirmed
                getPlugin(getContext()).requestActionConfirmation(title, message, actionstring);

            }
        });

        return view;
    }

}
