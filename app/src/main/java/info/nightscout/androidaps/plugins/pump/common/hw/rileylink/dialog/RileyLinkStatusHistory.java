package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.pump.common.dialog.RefreshableInterface;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpDeviceState;

/**
 * Created by andy on 5/19/18.
 */

public class RileyLinkStatusHistory extends Fragment implements RefreshableInterface {

    RecyclerView recyclerView;
    RecyclerViewAdapter recyclerViewAdapter;

    LinearLayoutManager llm;
    List<RLHistoryItem> filteredHistoryList = new ArrayList<>();


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.rileylink_status_history, container, false);

        recyclerView = (RecyclerView)rootView.findViewById(R.id.rileylink_history_list);

        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(getActivity().getApplicationContext());
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
        if (RileyLinkUtil.getRileyLinkHistory()!=null) {
            recyclerViewAdapter.addItemsAndClean(RileyLinkUtil.getRileyLinkHistory());
        }
    }


    public static class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder> {

        List<RLHistoryItem> historyList;


        RecyclerViewAdapter(List<RLHistoryItem> historyList) {
            this.historyList = historyList;
        }


        public void setHistoryList(List<RLHistoryItem> historyList) {
            this.historyList = historyList;
        }


        public void addItemsAndClean(List<RLHistoryItem> items) {
            this.historyList.clear();

            for (RLHistoryItem item : items) {

                if (!historyList.contains(item) && isValidItem(item)) {
                    historyList.add(item);
                }
            }

            notifyDataSetChanged();
        }


        private boolean isValidItem(RLHistoryItem item) {

            PumpDeviceState pumpState = item.getPumpDeviceState();

            if ((pumpState != null) && //
                (pumpState == PumpDeviceState.Sleeping || //
                    pumpState == PumpDeviceState.Active || //
                pumpState == PumpDeviceState.WakingUp //
                ))
                return false;

            return true;

        }


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
                holder.timeView.setText(StringUtil.toDateTimeString(item.getDateTime()));
                holder.typeView.setText(item.getSource().getDesc());
                holder.valueView.setText(item.getDescription());
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

        static class HistoryViewHolder extends RecyclerView.ViewHolder {

            TextView timeView;
            TextView typeView;
            TextView valueView;


            HistoryViewHolder(View itemView) {
                super(itemView);

                timeView = (TextView)itemView.findViewById(R.id.rileylink_history_time);
                typeView = (TextView)itemView.findViewById(R.id.rileylink_history_source);
                valueView = (TextView)itemView.findViewById(R.id.rileylink_history_description);
            }
        }
    }

}
