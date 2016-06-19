package info.nightscout.androidaps.plugins.SafetyFragment;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.ConstrainsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.plugins.APSResult;
import info.nightscout.client.data.NSProfile;

public class SafetyFragment extends Fragment implements PluginBase, ConstrainsInterface {
    private static Logger log = LoggerFactory.getLogger(SafetyFragment.class);


    @Override
    public int getType() {
        return PluginBase.CONSTRAINS;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.safety);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isVisibleInTabs() {
        return false;
    }

    @Override
    public boolean canBeHidden() {
        return true;
    }

    @Override
    public void setFragmentEnabled(boolean fragmentEnabled) {

    }

    @Override
    public void setFragmentVisible(boolean fragmentVisible) {
    }

    public static SafetyFragment newInstance() {
        SafetyFragment fragment = new SafetyFragment();
        return fragment;
    }

    /**
     * Constrains interface
     **/
    @Override
    public boolean isAutomaticProcessingEnabled() {
        return true;
    }

    @Override
    public boolean manualConfirmationNeeded() {
        return false;
    }

    @Override
    public APSResult applyBasalConstrains(APSResult result) {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        Double maxBasal = Double.parseDouble(SP.getString("openapsma_max_basal", "1").replace(",", "."));

        NSProfile profile = MainActivity.getConfigBuilder().getActiveProfile().getProfile();
        if (result.rate < 0) result.rate = 0;

        Integer maxBasalMult = 4;
        Integer maxBasalFromDaily = 3;
        // Check percentRate but absolute rate too, because we know real current basal in pump
        Double origRate = result.rate;
        if (result.rate > maxBasal) {
            result.rate = maxBasal;
            if (Config.logConstrainsChnages)
                log.debug("Limiting rate " + origRate + " by maxBasal preference to " + result.rate + "U/h");
        }
        if (result.rate > maxBasalMult * profile.getBasal(NSProfile.secondsFromMidnight())) {
            result.rate = Math.floor(maxBasalMult * profile.getBasal(NSProfile.secondsFromMidnight()) * 100) / 100;
            if (Config.logConstrainsChnages)
                log.debug("Limiting rate " + origRate + " by maxBasalMult to " + result.rate + "U/h");
        }
        if (result.rate > profile.getMaxDailyBasal() * maxBasalFromDaily) {
            result.rate = profile.getMaxDailyBasal() * maxBasalFromDaily;
            if (Config.logConstrainsChnages)
                log.debug("Limiting rate " + origRate + " by 3 * maxDailyBasal to " + result.rate + "U/h");
        }
        return result;
    }

}
