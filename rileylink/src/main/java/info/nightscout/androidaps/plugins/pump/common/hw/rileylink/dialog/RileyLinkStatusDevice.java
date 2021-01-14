package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import info.nightscout.androidaps.plugins.pump.common.R;
import info.nightscout.androidaps.plugins.pump.common.dialog.RefreshableInterface;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.CommandValueDefinition;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.CommandValueDefinitionType;
//import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.CommandValueDefinition;
//import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
//import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.CommandValueDefinitionType;
//import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;

/**
 * Created by andy on 5/19/18.
 */

// FIXME needs to be implemented

public class RileyLinkStatusDevice extends Fragment implements RefreshableInterface {

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

        this.listView = getActivity().findViewById(R.id.rileyLinkDeviceList);

        listView.setAdapter(adapter);

        setElements();
    }


    private void setElements() {

    }


    @Override
    public void refreshData() {
        // adapter.addItemsAndClean(RileyLinkUtil.getRileyLinkHistory());
    }

    static class ViewHolder {

        TextView itemDescription;
        Button itemValue;
    }

    private class RileyLinkCommandListAdapter extends BaseAdapter {

        private final List<CommandValueDefinition> commandValueList;
        private Map<CommandValueDefinitionType, CommandValueDefinition> commandValueMap;
        private final LayoutInflater mInflator;


        public RileyLinkCommandListAdapter() {
            super();
            commandValueList = new ArrayList<>();
            mInflator = RileyLinkStatusDevice.this.getLayoutInflater();
        }


        public void addItems(List<CommandValueDefinition> list) {
            commandValueList.addAll(list);

            for (CommandValueDefinition commandValueDefinition : list) {
                commandValueMap.put(commandValueDefinition.definitionType, commandValueDefinition);
            }

            notifyDataSetChanged();
        }


        public CommandValueDefinition getCommandValueItem(int position) {
            return commandValueList.get(position);
        }


        public void clear() {
            commandValueList.clear();
            notifyDataSetChanged();
        }


        @Override
        public int getCount() {
            return commandValueList.size();
        }


        @Override
        public Object getItem(int i) {
            return commandValueList.get(i);
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
                viewHolder.itemDescription = view.findViewById(R.id.rileylink_device_label);
                viewHolder.itemValue = view.findViewById(R.id.rileylink_device_action);
                view.setTag(viewHolder);
            } else {
                viewHolder = (RileyLinkStatusDevice.ViewHolder)view.getTag();
            }
            // Z
            // RLHistoryItem item = historyItemList.get(i);
            // viewHolder.itemTime.setText(StringUtil.toDateTimeString(item.getDateTime()));
            // viewHolder.itemSource.setText("Riley Link"); // for now
            // viewHolder.itemDescription.setText(item.getDescription());

            return view;
        }
    }

}
