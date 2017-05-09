package info.nightscout.androidaps.plugins.TempBasals;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;


public class TempBasalsFragment extends Fragment {
    private static Logger log = LoggerFactory.getLogger(TempBasalsFragment.class);

    private static TempBasalsPlugin tempBasalsPlugin = new TempBasalsPlugin();

    public static TempBasalsPlugin getPlugin() {
        return tempBasalsPlugin;
    }

    RecyclerView recyclerView;
    LinearLayoutManager llm;

    TextView tempBasalTotalView;

    Context context;

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.TempBasalsViewHolder> {

        List<TempBasal> tempBasalList;

        RecyclerViewAdapter(List<TempBasal> tempBasalList) {
            this.tempBasalList = tempBasalList;
        }

        @Override
        public TempBasalsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.tempbasals_item, viewGroup, false);
            return new TempBasalsViewHolder(v);
        }

        @Override
        public void onBindViewHolder(TempBasalsViewHolder holder, int position) {
            TempBasal tempBasal = tempBasalList.get(position);
            if (tempBasal.timeEnd != null) {
                holder.date.setText(DateUtil.dateAndTimeString(tempBasal.timeStart) + " - " + DateUtil.timeString(tempBasalList.get(position).timeEnd));
            } else {
                holder.date.setText(DateUtil.dateAndTimeString(tempBasal.timeStart));
            }
            holder.duration.setText(DecimalFormatter.to0Decimal(tempBasal.duration) + " min");
            if (tempBasal.isAbsolute) {
                holder.absolute.setText(DecimalFormatter.to0Decimal(tempBasal.absolute) + " U/h");
                holder.percent.setText("");
            } else {
                holder.absolute.setText("");
                holder.percent.setText(DecimalFormatter.to0Decimal(tempBasal.percent) + "%");
            }
            holder.realDuration.setText(DecimalFormatter.to0Decimal(tempBasal.getRealDuration()) + " min");
            IobTotal iob = tempBasal.iobCalc(new Date().getTime());
            holder.iob.setText(DecimalFormatter.to2Decimal(iob.basaliob) + " U");
            holder.netInsulin.setText(DecimalFormatter.to2Decimal(iob.netInsulin) + " U");
            holder.netRatio.setText(DecimalFormatter.to2Decimal(iob.netRatio) + " U/h");
            holder.extendedFlag.setVisibility(tempBasal.isExtended ? View.VISIBLE : View.GONE);
            if (tempBasal.isInProgress())
                holder.dateLinearLayout.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.colorInProgress));
            else if (tempBasal.timeEnd == null)
                holder.dateLinearLayout.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.colorNotEnded));
            else if (tempBasal.iobCalc(new Date().getTime()).basaliob != 0)
                holder.dateLinearLayout.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.colorAffectingIOB));
            else
                holder.dateLinearLayout.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.cardColorBackground));
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
            LinearLayout dateLinearLayout;
            TextView remove;

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
                remove = (TextView) itemView.findViewById(R.id.tempbasals_remove);
                remove.setOnClickListener(this);
                remove.setPaintFlags(remove.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            }

            @Override
            public void onClick(View v) {
                final TempBasal tempBasal = (TempBasal) v.getTag();
                switch (v.getId()) {
                    case R.id.tempbasals_remove:
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(MainApp.sResources.getString(R.string.confirmation));
                        builder.setMessage(MainApp.sResources.getString(R.string.removerecord) + "\n" + DateUtil.dateAndTimeString(tempBasal.timeStart));
                        builder.setPositiveButton(MainApp.sResources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // TODO: handle this in NS too
                                //final String _id = tempBasal._id;
                                //if (_id != null && !_id.equals("")) {
                                //    MainApp.getConfigBuilder().removeCareportalEntryFromNS(_id);
                                //}
                                MainApp.getDbHelper().delete(tempBasal);
                                tempBasalsPlugin.initializeData();
                                updateGUI();
                                Answers.getInstance().logCustom(new CustomEvent("RemoveTempBasal"));
                            }
                        });
                        builder.setNegativeButton(MainApp.sResources.getString(R.string.cancel), null);
                        builder.show();
                        break;
                }
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.tempbasals_fragment, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.tempbasals_recyclerview);
        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(tempBasalsPlugin.getMergedList());
        recyclerView.setAdapter(adapter);

        tempBasalTotalView = (TextView) view.findViewById(R.id.tempbasals_totaltempiob);

        context = getContext();

        updateGUI();
        return view;
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
    public void onStatusEvent(final EventTempBasalChange ev) {
        updateGUI();
    }

    public void updateGUI() {
        Activity activity = getActivity();
        if (activity != null && recyclerView != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    recyclerView.swapAdapter(new RecyclerViewAdapter(tempBasalsPlugin.getMergedList()), false);
                    if (tempBasalsPlugin.lastCalculation != null) {
                        String totalText = DecimalFormatter.to2Decimal(tempBasalsPlugin.lastCalculation.basaliob) + " U";
                        tempBasalTotalView.setText(totalText);
                    }
                }
            });
    }

}
