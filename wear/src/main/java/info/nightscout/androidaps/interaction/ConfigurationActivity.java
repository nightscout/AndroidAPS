package info.nightscout.androidaps.interaction;

import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import info.nightscout.androidaps.R;
import preference.WearPreferenceActivity;

public class ConfigurationActivity extends WearPreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Watchface");
        String configFileName=getIntent().getAction();
        int resXmlId = getResources().getIdentifier(configFileName, "xml", getApplicationContext().getPackageName());
        Log.d("ConfigurationActivity::onCreate --->> getIntent().getAction()",configFileName);
        Log.d("ConfigurationActivity::onCreate --->> resXmlId",String.valueOf(resXmlId));
        addPreferencesFromResource(resXmlId);
        ViewGroup view = (ViewGroup) getWindow().getDecorView();
        removeBackgroundRecursively(view);
        view.setBackground(getResources().getDrawable(R.drawable.settings_background));
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
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
