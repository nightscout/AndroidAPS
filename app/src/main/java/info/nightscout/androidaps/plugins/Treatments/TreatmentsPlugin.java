package info.nightscout.androidaps.plugins.Treatments;

import android.support.annotation.Nullable;

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
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventReloadTempBasalData;
import info.nightscout.androidaps.events.EventReloadTreatmentData;
import info.nightscout.androidaps.events.EventTempTargetChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensData;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.utils.OverlappingIntervals;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class TreatmentsPlugin implements PluginBase, TreatmentsInterface {
    private static Logger log = LoggerFactory.getLogger(TreatmentsPlugin.class);

    public static IobTotal lastTreatmentCalculation;
    public static IobTotal lastTempBasalsCalculation;

    public static List<Treatment> treatments;
    private static OverlappingIntervals<TemporaryBasal> tempBasals = new OverlappingIntervals<>();
    private static OverlappingIntervals<ExtendedBolus> extendedBoluses = new OverlappingIntervals<>();
    private static OverlappingIntervals<TempTarget> tempTargets = new OverlappingIntervals<>();

    private static boolean fragmentEnabled = true;
    private static boolean fragmentVisible = true;

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
        return true;
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

    public TreatmentsPlugin() {
        MainApp.bus().register(this);
        initializeTempBasalData();
        initializeTreatmentData();
        initializeExtendedBolusData();
        initializeTempTargetData();
    }

    public static void initializeTreatmentData() {
        // Treatments
        double dia = Constants.defaultDIA;
        if (MainApp.getConfigBuilder().getActiveProfile() != null && MainApp.getConfigBuilder().getActiveProfile().getProfile() != null)
            dia = MainApp.getConfigBuilder().getActiveProfile().getProfile().getDia();
        long fromMills = (long) (new Date().getTime() - 60 * 60 * 1000L * (24 + dia));

        treatments = MainApp.getDbHelper().getTreatmentDataFromTime(fromMills, false);
    }

    public static void initializeTempBasalData() {
        // Treatments
        double dia = Constants.defaultDIA;
        if (MainApp.getConfigBuilder().getActiveProfile() != null && MainApp.getConfigBuilder().getActiveProfile().getProfile() != null)
            dia = MainApp.getConfigBuilder().getActiveProfile().getProfile().getDia();
        long fromMills = (long) (new Date().getTime() - 60 * 60 * 1000L * (24 + dia));

        tempBasals.reset().add(MainApp.getDbHelper().getTemporaryBasalsDataFromTime(fromMills, false));

    }

    public static void initializeExtendedBolusData() {
        // Treatments
        double dia = Constants.defaultDIA;
        if (MainApp.getConfigBuilder().getActiveProfile() != null && MainApp.getConfigBuilder().getActiveProfile().getProfile() != null)
            dia = MainApp.getConfigBuilder().getActiveProfile().getProfile().getDia();
        long fromMills = (long) (new Date().getTime() - 60 * 60 * 1000L * (24 + dia));

        extendedBoluses.reset().add(MainApp.getDbHelper().getExtendedBolusDataFromTime(fromMills, false));

    }

    public void initializeTempTargetData() {
        long fromMills = new Date().getTime() - 60 * 60 * 1000L * 24;
        tempTargets.reset().add(MainApp.getDbHelper().getTemptargetsDataFromTime(fromMills, false));
    }

    @Override
    public IobTotal getLastCalculationTreatments() {
        return lastTreatmentCalculation;
    }

    @Override
    public IobTotal getCalculationToTimeTreatments(long time) {
        IobTotal total = new IobTotal(time);

        if (MainApp.getConfigBuilder() == null || ConfigBuilderPlugin.getActiveProfile() == null) // app not initialized yet
            return total;
        NSProfile profile = ConfigBuilderPlugin.getActiveProfile().getProfile();
        if (profile == null)
            return total;

        Double dia = profile.getDia();

        for (Integer pos = 0; pos < treatments.size(); pos++) {
            Treatment t = treatments.get(pos);
            if (t.date > time) continue;
            Iob tIOB = t.iobCalc(time, dia);
            total.iob += tIOB.iobContrib;
            total.activity += tIOB.activityContrib;
            Iob bIOB = t.iobCalc(time, dia / SP.getInt("openapsama_bolussnooze_dia_divisor", 2));
            total.bolussnooze += bIOB.iobContrib;
        }

        if (!MainApp.getConfigBuilder().isFakingTempsByExtendedBoluses())
            for (Integer pos = 0; pos < extendedBoluses.size(); pos++) {
                ExtendedBolus e = extendedBoluses.get(pos);
                if (e.date > time) continue;
                IobTotal calc = e.iobCalc(time);
                total.plus(calc);
            }
        return total;
    }

    @Override
    public void updateTotalIOBTreatments() {
        IobTotal total = getCalculationToTimeTreatments(new Date().getTime());

        lastTreatmentCalculation = total;
    }

    @Override
    public MealData getMealData() {
        MealData result = new MealData();

        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (profile == null) return result;

        long now = new Date().getTime();
        long dia_ago = now - (new Double(1.5d * profile.getDia() * 60 * 60 * 1000l)).longValue();

        for (Treatment treatment : treatments) {
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
            if (t.date <= time && t.date > time - 5 * 60 * 1000)
                in5minback.add(t);
        }
        return in5minback;
    }

    @Override
    public boolean isInHistoryRealTempBasalInProgress() {
        return getRealTempBasalFromHistory(new Date().getTime()) != null;
    }

    @Override
    public TemporaryBasal getRealTempBasalFromHistory(long time) {
        return (TemporaryBasal) tempBasals.getValueByInterval(time);
    }

    @Override
    public boolean isTempBasalInProgress() {
        return getTempBasalFromHistory(new Date().getTime()) != null;
    }

    @Override
    public boolean isInHistoryExtendedBoluslInProgress() {
        return getExtendedBolusFromHistory(new Date().getTime()) != null; //TODO:  crosscheck here
    }

    @Subscribe
    public void onStatusEvent(final EventReloadTreatmentData ev) {
        log.debug("EventReloadTreatmentData");
        initializeTreatmentData();
        initializeExtendedBolusData();
        updateTotalIOBTreatments();
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
        for (Integer pos = 0; pos < tempBasals.size(); pos++) {
            TemporaryBasal t = tempBasals.get(pos);
            if (t.date > time) continue;
            IobTotal calc = t.iobCalc(time);
            //log.debug("BasalIOB " + new Date(time) + " >>> " + calc.basaliob);
            total.plus(calc);
        }
        if (MainApp.getConfigBuilder().isFakingTempsByExtendedBoluses()) {
            IobTotal totalExt = new IobTotal(time);
            for (Integer pos = 0; pos < extendedBoluses.size(); pos++) {
                ExtendedBolus e = extendedBoluses.get(pos);
                if (e.date > time) continue;
                IobTotal calc = e.iobCalc(time);
                totalExt.plus(calc);
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
        IobTotal total = getCalculationToTimeTempBasals(new Date().getTime());

        lastTempBasalsCalculation = total;
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
        return (ExtendedBolus) extendedBoluses.getValueByInterval(time);
    }

    @Override
    public void addToHistoryExtendedBolusStart(ExtendedBolus extendedBolus) {
        log.debug("Adding new ExtentedBolus record" + extendedBolus);
        MainApp.getDbHelper().createOrUpdate(extendedBolus);
    }

    @Override
    public void addToHistoryExtendedBolusStop(long time) {
        ExtendedBolus extendedBolus = new ExtendedBolus();
        extendedBolus.date = time;
        extendedBolus.durationInMinutes = 0;
        log.debug("Adding new ExtentedBolus stop record" + extendedBolus);
        MainApp.getDbHelper().createOrUpdate(extendedBolus);
    }

    @Override
    public OverlappingIntervals<ExtendedBolus> getExtendedBolusesFromHistory() {
        return extendedBoluses;
    }

    @Override
    public double getTempBasalAbsoluteRateHistory() {
        PumpInterface pump = MainApp.getConfigBuilder();

        TemporaryBasal tb = getTempBasalFromHistory(new Date().getTime());
        if (tb != null) {
            if (tb.isAbsolute) {
                return tb.absoluteRate;
            } else {
                Double baseRate = pump.getBaseBasalRate();
                Double tempRate = baseRate * (tb.percentRate / 100d);
                return tempRate;
            }
        }
        return 0;
    }

    @Override
    public double getTempBasalRemainingMinutesFromHistory() {
        if (isTempBasalInProgress())
            return getTempBasalFromHistory(new Date().getTime()).getPlannedRemainingMinutes();
        return 0;
    }

    @Override
    public OverlappingIntervals<TemporaryBasal> getTemporaryBasalsFromHistory() {
        return tempBasals;
    }

    @Override
    public void addToHistoryTempBasalStart(TemporaryBasal tempBasal) {
        log.debug("Adding new TemporaryBasal record" + tempBasal);
        MainApp.getDbHelper().createOrUpdate(tempBasal);
    }

    @Override
    public void addToHistoryTempBasalStop(long time) {
        TemporaryBasal temporaryBasal = new TemporaryBasal();
        temporaryBasal.date = time;
        temporaryBasal.durationInMinutes = 0;
        log.debug("Adding new TemporaryBasal stop record" + temporaryBasal);
        MainApp.getDbHelper().createOrUpdate(temporaryBasal);
    }

    @Override
    public void addTreatmentToHistory(DetailedBolusInfo detailedBolusInfo) {
        Treatment treatment = new Treatment(detailedBolusInfo.insulinInterface);
        treatment.date = detailedBolusInfo.date;
        treatment.insulin = detailedBolusInfo.insulin;
        if (detailedBolusInfo.carbTime == 0)
            treatment.carbs = detailedBolusInfo.carbs;
        treatment.source = detailedBolusInfo.source;
        treatment.mealBolus = treatment.carbs > 0;
        MainApp.getDbHelper().createOrUpdate(treatment);
        log.debug("Adding new Treatment record" + treatment);
        if (detailedBolusInfo.carbTime != 0) {
            Treatment carbsTreatment = new Treatment(detailedBolusInfo.insulinInterface);
            carbsTreatment.date = detailedBolusInfo.date + detailedBolusInfo.carbTime * 60 * 1000L;
            carbsTreatment.carbs = detailedBolusInfo.carbs;
            carbsTreatment.source = detailedBolusInfo.source;
            MainApp.getDbHelper().createOrUpdate(carbsTreatment);
            log.debug("Adding new Treatment record" + carbsTreatment);
        }
    }

    @Override
    public long oldestDataAvaialable() {
        long oldestTime = new Date().getTime();
        if (tempBasals.size() > 0)
            oldestTime = Math.min(oldestTime, tempBasals.get(0).date);
        if (extendedBoluses.size() > 0)
            oldestTime = Math.min(oldestTime, extendedBoluses.get(0).date);
        if (treatments.size() > 0)
            oldestTime = Math.min(oldestTime, treatments.get(treatments.size() - 1).date);
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
    public TempTarget getTempTargetFromHistory(long time) {
        return (TempTarget) tempTargets.getValueByInterval(time);
    }

    @Override
    public OverlappingIntervals<TempTarget> getTempTargetsFromHistory() {
        return tempTargets;
    }


}
