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
import android.widget.LinearLayout;
import android.widget.TextView;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRConnectionStatus;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRNewStatus;
import info.nightscout.androidaps.plugins.OpenAPSMA.IobTotal;
import info.nightscout.androidaps.plugins.Treatments.events.EventTreatmentsUpdateGui;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DecimalFormatter;

public class TreatmentsFragment extends Fragment implements View.OnClickListener, FragmentBase {
    private static Logger log = LoggerFactory.getLogger(TreatmentsFragment.class);

    private static TreatmentsPlugin treatmentsPlugin = new TreatmentsPlugin();

    public static TreatmentsPlugin getPlugin() {
        return treatmentsPlugin;
    }

    RecyclerView recyclerView;
    LinearLayoutManager llm;

    TextView iobTotal;
    TextView activityTotal;
    Button refreshFromNS;

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
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
            holder.date.setText(df.format(treatments.get(position).created_at));
            holder.insulin.setText(DecimalFormatter.to2Decimal(treatments.get(position).insulin) + " U");
            holder.carbs.setText(DecimalFormatter.to0Decimal(treatments.get(position).carbs) + " g");
            Iob iob = treatments.get(position).iobCalc(new Date(), profile.getDia());
            holder.iob.setText(DecimalFormatter.to2Decimal(iob.iobContrib) + " U");
            holder.activity.setText(DecimalFormatter.to3Decimal(iob.activityContrib) + " U");
            if (iob.iobContrib != 0)
                holder.dateLinearLayout.setBackgroundColor(MainApp.instance().getResources().getColor(R.color.colorAffectingIOB));
            else
                holder.dateLinearLayout.setBackgroundColor(MainApp.instance().getResources().getColor(R.color.cardColorBackground));
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
            LinearLayout dateLinearLayout;

            TreatmentsViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.treatments_cardview);
                date = (TextView) itemView.findViewById(R.id.treatments_date);
                insulin = (TextView) itemView.findViewById(R.id.treatments_insulin);
                carbs = (TextView) itemView.findViewById(R.id.treatments_carbs);
                iob = (TextView) itemView.findViewById(R.id.treatments_iob);
                activity = (TextView) itemView.findViewById(R.id.treatments_activity);
                dateLinearLayout = (LinearLayout) itemView.findViewById(R.id.treatments_datelinearlayout);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.treatments_fragment, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.treatments_recyclerview);
        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(TreatmentsPlugin.treatments);
        recyclerView.setAdapter(adapter);

        iobTotal = (TextView) view.findViewById(R.id.treatments_iobtotal);
        activityTotal = (TextView) view.findViewById(R.id.treatments_iobactivitytotal);

        refreshFromNS = (Button) view.findViewById(R.id.treatments_reshreshfromnightscout);
        refreshFromNS.setOnClickListener(this);

        updateGUI();
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.treatments_reshreshfromnightscout:
                AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
                builder.setTitle(this.getContext().getString(R.string.confirmation));
                builder.setMessage(this.getContext().getString(R.string.refreshfromnightscout));
                builder.setPositiveButton(this.getContext().getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        MainApp.getDbHelper().resetTreatments();
                        treatmentsPlugin.initializeData();
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

    @Override
    public void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        MainApp.bus().register(this);
    }

    @Subscribe
    public void onStatusEvent(final EventTreatmentsUpdateGui ev) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventTreatmentChange ev) {
        updateGUI();
    }

    public void updateGUI() {
        Activity activity = getActivity();
        if (activity != null && recyclerView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    recyclerView.swapAdapter(new RecyclerViewAdapter(TreatmentsPlugin.treatments), false);
                    if (TreatmentsPlugin.lastCalculation != null)
                        iobTotal.setText(DecimalFormatter.to2Decimal(TreatmentsPlugin.lastCalculation.iob) + " U");
                    if (TreatmentsPlugin.lastCalculation != null)
                        activityTotal.setText(DecimalFormatter.to3Decimal(TreatmentsPlugin.lastCalculation.activity) + " U");
                }
            });
    }

}
