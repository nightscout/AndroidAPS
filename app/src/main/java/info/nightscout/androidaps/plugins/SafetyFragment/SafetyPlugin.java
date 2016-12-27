package info.nightscout.androidaps.plugins.SafetyFragment;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.Round;
import info.nightscout.utils.SafeParse;

/**
 * Created by mike on 05.08.2016.
 */
public class SafetyPlugin implements PluginBase, ConstraintsInterface {
    private static Logger log = LoggerFactory.getLogger(SafetyPlugin.class);

    @Override
    public String getFragmentClass() {
        return SafetyFragment.class.getName();
    }

    @Override
    public int getType() {
        return PluginBase.CONSTRAINTS;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.safety);
    }

    @Override
    public boolean isEnabled(int type) {
        return type == CONSTRAINTS;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return false;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {

    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
    }

    @Override
    public boolean isLoopEnabled() {
        return MainApp.getConfigBuilder().getPumpDescription().isTempBasalCapable;
    }

    /**
     * Constraints interface
     **/
    @Override
    public boolean isClosedModeEnabled() {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        String mode = SP.getString("aps_mode", "open");
        return mode.equals("closed") && BuildConfig.CLOSEDLOOP;
    }

    @Override
    public boolean isAutosensModeEnabled() {
        return true;
    }

    @Override
    public boolean isAMAModeEnabled() {
        return true;
    }

    @Override
    public Double applyBasalConstraints(Double absoluteRate) {
        Double origAbsoluteRate = absoluteRate;
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        Double maxBasal = SafeParse.stringToDouble(SP.getString("openapsma_max_basal", "1"));

        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (profile == null) return absoluteRate;
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
        if (profile == null) return percentRate;
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
        if (percentRateAfterConst < 100)
            percentRateAfterConst = Round.ceilTo((double) percentRateAfterConst, 10d).intValue();
        else percentRateAfterConst = Round.floorTo((double) percentRateAfterConst, 10d).intValue();

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
        if (insulin > BuildConfig.MAXBOLUS) insulin = Double.valueOf(BuildConfig.MAXBOLUS);
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

    @Override
    public Double applyMaxIOBConstraints(Double maxIob) {
        return maxIob;
    }

}
