package info.nightscout.androidaps.plugins.constraints.safety;

import androidx.annotation.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSMA.OpenAPSMAPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.HardLimits;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

@Singleton
public class SafetyPlugin extends PluginBase implements ConstraintsInterface {

    // TODO: dagger
    @Inject SP sp;
    @Inject RxBusWrapper rxBus;
    @Inject ResourceHelper resourceHelper;
    @Inject ConstraintChecker constraintChecker;
    @Inject OpenAPSAMAPlugin openAPSAMAPlugin;
    @Inject OpenAPSMAPlugin openAPSMAPlugin;
    @Inject OpenAPSSMBPlugin openAPSSMBPlugin;
    @Inject SensitivityOref1Plugin sensitivityOref1Plugin;
    @Inject ActivePluginProvider activePlugin;

    @Inject
    public SafetyPlugin(AAPSLogger aapsLogger, ResourceHelper resourceHelper) {
        super(new PluginDescription()
                .mainType(PluginType.CONSTRAINTS)
                .neverVisible(true)
                .alwaysEnabled(true)
                .showInList(false)
                .pluginName(R.string.safety)
                .preferencesId(R.xml.pref_safety), aapsLogger, resourceHelper
        );
    }

    /**
     * Constraints interface
     **/
    @NonNull @Override
    public Constraint<Boolean> isLoopInvocationAllowed(@NonNull Constraint<Boolean> value) {
        if (!activePlugin.getActivePumpPlugin().getPumpDescription().isTempBasalCapable)
            value.set(false, resourceHelper.gs(R.string.pumpisnottempbasalcapable), this);
        return value;
    }

    @NonNull @Override
    public Constraint<Boolean> isClosedLoopAllowed(@NonNull Constraint<Boolean> value) {
        String mode = sp.getString(R.string.key_aps_mode, "open");
        if (!mode.equals("closed"))
            value.set(false, resourceHelper.gs(R.string.closedmodedisabledinpreferences), this);

        if (!MainApp.isEngineeringModeOrRelease()) {
            if (value.value()) {
                Notification n = new Notification(Notification.TOAST_ALARM, resourceHelper.gs(R.string.closed_loop_disabled_on_dev_branch), Notification.NORMAL);
                rxBus.send(new EventNewNotification(n));
            }
            value.set(false, resourceHelper.gs(R.string.closed_loop_disabled_on_dev_branch), this);
        }
        PumpInterface pump = activePlugin.getActivePumpPlugin();
        if (pump != null && !pump.isFakingTempsByExtendedBoluses() && TreatmentsPlugin.getPlugin().isInHistoryExtendedBoluslInProgress()) {
            value.set(false, MainApp.gs(R.string.closed_loop_disabled_with_eb), this);
        }
        return value;
    }

    @NonNull @Override
    public Constraint<Boolean> isAutosensModeEnabled(@NonNull Constraint<Boolean> value) {
        boolean enabled = sp.getBoolean(R.string.key_openapsama_useautosens, false);
        if (!enabled)
            value.set(false, resourceHelper.gs(R.string.autosensdisabledinpreferences), this);
        return value;
    }

    @NonNull @Override
    public Constraint<Boolean> isSMBModeEnabled(@NonNull Constraint<Boolean> value) {
        boolean enabled = sp.getBoolean(R.string.key_use_smb, false);
        if (!enabled)
            value.set(false, resourceHelper.gs(R.string.smbdisabledinpreferences), this);
        Constraint<Boolean> closedLoop = constraintChecker.isClosedLoopAllowed();
        if (!closedLoop.value())
            value.set(false, resourceHelper.gs(R.string.smbnotallowedinopenloopmode), this);
        return value;
    }

    @NonNull @Override
    public Constraint<Boolean> isUAMEnabled(@NonNull Constraint<Boolean> value) {
        boolean enabled = sp.getBoolean(R.string.key_use_uam, false);
        if (!enabled)
            value.set(false, resourceHelper.gs(R.string.uamdisabledinpreferences), this);
        boolean oref1Enabled = sensitivityOref1Plugin.isEnabled(PluginType.SENSITIVITY);
        if (!oref1Enabled)
            value.set(false, resourceHelper.gs(R.string.uamdisabledoref1notselected), this);
        return value;
    }

    @NonNull @Override
    public Constraint<Boolean> isAdvancedFilteringEnabled(@NonNull Constraint<Boolean> value) {
        BgSourceInterface bgSource = activePlugin.getActiveBgSource();

        if (bgSource != null) {
            if (!bgSource.advancedFilteringSupported())
                value.set(false, resourceHelper.gs(R.string.smbalwaysdisabled), this);
        }
        return value;
    }

    @NonNull @Override
    public Constraint<Double> applyBasalConstraints(Constraint<Double> absoluteRate, @NonNull Profile profile) {

        absoluteRate.setIfGreater(0d, String.format(resourceHelper.gs(R.string.limitingbasalratio), 0d, resourceHelper.gs(R.string.itmustbepositivevalue)), this);

        if (Config.APS) {
            double maxBasal = sp.getDouble(R.string.key_openapsma_max_basal, 1d);
            if (maxBasal < profile.getMaxDailyBasal()) {
                maxBasal = profile.getMaxDailyBasal();
                absoluteRate.addReason(resourceHelper.gs(R.string.increasingmaxbasal), this);
            }
            absoluteRate.setIfSmaller(maxBasal, String.format(resourceHelper.gs(R.string.limitingbasalratio), maxBasal, resourceHelper.gs(R.string.maxvalueinpreferences)), this);

            // Check percentRate but absolute rate too, because we know real current basal in pump
            Double maxBasalMult = sp.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4d);
            double maxFromBasalMult = Math.floor(maxBasalMult * profile.getBasal() * 100) / 100;
            absoluteRate.setIfSmaller(maxFromBasalMult, String.format(resourceHelper.gs(R.string.limitingbasalratio), maxFromBasalMult, resourceHelper.gs(R.string.maxbasalmultiplier)), this);

            Double maxBasalFromDaily = sp.getDouble(R.string.key_openapsama_max_daily_safety_multiplier, 3d);
            double maxFromDaily = Math.floor(profile.getMaxDailyBasal() * maxBasalFromDaily * 100) / 100;
            absoluteRate.setIfSmaller(maxFromDaily, String.format(resourceHelper.gs(R.string.limitingbasalratio), maxFromDaily, resourceHelper.gs(R.string.maxdailybasalmultiplier)), this);
        }

        absoluteRate.setIfSmaller(HardLimits.maxBasal(), String.format(resourceHelper.gs(R.string.limitingbasalratio), HardLimits.maxBasal(), resourceHelper.gs(R.string.hardlimit)), this);

        PumpInterface pump = activePlugin.getActivePumpPlugin();
        // check for pump max
        if (pump != null && pump.getPumpDescription().tempBasalStyle == PumpDescription.ABSOLUTE) {
            double pumpLimit = pump.getPumpDescription().pumpType.getTbrSettings().getMaxDose();
            absoluteRate.setIfSmaller(pumpLimit, String.format(resourceHelper.gs(R.string.limitingbasalratio), pumpLimit, resourceHelper.gs(R.string.pumplimit)), this);
        }

        // do rounding
        if (pump != null && pump.getPumpDescription().tempBasalStyle == PumpDescription.ABSOLUTE) {
            absoluteRate.set(Round.roundTo(absoluteRate.value(), pump.getPumpDescription().tempAbsoluteStep));
        }
        return absoluteRate;
    }

    @NonNull @Override
    public Constraint<Integer> applyBasalPercentConstraints(Constraint<Integer> percentRate, Profile profile) {

        Double currentBasal = profile.getBasal();
        Double absoluteRate = currentBasal * ((double) percentRate.originalValue() / 100);

        percentRate.addReason("Percent rate " + percentRate.originalValue() + "% recalculated to " + DecimalFormatter.to2Decimal(absoluteRate) + " U/h with current basal " + DecimalFormatter.to2Decimal(currentBasal) + " U/h", this);

        Constraint<Double> absoluteConstraint = new Constraint<>(absoluteRate);
        applyBasalConstraints(absoluteConstraint, profile);
        percentRate.copyReasons(absoluteConstraint);

        PumpInterface pump = activePlugin.getActivePumpPlugin();

        Integer percentRateAfterConst = Double.valueOf(absoluteConstraint.value() / currentBasal * 100).intValue();
        if (pump != null) {
            if (percentRateAfterConst < 100)
                percentRateAfterConst = Round.ceilTo((double) percentRateAfterConst, (double) pump.getPumpDescription().tempPercentStep).intValue();
            else
                percentRateAfterConst = Round.floorTo((double) percentRateAfterConst, (double) pump.getPumpDescription().tempPercentStep).intValue();
        }

        percentRate.set(percentRateAfterConst, String.format(resourceHelper.gs(R.string.limitingpercentrate), percentRateAfterConst, resourceHelper.gs(R.string.pumplimit)), this);

        if (pump != null && pump.getPumpDescription().tempBasalStyle == PumpDescription.PERCENT) {
            double pumpLimit = pump.getPumpDescription().pumpType.getTbrSettings().getMaxDose();
            percentRate.setIfSmaller((int) pumpLimit, String.format(resourceHelper.gs(R.string.limitingbasalratio), pumpLimit, resourceHelper.gs(R.string.pumplimit)), this);
        }

        return percentRate;
    }

    @NonNull @Override
    public Constraint<Double> applyBolusConstraints(Constraint<Double> insulin) {
        insulin.setIfGreater(0d, String.format(resourceHelper.gs(R.string.limitingbolus), 0d, resourceHelper.gs(R.string.itmustbepositivevalue)), this);

        Double maxBolus = sp.getDouble(R.string.key_treatmentssafety_maxbolus, 3d);
        insulin.setIfSmaller(maxBolus, String.format(resourceHelper.gs(R.string.limitingbolus), maxBolus, resourceHelper.gs(R.string.maxvalueinpreferences)), this);

        insulin.setIfSmaller(HardLimits.maxBolus(), String.format(resourceHelper.gs(R.string.limitingbolus), HardLimits.maxBolus(), resourceHelper.gs(R.string.hardlimit)), this);

        PumpInterface pump = activePlugin.getActivePumpPlugin();
        if (pump != null) {
            double rounded = pump.getPumpDescription().pumpType.determineCorrectBolusSize(insulin.value());
            insulin.setIfDifferent(rounded, resourceHelper.gs(R.string.pumplimit), this);
        }
        return insulin;
    }

    @NonNull @Override
    public Constraint<Double> applyExtendedBolusConstraints(Constraint<Double> insulin) {
        insulin.setIfGreater(0d, String.format(resourceHelper.gs(R.string.limitingextendedbolus), 0d, resourceHelper.gs(R.string.itmustbepositivevalue)), this);

        Double maxBolus = sp.getDouble(R.string.key_treatmentssafety_maxbolus, 3d);
        insulin.setIfSmaller(maxBolus, String.format(resourceHelper.gs(R.string.limitingextendedbolus), maxBolus, resourceHelper.gs(R.string.maxvalueinpreferences)), this);

        insulin.setIfSmaller(HardLimits.maxBolus(), String.format(resourceHelper.gs(R.string.limitingextendedbolus), HardLimits.maxBolus(), resourceHelper.gs(R.string.hardlimit)), this);

        PumpInterface pump = activePlugin.getActivePumpPlugin();
        if (pump != null) {
            double rounded = pump.getPumpDescription().pumpType.determineCorrectExtendedBolusSize(insulin.value());
            insulin.setIfDifferent(rounded, resourceHelper.gs(R.string.pumplimit), this);
        }
        return insulin;
    }

    @NonNull @Override
    public Constraint<Integer> applyCarbsConstraints(Constraint<Integer> carbs) {
        carbs.setIfGreater(0, String.format(resourceHelper.gs(R.string.limitingcarbs), 0, resourceHelper.gs(R.string.itmustbepositivevalue)), this);

        Integer maxCarbs = sp.getInt(R.string.key_treatmentssafety_maxcarbs, 48);
        carbs.setIfSmaller(maxCarbs, String.format(resourceHelper.gs(R.string.limitingcarbs), maxCarbs, resourceHelper.gs(R.string.maxvalueinpreferences)), this);

        return carbs;
    }

    @NonNull @Override
    public Constraint<Double> applyMaxIOBConstraints(@NonNull Constraint<Double> maxIob) {
        double maxIobPref;
        if (openAPSSMBPlugin.isEnabled(PluginType.APS))
            maxIobPref = sp.getDouble(R.string.key_openapssmb_max_iob, 3d);
        else
            maxIobPref = sp.getDouble(R.string.key_openapsma_max_iob, 1.5d);
        maxIob.setIfSmaller(maxIobPref, String.format(resourceHelper.gs(R.string.limitingiob), maxIobPref, resourceHelper.gs(R.string.maxvalueinpreferences)), this);

        if (openAPSMAPlugin.isEnabled(PluginType.APS))
            maxIob.setIfSmaller(HardLimits.maxIobAMA(), String.format(resourceHelper.gs(R.string.limitingiob), HardLimits.maxIobAMA(), resourceHelper.gs(R.string.hardlimit)), this);
        if (openAPSAMAPlugin.isEnabled(PluginType.APS))
            maxIob.setIfSmaller(HardLimits.maxIobAMA(), String.format(resourceHelper.gs(R.string.limitingiob), HardLimits.maxIobAMA(), resourceHelper.gs(R.string.hardlimit)), this);
        if (openAPSSMBPlugin.isEnabled(PluginType.APS))
            maxIob.setIfSmaller(HardLimits.maxIobSMB(), String.format(resourceHelper.gs(R.string.limitingiob), HardLimits.maxIobSMB(), resourceHelper.gs(R.string.hardlimit)), this);
        return maxIob;
    }

}
