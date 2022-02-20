package info.nightscout.androidaps.interaction;


import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;

import info.nightscout.androidaps.R;
import preference.WearPreferenceActivity;

public class AAPSPreferences extends WearPreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        ViewGroup view = (ViewGroup) getWindow().getDecorView();
        removeBackgroundRecursively(view);
        view.setBackground(ContextCompat.getDrawable(this, R.drawable.settings_background));
        view.requestFocus();
    }

    void removeBackgroundRecursively(View parent) {
        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            for (int i = 0; i < group.getChildCount(); i++) {
                removeBackgroundRecursively(group.getChildAt(i));
            }
        }
        parent.setBackground(null);
    }

}
