package info.nightscout.androidaps.plugins.ProfileLocal;


import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
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
    TextView invalidProfile;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            Runnable save = new Runnable() {
                @Override
                public void run() {
                    LocalProfilePlugin.getPlugin().storeSettings();
                    if (basalView != null) {
                        basalView.updateLabel(MainApp.sResources.getString(R.string.nsprofileview_basal_label) + ": " + getSumLabel());
                    }
                    updateGUI();
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
                    LocalProfilePlugin.getPlugin().storeSettings();
                    updateGUI();
                }
            };

            PumpDescription pumpDescription = ConfigBuilderPlugin.getActivePump().getPumpDescription();

            View layout = inflater.inflate(R.layout.localprofile_fragment, container, false);
            diaView = (NumberPicker) layout.findViewById(R.id.localprofile_dia);
            diaView.setParams(LocalProfilePlugin.getPlugin().dia, 2d, 48d, 0.1d, new DecimalFormat("0.0"), false, textWatch);
            mgdlView = (RadioButton) layout.findViewById(R.id.localprofile_mgdl);
            mmolView = (RadioButton) layout.findViewById(R.id.localprofile_mmol);
            icView = new TimeListEdit(getContext(), layout, R.id.localprofile_ic, MainApp.sResources.getString(R.string.nsprofileview_ic_label) + ":", LocalProfilePlugin.getPlugin().ic, null, 0.5, 50d, 0.1d, new DecimalFormat("0.0"), save);
            isfView = new TimeListEdit(getContext(), layout, R.id.localprofile_isf, MainApp.sResources.getString(R.string.nsprofileview_isf_label) + ":", LocalProfilePlugin.getPlugin().isf, null, 0.5, 500d, 0.1d, new DecimalFormat("0.0"), save);
            basalView = new TimeListEdit(getContext(), layout, R.id.localprofile_basal, MainApp.sResources.getString(R.string.nsprofileview_basal_label) + ": " + getSumLabel(), LocalProfilePlugin.getPlugin().basal, null, pumpDescription.basalMinimumRate, 10, 0.01d, new DecimalFormat("0.00"), save);
            targetView = new TimeListEdit(getContext(), layout, R.id.localprofile_target, MainApp.sResources.getString(R.string.nsprofileview_target_label) + ":", LocalProfilePlugin.getPlugin().targetLow, LocalProfilePlugin.getPlugin().targetHigh, 3d, 200, 0.1d, new DecimalFormat("0.0"), save);
            profileswitchButton = (Button) layout.findViewById(R.id.localprofile_profileswitch);
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
                    LocalProfilePlugin.getPlugin().storeSettings();
                }
            });
            mmolView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LocalProfilePlugin.getPlugin().mmol = mmolView.isChecked();
                    LocalProfilePlugin.getPlugin().mgdl = !LocalProfilePlugin.getPlugin().mmol;
                    mgdlView.setChecked(LocalProfilePlugin.getPlugin().mgdl);
                    LocalProfilePlugin.getPlugin().storeSettings();
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


            return layout;
        } catch (Exception e) {
            log.error("Unhandled exception: ", e);
            Crashlytics.logException(e);
        }

        return null;
    }

    @NonNull
    public String getSumLabel() {
        ProfileStore profile = LocalProfilePlugin.getPlugin().getProfile();
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
                    boolean isValid = LocalProfilePlugin.getPlugin().getProfile() != null && LocalProfilePlugin.getPlugin().getProfile().getDefaultProfile().isValid(MainApp.gs(R.string.localprofile));
                    if (!ConfigBuilderPlugin.getActivePump().isInitialized() || ConfigBuilderPlugin.getActivePump().isSuspended() || !isValid) {
                        profileswitchButton.setVisibility(View.GONE);
                    } else {
                        profileswitchButton.setVisibility(View.VISIBLE);
                    }
                    if (isValid)
                        invalidProfile.setVisibility(View.GONE);
                    else
                        invalidProfile.setVisibility(View.VISIBLE);
                }
            });
    }

}
