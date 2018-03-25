package info.nightscout.androidaps.plugins.Treatments;

import android.support.annotation.Nullable;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Config;
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
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventReloadProfileSwitchData;
import info.nightscout.androidaps.events.EventReloadTempBasalData;
import info.nightscout.androidaps.events.EventReloadTreatmentData;
import info.nightscout.androidaps.events.EventTempTargetChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensData;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class TreatmentsPlugin implements PluginBase, TreatmentsInterface {
    private static Logger log = LoggerFactory.getLogger(TreatmentsPlugin.class);

    private static TreatmentsPlugin treatmentsPlugin;

    public static TreatmentsPlugin getPlugin() {
        if (treatmentsPlugin == null)
            treatmentsPlugin = new TreatmentsPlugin();
        return treatmentsPlugin;
    }

    private IobTotal lastTreatmentCalculation;
    private IobTotal lastTempBasalsCalculation;

    private final static ArrayList<Treatment> treatments = new ArrayList<>();
    private final static Intervals<TemporaryBasal> tempBasals = new NonOverlappingIntervals<>();
    private final static Intervals<ExtendedBolus> extendedBoluses = new NonOverlappingIntervals<>();
    private final static Intervals<TempTarget> tempTargets = new OverlappingIntervals<>();
    private final static ProfileIntervals<ProfileSwitch> profiles = new ProfileIntervals<>();

    private boolean fragmentEnabled = true;
    private boolean fragmentVisible = true;

    @Override
    public String getFragmentClass() {
        return TreatmentsFragment.class.getName();
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.treatments);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.treatments_shortname);
        if (!name.trim().isEmpty()) {
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return type == TREATMENT && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == TREATMENT && fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return !Config.NSCLIENT && !Config.G5UPLOADER;
    }

    @Override
    public void setPluginEnabled(int type, boolean fragmentEnabled) {
        if (type == TREATMENT) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == TREATMENT) this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getPreferencesId() {
        return -1;
    }

    @Override
    public int getType() {
        return PluginBase.TREATMENT;
    }

    private TreatmentsPlugin() {
        MainApp.bus().register(this);
        initializeTempBasalData();
        initializeTreatmentData();
        initializeExtendedBolusData();
        initializeTempTargetData();
        initializeProfileSwitchData();
    }

    private static void initializeTreatmentData() {
        double dia = Constants.defaultDIA;
        if (MainApp.getConfigBuilder() != null && MainApp.getConfigBuilder().getProfile() != null)
            dia = MainApp.getConfigBuilder().getProfile().getDia();
        long fromMills = (long) (System.currentTimeMillis() - 60 * 60 * 1000L * (24 + dia));
        synchronized (treatments) {
            treatments.clear();
            treatments.addAll(MainApp.getDbHelper().getTreatmentDataFromTime(fromMills, false));
        }
    }

    private static void initializeTempBasalData() {
         double dia = Constants.defaultDIA;
        if (MainApp.getConfigBuilder() != null && MainApp.getConfigBuilder().getProfile() != null)
            dia = MainApp.getConfigBuilder().getProfile().getDia();
        long fromMills = (long) (System.currentTimeMillis() - 60 * 60 * 1000L * (24 + dia));

        synchronized (tempBasals) {
            tempBasals.reset().add(MainApp.getDbHelper().getTemporaryBasalsDataFromTime(fromMills, false));
        }

    }

    private static void initializeExtendedBolusData() {
        double dia = Constants.defaultDIA;
        if (MainApp.getConfigBuilder() != null && MainApp.getConfigBuilder().getProfile() != null)
            dia = MainApp.getConfigBuilder().getProfile().getDia();
        long fromMills = (long) (System.currentTimeMillis() - 60 * 60 * 1000L * (24 + dia));

        synchronized (extendedBoluses) {
            extendedBoluses.reset().add(MainApp.getDbHelper().getExtendedBolusDataFromTime(fromMills, false));
        }

    }

    private void initializeTempTargetData() {
        synchronized (tempTargets) {
            long fromMills = System.currentTimeMillis() - 60 * 60 * 1000L * 24;
            tempTargets.reset().add(MainApp.getDbHelper().getTemptargetsDataFromTime(fromMills, false));
        }
    }

    private void initializeProfileSwitchData() {
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

        Profile profile = MainApp.getConfigBuilder().getProfile();
        if (profile == null)
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
                if (t.date > total.lastBolusTime)
                    total.lastBolusTime = t.date;
                if (!t.isSMB) {
                    // instead of dividing the DIA that only worked on the bilinear curves,
                    // multiply the time the treatment is seen active.
                    long timeSinceTreatment = time - t.date;
                    long snoozeTime = t.date + (long) (timeSinceTreatment * SP.getDouble("openapsama_bolussnooze_dia_divisor", 2.0));
                    Iob bIOB = t.iobCalc(snoozeTime, dia);
                    total.bolussnooze += bIOB.iobContrib;
                }
            }
        }

        if (!ConfigBuilderPlugin.getActivePump().isFakingTempsByExtendedBoluses())
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

        Profile profile = MainApp.getConfigBuilder().getProfile();
        if (profile == null) return result;

        long now = System.currentTimeMillis();
        long dia_ago = now - (Double.valueOf(1.5d * profile.getDia() * 60 * 60 * 1000l)).longValue();

        synchronized (treatments) {
            for (Treatment treatment : treatments) {
                if (!treatment.isValid)
                    continue;
                long t = treatment.date;
                if (t > dia_ago && t <= now) {
                    if (treatment.carbs >= 1) {
                        result.carbs += treatment.carbs;
                        result.lastCarbTime = t;
                    }
                    if (treatment.insulin > 0 && treatment.mealBolus) {
                        result.boluses += treatment.insulin;
                    }
                }
            }
        }

        AutosensData autosensData = IobCobCalculatorPlugin.getPlugin().getLastAutosensDataSynchronized("getMealData()");
        if (autosensData != null) {
            result.mealCOB = autosensData.cob;
            result.slopeFromMinDeviation = autosensData.slopeFromMinDeviation;
            result.slopeFromMaxDeviation = autosensData.slopeFromMaxDeviation;
        }
        result.lastBolusTime = getLastBolusTime();
        return result;
    }

    @Override
    public List<Treatment> getTreatmentsFromHistory() {
        synchronized (treatments) {
            return (List<Treatment>) treatments.clone();
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
                if (t.date > last && t.insulin > 0 && t.isValid && t.date <= now)
                    last = t.date;
            }
        }
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
        log.debug("EventReloadTreatmentData");
        initializeTreatmentData();
        initializeExtendedBolusData();
        updateTotalIOBTreatments();
        MainApp.bus().post(ev.next);
    }

    @Subscribe
    public void onStatusEvent(final EventReloadTempBasalData ev) {
        log.debug("EventReloadTempBasalData");
        initializeTempBasalData();
        updateTotalIOBTempBasals();
    }

    @Override
    public IobTotal getLastCalculationTempBasals() {
        return lastTempBasalsCalculation;
    }

    @Override
    public IobTotal getCalculationToTimeTempBasals(long time) {
        IobTotal total = new IobTotal(time);
        synchronized (tempBasals) {
            for (Integer pos = 0; pos < tempBasals.size(); pos++) {
                TemporaryBasal t = tempBasals.get(pos);
                if (t.date > time) continue;
                IobTotal calc = t.iobCalc(time);
                //log.debug("BasalIOB " + new Date(time) + " >>> " + calc.basaliob);
                total.plus(calc);
                if (!t.isEndingEvent()) {
                    total.lastTempDate = t.date;
                    total.lastTempDuration = t.durationInMinutes;
                    Profile profile = MainApp.getConfigBuilder().getProfile(t.date);
                    total.lastTempRate = t.tempBasalConvertedToAbsolute(t.date, profile);
                }

            }
        }
        if (ConfigBuilderPlugin.getActivePump().isFakingTempsByExtendedBoluses()) {
            IobTotal totalExt = new IobTotal(time);
            synchronized (extendedBoluses) {
                for (Integer pos = 0; pos < extendedBoluses.size(); pos++) {
                    ExtendedBolus e = extendedBoluses.get(pos);
                    if (e.date > time) continue;
                    IobTotal calc = e.iobCalc(time);
                    totalExt.plus(calc);
                    TemporaryBasal t = new TemporaryBasal(e);
                    if (!t.isEndingEvent() && t.date > total.lastTempDate) {
                        total.lastTempDate = t.date;
                        total.lastTempDuration = t.durationInMinutes;
                        Profile profile = MainApp.getConfigBuilder().getProfile(t.date);
                        total.lastTempRate = t.tempBasalConvertedToAbsolute(t.date, profile);
                    }
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
        lastTempBasalsCalculation = getCalculationToTimeTempBasals(System.currentTimeMillis());
    }

    @Nullable
    @Override
    public TemporaryBasal getTempBasalFromHistory(long time) {
        TemporaryBasal tb = getRealTempBasalFromHistory(time);
        if (tb != null)
            return tb;
        ExtendedBolus eb = getExtendedBolusFromHistory(time);
        if (eb != null && ConfigBuilderPlugin.getActivePump().isFakingTempsByExtendedBoluses())
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
        return MainApp.getDbHelper().createOrUpdate(extendedBolus);
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
        return MainApp.getDbHelper().createOrUpdate(tempBasal);
    }

    @Override
    public boolean addToHistoryTreatment(DetailedBolusInfo detailedBolusInfo) {
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
        boolean newRecordCreated = MainApp.getDbHelper().createOrUpdate(treatment);
        //log.debug("Adding new Treatment record" + treatment.toString());
        if (detailedBolusInfo.carbTime != 0) {
            Treatment carbsTreatment = new Treatment();
            carbsTreatment.source = detailedBolusInfo.source;
            carbsTreatment.pumpId = detailedBolusInfo.pumpId; // but this should never happen
            carbsTreatment.date = detailedBolusInfo.date + detailedBolusInfo.carbTime * 60 * 1000L + 1000L; // add 1 sec to make them different records
            carbsTreatment.carbs = detailedBolusInfo.carbs;
            carbsTreatment.source = detailedBolusInfo.source;
            MainApp.getDbHelper().createOrUpdate(carbsTreatment);
            //log.debug("Adding new Treatment record" + carbsTreatment);
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
            return new NonOverlappingIntervals<>(tempTargets);
        }
    }

    // Profile Switch
    @Subscribe
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
        MainApp.getDbHelper().createOrUpdate(profileSwitch);
    }


}
