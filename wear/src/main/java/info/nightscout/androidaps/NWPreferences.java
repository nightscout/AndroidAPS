package info.nightscout.androidaps;


import android.os.Bundle;
import android.preference.PreferenceActivity;

import info.nightscout.androidaps.R;

public class NWPreferences extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }

}