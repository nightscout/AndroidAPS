package info.nightscout.androidaps.plugins.ConfigBuilder;


import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.plugins.PluginBase;

/**
 * A simple {@link Fragment} subclass.
 */
public class ConfigBuilderFragment extends Fragment implements PluginBase {
    private static Logger log = LoggerFactory.getLogger(ConfigBuilderFragment.class);

    ListView pumpListView;
    ListView treatmentsListView;
    ListView tempsListView;
    ListView profileListView;
    ListView apsListView;
    ListView generalListView;

    PluginCustomAdapter pumpDataAdapter = null;
    PluginCustomAdapter treatmentsDataAdapter = null;
    PluginCustomAdapter tempsDataAdapter = null;
    PluginCustomAdapter profileDataAdapter = null;
    PluginCustomAdapter apsDataAdapter = null;
    PluginCustomAdapter generalDataAdapter = null;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.configbuilder_fragment, container, false);
        pumpListView = (ListView) view.findViewById(R.id.configbuilder_pumplistview);
        treatmentsListView = (ListView) view.findViewById(R.id.configbuilder_treatmentslistview);
        tempsListView = (ListView) view.findViewById(R.id.configbuilder_tempslistview);
        profileListView = (ListView) view.findViewById(R.id.configbuilder_profilelistview);
        apsListView = (ListView) view.findViewById(R.id.configbuilder_apslistview);
        generalListView = (ListView) view.findViewById(R.id.configbuilder_generallistview);

        //Array list of countries
        ArrayList<PluginBase> pluginList = MainActivity.getPageAdapter().getPluginsList();
        pumpDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainActivity.getPageAdapter().getSpecificPluginsList(PluginBase.PUMP));
        pumpListView.setAdapter(pumpDataAdapter);
        treatmentsDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainActivity.getPageAdapter().getSpecificPluginsList(PluginBase.TREATMENT));
        treatmentsListView.setAdapter(treatmentsDataAdapter);
        tempsDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainActivity.getPageAdapter().getSpecificPluginsList(PluginBase.TEMPBASAL));
        tempsListView.setAdapter(tempsDataAdapter);
        profileDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainActivity.getPageAdapter().getSpecificPluginsList(PluginBase.PROFILE));
        profileListView.setAdapter(profileDataAdapter);
        apsDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainActivity.getPageAdapter().getSpecificPluginsList(PluginBase.APS));
        apsListView.setAdapter(apsDataAdapter);
        generalDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainActivity.getPageAdapter().getSpecificPluginsList(PluginBase.GENERAL));
        generalListView.setAdapter(generalDataAdapter);


        apsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                // When clicked, show a toast with the TextView text
                PluginBase plugin = (PluginBase) parent.getItemAtPosition(position);
                Toast.makeText(MainApp.instance().getApplicationContext(),
                        "Clicked on Row: " + plugin.getName(),
                        Toast.LENGTH_LONG).show();
            }
        });

        return view;
    }

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.configbuilder);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isVisibleInTabs() {
        return true;
    }

    @Override
    public boolean canBeHidden() {
        return false;
    }

    @Override
    public void setFragmentEnabled(boolean fragmentEnabled) {
        // Always enabled
    }

    @Override
    public void setFragmentVisible(boolean fragmentVisible) {
        // Always visible
    }

    public static ConfigBuilderFragment newInstance() {
        ConfigBuilderFragment fragment = new ConfigBuilderFragment();
        return fragment;
    }

    private class PluginCustomAdapter extends ArrayAdapter<PluginBase> {

        private ArrayList<PluginBase> pluginList;

        public PluginCustomAdapter(Context context, int textViewResourceId,
                                   ArrayList<PluginBase> pluginList) {
            super(context, textViewResourceId, pluginList);
            this.pluginList = new ArrayList<PluginBase>();
            this.pluginList.addAll(pluginList);
        }

        private class PluginViewHolder {
            TextView name;
            CheckBox checkboxEnabled;
            CheckBox checkboxVisible;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            PluginViewHolder holder = null;

            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.configbuilder_simpleitem, null);

                holder = new PluginViewHolder();
                holder.name = (TextView) convertView.findViewById(R.id.configbuilder_simpleitem_name);
                holder.checkboxEnabled = (CheckBox) convertView.findViewById(R.id.configbuilder_simpleitem_checkboxenabled);
                holder.checkboxVisible = (CheckBox) convertView.findViewById(R.id.configbuilder_simpleitem_checkboxvisible);
                convertView.setTag(holder);

                holder.checkboxEnabled.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        CheckBox cb = (CheckBox) v;
                        PluginBase plugin = (PluginBase) cb.getTag();
                        Toast.makeText(MainApp.instance().getApplicationContext(),
                                "Clicked on ENABLED: " + plugin.getName() +
                                        " is " + cb.isChecked(),
                                Toast.LENGTH_LONG).show();
                        plugin.setFragmentEnabled(cb.isChecked());
                    }
                });

                holder.checkboxVisible.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        CheckBox cb = (CheckBox) v;
                        PluginBase plugin = (PluginBase) cb.getTag();
                        Toast.makeText(MainApp.instance().getApplicationContext(),
                                "Clicked on VISIBLE: " + plugin.getName() +
                                        " is " + cb.isChecked(),
                                Toast.LENGTH_LONG).show();
                        plugin.setFragmentVisible(cb.isChecked());
                        MainApp.bus().post(new EventRefreshGui());
                    }
                });
            } else {
                holder = (PluginViewHolder) convertView.getTag();
            }

            PluginBase plugin = pluginList.get(position);
            holder.name.setText(plugin.getName());
            holder.checkboxEnabled.setChecked(plugin.isEnabled());
            holder.checkboxVisible.setChecked(plugin.isVisibleInTabs());
            holder.name.setTag(plugin);
            holder.checkboxEnabled.setTag(plugin);
            holder.checkboxVisible.setTag(plugin);

            if (!plugin.canBeHidden()) {
                holder.checkboxEnabled.setEnabled(false);
                holder.checkboxVisible.setEnabled(false);
            }

            return convertView;

        }
    }

    private void displayListView() {

    }
}
