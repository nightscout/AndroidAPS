package info.nightscout.androidaps.plugins.Treatments;

import android.support.annotation.Nullable;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.data.Intervals;
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
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
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

    public static List<Treatment> treatments;
    private static Intervals<TemporaryBasal> tempBasals = new NonOverlappingIntervals<>();
    private static Intervals<ExtendedBolus> extendedBoluses = new NonOverlappingIntervals<>();
    private static Intervals<TempTarget> tempTargets = new OverlappingIntervals<>();
    private static ProfileIntervals<ProfileSwitch> profiles = new ProfileIntervals<>();

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
        return !Config.NSCLIENT;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == TREATMENT) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == TREATMENT) this.fragmentVisible = fragmentVisible;
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
        // Treatments
        double dia = MainApp.getConfigBuilder() == null ? Constants.defaultDIA : MainApp.getConfigBuilder().getProfile().getDia();
        long fromMills = (long) (System.currentTimeMillis() - 60 * 60 * 1000L * (24 + dia));

        treatments = MainApp.getDbHelper().getTreatmentDataFromTime(fromMills, false);
    }

    private static void initializeTempBasalData() {
        // Treatments
        double dia = MainApp.getConfigBuilder() == null ? Constants.defaultDIA : MainApp.getConfigBuilder().getProfile().getDia();
        long fromMills = (long) (System.currentTimeMillis() - 60 * 60 * 1000L * (24 + dia));

        tempBasals.reset().add(MainApp.getDbHelper().getTemporaryBasalsDataFromTime(fromMills, false));

    }

    private static void initializeExtendedBolusData() {
        // Treatments
        double dia = MainApp.getConfigBuilder() == null ? Constants.defaultDIA : MainApp.getConfigBuilder().getProfile().getDia();
        long fromMills = (long) (System.currentTimeMillis() - 60 * 60 * 1000L * (24 + dia));

        extendedBoluses.reset().add(MainApp.getDbHelper().getExtendedBolusDataFromTime(fromMills, false));

    }

    private void initializeTempTargetData() {
        long fromMills = System.currentTimeMillis() - 60 * 60 * 1000L * 24;
        tempTargets.reset().add(MainApp.getDbHelper().getTemptargetsDataFromTime(fromMills, false));
    }

    private void initializeProfileSwitchData() {
        profiles.reset().add(MainApp.getDbHelper().getProfileSwitchData(false));
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

        for (Integer pos = 0; pos < treatments.size(); pos++) {
            Treatment t = treatments.get(pos);
            if (!t.isValid) continue;
            if (t.date > time) continue;
            Iob tIOB = t.iobCalc(time, dia);
            total.iob += tIOB.iobContrib;
            total.activity += tIOB.activityContrib;
            if (!t.isSMB) {
                // instead of dividing the DIA that only worked on the bilinear curves,
                // multiply the time the treatment is seen active.
                long timeSinceTreatment =  time - t.date;
                long snoozeTime = t.date + (long)(timeSinceTreatment * SP.getDouble("openapsama_bolussnooze_dia_divisor", 2.0));
                Iob bIOB = t.iobCalc(snoozeTime, dia);
                total.bolussnooze += bIOB.iobContrib;
            } else {
                total.basaliob += t.insulin;
                total.microBolusIOB += tIOB.iobContrib;
            }
        }

        if (!MainApp.getConfigBuilder().isFakingTempsByExtendedBoluses())
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

        for (Treatment treatment : treatments) {
            if (!treatment.isValid)
                continue;
            long t = treatment.date;
            if (t > dia_ago && t <= now) {
                if (treatment.carbs >= 1) {
                    result.carbs += treatment.carbs;
                }
                if (treatment.insulin > 0 && treatment.mealBolus) {
                    result.boluses += treatment.insulin;
                }
            }
        }

        AutosensData autosensData = IobCobCalculatorPlugin.getLastAutosensData();
        if (autosensData != null) {
            result.mealCOB = autosensData.cob;
        }
        return result;
    }

    @Override
    public List<Treatment> getTreatmentsFromHistory() {
        return treatments;
    }

    @Override
    public List<Treatment> getTreatments5MinBackFromHistory(long time) {
        List<Treatment> in5minback = new ArrayList<>();
        for (Integer pos = 0; pos < treatments.size(); pos++) {
            Treatment t = treatments.get(pos);
            if (!t.isValid)
                continue;
            if (t.date <= time && t.date > time - 5 * 60 * 1000 && t.carbs > 0)
                in5minback.add(t);
        }
        return in5minback;
    }

    @Override
    public boolean isInHistoryRealTempBasalInProgress() {
        return getRealTempBasalFromHistory(System.currentTimeMillis()) != null;
    }

    @Override
    public TemporaryBasal getRealTempBasalFromHistory(long time) {
        return tempBasals.getValueByInterval(time);
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
            }
        }
        if (MainApp.getConfigBuilder().isFakingTempsByExtendedBoluses()) {
            IobTotal totalExt = new IobTotal(time);
            synchronized (extendedBoluses) {
                for (Integer pos = 0; pos < extendedBoluses.size(); pos++) {
                    ExtendedBolus e = extendedBoluses.get(pos);
                    if (e.date > time) continue;
                    IobTotal calc = e.iobCalc(time);
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
        lastTempBasalsCalculation = getCalculationToTimeTempBasals(System.currentTimeMillis());
    }

    @Nullable
    @Override
    public TemporaryBasal getTempBasalFromHistory(long time) {
        TemporaryBasal tb = getRealTempBasalFromHistory(time);
        if (tb != null)
            return tb;
        ExtendedBolus eb = getExtendedBolusFromHistory(time);
        if (eb != null && MainApp.getConfigBuilder().isFakingTempsByExtendedBoluses())
            return new TemporaryBasal(eb);
        return null;
    }

    @Override
    public ExtendedBolus getExtendedBolusFromHistory(long time) {
        return extendedBoluses.getValueByInterval(time);
    }

    @Override
    public boolean addToHistoryExtendedBolus(ExtendedBolus extendedBolus) {
        //log.debug("Adding new ExtentedBolus record" + extendedBolus.log());
        return MainApp.getDbHelper().createOrUpdate(extendedBolus);
    }

    @Override
    public Intervals<ExtendedBolus> getExtendedBolusesFromHistory() {
        return extendedBoluses;
    }

    @Override
    public double getTempBasalAbsoluteRateHistory() {
        PumpInterface pump = MainApp.getConfigBuilder();

        TemporaryBasal tb = getTempBasalFromHistory(System.currentTimeMillis());
        if (tb != null) {
            if (tb.isFakeExtended){
                double baseRate = pump.getBaseBasalRate();
                double tempRate = baseRate + tb.netExtendedRate;
                return tempRate;
            } else if (tb.isAbsolute) {
                return tb.absoluteRate;
            } else {
                double baseRate = pump.getBaseBasalRate();
                double tempRate = baseRate * (tb.percentRate / 100d);
                return tempRate;
            }
        }
        return 0;
    }

    @Override
    public double getTempBasalRemainingMinutesFromHistory() {
        if (isTempBasalInProgress())
            return getTempBasalFromHistory(System.currentTimeMillis()).getPlannedRemainingMinutes();
        return 0;
    }

    @Override
    public Intervals<TemporaryBasal> getTemporaryBasalsFromHistory() {
        return tempBasals;
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
        return tempTargets.getValueByInterval(System.currentTimeMillis());
    }

    @Nullable
    @Override
    public TempTarget getTempTargetFromHistory(long time) {
        return tempTargets.getValueByInterval(time);
    }

    @Override
    public Intervals<TempTarget> getTempTargetsFromHistory() {
        return tempTargets;
    }

    // Profile Switch
    @Subscribe
    public void onStatusEvent(final EventReloadProfileSwitchData ev) {
        initializeProfileSwitchData();
    }

    @Override
    public ProfileSwitch getProfileSwitchFromHistory(long time) {
        return (ProfileSwitch) profiles.getValueToTime(time);
    }

    @Override
    public ProfileIntervals<ProfileSwitch> getProfileSwitchesFromHistory() {
        return profiles;
    }

    @Override
    public void addToHistoryProfileSwitch(ProfileSwitch profileSwitch) {
        //log.debug("Adding new TemporaryBasal record" + profileSwitch.log());
        MainApp.getDbHelper().createOrUpdate(profileSwitch);
    }


}
