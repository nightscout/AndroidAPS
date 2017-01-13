package info.nightscout.androidaps.plugins.TempTargetRange;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.TempTargetRange.events.EventNewTempTargetRange;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;

/**
 * Created by mike on 13/01/17.
 */

public class TempTargetRangeFragment extends Fragment implements FragmentBase {

    private static TempTargetRangePlugin tempTargetRangePlugin = new TempTargetRangePlugin();

    public static TempTargetRangePlugin getPlugin() {
        return tempTargetRangePlugin;
    }

    RecyclerView recyclerView;
    LinearLayoutManager llm;

    public static class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.TempTargetsViewHolder> {

        List<TempTarget> tempTargetList;

        RecyclerViewAdapter(List<TempTarget> TempTargetList) {
            this.tempTargetList = TempTargetList;
        }

        @Override
        public TempTargetsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.temptargetrange_item, viewGroup, false);
            TempTargetsViewHolder TempTargetsViewHolder = new TempTargetsViewHolder(v);
            return TempTargetsViewHolder;
        }

        @Override
        public void onBindViewHolder(TempTargetsViewHolder holder, int position) {
            NSProfile profile = ConfigBuilderPlugin.getActiveProfile().getProfile();
            if (profile == null) return;
            TempTarget tempTarget = tempTargetList.get(position);
            holder.date.setText(DateUtil.dateAndTimeString(tempTarget.timeStart) + " - " + DateUtil.timeString(tempTargetList.get(position).getPlannedTimeEnd()));
            holder.duration.setText(DecimalFormatter.to0Decimal(tempTarget.duration) + " min");
            holder.low.setText(tempTarget.lowValueToUnitsToString(profile.getUnits()));
            holder.high.setText(tempTarget.highValueToUnitsToString(profile.getUnits()));
            holder.reason.setText(tempTarget.reason);
            if (tempTarget.isInProgress())
                holder.dateLinearLayout.setBackgroundColor(MainApp.instance().getResources().getColor(R.color.colorInProgress));
            else
                holder.dateLinearLayout.setBackgroundColor(MainApp.instance().getResources().getColor(R.color.cardColorBackground));
        }

        @Override
        public int getItemCount() {
            return tempTargetList.size();
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public static class TempTargetsViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            TextView date;
            TextView duration;
            TextView low;
            TextView high;
            TextView reason;
            LinearLayout dateLinearLayout;

            TempTargetsViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.temptargetrange_cardview);
                date = (TextView) itemView.findViewById(R.id.temptargetrange_date);
                duration = (TextView) itemView.findViewById(R.id.temptargetrange_duration);
                low = (TextView) itemView.findViewById(R.id.temptargetrange_low);
                high = (TextView) itemView.findViewById(R.id.temptargetrange_high);
                reason = (TextView) itemView.findViewById(R.id.temptargetrange_reason);
                dateLinearLayout = (LinearLayout) itemView.findViewById(R.id.temptargetrange_datelinearlayout);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.temptargetrange_fragment, container, false);

        recyclerView = (RecyclerView) view.findViewById(R.id.temptargetrange_recyclerview);
        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(view.getContext());
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(tempTargetRangePlugin.getList());
        recyclerView.setAdapter(adapter);

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
    public void onStatusEvent(final EventNewTempTargetRange ev) {
        updateGUI();
    }

    void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                }
            });
    }
}
