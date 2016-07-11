package info.nightscout.androidaps.plugins.SimpleProfile;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.NotificationCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.SafeParse;

public class SimpleProfileFragment extends Fragment implements PluginBase, ProfileInterface {
    private static Logger log = LoggerFactory.getLogger(SimpleProfileFragment.class);

    private static final String PREFS_NAME = "SimpleProfile";

    boolean fragmentEnabled = true;
    boolean fragmentVisible = true;

    EditText diaView;
    RadioButton mgdlView;
    RadioButton mmolView;
    EditText icView;
    EditText isfView;
    EditText carView;
    EditText basalView;
    EditText targetlowView;
    EditText targethighView;

    boolean mgdl;
    boolean mmol;
    Double dia;
    Double ic;
    Double isf;
    Double car;
    Double basal;
    Double targetLow;
    Double targetHigh;

    NSProfile convertedProfile = null;

    public SimpleProfileFragment() {
        super();
        registerBus();
        loadSettings();
    }

    @Override
    public int getType() {
        return PluginBase.PROFILE;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.simpleprofile);
    }

    @Override
    public boolean isEnabled(int type) {
        return fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        this.fragmentVisible = fragmentVisible;
    }

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

        mgdlView.setChecked(mgdl);
        mmolView.setChecked(mmol);
        diaView.setText(dia.toString());
        icView.setText(ic.toString());
        isfView.setText(isf.toString());
        carView.setText(car.toString());
        basalView.setText(basal.toString());
        targetlowView.setText(targetLow.toString());
        targethighView.setText(targetHigh.toString());

        mgdlView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mgdl = mgdlView.isChecked();
                mmol = !mgdl;
                mmolView.setChecked(mmol);
                storeSettings();
            }
        });
        mmolView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mmol = mmolView.isChecked();
                mgdl = !mmol;
                mgdlView.setChecked(mgdl);
                storeSettings();
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
                dia = SafeParse.stringToDouble(diaView.getText().toString());
                ic = SafeParse.stringToDouble(icView.getText().toString());
                isf = SafeParse.stringToDouble(isfView.getText().toString());
                car = SafeParse.stringToDouble(carView.getText().toString());
                basal = SafeParse.stringToDouble(basalView.getText().toString());
                targetLow = SafeParse.stringToDouble(targetlowView.getText().toString());
                targetHigh = SafeParse.stringToDouble(targethighView.getText().toString());
                storeSettings();
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

    public static SimpleProfileFragment newInstance() {
        SimpleProfileFragment fragment = new SimpleProfileFragment();
        return fragment;
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    private void storeSettings() {
        if (Config.logPrefsChange)
            log.debug("Storing settings");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("SimpleProfile" + "mmol", mmol);
        editor.putBoolean("SimpleProfile" + "mgdl", mgdl);
        editor.putFloat("SimpleProfile" + "dia", new Float(dia));
        editor.putFloat("SimpleProfile" + "ic", new Float(ic));
        editor.putFloat("SimpleProfile" + "isf", new Float(isf));
        editor.putFloat("SimpleProfile" + "car", new Float(car));
        editor.putFloat("SimpleProfile" + "basal", new Float(basal));
        editor.putFloat("SimpleProfile" + "targetlow", new Float(targetLow));
        editor.putFloat("SimpleProfile" + "targethigh", new Float(targetHigh));

        editor.commit();
        createConvertedProfile();
    }

    private void loadSettings() {
        if (Config.logPrefsChange)
            log.debug("Loading stored settings");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());

        if (settings.contains("SimpleProfile" + "mgdl")) mgdl = settings.getBoolean("SimpleProfile" + "mgdl", true); else mgdl = true;
        if (settings.contains("SimpleProfile" + "mmol")) mmol = settings.getBoolean("SimpleProfile" + "mmol", false); else mmol = false;
        if (settings.contains("SimpleProfile" + "dia")) dia = (double) settings.getFloat("SimpleProfile" + "dia", 3); else dia = 3d;
        if (settings.contains("SimpleProfile" + "ic")) ic = (double) settings.getFloat("SimpleProfile" + "ic", 20); else ic = 20d;
        if (settings.contains("SimpleProfile" + "isf")) isf = (double) settings.getFloat("SimpleProfile" + "isf", 200); else isf = 200d;
        if (settings.contains("SimpleProfile" + "car")) car = (double) settings.getFloat("SimpleProfile" + "car", 20); else car = 20d;
        if (settings.contains("SimpleProfile" + "basal")) basal = (double) settings.getFloat("SimpleProfile" + "basal", 1); else basal = 1d;
        if (settings.contains("SimpleProfile" + "targetlow")) targetLow = (double) settings.getFloat("SimpleProfile" + "targetlow", 80); else targetLow = 80d;
        if (settings.contains("SimpleProfile" + "targethigh"))
            targetHigh = (double) settings.getFloat("SimpleProfile" + "targethigh", 120); else targetHigh = 120d;
        createConvertedProfile();
    }


    /*
        {
            "_id": "576264a12771b7500d7ad184",
            "startDate": "2016-06-16T08:35:00.000Z",
            "defaultProfile": "Default",
            "store": {
                "Default": {
                    "dia": "3",
                    "carbratio": [{
                        "time": "00:00",
                        "value": "30"
                    }],
                    "carbs_hr": "20",
                    "delay": "20",
                    "sens": [{
                        "time": "00:00",
                        "value": "100"
                    }],
                    "timezone": "UTC",
                    "basal": [{
                        "time": "00:00",
                        "value": "0.1"
                    }],
                    "target_low": [{
                        "time": "00:00",
                        "value": "0"
                    }],
                    "target_high": [{
                        "time": "00:00",
                        "value": "0"
                    }],
                    "startDate": "1970-01-01T00:00:00.000Z",
                    "units": "mmol"
                }
            },
            "created_at": "2016-06-16T08:34:41.256Z"
        }
        */
    void createConvertedProfile() {
        JSONObject json = new JSONObject();
        JSONObject store = new JSONObject();
        JSONObject profile = new JSONObject();

        try {
            json.put("defaultProfile", "Profile");
            json.put("store", store);
            profile.put("dia", dia);
            profile.put("carbratio", new JSONArray().put(new JSONObject().put("timeAsSeconds", 0).put("value", ic)));
            profile.put("carbs_hr", car);
            profile.put("sens", new JSONArray().put(new JSONObject().put("timeAsSeconds", 0).put("value", isf)));
            profile.put("basal", new JSONArray().put(new JSONObject().put("timeAsSeconds", 0).put("value", basal)));
            profile.put("target_low", new JSONArray().put(new JSONObject().put("timeAsSeconds", 0).put("value", targetLow)));
            profile.put("target_high", new JSONArray().put(new JSONObject().put("timeAsSeconds", 0).put("value", targetHigh)));
            profile.put("units", mgdl ? Constants.MGDL : Constants.MMOL);
            store.put("Profile", profile);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        convertedProfile = new NSProfile(json, null);
    }

    @Override
    public NSProfile getProfile() {
        return convertedProfile;
    }
}
