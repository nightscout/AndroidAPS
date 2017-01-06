package info.nightscout.androidaps.plugins.TempBasals;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
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
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.TempBasalsInterface;
import info.nightscout.androidaps.data.IobTotal;

/**
 * Created by mike on 05.08.2016.
 */
public class TempBasalsPlugin implements PluginBase, TempBasalsInterface {
    private static Logger log = LoggerFactory.getLogger(TempBasalsPlugin.class);

    public static long lastCalculationTimestamp = 0;
    public static IobTotal lastCalculation;

    private static List<TempBasal> tempBasals;
    private static List<TempBasal> extendedBoluses;

    private static boolean useExtendedBoluses = false;

    private static boolean fragmentEnabled = true;
    private static boolean fragmentVisible = true;

    public TempBasalsPlugin() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        useExtendedBoluses = sharedPreferences.getBoolean("danar_useextended", false);
        initializeData();
        MainApp.bus().register(this);
    }

    @Override
    public String getFragmentClass() {
        return TempBasalsFragment.class.getName();
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.tempbasals);
    }

    @Override
    public boolean isEnabled(int type) {
        return type == TEMPBASAL && fragmentEnabled && MainApp.getConfigBuilder().getPumpDescription().isTempBasalCapable;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == TEMPBASAL && fragmentVisible && MainApp.getConfigBuilder().getPumpDescription().isTempBasalCapable;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == TEMPBASAL) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == TEMPBASAL) this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getType() {
        return PluginBase.TEMPBASAL;
    }

    private void initializeData() {
        try {
            Dao<TempBasal, Long> dao = MainApp.getDbHelper().getDaoTempBasals();
/*
            // **************** TESTING CREATE FAKE RECORD *****************
            TempBasal fake = new TempBasal();
            fake.timeStart = new Date(new Date().getTime() - 45 * 40 * 1000);
            fake.timeEnd = new Date(new Date().getTime() - new Double(Math.random() * 45d * 40 * 1000).longValue());
            fake.duration = 30;
            fake.percent = 150;
            fake.isAbsolute = false;
            fake.isExtended = false;
            dao.createOrUpdate(fake);
            // **************** TESTING CREATE FAKE RECORD *****************
*/
            QueryBuilder<TempBasal, Long> queryBuilder = dao.queryBuilder();
            queryBuilder.orderBy("timeIndex", false);
            Where where = queryBuilder.where();
            where.eq("isExtended", false);
            queryBuilder.limit(30L);
            PreparedQuery<TempBasal> preparedQuery = queryBuilder.prepare();
            tempBasals = dao.query(preparedQuery);

            QueryBuilder<TempBasal, Long> queryBuilderExt = dao.queryBuilder();
            queryBuilderExt.orderBy("timeIndex", false);
            Where whereExt = queryBuilderExt.where();
            whereExt.eq("isExtended", true);
            queryBuilderExt.limit(30L);
            PreparedQuery<TempBasal> preparedQueryExt = queryBuilderExt.prepare();
            extendedBoluses = dao.query(preparedQueryExt);

            // Update ended
            checkForExpiredExtended();
            checkForExpiredTemps();
        } catch (SQLException e) {
            log.debug(e.getMessage(), e);
            tempBasals = new ArrayList<TempBasal>();
            extendedBoluses = new ArrayList<TempBasal>();
        }
    }

    public void checkForExpiredTemps() {
        checkForExpired(tempBasals);
    }

    public void checkForExpiredExtended() {
        checkForExpired(extendedBoluses);
    }

    private void checkForExpired(List<TempBasal> list) {
        long now = new Date().getTime();
        for (int position = list.size() - 1; position >= 0; position--) {
            TempBasal t = list.get(position);
            boolean update = false;
            if (t.timeEnd == null && t.getPlannedTimeEnd().getTime() < now) {
                t.timeEnd = new Date(t.getPlannedTimeEnd().getTime());
                if (Config.logTempBasalsCut)
                    log.debug("Add timeEnd to old record");
                update = true;
            }
            if (position > 0) {
                Date startofnewer = list.get(position - 1).timeStart;
                if (t.timeEnd == null) {
                    t.timeEnd = new Date(Math.min(startofnewer.getTime(), t.getPlannedTimeEnd().getTime()));
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

    /*
     * Recalculate IOB if value is older than 1 minute
     */
    public void updateTotalIOBIfNeeded() {
        if (lastCalculationTimestamp > new Date().getTime() - 60 * 1000)
            return;
        updateTotalIOB();
    }

    @Override
    public IobTotal getLastCalculation() {
        return lastCalculation;
    }

    @Override
    public IobTotal getCalculationToTime(long time) {
        checkForExpired(tempBasals);
        checkForExpired(extendedBoluses);
        Date now = new Date(time);
        IobTotal total = new IobTotal();
        for (Integer pos = 0; pos < tempBasals.size(); pos++) {
            TempBasal t = tempBasals.get(pos);
            IobTotal calc = t.iobCalc(now);
            total.plus(calc);
        }
        if (useExtendedBoluses) {
            for (Integer pos = 0; pos < extendedBoluses.size(); pos++) {
                TempBasal t = extendedBoluses.get(pos);
                IobTotal calc = t.iobCalc(now);
                total.plus(calc);
            }
        }
        return total;
    }

    @Override
    public void updateTotalIOB() {
        IobTotal total = getCalculationToTime(new Date().getTime());

        lastCalculationTimestamp = new Date().getTime();
        lastCalculation = total;
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

    List<TempBasal> getMergedList() {
        if (useExtendedBoluses) {
            List<TempBasal> merged = new ArrayList<TempBasal>();
            merged.addAll(tempBasals);
            merged.addAll(extendedBoluses);

            class CustomComparator implements Comparator<TempBasal> {
                public int compare(TempBasal object1, TempBasal object2) {
                    return (int) (object2.timeIndex - object1.timeIndex);
                }
            }
            Collections.sort(merged, new CustomComparator());
            return merged;
        } else {
            return tempBasals;
        }
    }

    @Subscribe
    public void onStatusEvent(final EventTempBasalChange ev) {
        initializeData();
    }

    public void onStatusEvent(final EventPreferenceChange s) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        useExtendedBoluses = sharedPreferences.getBoolean("danar_useextended", false);
        initializeData();
    }


}
