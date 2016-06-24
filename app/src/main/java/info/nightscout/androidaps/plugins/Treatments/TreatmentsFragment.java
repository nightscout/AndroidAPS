package info.nightscout.androidaps.plugins.Treatments;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
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

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.OpenAPSMA.IobTotal;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.client.data.NSProfile;

public class TreatmentsFragment extends Fragment implements View.OnClickListener, PluginBase, TreatmentsInterface {
    private static Logger log = LoggerFactory.getLogger(TreatmentsFragment.class);

    RecyclerView recyclerView;
    LinearLayoutManager llm;

    TextView iobTotal;
    TextView activityTotal;
    Button refreshFromNS;

    private long lastCalculationTimestamp = 0;
    private IobTotal lastCalculation;

    private static DecimalFormat formatNumber0decimalplaces = new DecimalFormat("0");
    private static DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");
    private static DecimalFormat formatNumber3decimalplaces = new DecimalFormat("0.000");

    private List<Treatment> treatments;

    boolean fragmentEnabled = true;
    boolean fragmentVisible = true;
    boolean visibleNow = false;

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.treatments);
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
        return PluginBase.TREATMENT;
    }

    private void initializeData() {
        try {
            Dao<Treatment, Long> dao = MainApp.getDbHelper().getDaoTreatments();
            QueryBuilder<Treatment, Long> queryBuilder = dao.queryBuilder();
            queryBuilder.orderBy("timeIndex", false);
            queryBuilder.limit(30l);
            PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
            treatments = dao.query(preparedQuery);
        } catch (SQLException e) {
            log.debug(e.getMessage(), e);
            treatments = new ArrayList<Treatment>();
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
        IobTotal total = new IobTotal();

        if (MainApp.getConfigBuilder() == null || MainApp.getConfigBuilder().getActiveProfile() == null) // app not initialized yet
            return;
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (profile == null) {
            lastCalculation = total;
            return;
        }

        Double dia = profile.getDia();

        Date now = new Date();
        for (Integer pos = 0; pos < treatments.size(); pos++) {
            Treatment t = treatments.get(pos);
            Iob tIOB = t.iobCalc(now, dia);
            total.iob += tIOB.iobContrib;
            total.activity += tIOB.activityContrib;
            Iob bIOB = t.iobCalc(now, dia / 2);
            total.bolussnooze += bIOB.iobContrib;
        }

        lastCalculationTimestamp = new Date().getTime();
        lastCalculation = total;
    }

    public class MealData {
        public double boluses = 0d;
        public double carbs = 0d;
    }

    @Override
    public MealData getMealData() {
        MealData result = new MealData();
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (profile == null)
            return result;

        for (Treatment treatment : treatments) {
            long now = new Date().getTime();
            long dia_ago = now - (new Double(profile.getDia() * 60 * 60 * 1000l)).longValue();
            long t = treatment.created_at.getTime();
            if (t > dia_ago && t <= now) {
                if (treatment.carbs >= 1) {
                    result.carbs += treatment.carbs;
                }
                if (treatment.insulin >= 0.1) {
                    result.boluses += treatment.insulin;
                }
            }
        }
        return result;
    }

    public static class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.TreatmentsViewHolder> {

        List<Treatment> treatments;

        RecyclerViewAdapter(List<Treatment> treatments) {
            this.treatments = treatments;
        }

        @Override
        public TreatmentsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.treatments_item, viewGroup, false);
            TreatmentsViewHolder treatmentsViewHolder = new TreatmentsViewHolder(v);
            return treatmentsViewHolder;
        }

        @Override
        public void onBindViewHolder(TreatmentsViewHolder holder, int position) {
            if (MainApp.getConfigBuilder() == null || MainApp.getConfigBuilder().getActiveProfile() == null) // app not initialized yet
                return;
            NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
            if (profile == null)
                return;
            // TODO: implement locales
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, new Locale("cs", "CZ"));
            holder.date.setText(df.format(treatments.get(position).created_at));
            holder.insulin.setText(formatNumber2decimalplaces.format(treatments.get(position).insulin) + " U");
            holder.carbs.setText(formatNumber0decimalplaces.format(treatments.get(position).carbs) + " g");
            Iob iob = treatments.get(position).iobCalc(new Date(), profile.getDia());
            holder.iob.setText(formatNumber2decimalplaces.format(iob.iobContrib) + " U");
            holder.activity.setText(formatNumber3decimalplaces.format(iob.activityContrib) + " U");
        }

        @Override
        public int getItemCount() {
            return treatments.size();
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public static class TreatmentsViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            TextView date;
            TextView insulin;
            TextView carbs;
            TextView iob;
            TextView activity;

            TreatmentsViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.treatments_cardview);
                date = (TextView) itemView.findViewById(R.id.treatments_date);
                insulin = (TextView) itemView.findViewById(R.id.treatments_insulin);
                carbs = (TextView) itemView.findViewById(R.id.treatments_carbs);
                iob = (TextView) itemView.findViewById(R.id.treatments_iob);
                activity = (TextView) itemView.findViewById(R.id.treatments_activity);
            }
        }
    }

    public TreatmentsFragment() {
        super();
        registerBus();
        initializeData();
        updateGUI();
    }

    public static TreatmentsFragment newInstance() {
        TreatmentsFragment fragment = new TreatmentsFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.treatments_fragment, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.treatments_recyclerview);
        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(treatments);
        recyclerView.setAdapter(adapter);

        iobTotal = (TextView) view.findViewById(R.id.treatments_iobtotal);
        activityTotal = (TextView) view.findViewById(R.id.treatments_iobactivitytotal);

        refreshFromNS = (Button) view.findViewById(R.id.treatments_reshreshfromnightscout);
        refreshFromNS.setOnClickListener(this);

        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.treatments_reshreshfromnightscout:
                AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
                builder.setTitle(this.getContext().getString(R.string.dialog));
                builder.setMessage(this.getContext().getString(R.string.refreshfromnightscout));
                builder.setPositiveButton(this.getContext().getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        MainApp.getDbHelper().resetTreatments();
                        initializeData();
                        updateGUI();
                        Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
                        MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
                    }
                });
                builder.setNegativeButton(this.getContext().getString(R.string.cancel), null);
                builder.show();

                break;
        }
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
    public void onStatusEvent(final EventTreatmentChange ev) {
        initializeData();
        updateGUI();
    }

    public void updateGUI() {
        Activity activity = getActivity();
        if (visibleNow && activity != null && recyclerView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    recyclerView.swapAdapter(new RecyclerViewAdapter(treatments), false);
                    if (lastCalculation != null)
                        iobTotal.setText(formatNumber2decimalplaces.format(lastCalculation.iob));
                    if (lastCalculation != null)
                        activityTotal.setText(formatNumber3decimalplaces.format(lastCalculation.activity));
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
