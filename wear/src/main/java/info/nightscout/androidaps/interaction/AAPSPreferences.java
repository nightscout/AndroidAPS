package info.nightscout.androidaps.interaction;


import android.os.Bundle;
import android.preference.PreferenceActivity;

import info.nightscout.androidaps.R;
import preference.WearPreferenceActivity;

public class AAPSPreferences extends WearPreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    protected void onPause(){
        super.onPause();
        finish();
    }

}