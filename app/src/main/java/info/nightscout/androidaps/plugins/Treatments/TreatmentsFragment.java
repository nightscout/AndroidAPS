package info.nightscout.androidaps.plugins.Treatments;

import android.app.Activity;
import android.content.Context;
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
import info.nightscout.androidaps.plugins.Treatments.Dialogs.NewTreatmentDialogFragment;
import info.nightscout.androidaps.Services.Intents;

public class TreatmentsFragment extends Fragment implements View.OnClickListener, NewTreatmentDialogFragment.Communicator {
    private static Logger log = LoggerFactory.getLogger(TreatmentsFragment.class);

    RecyclerView recyclerView;
    LinearLayoutManager llm;

    TextView iobTotal;
    TextView activityTotal;
    Button refreshFromNS;

    public long lastCalculationTimestamp = 0;
    public Iob lastCalculation;

    private static DecimalFormat formatNumber0decimalplaces = new DecimalFormat("0");
    private static DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");
    private static DecimalFormat formatNumber3decimalplaces = new DecimalFormat("0.000");

    private OnFragmentInteractionListener mListener;

    private List<Treatment> treatments;

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
        if (recyclerView != null) {
            recyclerView.swapAdapter(new RecyclerViewAdapter(treatments), false);
        }
        updateTotalIOB();
    }

    /*
     * Recalculate IOB if value is older than 1 minute
     */
    public void updateTotalIOBIfNeeded() {
        if (lastCalculationTimestamp > new Date().getTime() - 60 * 1000)
            return;
        updateTotalIOB();
    }

    private void updateTotalIOB() {
        Iob total = new Iob();
        for (Integer pos = 0; pos < treatments.size(); pos++) {
            Treatment t = treatments.get(pos);
            total.plus(t.iobCalc(new Date()));
        }
        if (iobTotal != null)
            iobTotal.setText(formatNumber2decimalplaces.format(total.iobContrib));
        if (activityTotal != null)
            activityTotal.setText(formatNumber3decimalplaces.format(total.activityContrib));

        lastCalculationTimestamp = new Date().getTime();
        lastCalculation = total;
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
            // TODO: implement locales
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, new Locale("cs", "CZ"));
            holder.date.setText(df.format(treatments.get(position).created_at));
            holder.insulin.setText(formatNumber2decimalplaces.format(treatments.get(position).insulin) + " U");
            holder.carbs.setText(formatNumber0decimalplaces.format(treatments.get(position).carbs) + " g");
            Iob iob = treatments.get(position).iobCalc(new Date());
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
        initializeData();
    }

    public static TreatmentsFragment newInstance() {
        TreatmentsFragment fragment = new TreatmentsFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerBus();
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
                builder.setTitle("Dialog");
                builder.setMessage("Do you want to refresh treatments from Nightscout");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        MainApp.getDbHelper().resetTreatments();
                        initializeData();
                        Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
                        MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
                    }
                });
                builder.setNegativeButton("Cancel", null);
                builder.show();

                break;
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
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
        Activity activity = getActivity();
        if (activity != null && recyclerView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    initializeData();
                }
            });
        else
            log.debug("EventTreatmentChange: Activity is null");
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        Activity activity = getActivity();
        if (activity != null && recyclerView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateTotalIOB();
                    recyclerView.getAdapter().notifyDataSetChanged();
                }
            });
        else
            log.debug("EventNewBG: Activity is null");
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (isVisibleToUser)
            updateTotalIOBIfNeeded();
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(String param);
    }

    @Override
    public void treatmentDeliverRequest(Double insulin, Double carbs) {
        // TODO: implement treatment delivery
    }

}
