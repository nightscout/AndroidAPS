package info.nightscout.androidaps.plugins.treatments.fragments;

import android.content.Context;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Intervals;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.FabricPrivacy;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;


public class TreatmentsTemporaryBasalsFragment extends Fragment {
    private CompositeDisposable disposable = new CompositeDisposable();

    RecyclerView recyclerView;
    LinearLayoutManager llm;

    TextView tempBasalTotalView;

    Context context;

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.TempBasalsViewHolder> {

        Intervals<TemporaryBasal> tempBasalList;

        RecyclerViewAdapter(Intervals<TemporaryBasal> tempBasalList) {
            this.tempBasalList = tempBasalList;
        }

        @Override
        public TempBasalsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.treatments_tempbasals_item, viewGroup, false);
            return new TempBasalsViewHolder(v);
        }

        @Override
        public void onBindViewHolder(TempBasalsViewHolder holder, int position) {
            TemporaryBasal tempBasal = tempBasalList.getReversed(position);
            holder.ph.setVisibility(tempBasal.source == Source.PUMP ? View.VISIBLE : View.GONE);
            holder.ns.setVisibility(NSUpload.isIdValid(tempBasal._id) ? View.VISIBLE : View.GONE);
            if (tempBasal.isEndingEvent()) {
                holder.date.setText(DateUtil.dateAndTimeString(tempBasal.date));
                holder.duration.setText(MainApp.gs(R.string.cancel));
                holder.absolute.setText("");
                holder.percent.setText("");
                holder.realDuration.setText("");
                holder.iob.setText("");
                holder.netInsulin.setText("");
                holder.netRatio.setText("");
                holder.extendedFlag.setVisibility(View.GONE);
                holder.iob.setTextColor(holder.netRatio.getCurrentTextColor());
            } else {
                if (tempBasal.isInProgress()) {
                    holder.date.setText(DateUtil.dateAndTimeString(tempBasal.date));
                    holder.date.setTextColor(ContextCompat.getColor(MainApp.instance(), R.color.colorActive));
                } else {
                    holder.date.setText(DateUtil.dateAndTimeRangeString(tempBasal.date, tempBasal.end()));
                    holder.date.setTextColor(holder.netRatio.getCurrentTextColor());
                }
                holder.duration.setText(DecimalFormatter.to0Decimal(tempBasal.durationInMinutes, " min"));
                if (tempBasal.isAbsolute) {
                    Profile profile = ProfileFunctions.getInstance().getProfile(tempBasal.date);
                    if (profile != null) {
                        holder.absolute.setText(DecimalFormatter.to2Decimal(tempBasal.tempBasalConvertedToAbsolute(tempBasal.date, profile), " U/h"));
                        holder.percent.setText("");
                    } else {
                        holder.absolute.setText(MainApp.gs(R.string.noprofile));
                        holder.percent.setText("");
                    }
                } else {
                    holder.absolute.setText("");
                    holder.percent.setText(DecimalFormatter.to0Decimal(tempBasal.percentRate, "%"));
                }
                holder.realDuration.setText(DecimalFormatter.to0Decimal(tempBasal.getRealDuration(), " min"));
                long now = DateUtil.now();
                IobTotal iob = new IobTotal(now);
                Profile profile = ProfileFunctions.getInstance().getProfile(now);
                if (profile != null)
                    iob = tempBasal.iobCalc(now, profile);
                holder.iob.setText(DecimalFormatter.to2Decimal(iob.basaliob, " U"));
                holder.netInsulin.setText(DecimalFormatter.to2Decimal(iob.netInsulin, " U"));
                holder.netRatio.setText(DecimalFormatter.to2Decimal(iob.netRatio, " U/h"));
                holder.extendedFlag.setVisibility(View.GONE);
                if (iob.basaliob != 0)
                    holder.iob.setTextColor(ContextCompat.getColor(MainApp.instance(), R.color.colorActive));
                else
                    holder.iob.setTextColor(holder.netRatio.getCurrentTextColor());
            }
            holder.remove.setTag(tempBasal);
        }

        @Override
        public int getItemCount() {
            return tempBasalList.size();
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public class TempBasalsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
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
            TextView remove;
            TextView ph;
            TextView ns;

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
                ph = (TextView) itemView.findViewById(R.id.pump_sign);
                ns = (TextView) itemView.findViewById(R.id.ns_sign);
                remove = (TextView) itemView.findViewById(R.id.tempbasals_remove);
                remove.setOnClickListener(this);
                remove.setPaintFlags(remove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            }

            @Override
            public void onClick(View v) {
                final TemporaryBasal tempBasal = (TemporaryBasal) v.getTag();
                switch (v.getId()) {
                    case R.id.tempbasals_remove:
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(MainApp.gs(R.string.confirmation));
                        builder.setMessage(MainApp.gs(R.string.removerecord) + "\n" + DateUtil.dateAndTimeString(tempBasal.date));
                        builder.setPositiveButton(MainApp.gs(R.string.ok), (dialog, id) -> {
                            final String _id = tempBasal._id;
                            if (NSUpload.isIdValid(_id)) {
                                NSUpload.removeCareportalEntryFromNS(_id);
                            } else {
                                UploadQueue.removeID("dbAdd", _id);
                            }
                            MainApp.getDbHelper().delete(tempBasal);
                        });
                        builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
                        builder.show();
                        break;
                }
            }
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.treatments_tempbasals_fragment, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.tempbasals_recyclerview);
        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(TreatmentsPlugin.getPlugin().getTemporaryBasalsFromHistory());
        recyclerView.setAdapter(adapter);

        tempBasalTotalView = (TextView) view.findViewById(R.id.tempbasals_totaltempiob);

        context = getContext();

        return view;
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventTempBasalChange.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateGui(), FabricPrivacy::logException)
        );
        disposable.add(RxBus.INSTANCE
                .toObservable(EventAutosensCalculationFinished.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateGui(), FabricPrivacy::logException)
        );
        updateGui();
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        disposable.clear();
    }

    private void updateGui() {
        recyclerView.swapAdapter(new RecyclerViewAdapter(TreatmentsPlugin.getPlugin().getTemporaryBasalsFromHistory()), false);
        IobTotal tempBasalsCalculation = TreatmentsPlugin.getPlugin().getLastCalculationTempBasals();
        if (tempBasalsCalculation != null)
            tempBasalTotalView.setText(DecimalFormatter.to2Decimal(tempBasalsCalculation.basaliob, " U"));
    }

}
