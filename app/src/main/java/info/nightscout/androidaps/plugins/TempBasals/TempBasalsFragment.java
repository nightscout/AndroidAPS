package info.nightscout.androidaps.plugins.TempBasals;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.interfaces.TempBasalsInterface;
import info.nightscout.androidaps.plugins.OpenAPSMA.IobTotal;
import info.nightscout.androidaps.interfaces.PluginBase;


public class TempBasalsFragment extends Fragment implements PluginBase, TempBasalsInterface {
    private static Logger log = LoggerFactory.getLogger(TempBasalsFragment.class);

    RecyclerView recyclerView;
    LinearLayoutManager llm;

    TextView iobTotal;

    public long lastCalculationTimestamp = 0;
    public IobTotal lastCalculation;

    private static DecimalFormat formatNumber0decimalplaces = new DecimalFormat("0");
    private static DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");
    private static DecimalFormat formatNumber3decimalplaces = new DecimalFormat("0.000");

    private List<TempBasal> tempBasals;

    boolean fragmentEnabled = true;
    boolean fragmentVisible = true;
    boolean visibleNow = false;

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.tempbasals);
    }

    @Override
    public boolean isEnabled() {
        return fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs() {
        return fragmentVisible;
    }

    @Override
    public boolean canBeHidden() {
        return true;
    }

    @Override
    public void setFragmentEnabled(boolean fragmentEnabled) {
        this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(boolean fragmentVisible) {
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
            queryBuilder.limit(30l);
            PreparedQuery<TempBasal> preparedQuery = queryBuilder.prepare();
            tempBasals = dao.query(preparedQuery);
        } catch (SQLException e) {
            log.debug(e.getMessage(), e);
            tempBasals = new ArrayList<TempBasal>();
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
        Date now = new Date();
        IobTotal total = new IobTotal();
        for (Integer pos = 0; pos < tempBasals.size(); pos++) {
            TempBasal t = tempBasals.get(pos);
            total.plus(t.iobCalc(now));
        }
        if (iobTotal != null)
            iobTotal.setText(formatNumber2decimalplaces.format(total.basaliob));

        lastCalculationTimestamp = new Date().getTime();
        lastCalculation = total;
    }


    public static class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.TempBasalsViewHolder> {

        List<TempBasal> tempBasals;

        RecyclerViewAdapter(List<TempBasal> tempBasals) {
            this.tempBasals = tempBasals;
        }

        @Override
        public TempBasalsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.tempbasals_item, viewGroup, false);
            TempBasalsViewHolder tempBasalsViewHolder = new TempBasalsViewHolder(v);
            return tempBasalsViewHolder;
        }

        @Override
        public void onBindViewHolder(TempBasalsViewHolder holder, int position) {
            // TODO: implement locales
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, new Locale("cs", "CZ"));
            DateFormat enddf = DateFormat.getTimeInstance(DateFormat.SHORT, new Locale("cs", "CZ"));
            if (tempBasals.get(position).timeEnd != null) {
                holder.date.setText(df.format(tempBasals.get(position).timeStart) + " - " + enddf.format(tempBasals.get(position).timeEnd));
            } else {
                holder.date.setText(df.format(tempBasals.get(position).timeStart));
            }
            holder.duration.setText(formatNumber0decimalplaces.format(tempBasals.get(position).duration) + " min");
            if (tempBasals.get(position).isAbsolute) {
                holder.absolute.setText(formatNumber0decimalplaces.format(tempBasals.get(position).absolute) + " U/h");
                holder.percent.setText("");
            } else {
                holder.absolute.setText("");
                holder.percent.setText(formatNumber0decimalplaces.format(tempBasals.get(position).percent) + "%");
            }
            holder.realDuration.setText(formatNumber0decimalplaces.format(tempBasals.get(position).getRealDuration()) + " min");
            IobTotal iob = tempBasals.get(position).iobCalc(new Date());
            holder.iob.setText(formatNumber2decimalplaces.format(iob.basaliob) + " U");
            holder.netInsulin.setText(formatNumber2decimalplaces.format(iob.netInsulin) + " U");
            holder.netRatio.setText(formatNumber2decimalplaces.format(iob.netRatio) + " U/h");
        }

        @Override
        public int getItemCount() {
            return tempBasals.size();
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
            }
        }
    }

    public TempBasalsFragment() {
        super();
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

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(tempBasals);
        recyclerView.setAdapter(adapter);

        iobTotal = (TextView) view.findViewById(R.id.tempbasals_iobtotal);

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

    @Subscribe
    public void onStatusEvent(final EventTempBasalChange ev) {
        initializeData();
    }

    public void updateGUI() {
        Activity activity = getActivity();
        if (visibleNow && activity != null && recyclerView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    recyclerView.swapAdapter(new RecyclerViewAdapter(tempBasals), false);
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
