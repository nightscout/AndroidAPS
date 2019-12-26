package info.nightscout.androidaps.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;

import java.util.Arrays;

import javax.inject.Inject;

import dagger.android.AndroidInjection;
import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRebuildTabs;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.insulin.InsulinOrefFreePeakPlugin;
import info.nightscout.androidaps.utils.LocaleHelper;
import info.nightscout.androidaps.utils.OKDialog;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.SafeParse;

public class PreferencesActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener, HasAndroidInjector {
    MyPreferenceFragment myPreferenceFragment;

    @Inject
    DispatchingAndroidInjector<Object> androidInjector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AndroidInjection.inject(this);
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
    public AndroidInjector<Object> androidInjector() {
        return androidInjector;
    }

    @Override
    public void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.INSTANCE.wrap(newBase));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        RxBus.INSTANCE.send(new EventPreferenceChange(key));
        if (key.equals(MainApp.gs(R.string.key_language))) {
            RxBus.INSTANCE.send(new EventRebuildTabs(true));
            //recreate() does not update language so better close settings
            finish();
        }
        if (key.equals(MainApp.gs(R.string.key_short_tabtitles))) {
            RxBus.INSTANCE.send(new EventRebuildTabs());
        }
        if (key.equals(MainApp.gs(R.string.key_units))) {
            recreate();
            return;
        }
        if (key.equals(MainApp.gs(R.string.key_openapsama_useautosens)) && SP.getBoolean(R.string.key_openapsama_useautosens, false)) {
            OKDialog.show(this, MainApp.gs(R.string.configbuilder_sensitivity), MainApp.gs(R.string.sensitivity_warning));
        }
        updatePrefSummary(myPreferenceFragment.findPreference(key));
    }

    private static void adjustUnitDependentPrefs(Preference pref) {
        // convert preferences values to current units
        String[] unitDependent = new String[]{
                MainApp.gs(R.string.key_hypo_target),
                MainApp.gs(R.string.key_activity_target),
                MainApp.gs(R.string.key_eatingsoon_target),
                MainApp.gs(R.string.key_high_mark),
                MainApp.gs(R.string.key_low_mark)
        };
        if (Arrays.asList(unitDependent).contains(pref.getKey())) {
            EditTextPreference editTextPref = (EditTextPreference) pref;
            String converted = Profile.toCurrentUnitsString(SafeParse.stringToDouble(editTextPref.getText()));
            editTextPref.setSummary(converted);
            editTextPref.setText(converted);
        }
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
            } else if (editTextPref.getText() != null) {
                ((EditTextPreference) pref).setDialogMessage(editTextPref.getDialogMessage());
                pref.setSummary(editTextPref.getText());
            } else {
                for (PluginBase plugin : MainApp.getPluginsList()) {
                    plugin.updatePreferenceSummary(pref);
                }
            }
        }
        if (pref != null)
            adjustUnitDependentPrefs(pref);
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

}
