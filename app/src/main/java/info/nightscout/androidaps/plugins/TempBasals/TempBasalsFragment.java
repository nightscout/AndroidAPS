package info.nightscout.androidaps.plugins.TempBasals;

import android.content.Context;
import android.net.Uri;
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
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventTempBasalChange;


public class TempBasalsFragment extends Fragment {
    private static Logger log = LoggerFactory.getLogger(TempBasalsFragment.class);

    RecyclerView recyclerView;
    LinearLayoutManager llm;

    TextView iobTotal;
    TextView activityTotal;

    private static DecimalFormat formatNumber0decimalplaces = new DecimalFormat("0");
    private static DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");
    private static DecimalFormat formatNumber3decimalplaces = new DecimalFormat("0.000");

    private OnFragmentInteractionListener mListener;

    private List<TempBasal> tempBasals;

    private void initializeData() {
        try {
            Dao<TempBasal, Long> dao = MainApp.getDbHelper().getDaoTempBasals();

            // **************** TESTING CREATE FAKE RECORD *****************
            TempBasal fake = new TempBasal();
            fake.timeStart = new Date(new Date().getTime() - 45 * 40 * 1000);
            fake.timeEnd = new Date(new Date().getTime() - new Double(Math.random() * 45d * 40 * 1000).longValue());
            fake.duration = 30;
            fake.percent = 150;
            fake.isAbsolute = false;
            fake.isExtended = false;
            dao.create(fake);
            // **************** TESTING CREATE FAKE RECORD *****************

            QueryBuilder<TempBasal, Long> queryBuilder = dao.queryBuilder();
            queryBuilder.orderBy("timeIndex", false);
            queryBuilder.limit(30l);
            PreparedQuery<TempBasal> preparedQuery = queryBuilder.prepare();
            tempBasals = dao.query(preparedQuery);
        } catch (SQLException e) {
            log.debug(e.getMessage(), e);
            tempBasals = new ArrayList<TempBasal>();
        }
        if (recyclerView != null) {
            recyclerView.swapAdapter(new RecyclerViewAdapter(tempBasals), false);
        }



        updateTotalIOB();
    }

    private void updateTotalIOB() {
        Iob total = new Iob();
        for (Integer pos = 0; pos < tempBasals.size(); pos++) {
            TempBasal t = tempBasals.get(pos);
            total.plus(t.iobCalc(new Date()));
        }
        if (iobTotal != null)
            iobTotal.setText(formatNumber2decimalplaces.format(total.iobContrib));
        if (activityTotal != null)
            activityTotal.setText(formatNumber3decimalplaces.format(total.activityContrib));
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
            Iob iob = tempBasals.get(position).iobCalc(new Date());
            holder.iob.setText(formatNumber2decimalplaces.format(iob.iobContrib) + " U");
            holder.activity.setText(formatNumber3decimalplaces.format(iob.activityContrib) + " U");
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
            TextView activity;

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
                activity = (TextView) itemView.findViewById(R.id.tempbasals_activity);
            }
        }
    }

    public TempBasalsFragment() {
        super();
        initializeData();
    }

    public static TempBasalsFragment newInstance() {
        TempBasalsFragment fragment = new TempBasalsFragment();
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
        View view =  inflater.inflate(R.layout.tempbasals_fragment, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.tempbasals_recyclerview);
        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(tempBasals);
        recyclerView.setAdapter(adapter);

        iobTotal = (TextView) view.findViewById(R.id.tempbasals_iobtotal);
        activityTotal = (TextView) view.findViewById(R.id.tempbasals_iobactivitytotal);

        return view;
    }
    /*
    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }
    */

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
    public void onStatusEvent(final EventTempBasalChange ev) {
        initializeData();
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        updateTotalIOB();
        recyclerView.getAdapter().notifyDataSetChanged();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (isVisibleToUser)
            updateTotalIOB();
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
}
