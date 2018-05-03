package info.nightscout.androidaps.plugins.Treatments.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.crashlytics.android.answers.CustomEvent;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.Services.Intents;
import info.nightscout.androidaps.data.Iob;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.NSClientInternal.UploadQueue;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;

public class TreatmentsBolusFragment extends SubscriberFragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(TreatmentsBolusFragment.class);

    RecyclerView recyclerView;
    LinearLayoutManager llm;

    TextView iobTotal;
    TextView activityTotal;
    Button refreshFromNS;

    Context context;

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.TreatmentsViewHolder> {

        List<Treatment> treatments;

        RecyclerViewAdapter(List<Treatment> treatments) {
            this.treatments = treatments;
        }

        @Override
        public TreatmentsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.treatments_bolus_item, viewGroup, false);
            return new TreatmentsViewHolder(v);
        }

        @Override
        public void onBindViewHolder(TreatmentsViewHolder holder, int position) {
            Profile profile = MainApp.getConfigBuilder().getProfile();
            if (profile == null)
                return;
            Treatment t = treatments.get(position);
            holder.date.setText(DateUtil.dateAndTimeString(t.date));
            holder.insulin.setText(DecimalFormatter.toPumpSupportedBolus(t.insulin) + " U");
            holder.carbs.setText(DecimalFormatter.to0Decimal(t.carbs) + " g");
            Iob iob = t.iobCalc(System.currentTimeMillis(), profile.getDia());
            holder.iob.setText(DecimalFormatter.to2Decimal(iob.iobContrib) + " U");
            holder.activity.setText(DecimalFormatter.to3Decimal(iob.activityContrib) + " U");
            holder.mealOrCorrection.setText(t.isSMB ? "SMB" : t.mealBolus ? MainApp.gs(R.string.mealbolus) : MainApp.gs(R.string.correctionbous));
            holder.ph.setVisibility(t.source == Source.PUMP ? View.VISIBLE : View.GONE);
            holder.ns.setVisibility(NSUpload.isIdValid(t._id) ? View.VISIBLE : View.GONE);
            holder.invalid.setVisibility(t.isValid ? View.GONE : View.VISIBLE);
            if (iob.iobContrib != 0)
                holder.iob.setTextColor(ContextCompat.getColor(MainApp.instance(), R.color.colorActive));
            else
                holder.iob.setTextColor(holder.carbs.getCurrentTextColor());
            if (t.date > DateUtil.now())
                holder.date.setTextColor(ContextCompat.getColor(MainApp.instance(), R.color.colorScheduled));
            else
                holder.date.setTextColor(holder.carbs.getCurrentTextColor());
            holder.remove.setTag(t);
        }

        @Override
        public int getItemCount() {
            return treatments.size();
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public class TreatmentsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            CardView cv;
            TextView date;
            TextView insulin;
            TextView carbs;
            TextView iob;
            TextView activity;
            TextView mealOrCorrection;
            TextView remove;
            TextView ph;
            TextView ns;
            TextView invalid;

            TreatmentsViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.treatments_cardview);
                date = (TextView) itemView.findViewById(R.id.treatments_date);
                insulin = (TextView) itemView.findViewById(R.id.treatments_insulin);
                carbs = (TextView) itemView.findViewById(R.id.treatments_carbs);
                iob = (TextView) itemView.findViewById(R.id.treatments_iob);
                activity = (TextView) itemView.findViewById(R.id.treatments_activity);
                mealOrCorrection = (TextView) itemView.findViewById(R.id.treatments_mealorcorrection);
                ph = (TextView) itemView.findViewById(R.id.pump_sign);
                ns = (TextView) itemView.findViewById(R.id.ns_sign);
                invalid = (TextView) itemView.findViewById(R.id.invalid_sign);
                remove = (TextView) itemView.findViewById(R.id.treatments_remove);
                remove.setOnClickListener(this);
                remove.setPaintFlags(remove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            }

            @Override
            public void onClick(View v) {
                final Treatment treatment = (Treatment) v.getTag();
                switch (v.getId()) {
                    case R.id.treatments_remove:
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(MainApp.gs(R.string.confirmation));
                        builder.setMessage(MainApp.gs(R.string.removerecord) + "\n" + DateUtil.dateAndTimeString(treatment.date));
                        builder.setPositiveButton(MainApp.gs(R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                final String _id = treatment._id;
                                if (treatment.source == Source.PUMP) {
                                    treatment.isValid = false;
                                    TreatmentsPlugin.getPlugin().getService().update(treatment);
                                } else {
                                    if (NSUpload.isIdValid(_id)) {
                                        NSUpload.removeCareportalEntryFromNS(_id);
                                    } else {
                                        UploadQueue.removeID("dbAdd", _id);
                                    }
                                    TreatmentsPlugin.getPlugin().getService().delete(treatment);
                                }
                                updateGUI();
                                FabricPrivacy.getInstance().logCustom(new CustomEvent("RemoveTreatment"));
                            }
                        });
                        builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
                        builder.show();
                        break;
                }
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.treatments_bolus_fragment, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.treatments_recyclerview);
        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(TreatmentsPlugin.getPlugin().getTreatmentsFromHistory());
        recyclerView.setAdapter(adapter);

        iobTotal = (TextView) view.findViewById(R.id.treatments_iobtotal);
        activityTotal = (TextView) view.findViewById(R.id.treatments_iobactivitytotal);

        refreshFromNS = (Button) view.findViewById(R.id.treatments_reshreshfromnightscout);
        refreshFromNS.setOnClickListener(this);

        boolean nsUploadOnly = SP.getBoolean(R.string.key_ns_upload_only, false);
        if (nsUploadOnly)
            refreshFromNS.setVisibility(View.GONE);

        context = getContext();

        updateGUI();
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.treatments_reshreshfromnightscout:
                AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
                builder.setTitle(MainApp.gs(R.string.confirmation));
                builder.setMessage(MainApp.gs(R.string.refresheventsfromnightscout) + "?");
                builder.setPositiveButton(MainApp.gs(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        TreatmentsPlugin.getPlugin().getService().resetTreatments();
                        Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
                        MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
                    }
                });
                builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
                builder.show();
                break;
        }
    }

    @Subscribe
    public void onStatusEvent(final EventTreatmentChange ev) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    recyclerView.swapAdapter(new RecyclerViewAdapter(TreatmentsPlugin.getPlugin().getTreatmentsFromHistory()), false);
                    if (TreatmentsPlugin.getPlugin().getLastCalculationTreatments() != null) {
                        iobTotal.setText(DecimalFormatter.to2Decimal(TreatmentsPlugin.getPlugin().getLastCalculationTreatments().iob) + " U");
                        activityTotal.setText(DecimalFormatter.to3Decimal(TreatmentsPlugin.getPlugin().getLastCalculationTreatments().activity) + " U");
                    }
                }
            });
    }

}
