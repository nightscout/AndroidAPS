package info.nightscout.androidaps.plugins.profile.local;


import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.text.DecimalFormat;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.general.careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.general.careportal.OptionsToShow;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.NumberPicker;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.TimeListEdit;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

import static info.nightscout.androidaps.plugins.insulin.InsulinOrefBasePlugin.MIN_DIA;

public class LocalProfileFragment extends Fragment {
    private CompositeDisposable disposable = new CompositeDisposable();

    private NumberPicker diaView;
    private RadioButton mgdlView;
    private RadioButton mmolView;
    private TimeListEdit basalView;
    private Button profileswitchButton;
    private Button resetButton;
    private Button saveButton;

    private TextView invalidProfile;

    private Runnable save = () -> {
        doEdit();
        if (basalView != null) {
            basalView.updateLabel(MainApp.gs(R.string.nsprofileview_basal_label) + ": " + getSumLabel());
        }
    };

    private TextWatcher textWatch = new TextWatcher() {

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
        PumpDescription pumpDescription = ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription();
        View layout = inflater.inflate(R.layout.localprofile_fragment, container, false);
        diaView = layout.findViewById(R.id.localprofile_dia);
        diaView.setParams(LocalProfilePlugin.getPlugin().dia, MIN_DIA, 12d, 0.1d, new DecimalFormat("0.0"), false, layout.findViewById(R.id.localprofile_save), textWatch);
        mgdlView = layout.findViewById(R.id.localprofile_mgdl);
        mmolView = layout.findViewById(R.id.localprofile_mmol);
        new TimeListEdit(getContext(), layout, R.id.localprofile_ic, MainApp.gs(R.string.nsprofileview_ic_label) + ":", LocalProfilePlugin.getPlugin().ic, null, 0.5, 50d, 0.1d, new DecimalFormat("0.0"), save);
        new TimeListEdit(getContext(), layout, R.id.localprofile_isf, MainApp.gs(R.string.nsprofileview_isf_label) + ":", LocalProfilePlugin.getPlugin().isf, null, 0.5, 500d, 0.1d, new DecimalFormat("0.0"), save);
        basalView = new TimeListEdit(getContext(), layout, R.id.localprofile_basal, MainApp.gs(R.string.nsprofileview_basal_label) + ": " + getSumLabel(), LocalProfilePlugin.getPlugin().basal, null, pumpDescription.basalMinimumRate, 10, 0.01d, new DecimalFormat("0.00"), save);
        new TimeListEdit(getContext(), layout, R.id.localprofile_target, MainApp.gs(R.string.nsprofileview_target_label) + ":", LocalProfilePlugin.getPlugin().targetLow, LocalProfilePlugin.getPlugin().targetHigh, 3d, 200, 0.1d, new DecimalFormat("0.0"), save);
        profileswitchButton = layout.findViewById(R.id.localprofile_profileswitch);
        resetButton = layout.findViewById(R.id.localprofile_reset);
        saveButton = layout.findViewById(R.id.localprofile_save);


        invalidProfile = layout.findViewById(R.id.invalidprofile);

        if (!ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription().isTempBasalCapable) {
            layout.findViewById(R.id.localprofile_basal).setVisibility(View.GONE);
        }

        mgdlView.setChecked(LocalProfilePlugin.getPlugin().mgdl);
        mmolView.setChecked(LocalProfilePlugin.getPlugin().mmol);

        mgdlView.setOnClickListener(v -> {
            LocalProfilePlugin.getPlugin().mgdl = mgdlView.isChecked();
            LocalProfilePlugin.getPlugin().mmol = !LocalProfilePlugin.getPlugin().mgdl;
            mmolView.setChecked(LocalProfilePlugin.getPlugin().mmol);
            doEdit();
        });
        mmolView.setOnClickListener(v -> {
            LocalProfilePlugin.getPlugin().mmol = mmolView.isChecked();
            LocalProfilePlugin.getPlugin().mgdl = !LocalProfilePlugin.getPlugin().mmol;
            mgdlView.setChecked(LocalProfilePlugin.getPlugin().mgdl);
            doEdit();
        });

        profileswitchButton.setOnClickListener(view -> {
            NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
            final OptionsToShow profileswitch = CareportalFragment.PROFILESWITCHDIRECT;
            profileswitch.executeProfileSwitch = true;
            newDialog.setOptions(profileswitch, R.string.careportal_profileswitch);
            newDialog.show(getFragmentManager(), "NewNSTreatmentDialog");
        });

        resetButton.setOnClickListener(view -> {
            LocalProfilePlugin.getPlugin().loadSettings();
            mgdlView.setChecked(LocalProfilePlugin.getPlugin().mgdl);
            mmolView.setChecked(LocalProfilePlugin.getPlugin().mmol);
            diaView.setParams(LocalProfilePlugin.getPlugin().dia, MIN_DIA, 12d, 0.1d, new DecimalFormat("0.0"), false, view.findViewById(R.id.localprofile_save), textWatch);
            new TimeListEdit(getContext(), layout, R.id.localprofile_ic, MainApp.gs(R.string.nsprofileview_ic_label) + ":", LocalProfilePlugin.getPlugin().ic, null, 0.5, 50d, 0.1d, new DecimalFormat("0.0"), save);
            new TimeListEdit(getContext(), layout, R.id.localprofile_isf, MainApp.gs(R.string.nsprofileview_isf_label) + ":", LocalProfilePlugin.getPlugin().isf, null, 0.5, 500d, 0.1d, new DecimalFormat("0.0"), save);
            basalView = new TimeListEdit(getContext(), layout, R.id.localprofile_basal, MainApp.gs(R.string.nsprofileview_basal_label) + ": " + getSumLabel(), LocalProfilePlugin.getPlugin().basal, null, pumpDescription.basalMinimumRate, 10, 0.01d, new DecimalFormat("0.00"), save);
            new TimeListEdit(getContext(), layout, R.id.localprofile_target, MainApp.gs(R.string.nsprofileview_target_label) + ":", LocalProfilePlugin.getPlugin().targetLow, LocalProfilePlugin.getPlugin().targetHigh, 3d, 200, 0.1d, new DecimalFormat("0.0"), save);
            updateGUI();
        });

        saveButton.setOnClickListener(view -> {
            if (!LocalProfilePlugin.getPlugin().isValidEditState()) {
                return; //Should not happen as saveButton should not be visible if not valid
            }
            LocalProfilePlugin.getPlugin().storeSettings();
            updateGUI();
        });

        return layout;
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventInitializationChanged.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateGUI(), FabricPrivacy::logException)
        );
        updateGUI();
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        disposable.clear();
    }

    public void doEdit() {
        LocalProfilePlugin.getPlugin().setEdited(true);
        updateGUI();
    }

    @NonNull
    public String getSumLabel() {
        ProfileStore profile = LocalProfilePlugin.getPlugin().createProfileStore();
        if (profile != null)
            return " ∑" + DecimalFormatter.to2Decimal(profile.getDefaultProfile().baseBasalSum()) + MainApp.gs(R.string.insulin_unit_shortname);
        else
            return MainApp.gs(R.string.localprofile);
    }

    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
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
                if (isEdited) {
                    resetButton.setVisibility(View.VISIBLE);
                } else {
                    resetButton.setVisibility(View.GONE);
                }
            });
    }
}
