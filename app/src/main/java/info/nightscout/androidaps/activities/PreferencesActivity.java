package info.nightscout.androidaps.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.TextUtils;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRebuildTabs;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.general.careportal.CareportalPlugin;
import info.nightscout.androidaps.plugins.constraints.safety.SafetyPlugin;
import info.nightscout.androidaps.plugins.general.tidepool.TidepoolPlugin;
import info.nightscout.androidaps.plugins.general.tidepool.comm.TidepoolUploader;
import info.nightscout.androidaps.plugins.insulin.InsulinOrefFreePeakPlugin;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.NSClientPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSMA.OpenAPSMAPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin;
import info.nightscout.androidaps.plugins.pump.combo.ComboPlugin;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin;
import info.nightscout.androidaps.plugins.pump.danaRv2.DanaRv2Plugin;
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityAAPSPlugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref0Plugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityWeightedAveragePlugin;
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin;
import info.nightscout.androidaps.plugins.general.wear.WearPlugin;
import info.nightscout.androidaps.plugins.general.xdripStatusline.StatuslinePlugin;
import info.nightscout.androidaps.plugins.source.SourceDexcomPlugin;
import info.nightscout.androidaps.utils.LocaleHelper;
import info.nightscout.androidaps.utils.OKDialog;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.plugins.general.automation.AutomationPlugin;

public class PreferencesActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    MyPreferenceFragment myPreferenceFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme_NoActionBar);
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
        RxBus.INSTANCE.send(new EventPreferenceChange(key));
        if (key.equals("language")) {
            RxBus.INSTANCE.send(new EventRebuildTabs(true));
            //recreate() does not update language so better close settings
            finish();
        }
        if (key.equals("short_tabtitles")) {
            RxBus.INSTANCE.send(new EventRebuildTabs());
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
            } else if (editTextPref.getText() != null) {
                ((EditTextPreference) pref).setDialogMessage(editTextPref.getDialogMessage());
                pref.setSummary(editTextPref.getText());
            } else if (pref.getKey().contains("smscommunicator_allowednumbers") && (editTextPref.getText() == null || TextUtils.isEmpty(editTextPref.getText().trim()))) {
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

                if (!Config.NSCLIENT) {
                    addPreferencesFromResource(R.xml.pref_password);
                }
                addPreferencesFromResource(R.xml.pref_age);
                addPreferencesFromResource(R.xml.pref_language);

                addPreferencesFromResource(R.xml.pref_overview);

                addPreferencesFromResourceIfEnabled(SourceDexcomPlugin.INSTANCE, PluginType.BGSOURCE);
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
                addPreferencesFromResourceIfEnabled(SensitivityOref1Plugin.getPlugin(), PluginType.SENSITIVITY);

                if (Config.PUMPDRIVERS) {
                    addPreferencesFromResourceIfEnabled(DanaRPlugin.getPlugin(), PluginType.PUMP);
                    addPreferencesFromResourceIfEnabled(DanaRKoreanPlugin.getPlugin(), PluginType.PUMP);
                    addPreferencesFromResourceIfEnabled(DanaRv2Plugin.getPlugin(), PluginType.PUMP);
                    addPreferencesFromResourceIfEnabled(DanaRSPlugin.getPlugin(), PluginType.PUMP);
                    addPreferencesFromResourceIfEnabled(LocalInsightPlugin.getPlugin(), PluginType.PUMP);
                    addPreferencesFromResourceIfEnabled(ComboPlugin.getPlugin(), PluginType.PUMP);
                    addPreferencesFromResourceIfEnabled(MedtronicPumpPlugin.getPlugin(), PluginType.PUMP);

                    if (DanaRPlugin.getPlugin().isEnabled(PluginType.PROFILE)
                            || DanaRKoreanPlugin.getPlugin().isEnabled(PluginType.PROFILE)
                            || DanaRv2Plugin.getPlugin().isEnabled(PluginType.PROFILE)
                            || DanaRSPlugin.getPlugin().isEnabled(PluginType.PROFILE)) {
                        addPreferencesFromResource(R.xml.pref_danarprofile);
                    }
                }

                if (!Config.NSCLIENT) {
                    addPreferencesFromResourceIfEnabled(VirtualPumpPlugin.getPlugin(), PluginType.PUMP);
                }

                addPreferencesFromResourceIfEnabled(InsulinOrefFreePeakPlugin.getPlugin(), PluginType.INSULIN);

                addPreferencesFromResourceIfEnabled(NSClientPlugin.getPlugin(), PluginType.GENERAL);
                addPreferencesFromResourceIfEnabled(TidepoolPlugin.INSTANCE, PluginType.GENERAL);
                addPreferencesFromResourceIfEnabled(SmsCommunicatorPlugin.getPlugin(), PluginType.GENERAL);
                addPreferencesFromResourceIfEnabled(AutomationPlugin.INSTANCE, PluginType.GENERAL);

                addPreferencesFromResource(R.xml.pref_others);
                addPreferencesFromResource(R.xml.pref_datachoices);

                addPreferencesFromResourceIfEnabled(WearPlugin.getPlugin(), PluginType.GENERAL);
                addPreferencesFromResourceIfEnabled(StatuslinePlugin.getPlugin(), PluginType.GENERAL);
            }

            if (Config.NSCLIENT) {
                PreferenceScreen scrnAdvancedSettings = (PreferenceScreen) findPreference(getString(R.string.key_advancedsettings));
                if (scrnAdvancedSettings != null) {
                    scrnAdvancedSettings.removePreference(getPreference(getString(R.string.key_statuslights_res_warning)));
                    scrnAdvancedSettings.removePreference(getPreference(getString(R.string.key_statuslights_res_critical)));
                    scrnAdvancedSettings.removePreference(getPreference(getString(R.string.key_statuslights_bat_warning)));
                    scrnAdvancedSettings.removePreference(getPreference(getString(R.string.key_statuslights_bat_critical)));
                    scrnAdvancedSettings.removePreference(getPreference(getString(R.string.key_show_statuslights)));
                    scrnAdvancedSettings.removePreference(getPreference(getString(R.string.key_show_statuslights_extended)));
                }
            }

            initSummary(getPreferenceScreen());

            final Preference tidepoolTestLogin = findPreference(MainApp.gs(R.string.key_tidepool_test_login));
            if (tidepoolTestLogin != null)
                tidepoolTestLogin.setOnPreferenceClickListener(preference -> {
                    TidepoolUploader.INSTANCE.testLogin(getActivity());
                    return false;
                });
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
