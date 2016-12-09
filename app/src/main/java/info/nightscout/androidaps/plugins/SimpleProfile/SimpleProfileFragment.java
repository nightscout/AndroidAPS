package info.nightscout.androidaps.plugins.SimpleProfile;


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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;
import info.nightscout.utils.SafeParse;

public class SimpleProfileFragment extends Fragment implements FragmentBase {
    private static Logger log = LoggerFactory.getLogger(SimpleProfileFragment.class);

    private static SimpleProfilePlugin simpleProfilePlugin = new SimpleProfilePlugin();

    public static SimpleProfilePlugin getPlugin() {
        return simpleProfilePlugin;
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

        mgdlView.setChecked(simpleProfilePlugin.mgdl);
        mmolView.setChecked(simpleProfilePlugin.mmol);
        diaView.setText(simpleProfilePlugin.dia.toString());
        icView.setText(simpleProfilePlugin.ic.toString());
        isfView.setText(simpleProfilePlugin.isf.toString());
        carView.setText(simpleProfilePlugin.car.toString());
        basalView.setText(simpleProfilePlugin.basal.toString());
        targetlowView.setText(simpleProfilePlugin.targetLow.toString());
        targethighView.setText(simpleProfilePlugin.targetHigh.toString());

        mgdlView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpleProfilePlugin.mgdl = mgdlView.isChecked();
                simpleProfilePlugin.mmol = !simpleProfilePlugin.mgdl;
                mmolView.setChecked(simpleProfilePlugin.mmol);
                simpleProfilePlugin.storeSettings();
            }
        });
        mmolView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simpleProfilePlugin.mmol = mmolView.isChecked();
                simpleProfilePlugin.mgdl = !simpleProfilePlugin.mmol;
                mgdlView.setChecked(simpleProfilePlugin.mgdl);
                simpleProfilePlugin.storeSettings();
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
                simpleProfilePlugin.dia = SafeParse.stringToDouble(diaView.getText().toString());
                simpleProfilePlugin.ic = SafeParse.stringToDouble(icView.getText().toString());
                simpleProfilePlugin.isf = SafeParse.stringToDouble(isfView.getText().toString());
                simpleProfilePlugin.car = SafeParse.stringToDouble(carView.getText().toString());
                simpleProfilePlugin.basal = SafeParse.stringToDouble(basalView.getText().toString());
                simpleProfilePlugin.targetLow = SafeParse.stringToDouble(targetlowView.getText().toString());
                simpleProfilePlugin.targetHigh = SafeParse.stringToDouble(targethighView.getText().toString());
                simpleProfilePlugin.storeSettings();
            }
        };

        diaView.addTextChangedListener(textWatch);
        icView.addTextChangedListener(textWatch);
        isfView.addTextChangedListener(textWatch);
        carView.addTextChangedListener(textWatch);
        basalView.addTextChangedListener(textWatch);
        targetlowView.addTextChangedListener(textWatch);
        targethighView.addTextChangedListener(textWatch);
        return layout;
    }

}
