package info.nightscout.androidaps.plugins.constraints.safety;

import androidx.annotation.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Config;
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
import info.nightscout.androidaps.utils.buildHelper.BuildHelper;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

@Singleton
public class SafetyPlugin extends PluginBase implements ConstraintsInterface {

    private final SP sp;
    private final RxBusWrapper rxBus;
    private final ConstraintChecker constraintChecker;
    private final OpenAPSAMAPlugin openAPSAMAPlugin;
    private final OpenAPSSMBPlugin openAPSSMBPlugin;
    private final SensitivityOref1Plugin sensitivityOref1Plugin;
    private final ActivePluginProvider activePlugin;
    private final HardLimits hardLimits;
    private final BuildHelper buildHelper;
    private final TreatmentsPlugin treatmentsPlugin;
    private final Config config;

    @Inject
    public SafetyPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            ResourceHelper resourceHelper,
            SP sp,
            RxBusWrapper rxBus,
            ConstraintChecker constraintChecker,
            OpenAPSAMAPlugin openAPSAMAPlugin,
            OpenAPSSMBPlugin openAPSSMBPlugin,
            SensitivityOref1Plugin sensitivityOref1Plugin,
            ActivePluginProvider activePlugin,
            HardLimits hardLimits,
            BuildHelper buildHelper,
            TreatmentsPlugin treatmentsPlugin,
            Config config
    ) {
        super(new PluginDescription()
                        .mainType(PluginType.CONSTRAINTS)
                        .neverVisible(true)
                        .alwaysEnabled(true)
                        .showInList(false)
                        .pluginName(R.string.safety)
                        .preferencesId(R.xml.pref_safety),
                aapsLogger, resourceHelper, injector
        );
        this.sp = sp;
        this.rxBus = rxBus;
        this.constraintChecker = constraintChecker;
        this.openAPSAMAPlugin = openAPSAMAPlugin;
        this.openAPSSMBPlugin = openAPSSMBPlugin;
        this.sensitivityOref1Plugin = sensitivityOref1Plugin;
        this.activePlugin = activePlugin;
        this.hardLimits = hardLimits;
        this.buildHelper = buildHelper;
        this.treatmentsPlugin = treatmentsPlugin;
        this.config = config;
    }

    /**
     * Constraints interface
     **/
    @NonNull @Override
    public Constraint<Boolean> isLoopInvocationAllowed(@NonNull Constraint<Boolean> value) {
        if (!activePlugin.getActivePump().getPumpDescription().isTempBasalCapable)
            value.set(getAapsLogger(), false, getResourceHelper().gs(R.string.pumpisnottempbasalcapable), this);
        return value;
    }

    @NonNull @Override
    public Constraint<Boolean> isClosedLoopAllowed(@NonNull Constraint<Boolean> value) {
        String mode = sp.getString(R.string.key_aps_mode, "open");
        if ((mode.equals("open")))
            value.set(getAapsLogger(), false, getResourceHelper().gs(R.string.closedmodedisabledinpreferences), this);

        if (!buildHelper.isEngineeringModeOrRelease()) {
            if (value.value()) {
                Notification n = new Notification(Notification.TOAST_ALARM, getResourceHelper().gs(R.string.closed_loop_disabled_on_dev_branch), Notification.NORMAL);
                rxBus.send(new EventNewNotification(n));
            }
            value.set(getAapsLogger(), false, getResourceHelper().gs(R.string.closed_loop_disabled_on_dev_branch), this);
        }
        PumpInterface pump = activePlugin.getActivePump();
        if (!pump.isFakingTempsByExtendedBoluses() && treatmentsPlugin.isInHistoryExtendedBoluslInProgress()) {
            value.set(getAapsLogger(), false, getResourceHelper().gs(R.string.closed_loop_disabled_with_eb), this);
        }
        return value;
    }

    @NonNull @Override
    public Constraint<Boolean> isAutosensModeEnabled(@NonNull Constraint<Boolean> value) {
        boolean enabled = sp.getBoolean(R.string.key_openapsama_useautosens, false);
        if (!enabled)
            value.set(getAapsLogger(), false, getResourceHelper().gs(R.string.autosensdisabledinpreferences), this);
        return value;
    }

    @NonNull @Override
    public Constraint<Boolean> isSMBModeEnabled(@NonNull Constraint<Boolean> value) {
        boolean enabled = sp.getBoolean(R.string.key_use_smb, false);
        if (!enabled)
            value.set(getAapsLogger(), false, getResourceHelper().gs(R.string.smbdisabledinpreferences), this);
        Constraint<Boolean> closedLoop = constraintChecker.isClosedLoopAllowed();
        if (!closedLoop.value())
            value.set(getAapsLogger(), false, getResourceHelper().gs(R.string.smbnotallowedinopenloopmode), this);
        return value;
    }

    @NonNull @Override
    public Constraint<Boolean> isUAMEnabled(@NonNull Constraint<Boolean> value) {
        boolean enabled = sp.getBoolean(R.string.key_use_uam, false);
        if (!enabled)
            value.set(getAapsLogger(), false, getResourceHelper().gs(R.string.uamdisabledinpreferences), this);
        boolean oref1Enabled = sensitivityOref1Plugin.isEnabled(PluginType.SENSITIVITY);
        if (!oref1Enabled)
            value.set(getAapsLogger(), false, getResourceHelper().gs(R.string.uamdisabledoref1notselected), this);
        return value;
    }

    @NonNull @Override
    public Constraint<Boolean> isAdvancedFilteringEnabled(@NonNull Constraint<Boolean> value) {
        BgSourceInterface bgSource = activePlugin.getActiveBgSource();

        if (!bgSource.advancedFilteringSupported())
            value.set(getAapsLogger(), false, getResourceHelper().gs(R.string.smbalwaysdisabled), this);
        return value;
    }

    @NonNull @Override
    public Constraint<Double> applyBasalConstraints(Constraint<Double> absoluteRate, @NonNull Profile profile) {

        absoluteRate.setIfGreater(getAapsLogger(), 0d, String.format(getResourceHelper().gs(R.string.limitingbasalratio), 0d, getResourceHelper().gs(R.string.itmustbepositivevalue)), this);

        if (config.getAPS()) {
            double maxBasal = sp.getDouble(R.string.key_openapsma_max_basal, 1d);
            if (maxBasal < profile.getMaxDailyBasal()) {
                maxBasal = profile.getMaxDailyBasal();
                absoluteRate.addReason(getResourceHelper().gs(R.string.increasingmaxbasal), this);
            }
            absoluteRate.setIfSmaller(getAapsLogger(), maxBasal, String.format(getResourceHelper().gs(R.string.limitingbasalratio), maxBasal, getResourceHelper().gs(R.string.maxvalueinpreferences)), this);

            // Check percentRate but absolute rate too, because we know real current basal in pump
            double maxBasalMult = sp.getDouble(R.string.key_openapsama_current_basal_safety_multiplier, 4d);
            double maxFromBasalMult = Math.floor(maxBasalMult * profile.getBasal() * 100) / 100;
            absoluteRate.setIfSmaller(getAapsLogger(), maxFromBasalMult, String.format(getResourceHelper().gs(R.string.limitingbasalratio), maxFromBasalMult, getResourceHelper().gs(R.string.maxbasalmultiplier)), this);

            double maxBasalFromDaily = sp.getDouble(R.string.key_openapsama_max_daily_safety_multiplier, 3d);
            double maxFromDaily = Math.floor(profile.getMaxDailyBasal() * maxBasalFromDaily * 100) / 100;
            absoluteRate.setIfSmaller(getAapsLogger(), maxFromDaily, String.format(getResourceHelper().gs(R.string.limitingbasalratio), maxFromDaily, getResourceHelper().gs(R.string.maxdailybasalmultiplier)), this);
        }

        absoluteRate.setIfSmaller(getAapsLogger(), hardLimits.maxBasal(), String.format(getResourceHelper().gs(R.string.limitingbasalratio), hardLimits.maxBasal(), getResourceHelper().gs(R.string.hardlimit)), this);

        PumpInterface pump = activePlugin.getActivePump();
        // check for pump max
        if (pump.getPumpDescription().tempBasalStyle == PumpDescription.ABSOLUTE) {
            double pumpLimit = pump.getPumpDescription().pumpType.getTbrSettings().getMaxDose();
            absoluteRate.setIfSmaller(getAapsLogger(), pumpLimit, String.format(getResourceHelper().gs(R.string.limitingbasalratio), pumpLimit, getResourceHelper().gs(R.string.pumplimit)), this);
        }

        // do rounding
        if (pump.getPumpDescription().tempBasalStyle == PumpDescription.ABSOLUTE) {
            absoluteRate.set(getAapsLogger(), Round.roundTo(absoluteRate.value(), pump.getPumpDescription().tempAbsoluteStep));
        }
        return absoluteRate;
    }

    @NonNull @Override
    public Constraint<Integer> applyBasalPercentConstraints(Constraint<Integer> percentRate, Profile profile) {

        Double currentBasal = profile.getBasal();
        double absoluteRate = currentBasal * ((double) percentRate.originalValue() / 100);

        percentRate.addReason("Percent rate " + percentRate.originalValue() + "% recalculated to " + DecimalFormatter.to2Decimal(absoluteRate) + " U/h with current basal " + DecimalFormatter.to2Decimal(currentBasal) + " U/h", this);

        Constraint<Double> absoluteConstraint = new Constraint<>(absoluteRate);
        applyBasalConstraints(absoluteConstraint, profile);
        percentRate.copyReasons(absoluteConstraint);

        PumpInterface pump = activePlugin.getActivePump();

        int percentRateAfterConst = Double.valueOf(absoluteConstraint.value() / currentBasal * 100).intValue();
        if (percentRateAfterConst < 100)
            percentRateAfterConst = Round.ceilTo((double) percentRateAfterConst, (double) pump.getPumpDescription().tempPercentStep).intValue();
        else
            percentRateAfterConst = Round.floorTo((double) percentRateAfterConst, (double) pump.getPumpDescription().tempPercentStep).intValue();

        percentRate.set(getAapsLogger(), percentRateAfterConst, String.format(getResourceHelper().gs(R.string.limitingpercentrate), percentRateAfterConst, getResourceHelper().gs(R.string.pumplimit)), this);

        if (pump.getPumpDescription().tempBasalStyle == PumpDescription.PERCENT) {
            double pumpLimit = pump.getPumpDescription().pumpType.getTbrSettings().getMaxDose();
            percentRate.setIfSmaller(getAapsLogger(), (int) pumpLimit, String.format(getResourceHelper().gs(R.string.limitingbasalratio), pumpLimit, getResourceHelper().gs(R.string.pumplimit)), this);
        }

        return percentRate;
    }

    @NonNull @Override
    public Constraint<Double> applyBolusConstraints(Constraint<Double> insulin) {
        insulin.setIfGreater(getAapsLogger(), 0d, String.format(getResourceHelper().gs(R.string.limitingbolus), 0d, getResourceHelper().gs(R.string.itmustbepositivevalue)), this);

        Double maxBolus = sp.getDouble(R.string.key_treatmentssafety_maxbolus, 3d);
        insulin.setIfSmaller(getAapsLogger(), maxBolus, String.format(getResourceHelper().gs(R.string.limitingbolus), maxBolus, getResourceHelper().gs(R.string.maxvalueinpreferences)), this);

        insulin.setIfSmaller(getAapsLogger(), hardLimits.maxBolus(), String.format(getResourceHelper().gs(R.string.limitingbolus), hardLimits.maxBolus(), getResourceHelper().gs(R.string.hardlimit)), this);

        PumpInterface pump = activePlugin.getActivePump();
        double rounded = pump.getPumpDescription().pumpType.determineCorrectBolusSize(insulin.value());
        insulin.setIfDifferent(getAapsLogger(), rounded, getResourceHelper().gs(R.string.pumplimit), this);
        return insulin;
    }

    @NonNull @Override
    public Constraint<Double> applyExtendedBolusConstraints(Constraint<Double> insulin) {
        insulin.setIfGreater(getAapsLogger(), 0d, String.format(getResourceHelper().gs(R.string.limitingextendedbolus), 0d, getResourceHelper().gs(R.string.itmustbepositivevalue)), this);

        Double maxBolus = sp.getDouble(R.string.key_treatmentssafety_maxbolus, 3d);
        insulin.setIfSmaller(getAapsLogger(), maxBolus, String.format(getResourceHelper().gs(R.string.limitingextendedbolus), maxBolus, getResourceHelper().gs(R.string.maxvalueinpreferences)), this);

        insulin.setIfSmaller(getAapsLogger(), hardLimits.maxBolus(), String.format(getResourceHelper().gs(R.string.limitingextendedbolus), hardLimits.maxBolus(), getResourceHelper().gs(R.string.hardlimit)), this);

        PumpInterface pump = activePlugin.getActivePump();
        double rounded = pump.getPumpDescription().pumpType.determineCorrectExtendedBolusSize(insulin.value());
        insulin.setIfDifferent(getAapsLogger(), rounded, getResourceHelper().gs(R.string.pumplimit), this);
        return insulin;
    }

    @NonNull @Override
    public Constraint<Integer> applyCarbsConstraints(Constraint<Integer> carbs) {
        carbs.setIfGreater(getAapsLogger(), 0, String.format(getResourceHelper().gs(R.string.limitingcarbs), 0, getResourceHelper().gs(R.string.itmustbepositivevalue)), this);

        Integer maxCarbs = sp.getInt(R.string.key_treatmentssafety_maxcarbs, 48);
        carbs.setIfSmaller(getAapsLogger(), maxCarbs, String.format(getResourceHelper().gs(R.string.limitingcarbs), maxCarbs, getResourceHelper().gs(R.string.maxvalueinpreferences)), this);

        return carbs;
    }

    @NonNull @Override
    public Constraint<Double> applyMaxIOBConstraints(@NonNull Constraint<Double> maxIob) {
        double maxIobPref;
        String apsmode = sp.getString(R.string.key_aps_mode, "open");
        if (openAPSSMBPlugin.isEnabled(PluginType.APS))
            maxIobPref = sp.getDouble(R.string.key_openapssmb_max_iob, 3d);
        else
            maxIobPref = sp.getDouble(R.string.key_openapsma_max_iob, 1.5d);
        maxIob.setIfSmaller(getAapsLogger(), maxIobPref, String.format(getResourceHelper().gs(R.string.limitingiob), maxIobPref, getResourceHelper().gs(R.string.maxvalueinpreferences)), this);

        if (openAPSAMAPlugin.isEnabled(PluginType.APS))
            maxIob.setIfSmaller(getAapsLogger(), hardLimits.maxIobAMA(), String.format(getResourceHelper().gs(R.string.limitingiob), hardLimits.maxIobAMA(), getResourceHelper().gs(R.string.hardlimit)), this);
        if (openAPSSMBPlugin.isEnabled(PluginType.APS))
            maxIob.setIfSmaller(getAapsLogger(), hardLimits.maxIobSMB(), String.format(getResourceHelper().gs(R.string.limitingiob), hardLimits.maxIobSMB(), getResourceHelper().gs(R.string.hardlimit)), this);
        if ((apsmode.equals("lgs")))
            maxIob.setIfSmaller(getAapsLogger(), hardLimits.getMAXIOB_LGS(), String.format(getResourceHelper().gs(R.string.limitingiob), hardLimits.getMAXIOB_LGS(), getResourceHelper().gs(R.string.lowglucosesuspend)), this);

        return maxIob;
    }

}
