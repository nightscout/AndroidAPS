package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import dagger.android.support.DaggerFragment;
import info.nightscout.androidaps.plugins.pump.common.R;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.dialog.RefreshableInterface;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

/**
 * Created by andy on 5/19/18.
 */

public class RileyLinkStatusHistoryFragment extends DaggerFragment implements RefreshableInterface {

    @Inject RileyLinkUtil rileyLinkUtil;
    @Inject ResourceHelper resourceHelper;
    @Inject DateUtil dateUtil;

    RecyclerView recyclerView;
    RecyclerViewAdapter recyclerViewAdapter;

    LinearLayoutManager llm;
    List<RLHistoryItem> filteredHistoryList = new ArrayList<>();


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.rileylink_status_history, container, false);

        recyclerView = rootView.findViewById(R.id.rileylink_history_list);

        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(rootView.getContext());
        recyclerView.setLayoutManager(llm);

        recyclerViewAdapter = new RecyclerViewAdapter(filteredHistoryList);
        recyclerView.setAdapter(recyclerViewAdapter);

        return rootView;
    }


    @Override
    public void onStart() {
        super.onStart();

        refreshData();
    }


    @Override
    public void refreshData() {
        if (rileyLinkUtil.getRileyLinkHistory() != null) {
            recyclerViewAdapter.addItemsAndClean(rileyLinkUtil.getRileyLinkHistory());
        }
    }


    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder> {

        List<RLHistoryItem> historyList;


        RecyclerViewAdapter(List<RLHistoryItem> historyList) {
            this.historyList = historyList;
        }


        public void setHistoryList(List<RLHistoryItem> historyList) {
            this.historyList = historyList;
        }


        public void addItemsAndClean(List<RLHistoryItem> items) {
            this.historyList.clear();

            Collections.sort(items, new RLHistoryItem.Comparator());

            for (RLHistoryItem item : items) {

                if (!historyList.contains(item) && isValidItem(item)) {
                    historyList.add(item);
                }
            }

            notifyDataSetChanged();
        }


        private boolean isValidItem(RLHistoryItem item) {

            PumpDeviceState pumpState = item.getPumpDeviceState();

            //
            //
            return pumpState != PumpDeviceState.Sleeping && //
                    pumpState != PumpDeviceState.Active && //
                    pumpState != PumpDeviceState.WakingUp;

        }


        @NotNull
        @Override
        public RecyclerViewAdapter.HistoryViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.rileylink_status_history_item, //
                    viewGroup, false);
            return new RecyclerViewAdapter.HistoryViewHolder(v);
        }


        @Override
        public void onBindViewHolder(RecyclerViewAdapter.HistoryViewHolder holder, int position) {
            RLHistoryItem item = historyList.get(position);

            if (item != null) {
                holder.timeView.setText(dateUtil.dateAndTimeAndSecondsString(item.getDateTime().toDateTime().getMillis()));
                holder.typeView.setText(item.getSource().getDesc());
                holder.valueView.setText(item.getDescription(resourceHelper));
            }
        }


        @Override
        public int getItemCount() {
            return historyList.size();
        }


        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        class HistoryViewHolder extends RecyclerView.ViewHolder {

            TextView timeView;
            TextView typeView;
            TextView valueView;


            HistoryViewHolder(View itemView) {
                super(itemView);

                timeView = itemView.findViewById(R.id.rileylink_history_time);
                typeView = itemView.findViewById(R.id.rileylink_history_source);
                valueView = itemView.findViewById(R.id.rileylink_history_description);
            }
        }
    }

}
