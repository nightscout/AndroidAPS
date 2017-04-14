package info.nightscout.androidaps;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;

import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.DanaR.BluetoothDevicePreference;
import info.nightscout.androidaps.plugins.DanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.NSClientInternalPlugin;
import info.nightscout.androidaps.plugins.OpenAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.VirtualPump.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.Wear.WearPlugin;
import info.nightscout.utils.LocaleHelper;

public class PreferencesActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    MyPreferenceFragment myPreferenceFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myPreferenceFragment = new MyPreferenceFragment();
        getFragmentManager().beginTransaction().replace(android.R.id.content, myPreferenceFragment).commit();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        MainApp.bus().post(new EventPreferenceChange(key));
        if (key.equals("language")) {
            String lang = sharedPreferences.getString("language", "en");
            LocaleHelper.setLocale(getApplicationContext(), lang);
            recreate();
            MainApp.bus().post(new EventRefreshGui(true));
        }
        if (key.equals("short_tabtitles")) {
            MainApp.bus().post(new EventRefreshGui(true));
        }
        updatePrefSummary(myPreferenceFragment.getPreference(key));
    }

    private static void updatePrefSummary(Preference pref) {
        if (pref instanceof ListPreference || pref instanceof BluetoothDevicePreference) {
            ListPreference listPref = (ListPreference) pref;
            pref.setSummary(listPref.getEntry());
        }
        if (pref instanceof EditTextPreference) {
            EditTextPreference editTextPref = (EditTextPreference) pref;
            if (pref.getKey().contains("password") || pref.getKey().contains("secret")) {
                pref.setSummary("******");
            } else if (editTextPref.getText() != null && !editTextPref.getText().equals("")) {
                ((EditTextPreference) pref).setDialogMessage(editTextPref.getDialogMessage());
                pref.setSummary(editTextPref.getText());
            }
        }
        if (pref instanceof MultiSelectListPreference) {
            EditTextPreference editTextPref = (EditTextPreference) pref;
            pref.setSummary(editTextPref.getText());
        }
    }

    public static void initSummary(Preference p) {
        if (p instanceof PreferenceGroup) {
            PreferenceGroup pGrp = (PreferenceGroup) p;
            for (int i = 0; i < pGrp.getPreferenceCount(); i++) {
                initSummary(pGrp.getPreference(i));
            }
        } else {
            updatePrefSummary(p);
        }
    }

    public static class MyPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (Config.ALLPREFERENCES) {
                addPreferencesFromResource(R.xml.pref_password);
                addPreferencesFromResource(R.xml.pref_age);
            }
            addPreferencesFromResource(R.xml.pref_language);
            if (Config.ALLPREFERENCES) {
                addPreferencesFromResource(R.xml.pref_quickwizard);
            }
            addPreferencesFromResource(R.xml.pref_careportal);
            if (Config.ALLPREFERENCES) {
                addPreferencesFromResource(R.xml.pref_treatments);
            }
            if (Config.APS)
                addPreferencesFromResource(R.xml.pref_closedmode);
            if (Config.OPENAPSENABLED) {
                addPreferencesFromResource(R.xml.pref_openapsma);
                if (MainApp.getSpecificPlugin(OpenAPSAMAPlugin.class) != null && MainApp.getSpecificPlugin(OpenAPSAMAPlugin.class).isEnabled(PluginBase.APS))
                    addPreferencesFromResource(R.xml.pref_openapsama);
            }
            if (Config.ALLPREFERENCES) {
                addPreferencesFromResource(R.xml.pref_profile);
            }
            if (Config.DANAR) {
                DanaRPlugin danaRPlugin = (DanaRPlugin) MainApp.getSpecificPlugin(DanaRPlugin.class);
                DanaRKoreanPlugin danaRKoreanPlugin = (DanaRKoreanPlugin) MainApp.getSpecificPlugin(DanaRKoreanPlugin.class);
                if (danaRPlugin.isEnabled(PluginBase.PUMP) || danaRKoreanPlugin.isEnabled(PluginBase.PUMP)) {
                    addPreferencesFromResource(R.xml.pref_danar);
                }
                if (danaRPlugin.isEnabled(PluginBase.PROFILE) || danaRKoreanPlugin.isEnabled(PluginBase.PROFILE)) {
                    addPreferencesFromResource(R.xml.pref_danarprofile);
                }
            }
            VirtualPumpPlugin virtualPumpPlugin = (VirtualPumpPlugin) MainApp.getSpecificPlugin(VirtualPumpPlugin.class);
            if (virtualPumpPlugin != null && virtualPumpPlugin.isEnabled(PluginBase.PUMP)) {
                addPreferencesFromResource(R.xml.pref_virtualpump);
            }
            NSClientInternalPlugin nsClientInternalPlugin = (NSClientInternalPlugin) MainApp.getSpecificPlugin(NSClientInternalPlugin.class);
            if (nsClientInternalPlugin != null && nsClientInternalPlugin.isEnabled(PluginBase.GENERAL)) {
                addPreferencesFromResource(R.xml.pref_nsclientinternal);
            }
            if (Config.SMSCOMMUNICATORENABLED)
                addPreferencesFromResource(R.xml.pref_smscommunicator);
            if (Config.ALLPREFERENCES) {
                addPreferencesFromResource(R.xml.pref_others);
                addPreferencesFromResource(R.xml.pref_advanced);
            }

            if (Config.WEAR) {
                WearPlugin wearPlugin = (WearPlugin) MainApp.getSpecificPlugin(WearPlugin.class);
                if (wearPlugin != null && wearPlugin.isEnabled(PluginBase.GENERAL)) {
                    addPreferencesFromResource(R.xml.pref_wear);
                }
            }

            initSummary(getPreferenceScreen());
        }

        public Preference getPreference(String key) {
            return findPreference(key);
        }
    }
}
