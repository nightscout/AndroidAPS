package info.nightscout.androidaps.plugins.ProfileNS;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.ProfileNS.events.EventNSProfileUpdateGUI;
import info.nightscout.utils.DecimalFormatter;


public class NSProfileFragment extends SubscriberFragment implements AdapterView.OnItemSelectedListener {
    private Spinner profileSpinner;
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
        try {
            View layout = inflater.inflate(R.layout.nsprofile_fragment, container, false);

            profileSpinner = (Spinner) layout.findViewById(R.id.nsprofile_spinner);
            noProfile = (TextView) layout.findViewById(R.id.profileview_noprofile);
            units = (TextView) layout.findViewById(R.id.profileview_units);
            dia = (TextView) layout.findViewById(R.id.profileview_dia);
            activeProfile = (TextView) layout.findViewById(R.id.profileview_activeprofile);
            ic = (TextView) layout.findViewById(R.id.profileview_ic);
            isf = (TextView) layout.findViewById(R.id.profileview_isf);
            basal = (TextView) layout.findViewById(R.id.profileview_basal);
            target = (TextView) layout.findViewById(R.id.profileview_target);

            profileSpinner.setOnItemSelectedListener(this);

            updateGUI();
            return layout;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
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

        ProfileStore profileStore = NSProfilePlugin.getPlugin().getProfile();
        if (profileStore != null) {
            ArrayList<CharSequence> profileList = profileStore.getProfileList();
            ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(getContext(),
                    R.layout.spinner_centered, profileList);
            profileSpinner.setAdapter(adapter);
            // set selected to actual profile
            for (int p = 0; p < profileList.size(); p++) {
                if (profileList.get(p).equals(MainApp.getConfigBuilder().getProfileName()))
                    profileSpinner.setSelection(p);
            }
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String name = parent.getItemAtPosition(position).toString();

        ProfileStore store = NSProfilePlugin.getPlugin().getProfile();
        if (store != null) {
            Profile profile = store.getSpecificProfile(name);
            if (profile != null) {
                units.setText(profile.getUnits());
                dia.setText(DecimalFormatter.to2Decimal(profile.getDia()) + " h");
                activeProfile.setText(name);
                ic.setText(profile.getIcList());
                isf.setText(profile.getIsfList());
                basal.setText(profile.getBasalList());
                target.setText(profile.getTargetList());
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        noProfile.setVisibility(View.VISIBLE);
        units.setText("");
        dia.setText("");
        activeProfile.setText("");
        ic.setText("");
        isf.setText("");
        basal.setText("");
        target.setText("");
    }
}
