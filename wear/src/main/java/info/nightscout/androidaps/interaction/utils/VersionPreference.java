package info.nightscout.androidaps.interaction.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.widget.Toast;

import info.nightscout.androidaps.BuildConfig;
import preference.WearListPreference;

/**
 * Created by adrian on 07/08/17.
 */

public class VersionPreference extends WearListPreference {
    public VersionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        entries = new CharSequence[]{BuildConfig.BUILDVERSION};
        entryValues = new CharSequence[]{BuildConfig.BUILDVERSION};
    }

    @Override public CharSequence getSummary(@NonNull final Context context) {
       return BuildConfig.BUILDVERSION;
    }
    @Override
    public void onPreferenceClick(@NonNull Context context) {
        Toast.makeText(context, "Build version:" + BuildConfig.BUILDVERSION, Toast.LENGTH_LONG).show();
    }
}
