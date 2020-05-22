package info.nightscout.androidaps.plugins.general.autotune;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Intervals;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.NonOverlappingIntervals;
import info.nightscout.androidaps.data.OverlappingIntervals;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileIntervals;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.StaticInjector;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.interfaces.ProfileStore;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.disposables.CompositeDisposable;

//Todo remove all unused functions
//and verify iob calculation for autotune
@Singleton
public class AutotuneIob {
    private final HasAndroidInjector injector;
    private static Logger log = LoggerFactory.getLogger(AutotunePlugin.class);
    @Inject ProfileFunction profileFunction;
    @Inject SP sp;
    @Inject ResourceHelper resourceHelper;
    @Inject ActivePluginProvider activePlugin;
    @Inject AutotunePlugin autotunePlugin;
    @Inject DateUtil dateUtil;
    @Inject TreatmentsPlugin treatmentsPlugin;
    @Inject NSUpload nsUpload;

    private CompositeDisposable disposable = new CompositeDisposable();

    private ArrayList<NsTreatment> nsTreatments = new ArrayList<NsTreatment>();
    private ArrayList<Treatment> treatments = new ArrayList<>();
    public ArrayList<Treatment> meals = new ArrayList<>();
    public List<BgReading> glucose = new ArrayList<>();
    private Intervals<TemporaryBasal> tempBasals = new NonOverlappingIntervals<>();
    private Intervals<ExtendedBolus> extendedBoluses = new NonOverlappingIntervals<>();
    private Intervals<TempTarget> tempTargets = new OverlappingIntervals<>();
    private ProfileIntervals<ProfileSwitch> profiles = new ProfileIntervals<>();
    public long from;
    public long to;

    public AutotuneIob(
            //HasAndroidInjector injector,
            long from,
            long to
    ) {
        injector = StaticInjector.Companion.getInstance();
        //this.injector=injector;
        injector.androidInjector().inject(this);
        initializeData(from,to);
    }

    private long range() {
        double dia = Constants.defaultDIA;
        if (profileFunction.getProfile() != null)
            dia = profileFunction.getProfile().getDia();
        return (long) (60 * 60 * 1000L * dia);
    }

    private void initializeData(long from, long to) {
        this.from = from;
        this.to = to;
        nsTreatments.clear();
        initializeBgreadings(from, to);
        initializeTreatmentData(from-range(), to);
        initializeTempBasalData(from-range(), to);
        initializeExtendedBolusData(from-range(), to);
        initializeTempTargetData(from, to);
        initializeProfileSwitchData(from-range(), to);
        //NsTreatment is used to export all "ns-treatments" for cross execution of oref0-autotune on a virtual machine
        //it contains traitments, tempbasals and extendedbolus data (profileswitch data also included in ns-treatment files are not used by oref0-autotune)
        Collections.sort(nsTreatments, (o1, o2) -> (int) (o2.date  - o1.date) );
    }

    private void initializeBgreadings(long from, long to) {
        glucose = MainApp.getDbHelper().getBgreadingsDataFromTime(from, to, false);
    }

    private void initializeTreatmentData(long from, long to) {
        synchronized (treatments) {
            long oldestBgDate = glucose.size() > 0 ? glucose.get(glucose.size()-1).date : from ;
            treatments.clear();
            treatments.addAll(treatmentsPlugin.getService().getTreatmentDataFromTime(from, to, false));
            meals.clear();
            for(int i = 0; i < treatments.size();i++) {
                Treatment tp =  treatments.get(i);
                nsTreatments.add(new NsTreatment(tp));
                //only carbs after first BGReadings are taken into account in calculation of Autotune
                if (tp.carbs > 0 && tp.date >= oldestBgDate)
                    meals.add(treatments.get(i));
                else if (tp.carbs > 0 && tp.date < from)
                    treatments.get(i).carbs = 0;
            }
        }
    }

    private void initializeTempBasalData(long from, long to) {
        synchronized (tempBasals) {
            List<TemporaryBasal> temp = MainApp.getDbHelper().getTemporaryBasalsDataFromTime(from, to, false);
            for (TemporaryBasal tb: temp ) {
                nsTreatments.add(new NsTreatment(tb));
            }
            tempBasals.reset().add(temp);
        }
    }

    private void initializeExtendedBolusData(long from, long to) {
        synchronized (extendedBoluses) {
            List<ExtendedBolus> temp = MainApp.getDbHelper().getExtendedBolusDataFromTime(from, to, false);
            for (ExtendedBolus eb: temp ) {
                nsTreatments.add(new NsTreatment(eb));
            }
            extendedBoluses.reset().add(temp);
        }
    }

    private void initializeTempTargetData(long from, long to) {
        synchronized (tempTargets) {
            tempTargets.reset().add(MainApp.getDbHelper().getTemptargetsDataFromTime(from, to, false));
        }
    }

    private void initializeProfileSwitchData(long from, long to) {
        synchronized (profiles) {
            profiles.reset().add(MainApp.getDbHelper().getProfileSwitchData(from, false));
        }
    }

    public List<Treatment> getTreatmentsFromHistory() {
        synchronized (treatments) {
            return new ArrayList<>(treatments);
        }
    }

    IobTotal calculateFromTreatmentsAndTemps(long time, Profile profile, double currenBasalRate) {
        IobTotal bolusIob = getCalculationToTimeTreatments(time).round();
        IobTotal basalIob = getAbsoluteIOBTempBasals( time,  profile,  currenBasalRate).round();

        IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();

        return iobTotal;
    }


    public IobTotal calculateAbsInsulinFromTreatmentsAndTemps(long time) {
        IobTotal bolusIob = getCalculationToTimeTreatments(time);
        IobTotal basalIob = getAbsoluteIOBTempBasals(time);
        IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();

        return iobTotal;
    }

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
                    IobTotal calc = e.iobCalc(time);
                    total.plus(calc);
                }
            }
        return total;
    }

    public TemporaryBasal getRealTempBasalFromHistory(long time) {
        synchronized (tempBasals) {
            return tempBasals.getValueByInterval(time);
        }
    }
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
                    TemporaryBasal dummyTemp = new TemporaryBasal(injector);
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
                        ExtendedBolus dummyExt = new ExtendedBolus(injector);
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

    // for IOB calculations, use the average of the last 4 hours' basals to help convergence;
    // this helps since the basal this hour could be different from previous, especially if with autotune they start to diverge.
    // use the pumpbasalprofile to properly calculate IOB during periods where no temp basal is set
    public IobTotal getAbsoluteIOBTempBasals(long time, Profile tunedProfile, double running) {
        IobTotal total = new IobTotal(time);

        for (long i = time - range(); i < time; i += T.mins(5).msecs()) {
            TemporaryBasal runningTBR = getTempBasalFromHistory(i);
            if (runningTBR != null) {
                //Here I think I should use real tunedProfile because in autotune it get absolute rates
                Profile profile = profileFunction.getProfile(i);
                running = runningTBR.tempBasalConvertedToAbsolute(i, profile);
            }
            Treatment treatment = new Treatment(injector);
            treatment.date = i;
            treatment.insulin = running * 5.0 / 60.0; // 5 min chunk
            Iob iob = treatment.iobCalc(i, tunedProfile.getDia());
            total.iob += iob.iobContrib;
            total.activity += iob.activityContrib;
        }
        return total;
    }

    // for IOB calculations, use the average of the last 4 hours' basals to help convergence;
    // this helps since the basal this hour could be different from previous, especially if with autotune they start to diverge.
    // use the pumpbasalprofile to properly calculate IOB during periods where no temp basal is set
    public IobTotal getAbsoluteIOBTempBasals(long time) {
        IobTotal total = new IobTotal(time);

        for (long i = time - range(); i < time; i += T.mins(5).msecs()) {
            Profile profile = profileFunction.getProfile(i);
            double basal = profile.getBasal(i);
            TemporaryBasal runningTBR = getTempBasalFromHistory(i);
            double running = basal;
            if (runningTBR != null) {
                running = runningTBR.tempBasalConvertedToAbsolute(i, profile);
            }
            Treatment treatment = new Treatment(injector);
            treatment.date = i;
            treatment.insulin = running * 5.0 / 60.0; // 5 min chunk
            Iob iob = treatment.iobCalc(i, profile.getDia());
            total.iob += iob.iobContrib;
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
                    TemporaryBasal dummyTemp = new TemporaryBasal(injector);
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
                        ExtendedBolus dummyExt = new ExtendedBolus(injector);
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

    @Nullable
    public TemporaryBasal getTempBasalFromHistory(long time) {
        TemporaryBasal tb = getRealTempBasalFromHistory(time);
        if (tb != null)
            return tb;
        ExtendedBolus eb = getExtendedBolusFromHistory(time);
        if (eb != null && activePlugin.getActivePump().isFakingTempsByExtendedBoluses())
            return new TemporaryBasal(eb);
        return null;
    }

    public ExtendedBolus getExtendedBolusFromHistory(long time) {
        synchronized (extendedBoluses) {
            return extendedBoluses.getValueByInterval(time);
        }
    }

    @NonNull
    public Intervals<ExtendedBolus> getExtendedBolusesFromHistory() {
        synchronized (extendedBoluses) {
            return new NonOverlappingIntervals<>(extendedBoluses);
        }
    }

    @NonNull
    public NonOverlappingIntervals<TemporaryBasal> getTemporaryBasalsFromHistory() {
        synchronized (tempBasals) {
            return new NonOverlappingIntervals<>(tempBasals);
        }
    }

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
    public TempTarget getTempTargetFromHistory(long time) {
        synchronized (tempTargets) {
            return tempTargets.getValueByInterval(time);
        }
    }

    public Intervals<TempTarget> getTempTargetsFromHistory() {
        synchronized (tempTargets) {
            return new OverlappingIntervals<>(tempTargets);
        }
    }

    @Nullable
    public ProfileSwitch getProfileSwitchFromHistory(long time) {
        synchronized (profiles) {
            return (ProfileSwitch) profiles.getValueToTime(time);
        }
    }

    public ProfileIntervals<ProfileSwitch> getProfileSwitchesFromHistory() {
        synchronized (profiles) {
            return new ProfileIntervals<>(profiles);
        }
    }

    public void addToHistoryProfileSwitch(ProfileSwitch profileSwitch) {
        //log.debug("Adding new TemporaryBasal record" + profileSwitch.log());
        //rxBus.send(new EventDismissNotification(Notification.PROFILE_SWITCH_MISSING));
        MainApp.getDbHelper().createOrUpdate(profileSwitch);
        nsUpload.uploadProfileSwitch(profileSwitch);
    }

    public void doProfileSwitch(@NotNull final ProfileStore profileStore, @NotNull final String profileName, final int duration, final int percentage, final int timeShift, final long date) {
        ProfileSwitch profileSwitch = profileFunction.prepareProfileSwitch(profileStore, profileName, duration, percentage, timeShift, date);
        addToHistoryProfileSwitch(profileSwitch);
        if (percentage == 90 && duration == 10)
            sp.putBoolean(R.string.key_objectiveuseprofileswitch, true);
    }

    public void doProfileSwitch(final int duration, final int percentage, final int timeShift) {
        ProfileSwitch profileSwitch = getProfileSwitchFromHistory(System.currentTimeMillis());
        if (profileSwitch != null) {
            profileSwitch = new ProfileSwitch(injector);
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
            //log.error(LTag.PROFILE, "No profile switch exists");
        }
    }

    /*********************************************************************************************************************************************************************************************/

    public JSONArray glucosetoJSON()  {
        JSONArray glucoseJson = new JSONArray();
        Date now = new Date(System.currentTimeMillis());
        int utcOffset = (int) ((DateUtil.fromISODateString(DateUtil.toISOString(now,null,null)).getTime()  - DateUtil.fromISODateString(DateUtil.toISOString(now)).getTime()) / (60 * 1000));
        BgSourceInterface activeBgSource = activePlugin.getActiveBgSource();
        //String device = activeBgSource.getClass().getTypeName();
        try {
            for (BgReading bgreading:glucose ) {
                JSONObject bgjson = new JSONObject();
                bgjson.put("_id",bgreading._id);
                bgjson.put("device","AndroidAPS");
                bgjson.put("date",bgreading.date);
                bgjson.put("dateString", DateUtil.toISOString(bgreading.date));
                bgjson.put("sgv",bgreading.value);
                bgjson.put("direction",bgreading.direction);
                bgjson.put("type","sgv");
                bgjson.put("systime", DateUtil.toISOString(bgreading.date));
                bgjson.put("utcOffset", utcOffset);
                glucoseJson.put(bgjson);
            }
        } catch (JSONException e) {}
        return glucoseJson;
    }

    public JSONArray nsHistorytoJSON() {
        JSONArray json = new JSONArray();
        for (NsTreatment t: nsTreatments ) {
            if (t.isValid)
                json.put(t.toJson());
        }
        return json;
    }

    /*********************************************************************************************************************************************************************************************/

    //I add this internal class to be able to export easily ns-treatment files with same containt and format than NS query used by oref0-autotune
    private class NsTreatment {
        //Common properties
        public String _id;
        public long date;
        public boolean isValid;
        public String eventType;
        public String created_at;

        // treatment properties
        public Treatment treatment;
        public Double insulin=0d;
        public Double carbs=0d;
        public boolean isSMB;
        public boolean mealBolus;

        //TemporayBasal or ExtendedBolus properties
        public TemporaryBasal temporaryBasal;
        public Double absoluteRate;
        public boolean isEndingEvent;
        public int duration;            // msec converted in minutes
        public boolean isFakeExtended;
        public String enteredBy;
        public int percentRate;
        public boolean isAbsolute;
        public ExtendedBolus extendedBolus;
        private String origin;

        //CarePortalEvents
        public CareportalEvent careportalEvent;
        public String json;


        public NsTreatment(Treatment t) {
            treatment = t;
            _id=t._id;
            date=t.date;
            carbs=t.carbs;
            insulin=t.insulin;
            isSMB=t.isSMB;
            isValid=t.isValid;
            mealBolus=t.mealBolus;

            if(insulin > 0 && carbs > 0)
                eventType = CareportalEvent.BOLUSWIZARD;
            else if (carbs > 0)
                eventType = CareportalEvent.CARBCORRECTION;
            else
                eventType = CareportalEvent.CORRECTIONBOLUS;
            created_at = DateUtil.toISOString(t.date);
        }

        public NsTreatment (CareportalEvent t) {
            careportalEvent = t;
            _id=t._id;
            date=t.date;
            created_at = DateUtil.toISOString(t.date);
            eventType = t.eventType;
            duration= Math.round(t.getDuration() / 60f / 1000);
            isValid=t.isValid;
            json=t.json;
        }


        public NsTreatment (TemporaryBasal t) {
            temporaryBasal=t;
            _NsTreatment(t);
        }
        public NsTreatment(ExtendedBolus t) {
            extendedBolus = t;
            _NsTreatment(new TemporaryBasal(t));
        }

        private void _NsTreatment (TemporaryBasal t) {
            _id=t._id;
            date=t.date;
            absoluteRate= Round.roundTo(t.absoluteRate,0.001);
            isValid=t.isValid;
            isEndingEvent=t.isEndingEvent();
            eventType = CareportalEvent.TEMPBASAL;
            enteredBy = "openaps://" + resourceHelper.gs(R.string.app_name);
            duration = t.getRealDuration();
            percentRate = t.percentRate;
            isFakeExtended = t.isFakeExtended;
            created_at = DateUtil.toISOString(t.date);
            isAbsolute = t.isAbsolute;
        }

        //todo: See NSUpload to be as close as possible for each event type
        public JSONObject toJson() {
            JSONObject cPjson = new JSONObject();
            try {
                cPjson.put("_id", _id);
                cPjson.put("eventType",eventType);
                cPjson.put("date",date);
                cPjson.put("created_at",created_at);
                cPjson.put("insulin",insulin > 0 ? insulin : JSONObject.NULL);
                cPjson.put("carbs",carbs > 0 ? carbs : JSONObject.NULL );
                if(eventType==CareportalEvent.TEMPBASAL) {
                    if (!isEndingEvent) {
                        cPjson.put("duration", duration);
                        cPjson.put("absolute", absoluteRate);
                        cPjson.put("rate", absoluteRate);
                        // cPjson.put("percent", percentRate - 100);
                        cPjson.put("isFakeExtended", isFakeExtended);
                    }
                    cPjson.put("enteredBy", enteredBy);
                    //cPjson.put("isEnding", isEndingEvent);

                } else {
                    cPjson.put("isSMB", isSMB);
                    cPjson.put("isMealBolus", mealBolus);
                }

            } catch (JSONException e) {}
            return cPjson;
        }
    }

}
