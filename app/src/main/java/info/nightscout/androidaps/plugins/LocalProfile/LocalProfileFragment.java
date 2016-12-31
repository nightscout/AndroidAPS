package info.nightscout.androidaps.plugins.LocalProfile;


import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;
import info.nightscout.androidaps.plugins.SimpleProfile.SimpleProfilePlugin;
import info.nightscout.utils.SafeParse;

public class LocalProfileFragment extends Fragment implements FragmentBase {
    private static Logger log = LoggerFactory.getLogger(LocalProfileFragment.class);

    private static LocalProfilePlugin localProfilePlugin = new LocalProfilePlugin();

    public static LocalProfilePlugin getPlugin() {
        return localProfilePlugin;
    }

    EditText diaView;
    RadioButton mgdlView;
    RadioButton mmolView;
    EditText icView;
    EditText isfView;
    EditText carView;
    EditText basalView;
    EditText targetlowView;
    EditText targethighView;
    Button profileswitchButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.simpleprofile_fragment, container, false);
        diaView = (EditText) layout.findViewById(R.id.simpleprofile_dia);
        mgdlView = (RadioButton) layout.findViewById(R.id.simpleprofile_mgdl);
        mmolView = (RadioButton) layout.findViewById(R.id.simpleprofile_mmol);
        icView = (EditText) layout.findViewById(R.id.simpleprofile_ic);
        isfView = (EditText) layout.findViewById(R.id.simpleprofile_isf);
        carView = (EditText) layout.findViewById(R.id.simpleprofile_car);
        basalView = (EditText) layout.findViewById(R.id.simpleprofile_basalrate);
        targetlowView = (EditText) layout.findViewById(R.id.simpleprofile_targetlow);
        targethighView = (EditText) layout.findViewById(R.id.simpleprofile_targethigh);
        profileswitchButton = (Button) layout.findViewById(R.id.simpleprofile_profileswitch);

        onStatusEvent(null);

        mgdlView.setChecked(localProfilePlugin.mgdl);
        mmolView.setChecked(localProfilePlugin.mmol);
        diaView.setText(localProfilePlugin.dia.toString());
        icView.setText(localProfilePlugin.ic.toString());
        isfView.setText(localProfilePlugin.isf.toString());
        carView.setText(localProfilePlugin.car.toString());
        basalView.setText(localProfilePlugin.basal.toString());
        targetlowView.setText(localProfilePlugin.targetLow.toString());
        targethighView.setText(localProfilePlugin.targetHigh.toString());

        mgdlView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                localProfilePlugin.mgdl = mgdlView.isChecked();
                localProfilePlugin.mmol = !localProfilePlugin.mgdl;
                mmolView.setChecked(localProfilePlugin.mmol);
                localProfilePlugin.storeSettings();
            }
        });
        mmolView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                localProfilePlugin.mmol = mmolView.isChecked();
                localProfilePlugin.mgdl = !localProfilePlugin.mmol;
                mgdlView.setChecked(localProfilePlugin.mgdl);
                localProfilePlugin.storeSettings();
            }
        });

        profileswitchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
                final OptionsToShow profileswitch = new OptionsToShow(R.id.careportal_profileswitch, R.string.careportal_profileswitch, true, false, false, false, false, false, false, true, false);
                profileswitch.executeProfileSwitch = true;
                newDialog.setOptions(profileswitch);
                newDialog.show(getFragmentManager(), "NewNSTreatmentDialog");
            }
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
                localProfilePlugin.dia = SafeParse.stringToDouble(diaView.getText().toString());
                localProfilePlugin.ic = SafeParse.stringToDouble(icView.getText().toString());
                localProfilePlugin.isf = SafeParse.stringToDouble(isfView.getText().toString());
                localProfilePlugin.car = SafeParse.stringToDouble(carView.getText().toString());
                localProfilePlugin.basal = SafeParse.stringToDouble(basalView.getText().toString());
                localProfilePlugin.targetLow = SafeParse.stringToDouble(targetlowView.getText().toString());
                localProfilePlugin.targetHigh = SafeParse.stringToDouble(targethighView.getText().toString());
                localProfilePlugin.storeSettings();
            }
        };

        diaView.addTextChangedListener(textWatch);
        icView.addTextChangedListener(textWatch);
        isfView.addTextChangedListener(textWatch);
        carView.addTextChangedListener(textWatch);
        basalView.addTextChangedListener(textWatch);
        targetlowView.addTextChangedListener(textWatch);
        targethighView.addTextChangedListener(textWatch);

        onStatusEvent(null);

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
        onStatusEvent(null);
    }

    @Subscribe
    public void onStatusEvent(final EventInitializationChanged e) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!MainApp.getConfigBuilder().isInitialized() || !MainApp.getConfigBuilder().getPumpDescription().isSetBasalProfileCapable) {
                        profileswitchButton.setVisibility(View.GONE);
                    } else {
                        profileswitchButton.setVisibility(View.VISIBLE);
                    }
                }
            });
    }

}
