package info.nightscout.androidaps.plugins.SafetyFragment;


import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.APSResult;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.Round;
import info.nightscout.utils.SafeParse;

public class SafetyFragment extends Fragment implements PluginBase, ConstraintsInterface {
    private static Logger log = LoggerFactory.getLogger(SafetyFragment.class);


    @Override
    public int getType() {
        return PluginBase.CONSTRAINTS;
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
     * Constraints interface
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
    public APSResult applyBasalConstraints(APSResult result) {
        result.rate = applyBasalConstraints(result.rate);
        return result;
    }

    @Override
    public Double applyBasalConstraints(Double absoluteRate) {
        Double origAbsoluteRate = absoluteRate;
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        Double maxBasal = SafeParse.stringToDouble(SP.getString("openapsma_max_basal", "1"));

        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (absoluteRate < 0) absoluteRate = 0d;

        Integer maxBasalMult = 4;
        Integer maxBasalFromDaily = 3;
        // Check percentRate but absolute rate too, because we know real current basal in pump
        Double origRate = absoluteRate;
        if (absoluteRate > maxBasal) {
            absoluteRate = maxBasal;
            if (Config.logConstraintsChanges && origAbsoluteRate != Constants.basalAbsoluteOnlyForCheckLimit)
                log.debug("Limiting rate " + origRate + " by maxBasal preference to " + absoluteRate + "U/h");
        }
        if (absoluteRate > maxBasalMult * profile.getBasal(NSProfile.secondsFromMidnight())) {
            absoluteRate = Math.floor(maxBasalMult * profile.getBasal(NSProfile.secondsFromMidnight()) * 100) / 100;
            if (Config.logConstraintsChanges && origAbsoluteRate != Constants.basalAbsoluteOnlyForCheckLimit)
                log.debug("Limiting rate " + origRate + " by maxBasalMult to " + absoluteRate + "U/h");
        }
        if (absoluteRate > profile.getMaxDailyBasal() * maxBasalFromDaily) {
            absoluteRate = profile.getMaxDailyBasal() * maxBasalFromDaily;
            if (Config.logConstraintsChanges && origAbsoluteRate != Constants.basalAbsoluteOnlyForCheckLimit)
                log.debug("Limiting rate " + origRate + " by 3 * maxDailyBasal to " + absoluteRate + "U/h");
        }
        return absoluteRate;
    }

    @Override
    public Integer applyBasalConstraints(Integer percentRate) {
        Integer origPercentRate = percentRate;
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        Double maxBasal = SafeParse.stringToDouble(SP.getString("openapsma_max_basal", "1"));

        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        Double currentBasal = profile.getBasal(profile.secondsFromMidnight());

        Double absoluteRate = currentBasal * ((double) percentRate / 100);

        if (Config.logConstraintsChanges)
            log.debug("Percent rate " + percentRate + "% recalculated to " + absoluteRate + "U/h with current basal " + currentBasal + "U/h");

        if (absoluteRate < 0) absoluteRate = 0d;

        Integer maxBasalMult = 4;
        Integer maxBasalFromDaily = 3;
        // Check percentRate but absolute rate too, because we know real current basal in pump
        Double origRate = absoluteRate;
        if (absoluteRate > maxBasal) {
            absoluteRate = maxBasal;
            if (Config.logConstraintsChanges && origPercentRate != Constants.basalPercentOnlyForCheckLimit)
                log.debug("Limiting rate " + origRate + " by maxBasal preference to " + absoluteRate + "U/h");
        }
        if (absoluteRate > maxBasalMult * profile.getBasal(NSProfile.secondsFromMidnight())) {
            absoluteRate = Math.floor(maxBasalMult * profile.getBasal(NSProfile.secondsFromMidnight()) * 100) / 100;
            if (Config.logConstraintsChanges && origPercentRate != Constants.basalPercentOnlyForCheckLimit)
                log.debug("Limiting rate " + origRate + " by maxBasalMult to " + absoluteRate + "U/h");
        }
        if (absoluteRate > profile.getMaxDailyBasal() * maxBasalFromDaily) {
            absoluteRate = profile.getMaxDailyBasal() * maxBasalFromDaily;
            if (Config.logConstraintsChanges && origPercentRate != Constants.basalPercentOnlyForCheckLimit)
                log.debug("Limiting rate " + origRate + " by 3 * maxDailyBasal to " + absoluteRate + "U/h");
        }

        Integer percentRateAfterConst = new Double(absoluteRate / currentBasal * 100).intValue();
        if (percentRateAfterConst < 100) Round.ceilTo(absoluteRate, 10d).intValue();
        else  Round.floorTo(absoluteRate, 10d).intValue();

        if (Config.logConstraintsChanges && origPercentRate != Constants.basalPercentOnlyForCheckLimit)
            log.debug("Recalculated percent rate " + percentRate + "% to " + percentRateAfterConst + "%");
        return percentRateAfterConst;
    }

    @Override
    public Double applyBolusConstraints(Double insulin) {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        try {
            Double maxBolus = SafeParse.stringToDouble(SP.getString("treatmentssafety_maxbolus", "3"));

            if (insulin < 0) insulin = 0d;
            if (insulin > maxBolus) insulin = maxBolus;
        } catch (Exception e) {
            insulin = 0d;
        }
        return insulin;
    }

    @Override
    public Integer applyCarbsConstraints(Integer carbs) {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        try {
            Integer maxCarbs = Integer.parseInt(SP.getString("treatmentssafety_maxcarbs", "48"));

            if (carbs < 0) carbs = 0;
            if (carbs > maxCarbs) carbs = maxCarbs;
        } catch (Exception e) {
            carbs = 0;
        }
        return carbs;
    }

}
