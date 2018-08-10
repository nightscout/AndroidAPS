package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.dialog;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.PumpCommon.dialog.RefreshableInterface;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.PumpCommon.utils.StringUtil;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.PumpDeviceState;

/**
 * Created by andy on 5/19/18.
 */

public class RileyLinkStatusHistory extends Fragment implements RefreshableInterface {

    // @BindView(R.id.rileylink_history_list)
    ListView listView;

    RileyLinkHistoryListAdapter adapter;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.rileylink_status_history, container, false);

        adapter = new RileyLinkHistoryListAdapter();

        return rootView;
    }


    @Override
    public void onStart() {
        super.onStart();

        this.listView = (ListView)getActivity().findViewById(R.id.rileylink_history_list);

        listView.setAdapter(adapter);

        refreshData();
    }


    @Override
    public void refreshData() {
        adapter.addItemsAndClean(RileyLinkUtil.getRileyLinkHistory());
    }

    static class ViewHolder {

        TextView itemTime;
        TextView itemSource;
        TextView itemDescription;
    }

    private class RileyLinkHistoryListAdapter extends BaseAdapter {

        private List<RLHistoryItem> historyItemList;
        private LayoutInflater mInflator;


        public RileyLinkHistoryListAdapter() {
            super();
            historyItemList = new ArrayList<>();
            mInflator = RileyLinkStatusHistory.this.getLayoutInflater();
        }


        public void addItem(RLHistoryItem item) {
            if (!historyItemList.contains(item)) {
                historyItemList.add(item);
                notifyDataSetChanged();
            }
        }


        public RLHistoryItem getHistoryItem(int position) {
            return historyItemList.get(position);
        }


        public void addItemsAndClean(List<RLHistoryItem> items) {
            this.historyItemList.clear();

            for (RLHistoryItem item : items) {

                if (!historyItemList.contains(item) && isValidItem(item)) {
                    historyItemList.add(item);
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


        public void clear() {
            historyItemList.clear();
            notifyDataSetChanged();
        }


        @Override
        public int getCount() {
            return historyItemList.size();
        }


        @Override
        public Object getItem(int i) {
            return historyItemList.get(i);
        }


        @Override
        public long getItemId(int i) {
            return i;
        }


        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            RileyLinkStatusHistory.ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.rileylink_status_history_item, null);
                viewHolder = new RileyLinkStatusHistory.ViewHolder();
                viewHolder.itemTime = (TextView)view.findViewById(R.id.rileylink_history_time);
                viewHolder.itemSource = (TextView)view.findViewById(R.id.rileylink_history_source);
                viewHolder.itemDescription = (TextView)view.findViewById(R.id.rileylink_history_description);
                view.setTag(viewHolder);
            } else {
                viewHolder = (RileyLinkStatusHistory.ViewHolder)view.getTag();
            }

            RLHistoryItem item = historyItemList.get(i);
            viewHolder.itemTime.setText(StringUtil.toDateTimeString(item.getDateTime()));
            viewHolder.itemSource.setText(item.getSource().getDesc()); // for now
            viewHolder.itemDescription.setText(item.getDescription());

            return view;
        }
    }

}
