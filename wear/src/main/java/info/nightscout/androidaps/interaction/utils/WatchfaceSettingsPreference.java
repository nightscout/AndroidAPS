package info.nightscout.androidaps.interaction.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Toast;

import androidx.annotation.NonNull;

import preference.WearListPreference;


public class WatchfaceSettingsPreference extends WearListPreference {
    private final String pref_moreWatchfaceSettings;
    private final String pref_lookInYourWatchfaceConfiguration;

    public WatchfaceSettingsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        this.pref_moreWatchfaceSettings =context.getResources().getString(context.getResources().getIdentifier("pref_moreWatchfaceSettings", "string", context.getApplicationContext().getPackageName()));
        this.pref_lookInYourWatchfaceConfiguration=context.getResources().getString(context.getResources().getIdentifier("pref_lookInYourWatchfaceConfiguration", "string", context.getApplicationContext().getPackageName()));

        entries = new CharSequence[]{pref_moreWatchfaceSettings};
        entryValues = new CharSequence[]{""};
    }

    @Override public CharSequence getSummary(@NonNull final Context context) {
       return "";
    }
    @Override
    public void onPreferenceClick(@NonNull Context context) {
        Toast.makeText(context, pref_lookInYourWatchfaceConfiguration, Toast.LENGTH_LONG).show();
    }
}
