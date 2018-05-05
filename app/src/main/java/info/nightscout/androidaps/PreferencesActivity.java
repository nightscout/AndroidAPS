package info.nightscout.androidaps;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.Careportal.CareportalPlugin;
import info.nightscout.androidaps.plugins.ConstraintsSafety.SafetyPlugin;
import info.nightscout.androidaps.plugins.Insulin.InsulinOrefFreePeakPlugin;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.NSClientPlugin;
import info.nightscout.androidaps.plugins.OpenAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.OpenAPSMA.OpenAPSMAPlugin;
import info.nightscout.androidaps.plugins.OpenAPSSMB.OpenAPSSMBPlugin;
import info.nightscout.androidaps.plugins.PumpCombo.ComboPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRS.DanaRSPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRv2.DanaRv2Plugin;
import info.nightscout.androidaps.plugins.PumpInsight.InsightPlugin;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.SensitivityAAPS.SensitivityAAPSPlugin;
import info.nightscout.androidaps.plugins.SensitivityOref0.SensitivityOref0Plugin;
import info.nightscout.androidaps.plugins.SensitivityWeightedAverage.SensitivityWeightedAveragePlugin;
import info.nightscout.androidaps.plugins.SmsCommunicator.SmsCommunicatorPlugin;
import info.nightscout.androidaps.plugins.Source.SourceDexcomG5Plugin;
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
        if (key.equals(MainApp.gs(R.string.key_openapsama_useautosens)) && SP.getBoolean(R.string.key_openapsama_useautosens, false)) {
            OKDialog.show(this, MainApp.gs(R.string.configbuilder_sensitivity), MainApp.gs(R.string.sensitivity_warning), null);
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
            } else if (pref.getKey().equals(MainApp.gs(R.string.key_danars_name))) {
                pref.setSummary(SP.getString(R.string.key_danars_name, ""));
            } else if (editTextPref.getText() != null && !editTextPref.getText().equals("")) {
                ((EditTextPreference) pref).setDialogMessage(editTextPref.getDialogMessage());
                pref.setSummary(editTextPref.getText());
            } else if (pref.getKey().contains("smscommunicator_allowednumbers") && TextUtils.isEmpty(editTextPref.getText().trim())) {
                pref.setSummary(MainApp.gs(R.string.smscommunicator_allowednumbers_summary));
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

        void addPreferencesFromResourceIfEnabled(PluginBase p, PluginType type) {
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
            } else {

                if (!Config.NSCLIENT && !Config.G5UPLOADER) {
                    addPreferencesFromResource(R.xml.pref_password);
                }
                addPreferencesFromResource(R.xml.pref_age);
                addPreferencesFromResource(R.xml.pref_language);

                addPreferencesFromResource(R.xml.pref_overview);

                addPreferencesFromResourceIfEnabled(SourceDexcomG5Plugin.getPlugin(), PluginType.BGSOURCE);
                addPreferencesFromResourceIfEnabled(CareportalPlugin.getPlugin(), PluginType.GENERAL);
                addPreferencesFromResourceIfEnabled(SafetyPlugin.getPlugin(), PluginType.CONSTRAINTS);
                if (Config.APS) {
                    addPreferencesFromResourceIfEnabled(LoopPlugin.getPlugin(), PluginType.LOOP);
                    addPreferencesFromResourceIfEnabled(OpenAPSMAPlugin.getPlugin(), PluginType.APS);
                    addPreferencesFromResourceIfEnabled(OpenAPSAMAPlugin.getPlugin(), PluginType.APS);
                    addPreferencesFromResourceIfEnabled(OpenAPSSMBPlugin.getPlugin(), PluginType.APS);
                }

                addPreferencesFromResourceIfEnabled(SensitivityAAPSPlugin.getPlugin(), PluginType.SENSITIVITY);
                addPreferencesFromResourceIfEnabled(SensitivityWeightedAveragePlugin.getPlugin(), PluginType.SENSITIVITY);
                addPreferencesFromResourceIfEnabled(SensitivityOref0Plugin.getPlugin(), PluginType.SENSITIVITY);

                if (Config.HWPUMPS) {
                    addPreferencesFromResourceIfEnabled(DanaRPlugin.getPlugin(), PluginType.PUMP);
                    addPreferencesFromResourceIfEnabled(DanaRKoreanPlugin.getPlugin(), PluginType.PUMP);
                    addPreferencesFromResourceIfEnabled(DanaRv2Plugin.getPlugin(), PluginType.PUMP);
                    addPreferencesFromResourceIfEnabled(DanaRSPlugin.getPlugin(), PluginType.PUMP);
                    addPreferencesFromResourceIfEnabled(InsightPlugin.getPlugin(), PluginType.PUMP);
                    addPreferencesFromResourceIfEnabled(ComboPlugin.getPlugin(), PluginType.PUMP);

                    if (DanaRPlugin.getPlugin().isEnabled(PluginType.PROFILE)
                            || DanaRKoreanPlugin.getPlugin().isEnabled(PluginType.PROFILE)
                            || DanaRv2Plugin.getPlugin().isEnabled(PluginType.PROFILE)
                            || DanaRSPlugin.getPlugin().isEnabled(PluginType.PROFILE)) {
                        addPreferencesFromResource(R.xml.pref_danarprofile);
                    }
                }

                if (!Config.NSCLIENT && !Config.G5UPLOADER) {
                    addPreferencesFromResourceIfEnabled(VirtualPumpPlugin.getPlugin(), PluginType.PUMP);
                }

                addPreferencesFromResourceIfEnabled(InsulinOrefFreePeakPlugin.getPlugin(), PluginType.INSULIN);

                addPreferencesFromResourceIfEnabled(NSClientPlugin.getPlugin(), PluginType.GENERAL);
                addPreferencesFromResourceIfEnabled(SmsCommunicatorPlugin.getPlugin(), PluginType.GENERAL);

                if (!Config.NSCLIENT && !Config.G5UPLOADER) {
                    addPreferencesFromResource(R.xml.pref_others);
                }
                addPreferencesFromResource(R.xml.pref_datachoices);

                addPreferencesFromResourceIfEnabled(WearPlugin.getPlugin(), PluginType.GENERAL);
                addPreferencesFromResourceIfEnabled(StatuslinePlugin.getPlugin(), PluginType.GENERAL);
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
