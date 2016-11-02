package info.nightscout.androidaps.plugins.Overview;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;

/**
 * Created by mike on 05.08.2016.
 */
public class OverviewPlugin implements PluginBase {

    public static Double bgTargetLow = 80d;
    public static Double bgTargetHigh = 180d;

    public QuickWizard quickWizard = new QuickWizard();

    public OverviewPlugin() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        String storedData = preferences.getString("QuickWizard", "[]");
        try {
            quickWizard.setData(new JSONArray(storedData));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getFragmentClass() {
        return OverviewFragment.class.getName();
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.overview);
    }

    @Override
    public boolean isEnabled(int type) {
        return true;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return true;
    }

    @Override
    public boolean canBeHidden(int type) {
        return false;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        // Always enabled
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        // Always visible
    }

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }


}
