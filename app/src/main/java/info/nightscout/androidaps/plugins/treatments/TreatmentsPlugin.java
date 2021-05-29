package info.nightscout.androidaps.plugins.treatments;

import android.content.Context;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.database.AppRepository;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.ActivePlugin;
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.interfaces.TreatmentServiceInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.rx.AapsSchedulers;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.disposables.CompositeDisposable;

@Singleton
public class TreatmentsPlugin extends PluginBase implements TreatmentsInterface {

    private final SP sp;
    private final RxBusWrapper rxBus;
    private final ProfileFunction profileFunction;
    private final ActivePlugin activePlugin;
    private final FabricPrivacy fabricPrivacy;
    private final DateUtil dateUtil;
    private final DatabaseHelperInterface databaseHelper;
    private final AppRepository repository;

    private final CompositeDisposable disposable = new CompositeDisposable();

    protected TreatmentServiceInterface service;

    @Inject
    public TreatmentsPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            AapsSchedulers aapsSchedulers,
            ResourceHelper resourceHelper,
            Context context,
            SP sp,
            ProfileFunction profileFunction,
            ActivePlugin activePlugin,
            FabricPrivacy fabricPrivacy,
            DateUtil dateUtil,
            DatabaseHelperInterface databaseHelper,
            AppRepository repository
    ) {
        super(new PluginDescription()
                        .mainType(PluginType.TREATMENT)
                        .pluginIcon(R.drawable.ic_treatments)
                        .pluginName(R.string.treatments)
                        .shortName(R.string.treatments_shortname)
                        .alwaysEnabled(true)
                        .description(R.string.description_treatments)
                        .setDefault(),
                aapsLogger, resourceHelper, injector
        );
        this.rxBus = rxBus;
        this.sp = sp;
        this.profileFunction = profileFunction;
        this.activePlugin = activePlugin;
        this.fabricPrivacy = fabricPrivacy;
        this.dateUtil = dateUtil;
        this.databaseHelper = databaseHelper;
        this.repository = repository;
    }

    @Override
    protected void onStart() {
        this.service = new TreatmentService(getInjector());
        super.onStart();
//        disposable.add(rxBus
//                .toObservable(EventReloadProfileSwitchData.class)
//                .observeOn(aapsSchedulers.getIo())
//                .subscribe(event -> initializeProfileSwitchData(range()),
//                        fabricPrivacy::logException
//                ));
    }

    @Override
    protected void onStop() {
        disposable.clear();
        super.onStop();
    }

    @Override
    public TreatmentServiceInterface getService() {
        return this.service;
    }

    protected long range() {
        double dia = Constants.defaultDIA;
        if (profileFunction.getProfile() != null)
            dia = profileFunction.getProfile().getDia();
        return (long) (60 * 60 * 1000L * (24 + dia));
    }

    /**
     * Returns all Treatments after specified timestamp. Also returns invalid entries (required to
     * map "Fill Cannula" entries to history (and not to add double bolus for it)
     *
     * @param fromTimestamp
     * @return
     */
    @Deprecated
    @Override
    public List<Treatment> getTreatmentsFromHistoryAfterTimestamp(long fromTimestamp) {
        return repository.getBolusesIncludingInvalidFromTimeToTime(fromTimestamp, dateUtil.now(), true)
                .blockingGet()
                .stream()
                .map(bolus -> new Treatment(getInjector(), bolus))
                .collect(Collectors.toList());
/*
        List<Treatment> in5minback = new ArrayList<>();

        long time = System.currentTimeMillis();
        synchronized (treatments) {
//            getAapsLogger().debug(MedtronicHistoryData.doubleBolusDebug, LTag.DATATREATMENTS, "DoubleBolusDebug: AllTreatmentsInDb: " + new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(treatments));

            for (Treatment t : treatments) {
                if (t.date >= fromTimestamp && t.date <= time)
                    in5minback.add(t);
            }
//            getAapsLogger().debug(MedtronicHistoryData.doubleBolusDebug, LTag.DATATREATMENTS, "DoubleBolusDebug: FilteredTreatments: AfterTime={}, Items={} " + fromTimestamp + " " + new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(in5minback));
            return in5minback;
        }
*/
    }
/*
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


 */

    @Deprecated
    @Override
    public boolean addToHistoryExtendedBolus(ExtendedBolus extendedBolus) {
        throw new IllegalStateException("Migrate to new DB");
        //log.debug("Adding new ExtentedBolus record" + extendedBolus.log());
        /*
        boolean newRecordCreated = databaseHelper.createOrUpdate(extendedBolus);
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
         */
    }

    @Deprecated
    @Override
    public boolean addToHistoryTempBasal(TemporaryBasal tempBasal) {
        throw new IllegalStateException("Migrate to new DB");
/*
        //log.debug("Adding new TemporaryBasal record" + tempBasal.toString());
        boolean newRecordCreated = databaseHelper.createOrUpdate(tempBasal);
        if (newRecordCreated) {
            if (tempBasal.durationInMinutes == 0)
                nsUpload.uploadTempBasalEnd(tempBasal.date, false, tempBasal.pumpId);
            else if (tempBasal.isAbsolute)
                nsUpload.uploadTempBasalStartAbsolute(tempBasal, null);
            else
                nsUpload.uploadTempBasalStartPercent(tempBasal, profileFunction.getProfile(tempBasal.date));
        }
        return newRecordCreated;
 */
    }

    @Deprecated
    public TreatmentUpdateReturn createOrUpdateMedtronic(Treatment treatment, boolean fromNightScout) {
        throw new IllegalStateException("Migrate to new DB");
/*
        UpdateReturn resultRecord = getService().createOrUpdateMedtronic(treatment, fromNightScout);

        return new TreatmentUpdateReturn(resultRecord.getSuccess(), resultRecord.getNewRecord());
 */
    }

    // return true if new record is created
    @Deprecated
    @Override
    public boolean addToHistoryTreatment(DetailedBolusInfo detailedBolusInfo, boolean allowUpdate) {
        throw new IllegalStateException("Migrate to new DB");
/*
        boolean medtronicPump = activePlugin.getActivePump() instanceof MedtronicPumpPlugin;

        getAapsLogger().debug(MedtronicHistoryData.doubleBolusDebug, LTag.DATATREATMENTS, "DoubleBolusDebug: addToHistoryTreatment::isMedtronicPump={} " + medtronicPump);

        Treatment treatment = new Treatment();
        treatment.date = detailedBolusInfo.timestamp;
        treatment.source = (detailedBolusInfo.getPumpType() == PumpType.USER) ? Source.USER : Source.PUMP;
        treatment.pumpId = detailedBolusInfo.getBolusPumpId() != null ? detailedBolusInfo.getBolusPumpId() : 0;
        treatment.insulin = detailedBolusInfo.insulin;
        treatment.isValid = detailedBolusInfo.getBolusType() != DetailedBolusInfo.BolusType.PRIMING;
        treatment.isSMB = detailedBolusInfo.getBolusType() == DetailedBolusInfo.BolusType.SMB;
        if (detailedBolusInfo.carbTime == 0)
            treatment.carbs = detailedBolusInfo.carbs;
        treatment.mealBolus = treatment.carbs > 0;
        // treatment.boluscalc = detailedBolusInfo.boluscalc != null ? detailedBolusInfo.boluscalc.toString() : null;
        treatment.boluscalc = null;
        UpdateReturn creatOrUpdateResult;

        getAapsLogger().debug(medtronicPump && MedtronicHistoryData.doubleBolusDebug, LTag.DATATREATMENTS, "DoubleBolusDebug: addToHistoryTreatment::treatment={} " + treatment);

        if (!medtronicPump)
            creatOrUpdateResult = getService().createOrUpdate(treatment);
        else
            creatOrUpdateResult = getService().createOrUpdateMedtronic(treatment, false);

        boolean newRecordCreated = creatOrUpdateResult.getNewRecord();
        //log.debug("Adding new Treatment record" + treatment.toString());
        if (detailedBolusInfo.carbTime != 0) {

            Treatment carbsTreatment = new Treatment();
            carbsTreatment.source = (detailedBolusInfo.getPumpType() == PumpType.USER) ? Source.USER : Source.PUMP;
            carbsTreatment.pumpId = detailedBolusInfo.getCarbsPumpId() != null ? detailedBolusInfo.getCarbsPumpId() : 0; // but this should never happen
            carbsTreatment.date = detailedBolusInfo.timestamp + detailedBolusInfo.carbTime * 60 * 1000L + 1000L; // add 1 sec to make them different records
            carbsTreatment.carbs = detailedBolusInfo.carbs;

            getAapsLogger().debug(medtronicPump && MedtronicHistoryData.doubleBolusDebug, LTag.DATATREATMENTS, "DoubleBolusDebug: carbTime!=0, creating second treatment. CarbsTreatment={}" + carbsTreatment);

            if (!medtronicPump)
                getService().createOrUpdate(carbsTreatment);
            else
                getService().createOrUpdateMedtronic(carbsTreatment, false);
            //log.debug("Adding new Treatment record" + carbsTreatment);
        }
        if (newRecordCreated && detailedBolusInfo.getBolusType() != DetailedBolusInfo.BolusType.PRIMING)
            nsUpload.uploadTreatmentRecord(detailedBolusInfo);

        if (!allowUpdate && !creatOrUpdateResult.getSuccess()) {
            getAapsLogger().error("Treatment could not be added to DB", new Exception());

            String status = String.format(resourceHelper.gs(R.string.error_adding_treatment_message), treatment.insulin, (int) treatment.carbs, dateUtil.dateAndTimeString(treatment.date));

            ErrorHelperActivity.Companion.runAlarm(context, status, resourceHelper.gs(R.string.error_adding_treatment_title), R.raw.error);

            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_LIST_ID, "TreatmentClash");
            bundle.putString(FirebaseAnalytics.Param.ITEM_LIST_NAME, status);
            fabricPrivacy.logCustom(bundle);
        }

        return newRecordCreated;
 */
    }
}
