package info.nightscout.androidaps.plugins.profile.simple;


import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.general.careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.general.careportal.OptionsToShow;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.SafeParse;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class SimpleProfileFragment extends Fragment {
    private CompositeDisposable disposable = new CompositeDisposable();

    EditText diaView;
    RadioButton mgdlView;
    RadioButton mmolView;
    EditText icView;
    EditText isfView;
    EditText basalView;
    EditText targetlowView;
    EditText targethighView;
    Button profileswitchButton;
    TextView invalidProfile;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.simpleprofile_fragment, container, false);
        diaView = (EditText) layout.findViewById(R.id.simpleprofile_dia);
        mgdlView = (RadioButton) layout.findViewById(R.id.simpleprofile_mgdl);
        mmolView = (RadioButton) layout.findViewById(R.id.simpleprofile_mmol);
        icView = (EditText) layout.findViewById(R.id.simpleprofile_ic);
        isfView = (EditText) layout.findViewById(R.id.simpleprofile_isf);
        basalView = (EditText) layout.findViewById(R.id.simpleprofile_basalrate);
        targetlowView = (EditText) layout.findViewById(R.id.simpleprofile_targetlow);
        targethighView = (EditText) layout.findViewById(R.id.simpleprofile_targethigh);
        profileswitchButton = (Button) layout.findViewById(R.id.simpleprofile_profileswitch);
        invalidProfile = (TextView) layout.findViewById(R.id.invalidprofile);

        if (!ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription().isTempBasalCapable) {
            layout.findViewById(R.id.simpleprofile_basalrate).setVisibility(View.GONE);
            layout.findViewById(R.id.simpleprofile_basalrate_label).setVisibility(View.GONE);
        }

        mgdlView.setChecked(SimpleProfilePlugin.getPlugin().mgdl);
        mmolView.setChecked(SimpleProfilePlugin.getPlugin().mmol);
        diaView.setText(SimpleProfilePlugin.getPlugin().dia.toString());
        icView.setText(SimpleProfilePlugin.getPlugin().ic.toString());
        isfView.setText(SimpleProfilePlugin.getPlugin().isf.toString());
        basalView.setText(SimpleProfilePlugin.getPlugin().basal.toString());
        targetlowView.setText(SimpleProfilePlugin.getPlugin().targetLow.toString());
        targethighView.setText(SimpleProfilePlugin.getPlugin().targetHigh.toString());

        mgdlView.setOnClickListener(v -> {
            SimpleProfilePlugin.getPlugin().mgdl = mgdlView.isChecked();
            SimpleProfilePlugin.getPlugin().mmol = !SimpleProfilePlugin.getPlugin().mgdl;
            mmolView.setChecked(SimpleProfilePlugin.getPlugin().mmol);
            SimpleProfilePlugin.getPlugin().storeSettings();
        });
        mmolView.setOnClickListener(v -> {
            SimpleProfilePlugin.getPlugin().mmol = mmolView.isChecked();
            SimpleProfilePlugin.getPlugin().mgdl = !SimpleProfilePlugin.getPlugin().mmol;
            mgdlView.setChecked(SimpleProfilePlugin.getPlugin().mgdl);
            SimpleProfilePlugin.getPlugin().storeSettings();
        });

        profileswitchButton.setOnClickListener(view -> {
            NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
            final OptionsToShow profileswitch = CareportalFragment.PROFILESWITCH;
            profileswitch.executeProfileSwitch = true;
            newDialog.setOptions(profileswitch, R.string.careportal_profileswitch);
            newDialog.show(getFragmentManager(), "NewNSTreatmentDialog");
        });

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
                SimpleProfilePlugin.getPlugin().dia = SafeParse.stringToDouble(diaView.getText().toString());
                SimpleProfilePlugin.getPlugin().ic = SafeParse.stringToDouble(icView.getText().toString());
                SimpleProfilePlugin.getPlugin().isf = SafeParse.stringToDouble(isfView.getText().toString());
                SimpleProfilePlugin.getPlugin().basal = SafeParse.stringToDouble(basalView.getText().toString());
                SimpleProfilePlugin.getPlugin().targetLow = SafeParse.stringToDouble(targetlowView.getText().toString());
                SimpleProfilePlugin.getPlugin().targetHigh = SafeParse.stringToDouble(targethighView.getText().toString());
                SimpleProfilePlugin.getPlugin().storeSettings();
                updateGUI();
            }
        };

        diaView.addTextChangedListener(textWatch);
        icView.addTextChangedListener(textWatch);
        isfView.addTextChangedListener(textWatch);
        basalView.addTextChangedListener(textWatch);
        targetlowView.addTextChangedListener(textWatch);
        targethighView.addTextChangedListener(textWatch);

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

    protected void updateGUI() {
        boolean isValid = SimpleProfilePlugin.getPlugin().getProfile() != null && SimpleProfilePlugin.getPlugin().getProfile().getDefaultProfile().isValid(MainApp.gs(R.string.simpleprofile));
        if (!ConfigBuilderPlugin.getPlugin().getActivePump().isInitialized() || ConfigBuilderPlugin.getPlugin().getActivePump().isSuspended() || !isValid) {
            profileswitchButton.setVisibility(View.GONE);
        } else {
            profileswitchButton.setVisibility(View.VISIBLE);
        }
        if (isValid)
            invalidProfile.setVisibility(View.GONE);
        else
            invalidProfile.setVisibility(View.VISIBLE);
    }

}
