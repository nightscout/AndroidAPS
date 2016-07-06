package info.nightscout.androidaps;

import android.content.SharedPreferences;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.os.Bundle;

import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.utils.LocaleHelper;

public class PreferencesActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        MainApp.bus().post(new EventPreferenceChange());
        if (key.equals("language")) {
            SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String lang = SP.getString("language", "en");
            LocaleHelper.setLocale(getApplicationContext(), lang);
            recreate();
            MainApp.bus().post(new EventRefreshGui());
        }
    }

    public static class MyPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_language);
            addPreferencesFromResource(R.xml.pref_treatments);
            if (Config.APS)
                addPreferencesFromResource(R.xml.pref_closedmode);
            if (Config.OPENAPSMAENABLED)
                addPreferencesFromResource(R.xml.pref_openapsma);
            if (Config.LOWSUSPEDENABLED)
                addPreferencesFromResource(R.xml.pref_lowsuspend);
            if (Config.CAREPORTALENABLED)
                addPreferencesFromResource(R.xml.pref_careportal);

        }
    }
}