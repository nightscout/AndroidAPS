package info.nightscout.androidaps;

import android.content.Intent;
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
import android.text.TextUtils;

import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.Careportal.CareportalPlugin;
import info.nightscout.androidaps.plugins.ConstraintsSafety.SafetyPlugin;
import info.nightscout.androidaps.plugins.Insulin.InsulinOrefFreePeakPlugin;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.NSClientInternalPlugin;
import info.nightscout.androidaps.plugins.OpenAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.OpenAPSMA.OpenAPSMAPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRS.DanaRSPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRv2.DanaRv2Plugin;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.SensitivityAAPS.SensitivityAAPSPlugin;
import info.nightscout.androidaps.plugins.SensitivityOref0.SensitivityOref0Plugin;
import info.nightscout.androidaps.plugins.SensitivityWeightedAverage.SensitivityWeightedAveragePlugin;
import info.nightscout.androidaps.plugins.SmsCommunicator.SmsCommunicatorPlugin;
import info.nightscout.androidaps.plugins.SourceDexcomG5.SourceDexcomG5Plugin;
import info.nightscout.androidaps.plugins.Wear.WearPlugin;
import info.nightscout.androidaps.plugins.XDripStatusline.StatuslinePlugin;
import info.nightscout.utils.LocaleHelper;
import info.nightscout.utils.OKDialog;
import info.nightscout.utils.SP;

public class PreferencesActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    MyPreferenceFragment myPreferenceFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myPreferenceFragment = new MyPreferenceFragment();
        Bundle args = new Bundle();
        args.putInt("id", getIntent().getIntExtra("id", -1));
        myPreferenceFragment.setArguments(args);
        getFragmentManager().beginTransaction().replace(android.R.id.content, myPreferenceFragment).commit();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        MainApp.bus().post(new EventPreferenceChange(key));
        if (key.equals("language")) {
            String lang = sharedPreferences.getString("language", "en");
            LocaleHelper.setLocale(getApplicationContext(), lang);
            MainApp.bus().post(new EventRefreshGui(true));
            //recreate() does not update language so better close settings
            finish();
        }
        if (key.equals("short_tabtitles")) {
            MainApp.bus().post(new EventRefreshGui());
        }
        if (key.equals("openapsama_useautosens") && SP.getBoolean("openapsama_useautosens", false)) {
            OKDialog.show(this, MainApp.sResources.getString(R.string.configbuilder_sensitivity), MainApp.sResources.getString(R.string.sensitivity_warning), null);
        }
        updatePrefSummary(myPreferenceFragment.getPreference(key));
    }

    private static void updatePrefSummary(Preference pref) {
        if (pref instanceof ListPreference) {
            ListPreference listPref = (ListPreference) pref;
            pref.setSummary(listPref.getEntry());
        }
        if (pref instanceof EditTextPreference) {
            EditTextPreference editTextPref = (EditTextPreference) pref;
            if (pref.getKey().contains("password") || pref.getKey().contains("secret")) {
                pref.setSummary("******");
            } else if (pref.getKey().equals(MainApp.sResources.getString(R.string.key_danars_name))) {
                pref.setSummary(SP.getString(R.string.key_danars_name, ""));
            } else if (editTextPref.getText() != null && !editTextPref.getText().equals("")) {
                ((EditTextPreference) pref).setDialogMessage(editTextPref.getDialogMessage());
                pref.setSummary(editTextPref.getText());
            } else if (pref.getKey().contains("smscommunicator_allowednumbers") && TextUtils.isEmpty(editTextPref.getText().trim())) {
                pref.setSummary(MainApp.sResources.getString(R.string.smscommunicator_allowednumbers_summary));
            }
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
        private Integer id;

        @Override
        public void setArguments(Bundle args) {
            super.setArguments(args);
            id = args.getInt("id");
        }

        void addPreferencesFromResourceIfEnabled(PluginBase p, int type) {
            if (p.isEnabled(type) && p.getPreferencesId() != -1)
                addPreferencesFromResource(p.getPreferencesId());
        }

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            if (savedInstanceState != null && savedInstanceState.containsKey("id")) {
                id = savedInstanceState.getInt("id");
            }

            if (id != -1) {
                addPreferencesFromResource(id);
                addPreferencesFromResource(R.xml.pref_advanced);
            } else {
                if (!Config.NSCLIENT && !Config.G5UPLOADER) {
                    addPreferencesFromResource(R.xml.pref_password);
                }
                addPreferencesFromResource(R.xml.pref_age);
                addPreferencesFromResource(R.xml.pref_language);

                if (!Config.NSCLIENT && !Config.G5UPLOADER) {
                    addPreferencesFromResource(R.xml.pref_quickwizard);
                }
                addPreferencesFromResourceIfEnabled(SourceDexcomG5Plugin.getPlugin(), PluginBase.BGSOURCE);
                addPreferencesFromResourceIfEnabled(CareportalPlugin.getPlugin(), PluginBase.GENERAL);
                addPreferencesFromResourceIfEnabled(SafetyPlugin.getPlugin(), PluginBase.CONSTRAINTS);
                if (Config.APS) {
                    addPreferencesFromResourceIfEnabled(LoopPlugin.getPlugin(), PluginBase.LOOP);
                    addPreferencesFromResourceIfEnabled(OpenAPSMAPlugin.getPlugin(), PluginBase.APS);
                    addPreferencesFromResourceIfEnabled(OpenAPSAMAPlugin.getPlugin(), PluginBase.APS);
                }

                addPreferencesFromResourceIfEnabled(SensitivityAAPSPlugin.getPlugin(), PluginBase.SENSITIVITY);
                addPreferencesFromResourceIfEnabled(SensitivityWeightedAveragePlugin.getPlugin(), PluginBase.SENSITIVITY);
                addPreferencesFromResourceIfEnabled(SensitivityOref0Plugin.getPlugin(), PluginBase.SENSITIVITY);

                if (Config.DANAR) {
                    addPreferencesFromResourceIfEnabled(DanaRPlugin.getPlugin(), PluginBase.PUMP);
                    addPreferencesFromResourceIfEnabled(DanaRKoreanPlugin.getPlugin(), PluginBase.PUMP);
                    addPreferencesFromResourceIfEnabled(DanaRv2Plugin.getPlugin(), PluginBase.PUMP);
                    addPreferencesFromResourceIfEnabled(DanaRSPlugin.getPlugin(), PluginBase.PUMP);

                    if (DanaRPlugin.getPlugin().isEnabled(PluginBase.PROFILE)
                            || DanaRKoreanPlugin.getPlugin().isEnabled(PluginBase.PROFILE)
                            || DanaRv2Plugin.getPlugin().isEnabled(PluginBase.PROFILE)
                            || DanaRSPlugin.getPlugin().isEnabled(PluginBase.PROFILE)) {
                        addPreferencesFromResource(R.xml.pref_danarprofile);
                    }
                }

                if (!Config.NSCLIENT && !Config.G5UPLOADER) {
                    addPreferencesFromResourceIfEnabled(VirtualPumpPlugin.getPlugin(), PluginBase.PUMP);
                }

                addPreferencesFromResourceIfEnabled(InsulinOrefFreePeakPlugin.getPlugin(), PluginBase.INSULIN);

                addPreferencesFromResourceIfEnabled(NSClientInternalPlugin.getPlugin(), PluginBase.GENERAL);
                addPreferencesFromResourceIfEnabled(SmsCommunicatorPlugin.getPlugin(), PluginBase.GENERAL);

                if (!Config.NSCLIENT && !Config.G5UPLOADER) {
                    addPreferencesFromResource(R.xml.pref_others);
                }
                addPreferencesFromResource(R.xml.pref_advanced);

                addPreferencesFromResourceIfEnabled(WearPlugin.getPlugin(), PluginBase.GENERAL);
                addPreferencesFromResourceIfEnabled(StatuslinePlugin.getPlugin(), PluginBase.GENERAL);
            }

            initSummary(getPreferenceScreen());
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putInt("id", id);
        }

        public Preference getPreference(String key) {
            return findPreference(key);
        }
    }
}
