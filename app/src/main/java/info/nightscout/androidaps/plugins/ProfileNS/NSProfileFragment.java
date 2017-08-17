package info.nightscout.androidaps.plugins.ProfileNS;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.ProfileNS.events.EventNSProfileUpdateGUI;
import info.nightscout.utils.DecimalFormatter;

public class NSProfileFragment extends SubscriberFragment {
    private static NSProfilePlugin nsProfilePlugin = new NSProfilePlugin();

    public static NSProfilePlugin getPlugin() {
        return nsProfilePlugin;
    }

    private TextView noProfile;
    private TextView units;
    private TextView dia;
    private TextView activeProfile;
    private TextView ic;
    private TextView isf;
    private TextView basal;
    private TextView target;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.nsprofileviewer_fragment, container, false);

        noProfile = (TextView) layout.findViewById(R.id.profileview_noprofile);
        units = (TextView) layout.findViewById(R.id.profileview_units);
        dia = (TextView) layout.findViewById(R.id.profileview_dia);
        activeProfile = (TextView) layout.findViewById(R.id.profileview_activeprofile);
        ic = (TextView) layout.findViewById(R.id.profileview_ic);
        isf = (TextView) layout.findViewById(R.id.profileview_isf);
        basal = (TextView) layout.findViewById(R.id.profileview_basal);
        target = (TextView) layout.findViewById(R.id.profileview_target);

        updateGUI();
        return layout;
    }

    @Subscribe
    public void onStatusEvent(final EventNSProfileUpdateGUI ev) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateGUI();
                }
            });
    }

    @Override
    protected void updateGUI() {
        if (MainApp.getConfigBuilder().getProfile() == null) {
            noProfile.setVisibility(View.VISIBLE);
            return;
        } else {
            noProfile.setVisibility(View.GONE);
        }

        Profile profile = MainApp.getConfigBuilder().getProfile();
        units.setText(profile.getUnits());
        dia.setText(DecimalFormatter.to2Decimal(profile.getDia()) + " h");
        activeProfile.setText(MainApp.getConfigBuilder().getProfileName());
        ic.setText(profile.getIcList());
        isf.setText(profile.getIsfList());
        basal.setText(profile.getBasalList());
        target.setText(profile.getTargetList());
    }

}
