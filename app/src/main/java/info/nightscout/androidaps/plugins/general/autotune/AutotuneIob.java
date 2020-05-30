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
import info.nightscout.androidaps.historyBrowser.IobCobCalculatorPluginHistory;
import info.nightscout.androidaps.historyBrowser.TreatmentsPluginHistory;
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
    @Inject IobCobCalculatorPluginHistory iobCobCalculatorPluginHistory;
    @Inject TreatmentsPluginHistory treatmentsPluginHistory;
    @Inject TreatmentsPlugin treatmentsPlugin;
    @Inject NSUpload nsUpload;

    private CompositeDisposable disposable = new CompositeDisposable();

    private ArrayList<NsTreatment> nsTreatments = new ArrayList<NsTreatment>();
    public List<Treatment> treatments = new ArrayList<>();
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
            long from
    ) {
        injector = StaticInjector.Companion.getInstance();
        //this.injector=injector;
        injector.androidInjector().inject(this);
        loadData(from);
        //initializeData(from,to);
    }

    //Todo, limit data to only the end of selected day...
    private void loadData(long from) {
        treatmentsPluginHistory.initializeData(from - range());
    }

    private long range() {
        double dia = Constants.defaultDIA;
        if (profileFunction.getProfile() != null)
            dia = profileFunction.getProfile().getDia();
        return (long) (60 * 60 * 1000L * dia);
    }

    public void initializeData(long from, long to) {

        this.from = from;
        this.to = to;
        nsTreatments.clear();
        initializeBgreadings(from, to);
        initializeTreatmentData(from-range(), to);
        initializeTempBasalData(from-range(), to);
        initializeExtendedBolusData(from-range(), to);
        //initializeTempTargetData(from, to);
        //initializeProfileSwitchData(from-range(), to);
        //NsTreatment is used to export all "ns-treatments" for cross execution of oref0-autotune on a virtual machine
        //it contains traitments, tempbasals and extendedbolus data (profileswitch data also included in ns-treatment files are not used by oref0-autotune)
        Collections.sort(nsTreatments, (o1, o2) -> (int) (o2.date  - o1.date));
    }

    private void initializeBgreadings(long from, long to) {
        glucose = MainApp.getDbHelper().getBgreadingsDataFromTime(from, to, false);
    }

    //nsTreatment is used only for export data, meals is used in AutotunePrep
    private void initializeTreatmentData(long from, long to) {
        long oldestBgDate = glucose.size() > 0 ? glucose.get(glucose.size()-1).date : from ;
        List<Treatment> temp = treatmentsPluginHistory.getTreatmentsFromHistory();
        meals.clear();
        for(int i = 0; i < temp.size() ;i++) {
            Treatment tp =  temp.get(i);
            treatments.add(tp);
            if(tp.date > from-range() && tp.date < to && tp.isValid) {
                nsTreatments.add(new NsTreatment(tp));
                //only carbs after first BGReadings are taken into account in calculation of Autotune
                if (tp.carbs > 0 && tp.date >= oldestBgDate )
                    meals.add(temp.get(i));
            }
        }
    }

    //nsTreatment is used only for export data
    private void initializeTempBasalData(long from, long to) {
        List<TemporaryBasal> temp = MainApp.getDbHelper().getTemporaryBasalsDataFromTime(from, to, false);
        //first keep only valid data
        for(int i=0; i<temp.size(); i++) {
            if (!temp.get(i).isValid)
                temp.remove(i--);
        }
        //Then add neutral TBR if start of next TBR is after the end of previous one
        long previousend = temp.get(0).date + temp.get(0).getRealDuration() * 60 * 1000;
        for(int i=0; i<temp.size(); i++) {
            TemporaryBasal tb = temp.get(i);
            if(tb.date < previousend + 60 * 1000) {                         // 1 min is minimum duration for TBR
                nsTreatments.add(new NsTreatment(tb));
                previousend = tb.date + tb.getRealDuration() * 60 * 1000;
            } else {
                int minutesToFill = (int) (tb.date - previousend)/(60*1000);
                while (minutesToFill > 0) {
                    Profile profile = profileFunction.getProfile(previousend);
                    if ( Profile.secondsFromMidnight(tb.date) / 3600 == Profile.secondsFromMidnight(previousend) / 3600) {  // next tbr is in the same hour
                        TemporaryBasal neutralTbr = new TemporaryBasal(injector);
                        neutralTbr.date = previousend;
                        neutralTbr.isValid=true;
                        neutralTbr.absoluteRate = profile.getBasal(previousend);
                        neutralTbr.durationInMinutes=minutesToFill;
                        neutralTbr.isAbsolute = true;
                        minutesToFill = 0;
                        previousend += minutesToFill * 60 * 1000;
                        nsTreatments.add(new NsTreatment(neutralTbr));
                    } else {  //fill data until the end of current hour
                        int minutesFilled = 60 - (Profile.secondsFromMidnight(previousend) % 60);
                        TemporaryBasal neutralTbr = new TemporaryBasal(injector);
                        neutralTbr.date = previousend;
                        neutralTbr.isValid=true;
                        neutralTbr.absoluteRate = profile.getBasal(previousend);
                        neutralTbr.durationInMinutes=minutesFilled;
                        neutralTbr.isAbsolute = true;
                        minutesToFill -= minutesFilled;
                        previousend = DateUtil.toTimeMinutesFromMidnight(previousend, (Profile.secondsFromMidnight(previousend) / 3600 + 1)* 60); //previousend is updated at the beginning of next hour
                        nsTreatments.add(new NsTreatment(neutralTbr));
                    }
                }
                nsTreatments.add(new NsTreatment(tb));
            }

        }
    }

    //nsTreatment is used only for export data
    private void initializeExtendedBolusData(long from, long to) {
        List<ExtendedBolus> temp = MainApp.getDbHelper().getExtendedBolusDataFromTime(from, to, false);
        for (int i = 0; i < temp.size() ;i++) {
            ExtendedBolus eb = temp.get(i);
            nsTreatments.add(new NsTreatment(eb));
        }
    }


    public IobTotal calculateFromTreatmentsAndTempsSynchronized(long time) {
        IobTotal iobTotal = iobCobCalculatorPluginHistory.calculateFromTreatmentsAndTempsSynchronized(time, profileFunction.getProfile(time)).round();
        return iobTotal;
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
