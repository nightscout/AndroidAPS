package info.nightscout.androidaps.plugins.ProfileNS;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnItemSelected;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.ProfileNS.events.EventNSProfileUpdateGUI;
import info.nightscout.androidaps.plugins.Treatments.fragments.ProfileGraph;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.OKDialog;

import static butterknife.OnItemSelected.Callback.NOTHING_SELECTED;


public class NSProfileFragment extends SubscriberFragment {
    @BindView(R.id.nsprofile_spinner)
    Spinner profileSpinner;
    @BindView(R.id.profileview_noprofile)
    TextView noProfile;
    @BindView(R.id.profileview_invalidprofile)
    TextView invalidProfile;
    @BindView(R.id.profileview_units)
    TextView units;
    @BindView(R.id.profileview_dia)
    TextView dia;
    @BindView(R.id.profileview_activeprofile)
    TextView activeProfile;
    @BindView(R.id.profileview_ic)
    TextView ic;
    @BindView(R.id.profileview_isf)
    TextView isf;
    @BindView(R.id.profileview_basal)
    TextView basal;
    @BindView(R.id.profileview_target)
    TextView target;
    @BindView(R.id.basal_graph)
    ProfileGraph basalGraph;
    @BindView(R.id.nsprofile_profileswitch)
    Button activateButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.nsprofile_fragment, container, false);

            unbinder = ButterKnife.bind(this, view);
            updateGUI();
            return view;
        } catch (Exception e) {
            FabricPrivacy.logException(e);
        }

        return null;
    }

    @Subscribe
    public void onStatusEvent(final EventNSProfileUpdateGUI ev) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> updateGUI());
    }

    @Override
    protected void updateGUI() {
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
            noProfile.setVisibility(View.GONE);
        } else {
            noProfile.setVisibility(View.VISIBLE);
        }
    }

    @OnItemSelected(R.id.nsprofile_spinner)
    public void onItemSelected(Spinner spinner, int position) {
        String name = spinner.getItemAtPosition(position).toString();

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
                basalGraph.show(profile);
            }
            if (profile.isValid("NSProfileFragment")) {
                invalidProfile.setVisibility(View.GONE);
                activateButton.setVisibility(View.VISIBLE);
            } else {
                invalidProfile.setVisibility(View.VISIBLE);
                activateButton.setVisibility(View.GONE);
            }
        } else {
            activateButton.setVisibility(View.GONE);
        }
    }

    @OnItemSelected(value = R.id.nsprofile_spinner, callback = NOTHING_SELECTED)
    public void onNothingSelected() {
        invalidProfile.setVisibility(View.VISIBLE);
        noProfile.setVisibility(View.VISIBLE);
        units.setText("");
        dia.setText("");
        activeProfile.setText("");
        ic.setText("");
        isf.setText("");
        basal.setText("");
        target.setText("");
        activateButton.setVisibility(View.GONE);
    }

    @OnClick(R.id.nsprofile_profileswitch)
    public void onClickProfileSwitch() {
        String name = profileSpinner.getSelectedItem() != null ? profileSpinner.getSelectedItem().toString() : "";
        ProfileStore store = NSProfilePlugin.getPlugin().getProfile();
        if (store != null) {
            Profile profile = store.getSpecificProfile(name);
            if (profile != null) {
                OKDialog.showConfirmation(getActivity(), MainApp.gs(R.string.activate_profile) + ": " + name + " ?", () ->
                        NewNSTreatmentDialog.doProfileSwitch(store, name, 0, 100, 0)
                );
            }
        }
    }
}
