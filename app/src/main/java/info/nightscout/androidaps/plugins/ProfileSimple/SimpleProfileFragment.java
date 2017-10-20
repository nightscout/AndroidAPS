package info.nightscout.androidaps.plugins.ProfileSimple;


import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;

import com.crashlytics.android.Crashlytics;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.utils.SafeParse;

public class SimpleProfileFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(SimpleProfileFragment.class);

    EditText diaView;
    RadioButton mgdlView;
    RadioButton mmolView;
    EditText icView;
    EditText isfView;
    EditText basalView;
    EditText targetlowView;
    EditText targethighView;
    Button profileswitchButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
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

            PumpInterface pump = MainApp.getConfigBuilder();
            if (!pump.getPumpDescription().isTempBasalCapable) {
                layout.findViewById(R.id.simpleprofile_basalrate).setVisibility(View.GONE);
                layout.findViewById(R.id.simpleprofile_basalrate_label).setVisibility(View.GONE);
            }

            updateGUI();

            mgdlView.setChecked(SimpleProfilePlugin.getPlugin().mgdl);
            mmolView.setChecked(SimpleProfilePlugin.getPlugin().mmol);
            diaView.setText(SimpleProfilePlugin.getPlugin().dia.toString());
            icView.setText(SimpleProfilePlugin.getPlugin().ic.toString());
            isfView.setText(SimpleProfilePlugin.getPlugin().isf.toString());
            basalView.setText(SimpleProfilePlugin.getPlugin().basal.toString());
            targetlowView.setText(SimpleProfilePlugin.getPlugin().targetLow.toString());
            targethighView.setText(SimpleProfilePlugin.getPlugin().targetHigh.toString());

            mgdlView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SimpleProfilePlugin.getPlugin().mgdl = mgdlView.isChecked();
                    SimpleProfilePlugin.getPlugin().mmol = !SimpleProfilePlugin.getPlugin().mgdl;
                    mmolView.setChecked(SimpleProfilePlugin.getPlugin().mmol);
                    SimpleProfilePlugin.getPlugin().storeSettings();
                }
            });
            mmolView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SimpleProfilePlugin.getPlugin().mmol = mmolView.isChecked();
                    SimpleProfilePlugin.getPlugin().mgdl = !SimpleProfilePlugin.getPlugin().mmol;
                    mgdlView.setChecked(SimpleProfilePlugin.getPlugin().mgdl);
                    SimpleProfilePlugin.getPlugin().storeSettings();
                }
            });

            profileswitchButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
                    final OptionsToShow profileswitch = CareportalFragment.PROFILESWITCH;
                    profileswitch.executeProfileSwitch = true;
                    newDialog.setOptions(profileswitch, R.string.careportal_profileswitch);
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
                    SimpleProfilePlugin.getPlugin().dia = SafeParse.stringToDouble(diaView.getText().toString());
                    SimpleProfilePlugin.getPlugin().ic = SafeParse.stringToDouble(icView.getText().toString());
                    SimpleProfilePlugin.getPlugin().isf = SafeParse.stringToDouble(isfView.getText().toString());
                    SimpleProfilePlugin.getPlugin().basal = SafeParse.stringToDouble(basalView.getText().toString());
                    SimpleProfilePlugin.getPlugin().targetLow = SafeParse.stringToDouble(targetlowView.getText().toString());
                    SimpleProfilePlugin.getPlugin().targetHigh = SafeParse.stringToDouble(targethighView.getText().toString());
                    SimpleProfilePlugin.getPlugin().storeSettings();
                }
            };

            diaView.addTextChangedListener(textWatch);
            icView.addTextChangedListener(textWatch);
            isfView.addTextChangedListener(textWatch);
            basalView.addTextChangedListener(textWatch);
            targetlowView.addTextChangedListener(textWatch);
            targethighView.addTextChangedListener(textWatch);

            updateGUI();

            return layout;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
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
                    if (!MainApp.getConfigBuilder().isInitialized() || MainApp.getConfigBuilder().isSuspended()) {
                        profileswitchButton.setVisibility(View.GONE);
                    } else {
                        profileswitchButton.setVisibility(View.VISIBLE);
                    }
                }
            });
    }

}
