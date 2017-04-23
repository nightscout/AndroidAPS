package info.nightscout.androidaps.plugins.ProfileLocal;


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

import java.text.DecimalFormat;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.TimeListEdit;

public class LocalProfileFragment extends Fragment {
    private static Logger log = LoggerFactory.getLogger(LocalProfileFragment.class);

    private static LocalProfilePlugin localProfilePlugin = new LocalProfilePlugin();

    public static LocalProfilePlugin getPlugin() {
        return localProfilePlugin;
    }

    EditText diaView;
    RadioButton mgdlView;
    RadioButton mmolView;
    TimeListEdit icView;
    TimeListEdit isfView;
    TimeListEdit basalView;
    TimeListEdit targetView;
    Button profileswitchButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Runnable save = new Runnable() {
            @Override
            public void run() {
                localProfilePlugin.storeSettings();
            }
        };

        View layout = inflater.inflate(R.layout.localprofile_fragment, container, false);
        diaView = (EditText) layout.findViewById(R.id.localprofile_dia);
        mgdlView = (RadioButton) layout.findViewById(R.id.localprofile_mgdl);
        mmolView = (RadioButton) layout.findViewById(R.id.localprofile_mmol);
        icView = new TimeListEdit(getContext(), layout, R.id.localprofile_ic, MainApp.sResources.getString(R.string.nsprofileview_ic_label), getPlugin().ic, null, new DecimalFormat("0.0"), save);
        isfView = new TimeListEdit(getContext(), layout, R.id.localprofile_isf, MainApp.sResources.getString(R.string.nsprofileview_isf_label), getPlugin().isf, null, new DecimalFormat("0.0"), save);
        basalView = new TimeListEdit(getContext(), layout, R.id.localprofile_basal, MainApp.sResources.getString(R.string.nsprofileview_basal_label), getPlugin().basal, null, new DecimalFormat("0.00"), save);
        targetView = new TimeListEdit(getContext(), layout, R.id.localprofile_target, MainApp.sResources.getString(R.string.nsprofileview_target_label), getPlugin().targetLow, getPlugin().targetHigh, new DecimalFormat("0.0"), save);
        profileswitchButton = (Button) layout.findViewById(R.id.localprofile_profileswitch);

        PumpInterface pump = MainApp.getConfigBuilder();
        if (!pump.getPumpDescription().isTempBasalCapable) {
            layout.findViewById(R.id.localprofile_basal).setVisibility(View.GONE);
        }

        onStatusEvent(null);

        mgdlView.setChecked(localProfilePlugin.mgdl);
        mmolView.setChecked(localProfilePlugin.mmol);
        diaView.setText(localProfilePlugin.dia.toString());

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
                final OptionsToShow profileswitch = new OptionsToShow(R.id.careportal_profileswitch, R.string.careportal_profileswitch, true, false, false, false, false, false, false, true, false, false);
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
                localProfilePlugin.storeSettings();
            }
        };

        diaView.addTextChangedListener(textWatch);

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
                    if (!MainApp.getConfigBuilder().isInitialized() || MainApp.getConfigBuilder().isSuspended() || !MainApp.getConfigBuilder().getPumpDescription().isSetBasalProfileCapable) {
                        profileswitchButton.setVisibility(View.GONE);
                    } else {
                        profileswitchButton.setVisibility(View.VISIBLE);
                    }
                }
            });
    }

}
