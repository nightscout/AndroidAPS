package info.nightscout.androidaps.plugins.SafetyFragment;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
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

    private static final String PREFS_NAME = "Safety";

    EditText maxBolusEdit;
    EditText maxCarbsEdit;
    EditText maxBasalEdit;
    EditText maxBasalIOBEdit;

    Double maxBolus;
    Double maxCarbs;
    Double maxBasal;
    Double maxBasalIOB;

    boolean fragmentVisible = true;

    public SafetyFragment() {
        super();
        loadSettings();
    }

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
        return fragmentVisible;
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
        this.fragmentVisible = fragmentVisible;
    }

    public static SafetyFragment newInstance() {
        SafetyFragment fragment = new SafetyFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.safety_fragment, container, false);
        maxBolusEdit = (EditText) layout.findViewById(R.id.safety_maxbolus);
        maxCarbsEdit = (EditText) layout.findViewById(R.id.safety_maxcarbs);
        maxBasalEdit = (EditText) layout.findViewById(R.id.safety_maxbasal);
        maxBasalIOBEdit = (EditText) layout.findViewById(R.id.safety_maxiob);

        maxBolusEdit.setText(maxBolus.toString());
        maxCarbsEdit.setText(maxCarbs.toString());
        maxBasalEdit.setText(maxBasal.toString());
        maxBasalIOBEdit.setText(maxBasalIOB.toString());

        TextWatcher textWatch = new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
                try { maxBolus = Double.parseDouble(maxBolusEdit.getText().toString().replace(",", ".")); } catch (Exception e) {};
                try { maxCarbs = Double.parseDouble(maxCarbsEdit.getText().toString().replace(",", ".")); } catch (Exception e) {};
                try { maxBasal = Double.parseDouble(maxBasalEdit.getText().toString().replace(",", ".")); } catch (Exception e) {};
                try { maxBasalIOB = Double.parseDouble(maxBasalIOBEdit.getText().toString().replace(",", ".")); } catch (Exception e) {};
                storeSettings();
            }
        };
        maxBolusEdit.addTextChangedListener(textWatch);
        maxCarbsEdit.addTextChangedListener(textWatch);
        maxBasalEdit.addTextChangedListener(textWatch);
        maxBasalIOBEdit.addTextChangedListener(textWatch);

        return layout;
    }

    private void storeSettings() {
        if (Config.logPrefsChange)
            log.debug("Storing settings");
        SharedPreferences settings = MainApp.instance().getApplicationContext().getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putFloat("maxBolus", new Float(maxBolus));
        editor.putFloat("maxCarbs", new Float(maxCarbs));
        editor.putFloat("maxBasal", new Float(maxBasal));
        editor.putFloat("maxBasalIOB", new Float(maxBasalIOB));
        editor.commit();
    }

    private void loadSettings() {
        if (Config.logPrefsChange)
            log.debug("Loading stored settings");
        SharedPreferences settings = MainApp.instance().getApplicationContext().getSharedPreferences(PREFS_NAME, 0);

        if (settings.contains("maxBolus")) maxBolus = (double) settings.getFloat("maxBolus", 3); else maxBolus = 3d;
        if (settings.contains("maxCarbs")) maxCarbs = (double) settings.getFloat("maxCarbs", 48); else maxCarbs = 48d;
        if (settings.contains("maxBasal")) maxBasal = (double) settings.getFloat("maxBasal", 1); else maxBasal = 1d;
        if (settings.contains("maxBasalIOB")) maxBasalIOB = (double) settings.getFloat("maxBasalIOB", 1); else maxBasalIOB = 1d;
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
        NSProfile profile = MainActivity.getConfigBuilder().getActiveProfile().getProfile();
        if (result.rate < 0) result.rate = 0;

        Integer maxBasalMult = 4;
        Integer maxBasalFromDaily = 3;
        // Check percentRate but absolute rate too, because we know real current basal in pump
        Double origRate = result.rate;
        if (result.rate > maxBasal) {
            result.rate = maxBasal;
            if (Config.logConstrainsChnages)
                log.debug("Limiting rate " + origRate + " by maxBasal to " + result.rate + "U/h");
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
