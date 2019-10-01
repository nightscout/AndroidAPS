package info.nightscout.androidaps.plugins.constraints.safety;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ConstraintChecker;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSMA.OpenAPSMAPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.HardLimits;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class SafetyPlugin extends PluginBase implements ConstraintsInterface {

    static SafetyPlugin plugin = null;

    public static SafetyPlugin getPlugin() {
        if (plugin == null)
            plugin = new SafetyPlugin();
        return plugin;
    }

    public SafetyPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.CONSTRAINTS)
                .neverVisible(true)
                .alwaysEnabled(true)
                .showInList(false)
                .pluginName(R.string.safety)
                .preferencesId(R.xml.pref_safety)
        );
    }

    /**
     * Constraints interface
     **/
    @Override
    public Constraint<Boolean> isLoopInvocationAllowed(Constraint<Boolean> value) {
        if (!ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription().isTempBasalCapable)
            value.set(false, MainApp.gs(R.string.pumpisnottempbasalcapable), this);
        return value;
    }

    @Override
    public Constraint<Boolean> isClosedLoopAllowed(Constraint<Boolean> value) {
        String mode = SP.getString(R.string.key_aps_mode, "open");
        if (!mode.equals("closed"))
            value.set(false, MainApp.gs(R.string.closedmodedisabledinpreferences), this);

        if (!MainApp.isEngineeringModeOrRelease()) {
            if (value.value()) {
                Notification n = new Notification(Notification.TOAST_ALARM, MainApp.gs(R.string.closed_loop_disabled_on_dev_branch), Notification.NORMAL);
                RxBus.INSTANCE.send(new EventNewNotification(n));
            }
            value.set(false, MainApp.gs(R.string.closed_loop_disabled_on_dev_branch), this);
        }

        return value;
    }

    @Override
    public Constraint<Boolean> isAutosensModeEnabled(Constraint<Boolean> value) {
        boolean enabled = SP.getBoolean(R.string.key_openapsama_useautosens, false);
        if (!enabled)
            value.set(false, MainApp.gs(R.string.autosensdisabledinpreferences), this);
        return value;
    }

    @Override
    public Constraint<Boolean> isSMBModeEnabled(Constraint<Boolean> value) {
        boolean enabled = SP.getBoolean(R.string.key_use_smb, false);
        if (!enabled)
            value.set(false, MainApp.gs(R.string.smbdisabledinpreferences), this);
        ConstraintChecker constraintChecker = MainApp.getConstraintChecker();
        Constraint<Boolean> closedLoop = constraintChecker.isClosedLoopAllowed();
        if (!closedLoop.value())
            value.set(false, MainApp.gs(R.string.smbnotallowedinopenloopmode), this);
        return value;
    }

    @Override
    public Constraint<Boolean> isUAMEnabled(Constraint<Boolean> value) {
        boolean enabled = SP.getBoolean(R.string.key_use_uam, false);
        if (!enabled)
            value.set(false, MainApp.gs(R.string.uamdisabledinpreferences), this);
        boolean oref1Enabled = SensitivityOref1Plugin.getPlugin().isEnabled(PluginType.SENSITIVITY);
        if (!oref1Enabled)
            value.set(false, MainApp.gs(R.string.uamdisabledoref1notselected), this);
        return value;
    }

    @Override
    public Constraint<Boolean> isAdvancedFilteringEnabled(Constraint<Boolean> value) {
        BgSourceInterface bgSource = ConfigBuilderPlugin.getPlugin().getActiveBgSource();

        if (bgSource != null) {
            if (!bgSource.advancedFilteringSupported())
                value.set(false, MainApp.gs(R.string.smbalwaysdisabled), this);
        }
        return value;
    }

    @Override
    public Constraint<Double> applyBasalConstraints(Constraint<Double> absoluteRate, Profile profile) {

        absoluteRate.setIfGreater(0d, String.format(MainApp.gs(R.string.limitingbasalratio), 0d, MainApp.gs(R.string.itmustbepositivevalue)), this);

        if (Config.APS) {
            double maxBasal = SP.getDouble(R.string.key_openapsma_max_basal, 1d);
            if (maxBasal < profile.getMaxDailyBasal()) {
                maxBasal = profile.getMaxDailyBasal();
                absoluteRate.addReason(MainApp.gs(R.string.increasingmaxbasal), this);
            }
            absoluteRate.setIfSmaller(maxBasal, String.format(MainApp.gs(R.string.limitingbasalratio), maxBasal, MainApp.gs(R.string.maxvalueinpreferences)), this);

            // Check percentRate but absolute rate too, because we know real current basal in pump
            Double maxBasalMult = SP.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4d);
            double maxFromBasalMult = Math.floor(maxBasalMult * profile.getBasal() * 100) / 100;
            absoluteRate.setIfSmaller(maxFromBasalMult, String.format(MainApp.gs(R.string.limitingbasalratio), maxFromBasalMult, MainApp.gs(R.string.maxbasalmultiplier)), this);

            Double maxBasalFromDaily = SP.getDouble(R.string.key_openapsama_max_daily_safety_multiplier, 3d);
            double maxFromDaily = Math.floor(profile.getMaxDailyBasal() * maxBasalFromDaily * 100) / 100;
            absoluteRate.setIfSmaller(maxFromDaily, String.format(MainApp.gs(R.string.limitingbasalratio), maxFromDaily, MainApp.gs(R.string.maxdailybasalmultiplier)), this);
        }

        absoluteRate.setIfSmaller(HardLimits.maxBasal(), String.format(MainApp.gs(R.string.limitingbasalratio), HardLimits.maxBasal(), MainApp.gs(R.string.hardlimit)), this);

        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
        // check for pump max
        if (pump != null && pump.getPumpDescription().tempBasalStyle == PumpDescription.ABSOLUTE) {
            double pumpLimit = pump.getPumpDescription().pumpType.getTbrSettings().getMaxDose();
            absoluteRate.setIfSmaller(pumpLimit, String.format(MainApp.gs(R.string.limitingbasalratio), pumpLimit, MainApp.gs(R.string.pumplimit)), this);
        }

        // do rounding
        if (pump != null && pump.getPumpDescription().tempBasalStyle == PumpDescription.ABSOLUTE) {
            absoluteRate.set(Round.roundTo(absoluteRate.value(), pump.getPumpDescription().tempAbsoluteStep));
        }
        return absoluteRate;
    }

    @Override
    public Constraint<Integer> applyBasalPercentConstraints(Constraint<Integer> percentRate, Profile profile) {

        Double currentBasal = profile.getBasal();
        Double absoluteRate = currentBasal * ((double) percentRate.originalValue() / 100);

        percentRate.addReason("Percent rate " + percentRate.originalValue() + "% recalculated to " + DecimalFormatter.to2Decimal(absoluteRate) + " U/h with current basal " + DecimalFormatter.to2Decimal(currentBasal) + " U/h", this);

        Constraint<Double> absoluteConstraint = new Constraint<>(absoluteRate);
        applyBasalConstraints(absoluteConstraint, profile);
        percentRate.copyReasons(absoluteConstraint);

        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();

        Integer percentRateAfterConst = Double.valueOf(absoluteConstraint.value() / currentBasal * 100).intValue();
        if (pump != null) {
            if (percentRateAfterConst < 100)
                percentRateAfterConst = Round.ceilTo((double) percentRateAfterConst, (double) pump.getPumpDescription().tempPercentStep).intValue();
            else
                percentRateAfterConst = Round.floorTo((double) percentRateAfterConst, (double) pump.getPumpDescription().tempPercentStep).intValue();
        }

        percentRate.set(percentRateAfterConst, String.format(MainApp.gs(R.string.limitingpercentrate), percentRateAfterConst, MainApp.gs(R.string.pumplimit)), this);

        if (pump != null && pump.getPumpDescription().tempBasalStyle == PumpDescription.PERCENT) {
            double pumpLimit = pump.getPumpDescription().pumpType.getTbrSettings().getMaxDose();
            percentRate.setIfSmaller((int) pumpLimit, String.format(MainApp.gs(R.string.limitingbasalratio), pumpLimit, MainApp.gs(R.string.pumplimit)), this);
        }

        return percentRate;
    }

    @Override
    public Constraint<Double> applyBolusConstraints(Constraint<Double> insulin) {
        insulin.setIfGreater(0d, String.format(MainApp.gs(R.string.limitingbolus), 0d, MainApp.gs(R.string.itmustbepositivevalue)), this);

        Double maxBolus = SP.getDouble(R.string.key_treatmentssafety_maxbolus, 3d);
        insulin.setIfSmaller(maxBolus, String.format(MainApp.gs(R.string.limitingbolus), maxBolus, MainApp.gs(R.string.maxvalueinpreferences)), this);

        insulin.setIfSmaller(HardLimits.maxBolus(), String.format(MainApp.gs(R.string.limitingbolus), HardLimits.maxBolus(), MainApp.gs(R.string.hardlimit)), this);

        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
        if (pump != null) {
            double rounded = pump.getPumpDescription().pumpType.determineCorrectBolusSize(insulin.value());
            insulin.setIfDifferent(rounded, MainApp.gs(R.string.pumplimit), this);
        }
        return insulin;
    }

    @Override
    public Constraint<Double> applyExtendedBolusConstraints(Constraint<Double> insulin) {
        insulin.setIfGreater(0d, String.format(MainApp.gs(R.string.limitingextendedbolus), 0d, MainApp.gs(R.string.itmustbepositivevalue)), this);

        Double maxBolus = SP.getDouble(R.string.key_treatmentssafety_maxbolus, 3d);
        insulin.setIfSmaller(maxBolus, String.format(MainApp.gs(R.string.limitingextendedbolus), maxBolus, MainApp.gs(R.string.maxvalueinpreferences)), this);

        insulin.setIfSmaller(HardLimits.maxBolus(), String.format(MainApp.gs(R.string.limitingextendedbolus), HardLimits.maxBolus(), MainApp.gs(R.string.hardlimit)), this);

        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
        if (pump != null) {
            double rounded = pump.getPumpDescription().pumpType.determineCorrectExtendedBolusSize(insulin.value());
            insulin.setIfDifferent(rounded, MainApp.gs(R.string.pumplimit), this);
        }
        return insulin;
    }

    @Override
    public Constraint<Integer> applyCarbsConstraints(Constraint<Integer> carbs) {
        carbs.setIfGreater(0, String.format(MainApp.gs(R.string.limitingcarbs), 0, MainApp.gs(R.string.itmustbepositivevalue)), this);

        Integer maxCarbs = SP.getInt(R.string.key_treatmentssafety_maxcarbs, 48);
        carbs.setIfSmaller(maxCarbs, String.format(MainApp.gs(R.string.limitingcarbs), maxCarbs, MainApp.gs(R.string.maxvalueinpreferences)), this);

        return carbs;
    }

    @Override
    public Constraint<Double> applyMaxIOBConstraints(Constraint<Double> maxIob) {
        double maxIobPref;
        if (OpenAPSSMBPlugin.getPlugin().isEnabled(PluginType.APS))
            maxIobPref = SP.getDouble(R.string.key_openapssmb_max_iob, 3d);
        else
            maxIobPref = SP.getDouble(R.string.key_openapsma_max_iob, 1.5d);
        maxIob.setIfSmaller(maxIobPref, String.format(MainApp.gs(R.string.limitingiob), maxIobPref, MainApp.gs(R.string.maxvalueinpreferences)), this);

        if (OpenAPSMAPlugin.getPlugin().isEnabled(PluginType.APS))
            maxIob.setIfSmaller(HardLimits.maxIobAMA(), String.format(MainApp.gs(R.string.limitingiob), HardLimits.maxIobAMA(), MainApp.gs(R.string.hardlimit)), this);
        if (OpenAPSAMAPlugin.getPlugin().isEnabled(PluginType.APS))
            maxIob.setIfSmaller(HardLimits.maxIobAMA(), String.format(MainApp.gs(R.string.limitingiob), HardLimits.maxIobAMA(), MainApp.gs(R.string.hardlimit)), this);
        if (OpenAPSSMBPlugin.getPlugin().isEnabled(PluginType.APS))
            maxIob.setIfSmaller(HardLimits.maxIobSMB(), String.format(MainApp.gs(R.string.limitingiob), HardLimits.maxIobSMB(), MainApp.gs(R.string.hardlimit)), this);
        return maxIob;
    }

}
