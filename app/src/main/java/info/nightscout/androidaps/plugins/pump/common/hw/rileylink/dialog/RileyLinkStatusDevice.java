package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog;

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
import info.nightscout.androidaps.plugins.pump.common.dialog.RefreshableInterface;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;

/**
 * Created by andy on 5/19/18.
 */

// FIXME needs to be implemented
@Deprecated
public class RileyLinkStatusDevice extends Fragment implements RefreshableInterface {

    // @BindView(R.id.rileylink_history_list)
    ListView listView;

    RileyLinkCommandListAdapter adapter;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.rileylink_status_device, container, false);

        adapter = new RileyLinkCommandListAdapter();

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
        // adapter.addItemsAndClean(RileyLinkUtil.getRileyLinkHistory());
    }

    static class ViewHolder {

        TextView itemTime;
        TextView itemSource;
        TextView itemDescription;
    }

    private class RileyLinkCommandListAdapter extends BaseAdapter {

        private List<RLHistoryItem> historyItemList;
        private LayoutInflater mInflator;


        public RileyLinkCommandListAdapter() {
            super();
            historyItemList = new ArrayList<>();
            mInflator = RileyLinkStatusDevice.this.getLayoutInflater();
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

                if (!historyItemList.contains(item)) {
                    historyItemList.add(item);
                }
            }

            notifyDataSetChanged();
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
            RileyLinkStatusDevice.ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.rileylink_status_device_item, null);
                viewHolder = new RileyLinkStatusDevice.ViewHolder();
                viewHolder.itemTime = (TextView)view.findViewById(R.id.rileylink_history_time);
                viewHolder.itemSource = (TextView)view.findViewById(R.id.rileylink_history_source);
                viewHolder.itemDescription = (TextView)view.findViewById(R.id.rileylink_history_description);
                view.setTag(viewHolder);
            } else {
                viewHolder = (RileyLinkStatusDevice.ViewHolder)view.getTag();
            }

            RLHistoryItem item = historyItemList.get(i);
            viewHolder.itemTime.setText(StringUtil.toDateTimeString(item.getDateTime()));
            viewHolder.itemSource.setText("Riley Link"); // for now
            viewHolder.itemDescription.setText(item.getDescription());

            return view;
        }
    }

}
