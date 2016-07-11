package info.nightscout.androidaps.plugins.TempBasals;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.*;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.text.DateFormat;
import java.text.DecimalFormat;
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
import info.nightscout.androidaps.interfaces.TempBasalsInterface;
import info.nightscout.androidaps.plugins.OpenAPSMA.IobTotal;
import info.nightscout.androidaps.interfaces.PluginBase;


public class TempBasalsFragment extends Fragment implements PluginBase, TempBasalsInterface {
    private static Logger log = LoggerFactory.getLogger(TempBasalsFragment.class);

    RecyclerView recyclerView;
    LinearLayoutManager llm;

    TextView tempBasalTotalView;

    public long lastCalculationTimestamp = 0;
    public IobTotal lastCalculation;

    private static DecimalFormat formatNumber0decimalplaces = new DecimalFormat("0");
    private static DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");
    private static DecimalFormat formatNumber3decimalplaces = new DecimalFormat("0.000");

    private List<TempBasal> tempBasals;
    private List<TempBasal> extendedBoluses;

    private boolean useExtendedBoluses = false;

    boolean fragmentEnabled = true;
    boolean fragmentVisible = true;
    boolean visibleNow = false;

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.tempbasals);
    }

    @Override
    public boolean isEnabled(int type) {
        return fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        this.fragmentVisible = fragmentVisible;
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
            queryBuilderExt.limit(5L);
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
    public void updateTotalIOB() {
        checkForExpired(tempBasals);
        checkForExpired(extendedBoluses);
        Date now = new Date();
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

    public static class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.TempBasalsViewHolder> {

        List<TempBasal> tempBasalList;

        RecyclerViewAdapter(List<TempBasal> tempBasalList) {
            this.tempBasalList = tempBasalList;
        }

        @Override
        public TempBasalsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.tempbasals_item, viewGroup, false);
            TempBasalsViewHolder tempBasalsViewHolder = new TempBasalsViewHolder(v);
            return tempBasalsViewHolder;
        }

        @Override
        public void onBindViewHolder(TempBasalsViewHolder holder, int position) {
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
            DateFormat enddf = DateFormat.getTimeInstance(DateFormat.SHORT);
            TempBasal tempBasal = tempBasalList.get(position);
            if (tempBasal.timeEnd != null) {
                holder.date.setText(df.format(tempBasal.timeStart) + " - " + enddf.format(tempBasalList.get(position).timeEnd));
            } else {
                holder.date.setText(df.format(tempBasal.timeStart));
            }
            holder.duration.setText(formatNumber0decimalplaces.format(tempBasal.duration) + " min");
            if (tempBasal.isAbsolute) {
                holder.absolute.setText(formatNumber0decimalplaces.format(tempBasal.absolute) + " U/h");
                holder.percent.setText("");
            } else {
                holder.absolute.setText("");
                holder.percent.setText(formatNumber0decimalplaces.format(tempBasal.percent) + "%");
            }
            holder.realDuration.setText(formatNumber0decimalplaces.format(tempBasal.getRealDuration()) + " min");
            IobTotal iob = tempBasal.iobCalc(new Date());
            holder.iob.setText(formatNumber2decimalplaces.format(iob.basaliob) + " U");
            holder.netInsulin.setText(formatNumber2decimalplaces.format(iob.netInsulin) + " U");
            holder.netRatio.setText(formatNumber2decimalplaces.format(iob.netRatio) + " U/h");
            holder.extendedFlag.setVisibility(tempBasal.isExtended ? View.VISIBLE : View.GONE);
            if (tempBasal.isInProgress())
                holder.dateLinearLayout.setBackgroundColor(MainApp.instance().getResources().getColor(R.color.colorInProgress));
            else if (tempBasal.timeEnd == null)
                holder.dateLinearLayout.setBackgroundColor(MainApp.instance().getResources().getColor(R.color.colorNotEnded));
            else if (tempBasal.iobCalc(new Date()).basaliob != 0)
                holder.dateLinearLayout.setBackgroundColor(MainApp.instance().getResources().getColor(R.color.colorAffectingIOB));
            else
                holder.dateLinearLayout.setBackgroundColor(MainApp.instance().getResources().getColor(R.color.cardColorBackground));
        }

        @Override
        public int getItemCount() {
            return tempBasalList.size();
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public static class TempBasalsViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            TextView date;
            TextView duration;
            TextView absolute;
            TextView percent;
            TextView realDuration;
            TextView netRatio;
            TextView netInsulin;
            TextView iob;
            TextView extendedFlag;
            LinearLayout dateLinearLayout;

            TempBasalsViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.tempbasals_cardview);
                date = (TextView) itemView.findViewById(R.id.tempbasals_date);
                duration = (TextView) itemView.findViewById(R.id.tempbasals_duration);
                absolute = (TextView) itemView.findViewById(R.id.tempbasals_absolute);
                percent = (TextView) itemView.findViewById(R.id.tempbasals_percent);
                realDuration = (TextView) itemView.findViewById(R.id.tempbasals_realduration);
                netRatio = (TextView) itemView.findViewById(R.id.tempbasals_netratio);
                netInsulin = (TextView) itemView.findViewById(R.id.tempbasals_netinsulin);
                iob = (TextView) itemView.findViewById(R.id.tempbasals_iob);
                extendedFlag = (TextView) itemView.findViewById(R.id.tempbasals_extendedflag);
                dateLinearLayout = (LinearLayout) itemView.findViewById(R.id.tempbasals_datelinearlayout);
            }
        }
    }

    public TempBasalsFragment() {
        super();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        useExtendedBoluses = sharedPreferences.getBoolean("danar_useextended", false);
        registerBus();
        initializeData();
        updateGUI();
    }

    public static TempBasalsFragment newInstance() {
        TempBasalsFragment fragment = new TempBasalsFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.tempbasals_fragment, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.tempbasals_recyclerview);
        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(getMergedList());
        recyclerView.setAdapter(adapter);

        tempBasalTotalView = (TextView) view.findViewById(R.id.tempbasals_totaltempiob);
        updateGUI();
        return view;
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
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

    public void updateGUI() {
        Activity activity = getActivity();
        if (visibleNow && activity != null && recyclerView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    recyclerView.swapAdapter(new RecyclerViewAdapter(getMergedList()), false);
                    if (lastCalculation != null) {
                        String totalText = formatNumber2decimalplaces.format(lastCalculation.basaliob) + " U";
                        tempBasalTotalView.setText(totalText);
                    }
                }
            });
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (isVisibleToUser) {
            visibleNow = true;
            updateTotalIOBIfNeeded();
            updateGUI();
        } else
            visibleNow = false;
    }

}
