package info.nightscout.androidaps.plugins.treatments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.activities.ErrorHelperActivity;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Intervals;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.NonOverlappingIntervals;
import info.nightscout.androidaps.data.OverlappingIntervals;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileIntervals;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventReloadProfileSwitchData;
import info.nightscout.androidaps.events.EventReloadTempBasalData;
import info.nightscout.androidaps.events.EventReloadTreatmentData;
import info.nightscout.androidaps.events.EventTempTargetChange;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.interfaces.ProfileStore;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.data.MedtronicHistoryData;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

@Singleton
public class TreatmentsPlugin extends PluginBase implements TreatmentsInterface {

    private final Context context;
    private final SP sp;
    private final RxBusWrapper rxBus;
    private final ResourceHelper resourceHelper;
    private final ProfileFunction profileFunction;
    private final ActivePluginProvider activePlugin;
    private final NSUpload nsUpload;
    private final UploadQueue uploadQueue;
    private final FabricPrivacy fabricPrivacy;
    private final DateUtil dateUtil;

    private final CompositeDisposable disposable = new CompositeDisposable();

    protected TreatmentService service;

    private IobTotal lastTreatmentCalculation;
    private IobTotal lastTempBasalsCalculation;

    private final ArrayList<Treatment> treatments = new ArrayList<>();
    private final Intervals<TemporaryBasal> tempBasals = new NonOverlappingIntervals<>();
    private final Intervals<ExtendedBolus> extendedBoluses = new NonOverlappingIntervals<>();
    private final Intervals<TempTarget> tempTargets = new OverlappingIntervals<>();
    private final ProfileIntervals<ProfileSwitch> profiles = new ProfileIntervals<>();

    @Inject
    public TreatmentsPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            ResourceHelper resourceHelper,
            Context context,
            SP sp,
            ProfileFunction profileFunction,
            ActivePluginProvider activePlugin,
            NSUpload nsUpload,
            FabricPrivacy fabricPrivacy,
            DateUtil dateUtil,
            UploadQueue uploadQueue
    ) {
        super(new PluginDescription()
                        .mainType(PluginType.TREATMENT)
                        .fragmentClass(TreatmentsFragment.class.getName())
                        .pluginIcon(R.drawable.ic_treatments)
                        .pluginName(R.string.treatments)
                        .shortName(R.string.treatments_shortname)
                        .alwaysEnabled(true)
                        .description(R.string.description_treatments)
                        .setDefault(),
                aapsLogger, resourceHelper, injector
        );
        this.resourceHelper = resourceHelper;
        this.context = context;
        this.rxBus = rxBus;
        this.sp = sp;
        this.profileFunction = profileFunction;
        this.activePlugin = activePlugin;
        this.fabricPrivacy = fabricPrivacy;
        this.dateUtil = dateUtil;
        this.nsUpload = nsUpload;
        this.uploadQueue = uploadQueue;
    }

    @Override
    protected void onStart() {
        this.service = new TreatmentService(getInjector());
        initializeData(range());
        super.onStart();
        disposable.add(rxBus
                .toObservable(EventReloadTreatmentData.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                            getAapsLogger().debug(LTag.DATATREATMENTS, "EventReloadTreatmentData");
                            initializeTreatmentData(range());
                            initializeExtendedBolusData(range());
                            updateTotalIOBTreatments();
                            rxBus.send(event.getNext());
                        },
                        fabricPrivacy::logException
                ));
        disposable.add(rxBus
                .toObservable(EventReloadProfileSwitchData.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> initializeProfileSwitchData(range()),
                        fabricPrivacy::logException
                ));
        disposable.add(rxBus
                .toObservable(EventTempTargetChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> initializeTempTargetData(range()),
                        fabricPrivacy::logException
                ));
        disposable.add(rxBus
                .toObservable(EventReloadTempBasalData.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                            getAapsLogger().debug(LTag.DATATREATMENTS, "EventReloadTempBasalData");
                            initializeTempBasalData(range());
                            updateTotalIOBTempBasals();
                        },
                        fabricPrivacy::logException
                ));
    }

    @Override
    protected void onStop() {
        disposable.clear();
        super.onStop();
    }

    public TreatmentService getService() {
        return this.service;
    }

    protected long range() {
        double dia = Constants.defaultDIA;
        if (profileFunction.getProfile() != null)
            dia = profileFunction.getProfile().getDia();
        return (long) (60 * 60 * 1000L * (24 + dia));
    }

    public void initializeData(long range) {
        initializeTempBasalData(range);
        initializeTreatmentData(range);
        initializeExtendedBolusData(range);
        initializeTempTargetData(range);
        initializeProfileSwitchData(range);
    }

    private void initializeTreatmentData(long range) {
        getAapsLogger().debug(LTag.DATATREATMENTS, "initializeTreatmentData");
        synchronized (treatments) {
            treatments.clear();
            treatments.addAll(getService().getTreatmentDataFromTime(DateUtil.now() - range, false));
        }
    }

    private void initializeTempBasalData(long range) {
        getAapsLogger().debug(LTag.DATATREATMENTS, "initializeTempBasalData");
        synchronized (tempBasals) {
            tempBasals.reset().add(MainApp.getDbHelper().getTemporaryBasalsDataFromTime(DateUtil.now() - range, false));
        }

    }

    private void initializeExtendedBolusData(long range) {
        getAapsLogger().debug(LTag.DATATREATMENTS, "initializeExtendedBolusData");
        synchronized (extendedBoluses) {
            extendedBoluses.reset().add(MainApp.getDbHelper().getExtendedBolusDataFromTime(DateUtil.now() - range, false));
        }

    }

    private void initializeTempTargetData(long range) {
        getAapsLogger().debug(LTag.DATATREATMENTS, "initializeTempTargetData");
        synchronized (tempTargets) {
            tempTargets.reset().add(MainApp.getDbHelper().getTemptargetsDataFromTime(DateUtil.now() - range, false));
        }
    }

    private void initializeProfileSwitchData(long range) {
        getAapsLogger().debug(LTag.DATATREATMENTS, "initializeProfileSwitchData");
        synchronized (profiles) {
            profiles.reset().add(MainApp.getDbHelper().getProfileSwitchData(DateUtil.now() - range, false));
        }
    }

    @Override
    public IobTotal getLastCalculationTreatments() {
        return lastTreatmentCalculation;
    }

    @Override
    public IobTotal getCalculationToTimeTreatments(long time) {
        IobTotal total = new IobTotal(time);

        Profile profile = profileFunction.getProfile();
        if (profile == null)
            return total;

        PumpInterface pumpInterface = activePlugin.getActivePump();

        double dia = profile.getDia();

        synchronized (treatments) {
            for (int pos = 0; pos < treatments.size(); pos++) {
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
                    long snoozeTime = t.date + (long) (timeSinceTreatment * sp.getDouble(R.string.key_openapsama_bolussnooze_dia_divisor, 2.0));
                    Iob bIOB = t.iobCalc(snoozeTime, dia);
                    total.bolussnooze += bIOB.iobContrib;
                }
            }
        }

        if (!pumpInterface.isFakingTempsByExtendedBoluses())
            synchronized (extendedBoluses) {
                for (int pos = 0; pos < extendedBoluses.size(); pos++) {
                    ExtendedBolus e = extendedBoluses.get(pos);
                    if (e.date > time) continue;
                    IobTotal calc = e.iobCalc(time, profile);
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
    public List<Treatment> getTreatmentsFromHistory() {
        synchronized (treatments) {
            return new ArrayList<>(treatments);
        }
    }


    /**
     * Returns all Treatments after specified timestamp. Also returns invalid entries (required to
     * map "Fill Canula" entries to history (and not to add double bolus for it)
     *
     * @param fromTimestamp
     * @return
     */
    @Override
    public List<Treatment> getTreatmentsFromHistoryAfterTimestamp(long fromTimestamp) {
        List<Treatment> in5minback = new ArrayList<>();

        long time = System.currentTimeMillis();
        synchronized (treatments) {
//            getAapsLogger().debug(MedtronicHistoryData.doubleBolusDebug, LTag.DATATREATMENTS, "DoubleBolusDebug: AllTreatmentsInDb: " + new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(treatments));

            for (Treatment t : treatments) {
                if (t.date <= time && t.date >= fromTimestamp)
                    in5minback.add(t);
            }
//            getAapsLogger().debug(MedtronicHistoryData.doubleBolusDebug, LTag.DATATREATMENTS, "DoubleBolusDebug: FilteredTreatments: AfterTime={}, Items={} " + fromTimestamp + " " + new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(in5minback));
            return in5minback;
        }
    }


    @Override
    public List<Treatment> getCarbTreatments5MinBackFromHistory(long time) {
        List<Treatment> in5minback = new ArrayList<>();
        synchronized (treatments) {
            for (Treatment t : treatments) {
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
        Treatment last = getService().getLastBolus(false);
        if (last == null) {
            getAapsLogger().debug(LTag.DATATREATMENTS, "Last bolus time: NOTHING FOUND");
            return 0;
        } else {
            getAapsLogger().debug(LTag.DATATREATMENTS, "Last bolus time: " + dateUtil.dateAndTimeString(last.date));
            return last.date;
        }
    }

    public long getLastBolusTime(boolean excludeSMB) {
        Treatment last = getService().getLastBolus(excludeSMB);
        if (last == null) {
            getAapsLogger().debug(LTag.DATATREATMENTS, "Last manual bolus time: NOTHING FOUND");
            return 0;
        } else {
            getAapsLogger().debug(LTag.DATATREATMENTS, "Last manual bolus time: " + dateUtil.dateAndTimeString(last.date));
            return last.date;
        }
    }

    public long getLastCarbTime() {
        Treatment last = getService().getLastCarb();
        if (last == null) {
            getAapsLogger().debug(LTag.DATATREATMENTS, "Last Carb time: NOTHING FOUND");
            return 0;
        } else {
            getAapsLogger().debug(LTag.DATATREATMENTS, "Last Carb time: " + dateUtil.dateAndTimeString(last.date));
            return last.date;
        }
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

    @Override public void removeTempBasal(TemporaryBasal tempBasal) {
        String tempBasalId = tempBasal._id;
        if (NSUpload.isIdValid(tempBasalId)) {
            nsUpload.removeCareportalEntryFromNS(tempBasalId);
        } else {
            uploadQueue.removeID("dbAdd", tempBasalId);
        }
        MainApp.getDbHelper().delete(tempBasal);
    }

    @Override
    public boolean isInHistoryExtendedBoluslInProgress() {
        return getExtendedBolusFromHistory(System.currentTimeMillis()) != null; //TODO:  crosscheck here
    }

    @Override
    public IobTotal getLastCalculationTempBasals() {
        return lastTempBasalsCalculation;
    }

    @Override
    public IobTotal getCalculationToTimeTempBasals(long time) {
        return getCalculationToTimeTempBasals(time, false, 0);
    }

    public IobTotal getCalculationToTimeTempBasals(long time, boolean truncate, long truncateTime) {
        IobTotal total = new IobTotal(time);

        PumpInterface pumpInterface = activePlugin.getActivePump();

        synchronized (tempBasals) {
            for (Integer pos = 0; pos < tempBasals.size(); pos++) {
                TemporaryBasal t = tempBasals.get(pos);
                if (t.date > time) continue;
                IobTotal calc;
                Profile profile = profileFunction.getProfile(t.date);
                if (profile == null) continue;
                if (truncate && t.end() > truncateTime) {
                    TemporaryBasal dummyTemp = new TemporaryBasal(getInjector());
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
        if (pumpInterface.isFakingTempsByExtendedBoluses()) {
            IobTotal totalExt = new IobTotal(time);
            synchronized (extendedBoluses) {
                for (int pos = 0; pos < extendedBoluses.size(); pos++) {
                    ExtendedBolus e = extendedBoluses.get(pos);
                    if (e.date > time) continue;
                    IobTotal calc;
                    Profile profile = profileFunction.getProfile(e.date);
                    if (profile == null) continue;
                    if (truncate && e.end() > truncateTime) {
                        ExtendedBolus dummyExt = new ExtendedBolus(getInjector());
                        dummyExt.copyFrom(e);
                        dummyExt.cutEndTo(truncateTime);
                        calc = dummyExt.iobCalc(time, profile);
                    } else {
                        calc = e.iobCalc(time, profile);
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

    public IobTotal getAbsoluteIOBTempBasals(long time) {
        IobTotal total = new IobTotal(time);

        for (long i = time - range(); i < time; i += T.mins(5).msecs()) {
            Profile profile = profileFunction.getProfile(i);
            if (profile == null) continue;
            double basal = profile.getBasal(i);
            TemporaryBasal runningTBR = getTempBasalFromHistory(i);
            double running = basal;
            if (runningTBR != null) {
                running = runningTBR.tempBasalConvertedToAbsolute(i, profile);
            }
            Treatment treatment = new Treatment(getInjector());
            treatment.date = i;
            treatment.insulin = running * 5.0 / 60.0; // 5 min chunk
            Iob iob = treatment.iobCalc(time, profile.getDia());
            total.basaliob += iob.iobContrib;
            total.activity += iob.activityContrib;
        }
        return total;
    }

    public IobTotal getCalculationToTimeTempBasals(long time, long truncateTime, AutosensResult lastAutosensResult, boolean exercise_mode, int half_basal_exercise_target, boolean isTempTarget) {
        IobTotal total = new IobTotal(time);

        PumpInterface pumpInterface = activePlugin.getActivePump();

        synchronized (tempBasals) {
            for (int pos = 0; pos < tempBasals.size(); pos++) {
                TemporaryBasal t = tempBasals.get(pos);
                if (t.date > time) continue;
                IobTotal calc;
                Profile profile = profileFunction.getProfile(t.date);
                if (profile == null) continue;
                if (t.end() > truncateTime) {
                    TemporaryBasal dummyTemp = new TemporaryBasal(getInjector());
                    dummyTemp.copyFrom(t);
                    dummyTemp.cutEndTo(truncateTime);
                    calc = dummyTemp.iobCalc(time, profile, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget);
                } else {
                    calc = t.iobCalc(time, profile, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget);
                }
                //log.debug("BasalIOB " + new Date(time) + " >>> " + calc.basaliob);
                total.plus(calc);
            }
        }
        if (pumpInterface.isFakingTempsByExtendedBoluses()) {
            IobTotal totalExt = new IobTotal(time);
            synchronized (extendedBoluses) {
                for (int pos = 0; pos < extendedBoluses.size(); pos++) {
                    ExtendedBolus e = extendedBoluses.get(pos);
                    if (e.date > time) continue;
                    IobTotal calc;
                    Profile profile = profileFunction.getProfile(e.date);
                    if (profile == null) continue;
                    if (e.end() > truncateTime) {
                        ExtendedBolus dummyExt = new ExtendedBolus(getInjector());
                        dummyExt.copyFrom(e);
                        dummyExt.cutEndTo(truncateTime);
                        calc = dummyExt.iobCalc(time, profile, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget);
                    } else {
                        calc = e.iobCalc(time, profile, lastAutosensResult, exercise_mode, half_basal_exercise_target, isTempTarget);
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
        lastTempBasalsCalculation = getCalculationToTimeTempBasals(DateUtil.now());
    }

    @Nullable
    @Override
    public TemporaryBasal getTempBasalFromHistory(long time) {
        TemporaryBasal tb = getRealTempBasalFromHistory(time);
        if (tb != null)
            return tb;
        ExtendedBolus eb = getExtendedBolusFromHistory(time);
        if (eb != null && activePlugin.getActivePump().isFakingTempsByExtendedBoluses())
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
                if (activePlugin.getActivePump().isFakingTempsByExtendedBoluses())
                    nsUpload.uploadTempBasalEnd(extendedBolus.date, true, extendedBolus.pumpId);
                else
                    nsUpload.uploadExtendedBolusEnd(extendedBolus.date, extendedBolus.pumpId);
            } else if (activePlugin.getActivePump().isFakingTempsByExtendedBoluses())
                nsUpload.uploadTempBasalStartAbsolute(new TemporaryBasal(extendedBolus), extendedBolus.insulin);
            else
                nsUpload.uploadExtendedBolus(extendedBolus);
        }
        return newRecordCreated;
    }

    @Override
    @NonNull
    public Intervals<ExtendedBolus> getExtendedBolusesFromHistory() {
        synchronized (extendedBoluses) {
            return new NonOverlappingIntervals<>(extendedBoluses);
        }
    }

    @Override
    @NonNull
    public NonOverlappingIntervals<TemporaryBasal> getTemporaryBasalsFromHistory() {
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
                nsUpload.uploadTempBasalEnd(tempBasal.date, false, tempBasal.pumpId);
            else if (tempBasal.isAbsolute)
                nsUpload.uploadTempBasalStartAbsolute(tempBasal, null);
            else
                nsUpload.uploadTempBasalStartPercent(tempBasal, profileFunction.getProfile(tempBasal.date));
        }
        return newRecordCreated;
    }

    public TreatmentUpdateReturn createOrUpdateMedtronic(Treatment treatment, boolean fromNightScout) {
        TreatmentService.UpdateReturn resultRecord = getService().createOrUpdateMedtronic(treatment, fromNightScout);

        return new TreatmentUpdateReturn(resultRecord.success, resultRecord.newRecord);
    }

    // return true if new record is created
    @Override
    public boolean addToHistoryTreatment(DetailedBolusInfo detailedBolusInfo, boolean allowUpdate) {
        boolean medtronicPump = activePlugin.getActivePump() instanceof MedtronicPumpPlugin;

        getAapsLogger().debug(MedtronicHistoryData.doubleBolusDebug, LTag.DATATREATMENTS, "DoubleBolusDebug: addToHistoryTreatment::isMedtronicPump={} " + medtronicPump);

        Treatment treatment = new Treatment();
        treatment.date = detailedBolusInfo.date;
        treatment.source = detailedBolusInfo.source;
        treatment.pumpId = detailedBolusInfo.pumpId;
        treatment.insulin = detailedBolusInfo.insulin;
        treatment.isValid = detailedBolusInfo.isValid;
        treatment.isSMB = detailedBolusInfo.isSMB;
        if (detailedBolusInfo.carbTime == 0)
            treatment.carbs = detailedBolusInfo.carbs;
        treatment.mealBolus = treatment.carbs > 0;
        treatment.boluscalc = detailedBolusInfo.boluscalc != null ? detailedBolusInfo.boluscalc.toString() : null;
        TreatmentService.UpdateReturn creatOrUpdateResult;

        getAapsLogger().debug(medtronicPump && MedtronicHistoryData.doubleBolusDebug, LTag.DATATREATMENTS, "DoubleBolusDebug: addToHistoryTreatment::treatment={} " + treatment);

        if (!medtronicPump)
            creatOrUpdateResult = getService().createOrUpdate(treatment);
        else
            creatOrUpdateResult = getService().createOrUpdateMedtronic(treatment, false);

        boolean newRecordCreated = creatOrUpdateResult.newRecord;
        //log.debug("Adding new Treatment record" + treatment.toString());
        if (detailedBolusInfo.carbTime != 0) {

            Treatment carbsTreatment = new Treatment();
            carbsTreatment.source = detailedBolusInfo.source;
            carbsTreatment.pumpId = detailedBolusInfo.pumpId; // but this should never happen
            carbsTreatment.date = detailedBolusInfo.date + detailedBolusInfo.carbTime * 60 * 1000L + 1000L; // add 1 sec to make them different records
            carbsTreatment.carbs = detailedBolusInfo.carbs;

            getAapsLogger().debug(medtronicPump && MedtronicHistoryData.doubleBolusDebug, LTag.DATATREATMENTS, "DoubleBolusDebug: carbTime!=0, creating second treatment. CarbsTreatment={}" + carbsTreatment);

            if (!medtronicPump)
                getService().createOrUpdate(carbsTreatment);
            else
                getService().createOrUpdateMedtronic(carbsTreatment, false);
            //log.debug("Adding new Treatment record" + carbsTreatment);
        }
        if (newRecordCreated && detailedBolusInfo.isValid)
            nsUpload.uploadTreatmentRecord(detailedBolusInfo);

        if (!allowUpdate && !creatOrUpdateResult.success) {
            getAapsLogger().error("Treatment could not be added to DB", new Exception());

            String status = String.format(resourceHelper.gs(R.string.error_adding_treatment_message), treatment.insulin, (int) treatment.carbs, dateUtil.dateAndTimeString(treatment.date));

            Intent i = new Intent(context, ErrorHelperActivity.class);
            i.putExtra("soundid", R.raw.error);
            i.putExtra("title", resourceHelper.gs(R.string.error_adding_treatment_title));
            i.putExtra("status", status);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);

            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_LIST_ID, "TreatmentClash");
            bundle.putString(FirebaseAnalytics.Param.ITEM_LIST_NAME, status);
            fabricPrivacy.logCustom(bundle);
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
        nsUpload.uploadTempTarget(tempTarget, profileFunction);
    }

    @Override
    @Nullable
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
        rxBus.send(new EventDismissNotification(Notification.PROFILE_SWITCH_MISSING));
        MainApp.getDbHelper().createOrUpdate(profileSwitch);
        nsUpload.uploadProfileSwitch(profileSwitch);
    }

    @Override
    public void doProfileSwitch(@NotNull final ProfileStore profileStore, @NotNull final String profileName, final int duration, final int percentage, final int timeShift, final long date) {
        ProfileSwitch profileSwitch = profileFunction.prepareProfileSwitch(profileStore, profileName, duration, percentage, timeShift, date);
        addToHistoryProfileSwitch(profileSwitch);
        if (percentage == 90 && duration == 10)
            sp.putBoolean(R.string.key_objectiveuseprofileswitch, true);
    }

    @Override
    public void doProfileSwitch(final int duration, final int percentage, final int timeShift) {
        ProfileSwitch profileSwitch = getProfileSwitchFromHistory(System.currentTimeMillis());
        if (profileSwitch != null) {
            profileSwitch = new ProfileSwitch(getInjector());
            profileSwitch.date = System.currentTimeMillis();
            profileSwitch.source = Source.USER;
            profileSwitch.profileName = profileFunction.getProfileName(System.currentTimeMillis(), false, false);
            profileSwitch.profileJson = profileFunction.getProfile().getData().toString();
            profileSwitch.profilePlugin = activePlugin.getActiveProfileInterface().getClass().getName();
            profileSwitch.durationInMinutes = duration;
            profileSwitch.isCPP = percentage != 100 || timeShift != 0;
            profileSwitch.timeshift = timeShift;
            profileSwitch.percentage = percentage;
            addToHistoryProfileSwitch(profileSwitch);
        } else {
            getAapsLogger().error(LTag.PROFILE, "No profile switch exists");
        }
    }

}
