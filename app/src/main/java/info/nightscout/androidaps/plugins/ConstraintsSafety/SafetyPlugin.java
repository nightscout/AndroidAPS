package info.nightscout.androidaps.plugins.ConstraintsSafety;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.utils.HardLimits;
import info.nightscout.utils.Round;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class SafetyPlugin implements PluginBase, ConstraintsInterface {
    private static Logger log = LoggerFactory.getLogger(SafetyPlugin.class);

    static SafetyPlugin plugin = null;

    public static SafetyPlugin getPlugin() {
        if (plugin == null)
            plugin = new SafetyPlugin();
        return plugin;
    }

    @Override
    public String getFragmentClass() {
        return null;
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
    public String getNameShort() {
        // use long name as fallback (no tabs)
        return getName();
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
    public boolean hasFragment() {
        return false;
    }

    @Override
    public boolean showInList(int type) {
        return false;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {

    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
    }

    @Override
    public int getPreferencesId() {
        return R.xml.pref_safety;
    }

    /**
     * Constraints interface
     **/
    @Override
    public Constraint<Boolean> isLoopInvokationAllowed(Constraint<Boolean> value) {
        if (!ConfigBuilderPlugin.getActivePump().getPumpDescription().isTempBasalCapable)
            value.set(false, MainApp.gs(R.string.pumpisnottempbasalcapable));
        return value;
    }

    @Override
    public Constraint<Boolean> isClosedLoopAllowed(Constraint<Boolean> value) {
        if (!MainApp.isEngineeringModeOrRelease())
            value.set(false, MainApp.gs(R.string.closed_loop_disabled_on_dev_branch));

        String mode = SP.getString("aps_mode", "open");
        if (!mode.equals("closed"))
            value.set(false, MainApp.gs(R.string.closedmodedisabledinpreferences));
        return value;
    }

    @Override
    public Constraint<Boolean> isAutosensModeEnabled(Constraint<Boolean> value) {
        return value;
    }

    @Override
    public Constraint<Boolean> isAMAModeEnabled(Constraint<Boolean> value) {
        boolean enabled = SP.getBoolean("openapsama_useautosens", false);
        if (!enabled)
            value.set(false, MainApp.gs(R.string.amadisabledinpreferences));
        return value;
    }

    @Override
    public Constraint<Boolean> isSMBModeEnabled(Constraint<Boolean> value) {
        boolean enabled = SP.getBoolean(R.string.key_use_smb, false);
        if (!enabled)
            value.set(false, MainApp.gs(R.string.smbdisabledinpreferences));
        return value;
    }

    @Override
    public Constraint<Double> applyBasalConstraints(Constraint<Double> absoluteRate, Profile profile) {

        absoluteRate.setIfGreater(0d, String.format(MainApp.gs(R.string.limitingbasalratio), 0d, MainApp.gs(R.string.basalmustbepositivevalue)));

        double maxBasal = SP.getDouble(R.string.key_openapsma_max_basal, 1d);
        absoluteRate.setIfSmaller(maxBasal, String.format(MainApp.gs(R.string.limitingbasalratio), maxBasal, MainApp.gs(R.string.maxbasalinpreferences)));

        // Check percentRate but absolute rate too, because we know real current basal in pump
        Double maxBasalMult = SP.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4d);
        double maxFromBasalMult = Math.floor(maxBasalMult * profile.getBasal() * 100) / 100;
        absoluteRate.setIfSmaller(maxFromBasalMult, String.format(MainApp.gs(R.string.limitingbasalratio), maxFromBasalMult, MainApp.gs(R.string.maxbasalmultiplier)));

        Double maxBasalFromDaily = SP.getDouble(R.string.key_openapsama_max_daily_safety_multiplier, 3d);
        double maxFromDaily = Math.floor(profile.getMaxDailyBasal() * maxBasalFromDaily * 100) / 100;
        absoluteRate.setIfSmaller(maxFromDaily, String.format(MainApp.gs(R.string.limitingbasalratio), maxFromDaily, MainApp.gs(R.string.maxdailybasalmultiplier)));
        return absoluteRate;
    }

    @Override
    public Integer applyBasalPercentConstraints(Integer percentRate) {
        Integer origPercentRate = percentRate;
        Double maxBasal = SP.getDouble(R.string.key_openapsma_max_basal, 1d);

        Profile profile = MainApp.getConfigBuilder().getProfile();
        if (profile == null) return percentRate;
        Double currentBasal = profile.getBasal();

        Double absoluteRate = currentBasal * ((double) percentRate / 100);

        if (Config.logConstraintsChanges)
            log.debug("Percent rate " + percentRate + "% recalculated to " + absoluteRate + "U/h with current basal " + currentBasal + "U/h");

        if (absoluteRate < 0) absoluteRate = 0d;

        Double maxBasalMult = SP.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4d);
        Integer maxBasalFromDaily = SP.getInt(R.string.key_openapsama_max_daily_safety_multiplier, 3);
        // Check percentRate but absolute rate too, because we know real current basal in pump
        Double origRate = absoluteRate;
        if (absoluteRate > maxBasal) {
            absoluteRate = maxBasal;
            if (Config.logConstraintsChanges && !Objects.equals(origPercentRate, Constants.basalPercentOnlyForCheckLimit))
                log.debug("Limiting rate " + origRate + " by maxBasal preference to " + absoluteRate + "U/h");
        }
        if (absoluteRate > maxBasalMult * profile.getBasal()) {
            absoluteRate = Math.floor(maxBasalMult * profile.getBasal() * 100) / 100;
            if (Config.logConstraintsChanges && !Objects.equals(origPercentRate, Constants.basalPercentOnlyForCheckLimit))
                log.debug("Limiting rate " + origRate + " by maxBasalMult to " + absoluteRate + "U/h");
        }
        if (absoluteRate > profile.getMaxDailyBasal() * maxBasalFromDaily) {
            absoluteRate = profile.getMaxDailyBasal() * maxBasalFromDaily;
            if (Config.logConstraintsChanges && !Objects.equals(origPercentRate, Constants.basalPercentOnlyForCheckLimit))
                log.debug("Limiting rate " + origRate + " by 3 * maxDailyBasal to " + absoluteRate + "U/h");
        }

        Integer percentRateAfterConst = new Double(absoluteRate / currentBasal * 100).intValue();
        if (percentRateAfterConst < 100)
            percentRateAfterConst = Round.ceilTo((double) percentRateAfterConst, 10d).intValue();
        else percentRateAfterConst = Round.floorTo((double) percentRateAfterConst, 10d).intValue();

        if (Config.logConstraintsChanges && !Objects.equals(origPercentRate, Constants.basalPercentOnlyForCheckLimit))
            log.debug("Recalculated percent rate " + percentRate + "% to " + percentRateAfterConst + "%");
        return percentRateAfterConst;
    }

    @Override
    public Double applyBolusConstraints(Double insulin) {
        try {
            Double maxBolus = SP.getDouble("treatmentssafety_maxbolus", 3d);

            if (insulin < 0) insulin = 0d;
            if (insulin > maxBolus) insulin = maxBolus;
        } catch (Exception e) {
            insulin = 0d;
        }
        if (insulin > HardLimits.maxBolus()) insulin = HardLimits.maxBolus();
        return insulin;
    }

    @Override
    public Integer applyCarbsConstraints(Integer carbs) {
        try {
            Integer maxCarbs = SP.getInt("treatmentssafety_maxcarbs", 48);

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
