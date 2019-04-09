package info.nightscout.androidaps.plugins.treatments;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Intervals;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.data.NonOverlappingIntervals;
import info.nightscout.androidaps.data.OverlappingIntervals;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileIntervals;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventReloadProfileSwitchData;
import info.nightscout.androidaps.events.EventReloadTempBasalData;
import info.nightscout.androidaps.events.EventReloadTreatmentData;
import info.nightscout.androidaps.events.EventTempTargetChange;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.overview.dialogs.ErrorHelperActivity;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensData;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityAAPSPlugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityWeightedAveragePlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;

/**
 * Created by mike on 05.08.2016.
 */
public class TreatmentsPlugin extends PluginBase implements TreatmentsInterface {
    private Logger log = LoggerFactory.getLogger(L.DATATREATMENTS);

    private static TreatmentsPlugin treatmentsPlugin;

    public static TreatmentsPlugin getPlugin() {
        if (treatmentsPlugin == null)
            treatmentsPlugin = new TreatmentsPlugin();
        return treatmentsPlugin;
    }

    private TreatmentService service;

    private IobTotal lastTreatmentCalculation;
    private IobTotal lastTempBasalsCalculation;

    private final ArrayList<Treatment> treatments = new ArrayList<>();
    private final Intervals<TemporaryBasal> tempBasals = new NonOverlappingIntervals<>();
    private final Intervals<ExtendedBolus> extendedBoluses = new NonOverlappingIntervals<>();
    private final Intervals<TempTarget> tempTargets = new OverlappingIntervals<>();
    private final ProfileIntervals<ProfileSwitch> profiles = new ProfileIntervals<>();

    public TreatmentsPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.TREATMENT)
                .fragmentClass(TreatmentsFragment.class.getName())
                .pluginName(R.string.treatments)
                .shortName(R.string.treatments_shortname)
                .alwaysEnabled(true)
                .description(R.string.description_treatments)
        );
        this.service = new TreatmentService();
    }

    @Override
    protected void onStart() {
        MainApp.bus().register(this);
        initializeTempBasalData();
        initializeTreatmentData();
        initializeExtendedBolusData();
        initializeTempTargetData();
        initializeProfileSwitchData();
        super.onStart();
    }

    @Override
    protected void onStop() {
        MainApp.bus().register(this);
    }

    public TreatmentService getService() {
        return this.service;
    }

    private void initializeTreatmentData() {
        if (L.isEnabled(L.DATATREATMENTS))
            log.debug("initializeTreatmentData");
        double dia = Constants.defaultDIA;
        if (ConfigBuilderPlugin.getPlugin() != null && ProfileFunctions.getInstance().getProfile() != null)
            dia = ProfileFunctions.getInstance().getProfile().getDia();
        long fromMills = (long) (System.currentTimeMillis() - 60 * 60 * 1000L * (24 + dia));
        synchronized (treatments) {
            treatments.clear();
            treatments.addAll(getService().getTreatmentDataFromTime(fromMills, false));
        }
    }

    private void initializeTempBasalData() {
        if (L.isEnabled(L.DATATREATMENTS))
            log.debug("initializeTempBasalData");
        double dia = Constants.defaultDIA;
        if (ConfigBuilderPlugin.getPlugin() != null && ProfileFunctions.getInstance().getProfile() != null)
            dia = ProfileFunctions.getInstance().getProfile().getDia();
        long fromMills = (long) (System.currentTimeMillis() - 60 * 60 * 1000L * (24 + dia));

        synchronized (tempBasals) {
            tempBasals.reset().add(MainApp.getDbHelper().getTemporaryBasalsDataFromTime(fromMills, false));
        }

    }

    private void initializeExtendedBolusData() {
        if (L.isEnabled(L.DATATREATMENTS))
            log.debug("initializeExtendedBolusData");
        double dia = Constants.defaultDIA;
        if (ConfigBuilderPlugin.getPlugin() != null && ProfileFunctions.getInstance().getProfile() != null)
            dia = ProfileFunctions.getInstance().getProfile().getDia();
        long fromMills = (long) (System.currentTimeMillis() - 60 * 60 * 1000L * (24 + dia));

        synchronized (extendedBoluses) {
            extendedBoluses.reset().add(MainApp.getDbHelper().getExtendedBolusDataFromTime(fromMills, false));
        }

    }

    private void initializeTempTargetData() {
        if (L.isEnabled(L.DATATREATMENTS))
            log.debug("initializeTempTargetData");
        synchronized (tempTargets) {
            long fromMills = System.currentTimeMillis() - 60 * 60 * 1000L * 24;
            tempTargets.reset().add(MainApp.getDbHelper().getTemptargetsDataFromTime(fromMills, false));
        }
    }

    private void initializeProfileSwitchData() {
        if (L.isEnabled(L.DATATREATMENTS))
            log.debug("initializeProfileSwitchData");
        synchronized (profiles) {
            profiles.reset().add(MainApp.getDbHelper().getProfileSwitchData(false));
        }
    }

    @Override
    public IobTotal getLastCalculationTreatments() {
        return lastTreatmentCalculation;
    }

    @Override
    public IobTotal getCalculationToTimeTreatments(long time) {
        IobTotal total = new IobTotal(time);

        Profile profile = ProfileFunctions.getInstance().getProfile();
        if (profile == null)
            return total;

        InsulinInterface insulinInterface = ConfigBuilderPlugin.getPlugin().getActiveInsulin();
        if (insulinInterface == null)
            return total;

        PumpInterface pumpInterface = ConfigBuilderPlugin.getPlugin().getActivePump();
        if (pumpInterface == null)
            return total;

        double dia = profile.getDia();

        synchronized (treatments) {
            for (Integer pos = 0; pos < treatments.size(); pos++) {
                Treatment t = treatments.get(pos);
                if (!t.isValid) continue;
                if (t.date > time) continue;
                Iob tIOB = t.iobCalc(time, dia);
                total.iob += tIOB.iobContrib;
                total.activity += tIOB.activityContrib;
                if (t.insulin > 0 && t.date > total.lastBolusTime)
                    total.lastBolusTime = t.date;
                if (!t.isSMB) {
                    // instead of dividing the DIA that only worked on the bilinear curves,
                    // multiply the time the treatment is seen active.
                    long timeSinceTreatment = time - t.date;
                    long snoozeTime = t.date + (long) (timeSinceTreatment * SP.getDouble(R.string.key_openapsama_bolussnooze_dia_divisor, 2.0));
                    Iob bIOB = t.iobCalc(snoozeTime, dia);
                    total.bolussnooze += bIOB.iobContrib;
                }
            }
        }

        if (!pumpInterface.isFakingTempsByExtendedBoluses())
            synchronized (extendedBoluses) {
                for (Integer pos = 0; pos < extendedBoluses.size(); pos++) {
                    ExtendedBolus e = extendedBoluses.get(pos);
                    if (e.date > time) continue;
                    IobTotal calc = e.iobCalc(time);
                    total.plus(calc);
                }
            }
        return total;
    }

    @Override
    public void updateTotalIOBTreatments() {
        lastTreatmentCalculation = getCalculationToTimeTreatments(System.currentTimeMillis());
    }

    @Override
    public MealData getMealData() {
        MealData result = new MealData();

        Profile profile = ProfileFunctions.getInstance().getProfile();
        if (profile == null) return result;

        long now = System.currentTimeMillis();
        long dia_ago = now - (Double.valueOf(profile.getDia() * T.hours(1).msecs())).longValue();

        double maxAbsorptionHours = Constants.DEFAULT_MAX_ABSORPTION_TIME;
        if (SensitivityAAPSPlugin.getPlugin().isEnabled(PluginType.SENSITIVITY) || SensitivityWeightedAveragePlugin.getPlugin().isEnabled(PluginType.SENSITIVITY)) {
            maxAbsorptionHours = SP.getDouble(R.string.key_absorption_maxtime, Constants.DEFAULT_MAX_ABSORPTION_TIME);
        } else {
            maxAbsorptionHours = SP.getDouble(R.string.key_absorption_cutoff, Constants.DEFAULT_MAX_ABSORPTION_TIME);
        }
        long absorptionTime_ago = now - (Double.valueOf(maxAbsorptionHours * T.hours(1).msecs())).longValue();

        synchronized (treatments) {
            for (Treatment treatment : treatments) {
                if (!treatment.isValid)
                    continue;
                long t = treatment.date;

                if (t > dia_ago && t <= now) {
                    if (treatment.insulin > 0 && treatment.mealBolus) {
                        result.boluses += treatment.insulin;
                    }
                }

                if (t > absorptionTime_ago && t <= now) {
                    if (treatment.carbs >= 1) {
                        result.carbs += treatment.carbs;
                        if (t > result.lastCarbTime)
                            result.lastCarbTime = t;
                    }
                }
            }
        }

        AutosensData autosensData = IobCobCalculatorPlugin.getPlugin().getLastAutosensDataSynchronized("getMealData()");
        if (autosensData != null) {
            result.mealCOB = autosensData.cob;
            result.slopeFromMinDeviation = autosensData.slopeFromMinDeviation;
            result.slopeFromMaxDeviation = autosensData.slopeFromMaxDeviation;
            result.usedMinCarbsImpact = autosensData.usedMinCarbsImpact;
        }
        result.lastBolusTime = getLastBolusTime();
        return result;
    }

    @Override
    public List<Treatment> getTreatmentsFromHistory() {
        synchronized (treatments) {
            return new ArrayList<>(treatments);
        }
    }

    @Override
    public List<Treatment> getTreatments5MinBackFromHistory(long time) {
        List<Treatment> in5minback = new ArrayList<>();
        synchronized (treatments) {
            for (Integer pos = 0; pos < treatments.size(); pos++) {
                Treatment t = treatments.get(pos);
                if (!t.isValid)
                    continue;
                if (t.date <= time && t.date > time - 5 * 60 * 1000 && t.carbs > 0)
                    in5minback.add(t);
            }
            return in5minback;
        }
    }

    @Override
    public long getLastBolusTime() {
        long now = System.currentTimeMillis();
        long last = 0;
        synchronized (treatments) {
            for (Treatment t : treatments) {
                if (!t.isValid)
                    continue;
                if (t.date > last && t.insulin > 0 && t.isValid && t.date <= now)
                    last = t.date;
            }
        }
        if (L.isEnabled(L.DATATREATMENTS))
            log.debug("Last bolus time: " + new Date(last).toLocaleString());
        return last;
    }

    @Override
    public boolean isInHistoryRealTempBasalInProgress() {
        return getRealTempBasalFromHistory(System.currentTimeMillis()) != null;
    }

    @Override
    public TemporaryBasal getRealTempBasalFromHistory(long time) {
        synchronized (tempBasals) {
            return tempBasals.getValueByInterval(time);
        }
    }

    @Override
    public boolean isTempBasalInProgress() {
        return getTempBasalFromHistory(System.currentTimeMillis()) != null;
    }

    @Override
    public boolean isInHistoryExtendedBoluslInProgress() {
        return getExtendedBolusFromHistory(System.currentTimeMillis()) != null; //TODO:  crosscheck here
    }

    @Subscribe
    public void onStatusEvent(final EventReloadTreatmentData ev) {
        if (L.isEnabled(L.DATATREATMENTS))
            log.debug("EventReloadTreatmentData");
        initializeTreatmentData();
        initializeExtendedBolusData();
        updateTotalIOBTreatments();
        MainApp.bus().post(ev.next);
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onStatusEvent(final EventReloadTempBasalData ev) {
        if (L.isEnabled(L.DATATREATMENTS))
            log.debug("EventReloadTempBasalData");
        initializeTempBasalData();
        updateTotalIOBTempBasals();
    }

    @Override
    public IobTotal getLastCalculationTempBasals() {
        return lastTempBasalsCalculation;
    }

    @Override
    public IobTotal getCalculationToTimeTempBasals(long time, Profile profile) {
        return getCalculationToTimeTempBasals(time, profile, false, 0);
    }

    public IobTotal getCalculationToTimeTempBasals(long time, Profile profile, boolean truncate, long truncateTime) {
        IobTotal total = new IobTotal(time);

        InsulinInterface insulinInterface = ConfigBuilderPlugin.getPlugin().getActiveInsulin();
        if (insulinInterface == null)
            return total;

        synchronized (tempBasals) {
            for (Integer pos = 0; pos < tempBasals.size(); pos++) {
                TemporaryBasal t = tempBasals.get(pos);
                if (t.date > time) continue;
                IobTotal calc;
                if (truncate && t.end() > truncateTime) {
                    TemporaryBasal dummyTemp = new TemporaryBasal();
                    dummyTemp.copyFrom(t);
                    dummyTemp.cutEndTo(truncateTime);
                    calc = dummyTemp.iobCalc(time, profile);
                } else {
                    calc = t.iobCalc(time, profile);
                }
                //log.debug("BasalIOB " + new Date(time) + " >>> " + calc.basaliob);
                total.plus(calc);
            }
        }
        if (ConfigBuilderPlugin.getPlugin().getActivePump().isFakingTempsByExtendedBoluses()) {
            IobTotal totalExt = new IobTotal(time);
            synchronized (extendedBoluses) {
                for (Integer pos = 0; pos < extendedBoluses.size(); pos++) {
                    ExtendedBolus e = extendedBoluses.get(pos);
                    if (e.date > time) continue;
                    IobTotal calc;
                    if (truncate && e.end() > truncateTime) {
                        ExtendedBolus dummyExt = new ExtendedBolus();
                        dummyExt.copyFrom(e);
                        dummyExt.cutEndTo(truncateTime);
                        calc = dummyExt.iobCalc(time);
                    } else {
                        calc = e.iobCalc(time);
                    }
                    totalExt.plus(calc);
                }
            }
            // Convert to basal iob
            totalExt.basaliob = totalExt.iob;
            totalExt.iob = 0d;
            totalExt.netbasalinsulin = totalExt.extendedBolusInsulin;
            totalExt.hightempinsulin = totalExt.extendedBolusInsulin;
            total.plus(totalExt);
        }
        return total;
    }

    @Override
    public void updateTotalIOBTempBasals() {
        Profile profile = ProfileFunctions.getInstance().getProfile();
        if (profile != null)
            lastTempBasalsCalculation = getCalculationToTimeTempBasals(DateUtil.now(), profile);
    }

    @Nullable
    @Override
    public TemporaryBasal getTempBasalFromHistory(long time) {
        TemporaryBasal tb = getRealTempBasalFromHistory(time);
        if (tb != null)
            return tb;
        ExtendedBolus eb = getExtendedBolusFromHistory(time);
        if (eb != null && ConfigBuilderPlugin.getPlugin().getActivePump().isFakingTempsByExtendedBoluses())
            return new TemporaryBasal(eb);
        return null;
    }

    @Override
    public ExtendedBolus getExtendedBolusFromHistory(long time) {
        synchronized (extendedBoluses) {
            return extendedBoluses.getValueByInterval(time);
        }
    }

    @Override
    public boolean addToHistoryExtendedBolus(ExtendedBolus extendedBolus) {
        //log.debug("Adding new ExtentedBolus record" + extendedBolus.log());
        boolean newRecordCreated = MainApp.getDbHelper().createOrUpdate(extendedBolus);
        if (newRecordCreated) {
            if (extendedBolus.durationInMinutes == 0) {
                if (ConfigBuilderPlugin.getPlugin().getActivePump().isFakingTempsByExtendedBoluses())
                    NSUpload.uploadTempBasalEnd(extendedBolus.date, true, extendedBolus.pumpId);
                else
                    NSUpload.uploadExtendedBolusEnd(extendedBolus.date, extendedBolus.pumpId);
            } else if (ConfigBuilderPlugin.getPlugin().getActivePump().isFakingTempsByExtendedBoluses())
                NSUpload.uploadTempBasalStartAbsolute(new TemporaryBasal(extendedBolus), extendedBolus.insulin);
            else
                NSUpload.uploadExtendedBolus(extendedBolus);
        }
        return newRecordCreated;
    }

    @Override
    public Intervals<ExtendedBolus> getExtendedBolusesFromHistory() {
        synchronized (extendedBoluses) {
            return new NonOverlappingIntervals<>(extendedBoluses);
        }
    }

    @Override
    public Intervals<TemporaryBasal> getTemporaryBasalsFromHistory() {
        synchronized (tempBasals) {
            return new NonOverlappingIntervals<>(tempBasals);
        }
    }

    @Override
    public boolean addToHistoryTempBasal(TemporaryBasal tempBasal) {
        //log.debug("Adding new TemporaryBasal record" + tempBasal.toString());
        boolean newRecordCreated = MainApp.getDbHelper().createOrUpdate(tempBasal);
        if (newRecordCreated) {
            if (tempBasal.durationInMinutes == 0)
                NSUpload.uploadTempBasalEnd(tempBasal.date, false, tempBasal.pumpId);
            else if (tempBasal.isAbsolute)
                NSUpload.uploadTempBasalStartAbsolute(tempBasal, null);
            else
                NSUpload.uploadTempBasalStartPercent(tempBasal);
        }
        return newRecordCreated;
    }

    // return true if new record is created
    @Override
    public boolean addToHistoryTreatment(DetailedBolusInfo detailedBolusInfo, boolean allowUpdate) {
        Treatment treatment = new Treatment();
        treatment.date = detailedBolusInfo.date;
        treatment.source = detailedBolusInfo.source;
        treatment.pumpId = detailedBolusInfo.pumpId;
        treatment.insulin = detailedBolusInfo.insulin;
        treatment.isValid = detailedBolusInfo.isValid;
        treatment.isSMB = detailedBolusInfo.isSMB;
        if (detailedBolusInfo.carbTime == 0)
            treatment.carbs = detailedBolusInfo.carbs;
        treatment.source = detailedBolusInfo.source;
        treatment.mealBolus = treatment.carbs > 0;
        treatment.boluscalc = detailedBolusInfo.boluscalc != null ? detailedBolusInfo.boluscalc.toString() : null;
        TreatmentService.UpdateReturn creatOrUpdateResult = getService().createOrUpdate(treatment);
        boolean newRecordCreated = creatOrUpdateResult.newRecord;
        //log.debug("Adding new Treatment record" + treatment.toString());
        if (detailedBolusInfo.carbTime != 0) {
            Treatment carbsTreatment = new Treatment();
            carbsTreatment.source = detailedBolusInfo.source;
            carbsTreatment.pumpId = detailedBolusInfo.pumpId; // but this should never happen
            carbsTreatment.date = detailedBolusInfo.date + detailedBolusInfo.carbTime * 60 * 1000L + 1000L; // add 1 sec to make them different records
            carbsTreatment.carbs = detailedBolusInfo.carbs;
            carbsTreatment.source = detailedBolusInfo.source;
            getService().createOrUpdate(carbsTreatment);
            //log.debug("Adding new Treatment record" + carbsTreatment);
        }
        if (newRecordCreated && detailedBolusInfo.isValid)
            NSUpload.uploadTreatmentRecord(detailedBolusInfo);

        if (!allowUpdate && !creatOrUpdateResult.success) {
            log.error("Treatment could not be added to DB", new Exception());

            String status = String.format(MainApp.gs(R.string.error_adding_treatment_message), treatment.insulin, (int) treatment.carbs, DateUtil.dateAndTimeString(treatment.date));

            Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
            i.putExtra("soundid", R.raw.error);
            i.putExtra("title", MainApp.gs(R.string.error_adding_treatment_title));
            i.putExtra("status", status);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            MainApp.instance().startActivity(i);

            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "TreatmentClash");
            bundle.putString(FirebaseAnalytics.Param.VALUE, status);
            FabricPrivacy.getInstance().logCustom(bundle);
        }

        return newRecordCreated;
    }

    @Override
    public long oldestDataAvailable() {
        long oldestTime = System.currentTimeMillis();
        synchronized (tempBasals) {
            if (tempBasals.size() > 0)
                oldestTime = Math.min(oldestTime, tempBasals.get(0).date);
        }
        synchronized (extendedBoluses) {
            if (extendedBoluses.size() > 0)
                oldestTime = Math.min(oldestTime, extendedBoluses.get(0).date);
        }
        synchronized (treatments) {
            if (treatments.size() > 0)
                oldestTime = Math.min(oldestTime, treatments.get(treatments.size() - 1).date);
        }
        oldestTime -= 15 * 60 * 1000L; // allow 15 min before
        return oldestTime;
    }

    // TempTargets
    @Subscribe
    @SuppressWarnings("unused")
    public void onStatusEvent(final EventTempTargetChange ev) {
        initializeTempTargetData();
    }

    @Nullable
    @Override
    public TempTarget getTempTargetFromHistory() {
        synchronized (tempTargets) {
            return tempTargets.getValueByInterval(System.currentTimeMillis());
        }
    }

    @Nullable
    @Override
    public TempTarget getTempTargetFromHistory(long time) {
        synchronized (tempTargets) {
            return tempTargets.getValueByInterval(time);
        }
    }

    @Override
    public Intervals<TempTarget> getTempTargetsFromHistory() {
        synchronized (tempTargets) {
            return new OverlappingIntervals<>(tempTargets);
        }
    }

    @Override
    public void addToHistoryTempTarget(TempTarget tempTarget) {
        //log.debug("Adding new TemporaryBasal record" + profileSwitch.log());
        MainApp.getDbHelper().createOrUpdate(tempTarget);
        NSUpload.uploadTempTarget(tempTarget);
    }

    // Profile Switch
    @Subscribe
    @SuppressWarnings("unused")
    public void onStatusEvent(final EventReloadProfileSwitchData ev) {
        initializeProfileSwitchData();
    }

    @Override
    public ProfileSwitch getProfileSwitchFromHistory(long time) {
        synchronized (profiles) {
            return (ProfileSwitch) profiles.getValueToTime(time);
        }
    }

    @Override
    public ProfileIntervals<ProfileSwitch> getProfileSwitchesFromHistory() {
        synchronized (profiles) {
            return new ProfileIntervals<>(profiles);
        }
    }

    @Override
    public void addToHistoryProfileSwitch(ProfileSwitch profileSwitch) {
        //log.debug("Adding new TemporaryBasal record" + profileSwitch.log());
        MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_SWITCH_MISSING));
        MainApp.getDbHelper().createOrUpdate(profileSwitch);
        NSUpload.uploadProfileSwitch(profileSwitch);
    }


}
