package info.nightscout.androidaps.plugins.NSProfileViewer;

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
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.androidaps.plugins.NSProfileViewer.events.EventNSProfileViewerUpdateGui;
import info.nightscout.utils.DecimalFormatter;

public class NSProfileViewerFragment extends Fragment implements FragmentBase {
    private static NSProfileViewerPlugin nsProfileViewerPlugin = new NSProfileViewerPlugin();

    public static NSProfileViewerPlugin getPlugin() {
        return nsProfileViewerPlugin;
    }

    private static TextView noProfile;
    private static TextView units;
    private static TextView dia;
    private static TextView activeProfile;
    private static TextView ic;
    private static TextView isf;
    private static TextView basal;
    private static TextView target;

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
    public void onStatusEvent(final EventNSProfileViewerUpdateGui ev) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateGUI();
                }
            });
    }

    private void updateGUI() {
        if (nsProfileViewerPlugin.profile == null) {
            noProfile.setVisibility(View.VISIBLE);
            return;
        } else {
            noProfile.setVisibility(View.GONE);
        }
        units.setText(nsProfileViewerPlugin.profile.getUnits());
        dia.setText(DecimalFormatter.to2Decimal(nsProfileViewerPlugin.profile.getDia()) + " h");
        activeProfile.setText(nsProfileViewerPlugin.profile.getActiveProfile());
        ic.setText(nsProfileViewerPlugin.profile.getIcList());
        isf.setText(nsProfileViewerPlugin.profile.getIsfList());
        basal.setText(nsProfileViewerPlugin.profile.getBasalList());
        target.setText(nsProfileViewerPlugin.profile.getTargetList());
    }

}
