package info.nightscout.androidaps.plugins.TreatmentsFromHistory;

import android.support.annotation.Nullable;

import com.j256.ormlite.dao.Dao;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.MealData;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensData;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsFragment;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class TreatmentsFromHistoryPlugin implements PluginBase, TreatmentsInterface {
    private static Logger log = LoggerFactory.getLogger(TreatmentsFromHistoryPlugin.class);

    public static IobTotal lastTreatmentCalculation;
    public static IobTotal lastTempBasalsCalculation;

    public static List<Treatment> treatments;
    private static List<TempBasal> tempBasals;
    private static List<TempBasal> extendedBoluses;

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
        initializeData();
    }

    public static void initializeData() {
        // Treatments
        double dia = Constants.defaultDIA;
        if (MainApp.getConfigBuilder().getActiveProfile() != null && MainApp.getConfigBuilder().getActiveProfile().getProfile() != null)
            dia = MainApp.getConfigBuilder().getActiveProfile().getProfile().getDia();
        long fromMills = (long) (new Date().getTime() - 60 * 60 * 1000L * (24 + dia));
        treatments = MainApp.getDbHelper().getTreatmentDataFromTime(fromMills, false);

        // Temp basals
        tempBasals = MainApp.getDbHelper().getTempbasalsDataFromTime(fromMills, false, false);
        extendedBoluses = MainApp.getDbHelper().getTempbasalsDataFromTime(fromMills, false, true);

        // Update ended
        checkForExpiredExtended();
        checkForExpiredTemps();
    }

    public static void checkForExpiredTemps() {
        checkForExpired(tempBasals);
    }

    public static void checkForExpiredExtended() {
        checkForExpired(extendedBoluses);
    }

    private static void checkForExpired(List<TempBasal> list) {
        long now = new Date().getTime();
        for (int position = list.size() - 1; position >= 0; position--) {
            TempBasal t = list.get(position);
            boolean update = false;
            if (t.timeEnd == null && t.getPlannedTimeEnd() < now) {
                t.timeEnd = new Date(t.getPlannedTimeEnd());
                if (Config.logTempBasalsCut)
                    log.debug("Add timeEnd to old record");
                update = true;
            }
            if (position > 0) {
                Date startofnewer = list.get(position - 1).timeStart;
                if (t.timeEnd == null) {
                    t.timeEnd = new Date(Math.min(startofnewer.getTime(), t.getPlannedTimeEnd()));
                    if (Config.logTempBasalsCut)
                        log.debug("Add timeEnd to old record");
                    update = true;
                } else if (t.timeEnd.getTime() > startofnewer.getTime()) {
                    t.timeEnd = startofnewer;
                    update = true;
                }
            }
            if (update) {
                try {
                    Dao<TempBasal, Long> dao = MainApp.getDbHelper().getDaoTempBasals();
                    dao.update(t);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                if (Config.logTempBasalsCut) {
                    log.debug("Fixing unfinished temp end: " + t.log());
                    if (position > 0)
                        log.debug("Previous: " + list.get(position - 1).log());
                }
            }
        }
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
        InsulinInterface insulinInterface = MainApp.getConfigBuilder().getActiveInsulin();
        if (profile == null)
            return total;

        Double dia = profile.getDia();

        for (Integer pos = 0; pos < treatments.size(); pos++) {
            Treatment t = treatments.get(pos);
            if (t.created_at.getTime() > time) continue;
            Iob tIOB = insulinInterface.iobCalc(t, time, dia);
            total.iob += tIOB.iobContrib;
            total.activity += tIOB.activityContrib;
            Iob bIOB = insulinInterface.iobCalc(t, time, dia / SP.getInt("openapsama_bolussnooze_dia_divisor", 2));
            total.bolussnooze += bIOB.iobContrib;
        }

        checkForExpired(extendedBoluses);
        for (Integer pos = 0; pos < extendedBoluses.size(); pos++) {
            TempBasal t = extendedBoluses.get(pos);
            if (t.timeStart.getTime() > time) continue;
            IobTotal calc = t.iobCalc(time);
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
            long t = treatment.created_at.getTime();
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
            if (t.created_at.getTime() <= time && t.created_at.getTime() > time - 5 * 60 * 1000)
                in5minback.add(t);
        }
        return in5minback;
    }

    @Subscribe
    public void onStatusEvent(final EventTreatmentChange ev) {
        initializeData();
        updateTotalIOBTreatments();
    }

    @Override
    public IobTotal getLastCalculationTempBasals() {
        return lastTempBasalsCalculation;
    }

    @Override
    public IobTotal getCalculationToTimeTempBasals(long time) {
        checkForExpired(tempBasals);
        IobTotal total = new IobTotal(time);
        for (Integer pos = 0; pos < tempBasals.size(); pos++) {
            TempBasal t = tempBasals.get(pos);
            if (t.timeStart.getTime() > time) continue;
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
    public TempBasal getTempBasal(Date time) {
        checkForExpired(tempBasals);
        for (TempBasal t : tempBasals) {
            if (t.isInProgress(time)) return t;
        }
        return null;
    }

    @Override
    public TempBasal getExtendedBolus(Date time) {
        checkForExpired(extendedBoluses);
        for (TempBasal t : extendedBoluses) {
            if (t.isInProgress(time)) return t;
        }
        return null;
    }

    @Override
    public long oldestDataAvaialable() {
        long oldestTemp = new Date().getTime();
        if (tempBasals.size() > 0)
            oldestTemp = Math.min(oldestTemp, tempBasals.get(tempBasals.size() - 1).timeStart.getTime());
        if (extendedBoluses.size() > 0)
            oldestTemp = Math.min(oldestTemp, extendedBoluses.get(extendedBoluses.size() - 1).timeStart.getTime());
        oldestTemp -= 15 * 60 * 1000L; // allow 15 min before
        return oldestTemp;
    }

    @Subscribe
    public void onStatusEvent(final EventTempBasalChange ev) {
        initializeData();
        updateTotalIOBTempBasals();
    }

}
