package info.nightscout.androidaps.plugins.ProfileLocal;


import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentTransaction;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.plugins.Careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.NumberPicker;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.TimeListEdit;

public class LocalProfileFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(LocalProfileFragment.class);

    NumberPicker diaView;
    RadioButton mgdlView;
    RadioButton mmolView;
    TimeListEdit icView;
    TimeListEdit isfView;
    TimeListEdit basalView;
    TimeListEdit targetView;
    Button profileswitchButton;
    Button resetButton;
    Button saveButton;

    TextView invalidProfile;

    Runnable save = () -> {
        doEdit();
        if (basalView != null) {
            basalView.updateLabel(MainApp.gs(R.string.nsprofileview_basal_label) + ": " + getSumLabel());
        }
    };

    TextWatcher textWatch = new TextWatcher() {

        @Override
        public void afterTextChanged(Editable s) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start,
                                      int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start,
                                  int before, int count) {
            LocalProfilePlugin.getPlugin().dia = SafeParse.stringToDouble(diaView.getText().toString());
            doEdit();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {

            PumpDescription pumpDescription = ConfigBuilderPlugin.getActivePump().getPumpDescription();
            View layout = inflater.inflate(R.layout.localprofile_fragment, container, false);
            diaView = (NumberPicker) layout.findViewById(R.id.localprofile_dia);
            diaView.setParams(LocalProfilePlugin.getPlugin().dia, 2d, 48d, 0.1d, new DecimalFormat("0.0"), false, textWatch);
            mgdlView = (RadioButton) layout.findViewById(R.id.localprofile_mgdl);
            mmolView = (RadioButton) layout.findViewById(R.id.localprofile_mmol);
            icView = new TimeListEdit(getContext(), layout, R.id.localprofile_ic, MainApp.gs(R.string.nsprofileview_ic_label) + ":", LocalProfilePlugin.getPlugin().ic, null, 0.5, 50d, 0.1d, new DecimalFormat("0.0"), save);
            isfView = new TimeListEdit(getContext(), layout, R.id.localprofile_isf, MainApp.gs(R.string.nsprofileview_isf_label) + ":", LocalProfilePlugin.getPlugin().isf, null, 0.5, 500d, 0.1d, new DecimalFormat("0.0"), save);
            basalView = new TimeListEdit(getContext(), layout, R.id.localprofile_basal, MainApp.gs(R.string.nsprofileview_basal_label) + ": " + getSumLabel(), LocalProfilePlugin.getPlugin().basal, null, pumpDescription.basalMinimumRate, 10, 0.01d, new DecimalFormat("0.00"), save);
            targetView = new TimeListEdit(getContext(), layout, R.id.localprofile_target, MainApp.gs(R.string.nsprofileview_target_label) + ":", LocalProfilePlugin.getPlugin().targetLow, LocalProfilePlugin.getPlugin().targetHigh, 3d, 200, 0.1d, new DecimalFormat("0.0"), save);
            profileswitchButton = (Button) layout.findViewById(R.id.localprofile_profileswitch);
            resetButton = (Button) layout.findViewById(R.id.localprofile_reset);
            saveButton = (Button) layout.findViewById(R.id.localprofile_save);


            invalidProfile = (TextView) layout.findViewById(R.id.invalidprofile);

            if (!ConfigBuilderPlugin.getActivePump().getPumpDescription().isTempBasalCapable) {
                layout.findViewById(R.id.localprofile_basal).setVisibility(View.GONE);
            }

            mgdlView.setChecked(LocalProfilePlugin.getPlugin().mgdl);
            mmolView.setChecked(LocalProfilePlugin.getPlugin().mmol);

            mgdlView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LocalProfilePlugin.getPlugin().mgdl = mgdlView.isChecked();
                    LocalProfilePlugin.getPlugin().mmol = !LocalProfilePlugin.getPlugin().mgdl;
                    mmolView.setChecked(LocalProfilePlugin.getPlugin().mmol);
                    doEdit();
                }
            });
            mmolView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LocalProfilePlugin.getPlugin().mmol = mmolView.isChecked();
                    LocalProfilePlugin.getPlugin().mgdl = !LocalProfilePlugin.getPlugin().mmol;
                    mgdlView.setChecked(LocalProfilePlugin.getPlugin().mgdl);
                    doEdit();
                }
            });

            profileswitchButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
                    final OptionsToShow profileswitch = CareportalFragment.PROFILESWITCHDIRECT;
                    profileswitch.executeProfileSwitch = true;
                    newDialog.setOptions(profileswitch, R.string.careportal_profileswitch);
                    newDialog.show(getFragmentManager(), "NewNSTreatmentDialog");
                }
            });

            resetButton.setOnClickListener(view -> {
                LocalProfilePlugin.getPlugin().loadSettings();
                mgdlView.setChecked(LocalProfilePlugin.getPlugin().mgdl);
                mmolView.setChecked(LocalProfilePlugin.getPlugin().mmol);
                diaView.setParams(LocalProfilePlugin.getPlugin().dia, 2d, 48d, 0.1d, new DecimalFormat("0.0"), false, textWatch);
                icView = new TimeListEdit(getContext(), layout, R.id.localprofile_ic, MainApp.gs(R.string.nsprofileview_ic_label) + ":", LocalProfilePlugin.getPlugin().ic, null, 0.5, 50d, 0.1d, new DecimalFormat("0.0"), save);
                isfView = new TimeListEdit(getContext(), layout, R.id.localprofile_isf, MainApp.gs(R.string.nsprofileview_isf_label) + ":", LocalProfilePlugin.getPlugin().isf, null, 0.5, 500d, 0.1d, new DecimalFormat("0.0"), save);
                basalView = new TimeListEdit(getContext(), layout, R.id.localprofile_basal, MainApp.gs(R.string.nsprofileview_basal_label) + ": " + getSumLabel(), LocalProfilePlugin.getPlugin().basal, null, pumpDescription.basalMinimumRate, 10, 0.01d, new DecimalFormat("0.00"), save);
                targetView = new TimeListEdit(getContext(), layout, R.id.localprofile_target, MainApp.gs(R.string.nsprofileview_target_label) + ":", LocalProfilePlugin.getPlugin().targetLow, LocalProfilePlugin.getPlugin().targetHigh, 3d, 200, 0.1d, new DecimalFormat("0.0"), save);
                updateGUI();
            });

            saveButton.setOnClickListener(view -> {
                if(!LocalProfilePlugin.getPlugin().isValidEditState()){
                    return; //Should not happen as saveButton should not be visible if not valid
                }
                LocalProfilePlugin.getPlugin().storeSettings();
                updateGUI();
            });

            return layout;
        } catch (Exception e) {
            log.error("Unhandled exception: ", e);
            FabricPrivacy.logException(e);
        }

        return null;
    }

    public void doEdit() {
        LocalProfilePlugin.getPlugin().setEdited(true);
        updateGUI();
    }

    @NonNull
    public String getSumLabel() {
        ProfileStore profile = LocalProfilePlugin.getPlugin().createProfileStore();
        if (profile != null)
            return " ∑" + DecimalFormatter.to2Decimal(profile.getDefaultProfile().baseBasalSum()) + "U";
        else
            return MainApp.gs(R.string.localprofile);
    }

    @Subscribe
    public void onStatusEvent(final EventInitializationChanged e) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    boolean isValid = LocalProfilePlugin.getPlugin().isValidEditState();
                    boolean isEdited = LocalProfilePlugin.getPlugin().isEdited();
                    if (isValid) {
                        invalidProfile.setVisibility(View.GONE); //show invalid profile

                        if (isEdited) {
                            //edited profile -> save first
                            profileswitchButton.setVisibility(View.GONE);
                            saveButton.setVisibility(View.VISIBLE);
                        } else {
                            profileswitchButton.setVisibility(View.VISIBLE);
                            saveButton.setVisibility(View.GONE);
                        }
                    } else {
                        invalidProfile.setVisibility(View.VISIBLE);
                        profileswitchButton.setVisibility(View.GONE);
                        saveButton.setVisibility(View.GONE); //don't save an invalid profile
                    }

                    //Show reset button iff data was edited
                    if(isEdited) {
                        resetButton.setVisibility(View.VISIBLE);
                    } else {
                        resetButton.setVisibility(View.GONE);
                    }
                }
            });
    }

}
