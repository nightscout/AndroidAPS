package info.nightscout.androidaps.plugins.ProfileViewer;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventNewBasalProfile;
import info.nightscout.client.data.NSProfile;

public class ProfileViewerFragment extends Fragment {
    private static Logger log = LoggerFactory.getLogger(ProfileViewerFragment.class);

    private static TextView noProfile;
    private static TextView dia;
    private static TextView activeProfile;
    private static TextView ic;
    private static TextView isf;
    private static TextView basal;
    private static TextView target;

    private static DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");

    public ProfileViewerFragment() {
    }

    public static ProfileViewerFragment newInstance(String param1, String param2) {
        ProfileViewerFragment fragment = new ProfileViewerFragment();
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
        View layout = inflater.inflate(R.layout.profileviewer_fragment, container, false);

        noProfile = (TextView) layout.findViewById(R.id.profileview_noprofile);
        dia = (TextView) layout.findViewById(R.id.profileview_dia);
        activeProfile = (TextView) layout.findViewById(R.id.profileview_activeprofile);
        ic = (TextView) layout.findViewById(R.id.profileview_ic);
        isf = (TextView) layout.findViewById(R.id.profileview_isf);
        basal = (TextView) layout.findViewById(R.id.profileview_basal);
        target = (TextView) layout.findViewById(R.id.profileview_target);

        setContent();
        return layout;
    }

    public static ProfileViewerFragment newInstance() {
        ProfileViewerFragment fragment = new ProfileViewerFragment();
        return fragment;
    }

    private void setContent() {
        NSProfile profile = MainApp.getNSProfile();
        if (profile == null) {
            noProfile.setVisibility(View.VISIBLE);
            return;
        } else {
            noProfile.setVisibility(View.GONE);
        }
        dia.setText(formatNumber2decimalplaces.format(profile.getDia()) + " h");
        activeProfile.setText(profile.getActiveProfile());
        ic.setText(profile.getIcList());
        isf.setText(profile.getIsfList());
        basal.setText(profile.getBasalList());
        target.setText(profile.getTargetList());
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    @Subscribe
    public void onStatusEvent(final EventNewBasalProfile ev) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setContent();
                }
            });
        else
            log.debug("EventNewBG: Activity is null");
    }
}
