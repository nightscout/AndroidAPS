package info.nightscout.androidaps.plugins.TreatmentsFromHistory;

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
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
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
public class TreatmentsFromHistoryPlugin implements PluginBase, TreatmentsInterface {
    private static Logger log = LoggerFactory.getLogger(TreatmentsFromHistoryPlugin.class);

    public static IobTotal lastTreatmentCalculation;
    public static IobTotal lastTempBasalsCalculation;

    public static List<Treatment> treatments;
    private static OverlappingIntervals<TemporaryBasal> tempBasals = new OverlappingIntervals<>();
    private static OverlappingIntervals<ExtendedBolus> extendedBoluses = new OverlappingIntervals<>();

    private static boolean fragmentEnabled = true;
    private static boolean fragmentVisible = true;

    private static TreatmentsFromHistoryPlugin treatmentsPlugin = new TreatmentsFromHistoryPlugin();

    public static TreatmentsFromHistoryPlugin getPlugin() {
        return treatmentsPlugin;
    }

    @Override
    public String getFragmentClass() {
        return TreatmentsFromHistoryFragment.class.getName();
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.treatments) + "FromHistory"; // TODO: remove later
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
        boolean canBeEnabled = MainApp.getConfigBuilder().treatmentPlugin() == null ? true : MainApp.getConfigBuilder().treatmentPlugin().equals(getClass().getName());
        return type == TREATMENT && fragmentEnabled && canBeEnabled;
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
        boolean canBeEnabled = MainApp.getConfigBuilder().treatmentPlugin() == null ? true : MainApp.getConfigBuilder().treatmentPlugin().equals(getClass().getName());
        return canBeEnabled;
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

    public TreatmentsFromHistoryPlugin() {
        MainApp.bus().register(this);
        initializeTempBasalData();
        initializeTreatmentData();
        initializeExtendedBolusData();
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
    public List<Treatment> getTreatments() {
        return treatments;
    }

    @Override
    public List<Treatment> getTreatments5MinBack(long time) {
        List<Treatment> in5minback = new ArrayList<>();
        for (Integer pos = 0; pos < treatments.size(); pos++) {
            Treatment t = treatments.get(pos);
            if (t.date <= time && t.date > time - 5 * 60 * 1000)
                in5minback.add(t);
        }
        return in5minback;
    }

    @Override
    public boolean isRealTempBasalInProgress() {
        return false;
    }

    @Override
    public TemporaryBasal getRealTempBasal(long time) {
        return null;
    }

    @Override
    public boolean isTempBasalInProgress() {
        return getTempBasal(new Date().getTime()) != null;
    }

    @Override
    public boolean isExtendedBoluslInProgress() {
        return getExtendedBolus(new Date().getTime()) != null; //TODO:  crosscheck here
    }

    @Subscribe
    public void onStatusEvent(final EventTreatmentChange ev) {
        initializeTreatmentData();
        updateTotalIOBTreatments();
    }

    @Subscribe
    public void onStatusEvent(final EventTempBasalChange ev) {
        initializeTempBasalData();
        updateTotalIOBTempBasals();
    }

    @Subscribe
    public void onStatusEvent(final EventExtendedBolusChange ev) {
        initializeExtendedBolusData();
        updateTotalIOBTreatments();
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
        return total;
    }

    @Override
    public void updateTotalIOBTempBasals() {
        IobTotal total = getCalculationToTimeTempBasals(new Date().getTime());

        lastTempBasalsCalculation = total;
    }

    @Nullable
    @Override
    public TemporaryBasal getTempBasal(long time) {
        return (TemporaryBasal) tempBasals.getValueByInterval(time);
    }

    @Override
    public ExtendedBolus getExtendedBolus(long time) {
        return (ExtendedBolus) extendedBoluses.getValueByInterval(time);
    }

    @Override
    public void extendedBolusStart(ExtendedBolus extendedBolus) {
        MainApp.getDbHelper().create(extendedBolus);
    }

    @Override
    public void extendedBolusStop(long time) {
        ExtendedBolus extendedBolus = new ExtendedBolus();
        extendedBolus.date = time;
        extendedBolus.durationInMinutes = 0;
        MainApp.getDbHelper().create(extendedBolus);
    }

    @Override
    public OverlappingIntervals<ExtendedBolus> getExtendedBoluses() {
        return extendedBoluses;
    }

    @Override
    public double getTempBasalAbsoluteRate() {
        PumpInterface pump = MainApp.getConfigBuilder();

        TemporaryBasal tb = getTempBasal(new Date().getTime());
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
    public double getTempBasalRemainingMinutes() {
        if (isTempBasalInProgress())
            return getTempBasal(new Date().getTime()).getPlannedRemainingMinutes();
        return 0;
    }

    @Override
    public OverlappingIntervals<TemporaryBasal> getTemporaryBasals() {
        return tempBasals;
    }

    @Override
    public void tempBasalStart(TemporaryBasal tempBasal) {
        MainApp.getDbHelper().create(tempBasal);
    }

    @Override
    public void tempBasalStop(long time) {
        TemporaryBasal temporaryBasal = new TemporaryBasal();
        temporaryBasal.date = time;
        temporaryBasal.durationInMinutes = 0;
        MainApp.getDbHelper().create(temporaryBasal);
    }

    @Override
    public long oldestDataAvaialable() {
        long oldestTime = new Date().getTime();
        if (tempBasals.size() > 0)
            oldestTime = Math.min(oldestTime, tempBasals.get(0).date);
        if (extendedBoluses.size() > 0)
            oldestTime = Math.min(oldestTime, extendedBoluses.get(0).date);
        if (treatments.size() > 0)
            oldestTime = Math.min(oldestTime, extendedBoluses.get(treatments.size() - 1).date);
        oldestTime -= 15 * 60 * 1000L; // allow 15 min before
        return oldestTime;
    }

}
