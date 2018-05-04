package info.nightscout.androidaps.plugins.Treatments.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.TextView;

import com.crashlytics.android.answers.CustomEvent;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Intervals;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.NSClientInternal.UploadQueue;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.NSUpload;


public class TreatmentsExtendedBolusesFragment extends SubscriberFragment {
    private static Logger log = LoggerFactory.getLogger(TreatmentsExtendedBolusesFragment.class);

    RecyclerView recyclerView;
    LinearLayoutManager llm;

    Context context;

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ExtendedBolusesViewHolder> {

        Intervals<ExtendedBolus> extendedBolusList;

        RecyclerViewAdapter(Intervals<ExtendedBolus> extendedBolusList) {
            this.extendedBolusList = extendedBolusList;
        }

        @Override
        public ExtendedBolusesViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.treatments_extendedbolus_item, viewGroup, false);
            return new ExtendedBolusesViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ExtendedBolusesViewHolder holder, int position) {
            ExtendedBolus extendedBolus = extendedBolusList.getReversed(position);
            holder.ph.setVisibility(extendedBolus.source == Source.PUMP ? View.VISIBLE : View.GONE);
            holder.ns.setVisibility(NSUpload.isIdValid(extendedBolus._id) ? View.VISIBLE : View.GONE);
            if (extendedBolus.isEndingEvent()) {
                holder.date.setText(DateUtil.dateAndTimeString(extendedBolus.date));
                holder.duration.setText(MainApp.gs(R.string.cancel));
                holder.insulin.setText("");
                holder.realDuration.setText("");
                holder.iob.setText("");
                holder.insulinSoFar.setText("");
                holder.ratio.setText("");
            } else {
                if (extendedBolus.isInProgress()) {
                    holder.date.setText(DateUtil.dateAndTimeString(extendedBolus.date));
                } else {
                    holder.date.setText(DateUtil.dateAndTimeString(extendedBolus.date) + " - " + DateUtil.timeString(extendedBolus.end()));
                }
                holder.duration.setText(DecimalFormatter.to0Decimal(extendedBolus.durationInMinutes) + " min");
                holder.insulin.setText(DecimalFormatter.toPumpSupportedBolus(extendedBolus.insulin) + " U");
                holder.realDuration.setText(DecimalFormatter.to0Decimal(extendedBolus.getRealDuration()) + " min");
                IobTotal iob = extendedBolus.iobCalc(System.currentTimeMillis());
                holder.iob.setText(DecimalFormatter.to2Decimal(iob.iob) + " U");
                holder.insulinSoFar.setText(DecimalFormatter.to2Decimal(extendedBolus.insulinSoFar()) + " U");
                holder.ratio.setText(DecimalFormatter.to2Decimal(extendedBolus.absoluteRate()) + " U/h");
                if (extendedBolus.isInProgress())
                    holder.date.setTextColor(ContextCompat.getColor(MainApp.instance(), R.color.colorActive));
                else
                    holder.date.setTextColor(holder.insulin.getCurrentTextColor());
                if (extendedBolus.iobCalc(System.currentTimeMillis()).iob != 0)
                    holder.iob.setTextColor(ContextCompat.getColor(MainApp.instance(), R.color.colorActive));
                else
                    holder.iob.setTextColor(holder.insulin.getCurrentTextColor());
            }
            holder.remove.setTag(extendedBolus);
        }

        @Override
        public int getItemCount() {
            return extendedBolusList.size();
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public class ExtendedBolusesViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            CardView cv;
            TextView date;
            TextView duration;
            TextView insulin;
            TextView realDuration;
            TextView ratio;
            TextView insulinSoFar;
            TextView iob;
            TextView remove;
            TextView ph;
            TextView ns;

            ExtendedBolusesViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.extendedboluses_cardview);
                date = (TextView) itemView.findViewById(R.id.extendedboluses_date);
                duration = (TextView) itemView.findViewById(R.id.extendedboluses_duration);
                insulin = (TextView) itemView.findViewById(R.id.extendedboluses_insulin);
                realDuration = (TextView) itemView.findViewById(R.id.extendedboluses_realduration);
                ratio = (TextView) itemView.findViewById(R.id.extendedboluses_ratio);
                insulinSoFar = (TextView) itemView.findViewById(R.id.extendedboluses_netinsulin);
                iob = (TextView) itemView.findViewById(R.id.extendedboluses_iob);
                ph = (TextView) itemView.findViewById(R.id.pump_sign);
                ns = (TextView) itemView.findViewById(R.id.ns_sign);
                remove = (TextView) itemView.findViewById(R.id.extendedboluses_remove);
                remove.setOnClickListener(this);
                remove.setPaintFlags(remove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            }

            @Override
            public void onClick(View v) {
                final ExtendedBolus extendedBolus = (ExtendedBolus) v.getTag();
                switch (v.getId()) {
                    case R.id.extendedboluses_remove:
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(MainApp.gs(R.string.confirmation));
                        builder.setMessage(MainApp.gs(R.string.removerecord) + "\n" + DateUtil.dateAndTimeString(extendedBolus.date));
                        builder.setPositiveButton(MainApp.gs(R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                final String _id = extendedBolus._id;
                                if (NSUpload.isIdValid(_id)) {
                                    NSUpload.removeCareportalEntryFromNS(_id);
                                } else {
                                    UploadQueue.removeID("dbAdd", _id);
                                }
                                MainApp.getDbHelper().delete(extendedBolus);
                                FabricPrivacy.getInstance().logCustom(new CustomEvent("RemoveExtendedBolus"));
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
        View view = inflater.inflate(R.layout.treatments_extendedbolus_fragment, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.extendedboluses_recyclerview);
        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(TreatmentsPlugin.getPlugin().getExtendedBolusesFromHistory());
        recyclerView.setAdapter(adapter);

        context = getContext();

        updateGUI();
        return view;
    }

    @Subscribe
    public void onStatusEvent(final EventExtendedBolusChange ev) {
        updateGUI();
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null && recyclerView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    recyclerView.swapAdapter(new RecyclerViewAdapter(TreatmentsPlugin.getPlugin().getExtendedBolusesFromHistory()), false);
                }
            });
    }

}
