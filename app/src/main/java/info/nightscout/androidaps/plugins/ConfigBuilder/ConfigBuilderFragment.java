package info.nightscout.androidaps.plugins.ConfigBuilder;


import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.NSProfile.NSProfilePlugin;
import info.nightscout.androidaps.plugins.VirtualPump.VirtualPumpPlugin;
import info.nightscout.utils.PasswordProtection;


public class ConfigBuilderFragment extends Fragment implements FragmentBase {

    static ConfigBuilderPlugin configBuilderPlugin = new ConfigBuilderPlugin();

    static public ConfigBuilderPlugin getPlugin() {
        return configBuilderPlugin;
    }

    ListView bgsourceListView;
    ListView pumpListView;
    ListView loopListView;
    TextView loopLabel;
    ListView treatmentsListView;
    ListView tempsListView;
    ListView profileListView;
    ListView apsListView;
    TextView apsLabel;
    ListView constraintsListView;
    ListView generalListView;
    TextView nsclientVerView;
    TextView nightscoutVerView;

    PluginCustomAdapter bgsourceDataAdapter = null;
    PluginCustomAdapter pumpDataAdapter = null;
    PluginCustomAdapter loopDataAdapter = null;
    PluginCustomAdapter treatmentsDataAdapter = null;
    PluginCustomAdapter tempsDataAdapter = null;
    PluginCustomAdapter profileDataAdapter = null;
    PluginCustomAdapter apsDataAdapter = null;
    PluginCustomAdapter constraintsDataAdapter = null;
    PluginCustomAdapter generalDataAdapter = null;

    LinearLayout mainLayout;
    Button unlock;

    // TODO: sorting

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.configbuilder_fragment, container, false);
        bgsourceListView = (ListView) view.findViewById(R.id.configbuilder_bgsourcelistview);
        pumpListView = (ListView) view.findViewById(R.id.configbuilder_pumplistview);
        loopListView = (ListView) view.findViewById(R.id.configbuilder_looplistview);
        loopLabel = (TextView) view.findViewById(R.id.configbuilder_looplabel);
        treatmentsListView = (ListView) view.findViewById(R.id.configbuilder_treatmentslistview);
        tempsListView = (ListView) view.findViewById(R.id.configbuilder_tempslistview);
        profileListView = (ListView) view.findViewById(R.id.configbuilder_profilelistview);
        apsListView = (ListView) view.findViewById(R.id.configbuilder_apslistview);
        apsLabel = (TextView) view.findViewById(R.id.configbuilder_apslabel);
        constraintsListView = (ListView) view.findViewById(R.id.configbuilder_constraintslistview);
        generalListView = (ListView) view.findViewById(R.id.configbuilder_generallistview);
        nsclientVerView = (TextView) view.findViewById(R.id.configbuilder_nsclientversion);
        nightscoutVerView = (TextView) view.findViewById(R.id.configbuilder_nightscoutversion);

        nsclientVerView.setText(ConfigBuilderPlugin.nsClientVersionName);
        nightscoutVerView.setText(ConfigBuilderPlugin.nightscoutVersionName);
        if (ConfigBuilderPlugin.nsClientVersionCode < 117) nsclientVerView.setTextColor(Color.RED);
        if (ConfigBuilderPlugin.nightscoutVersionCode < 900)
            nightscoutVerView.setTextColor(Color.RED);
        setViews();

        unlock = (Button) view.findViewById(R.id.configbuilder_unlock);
        mainLayout = (LinearLayout) view.findViewById(R.id.configbuilder_mainlayout);

        if (PasswordProtection.isLocked("settings_password")) {
            mainLayout.setVisibility(View.GONE);
            unlock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PasswordProtection.QueryPassword(getContext(), R.string.settings_password, "settings_password", new Runnable() {
                        @Override
                        public void run() {
                            mainLayout.setVisibility(View.VISIBLE);
                            unlock.setVisibility(View.GONE);
                        }
                    }, null);
                }
            });
        }
        return view;
    }

    void setViews() {
        bgsourceDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsListByInterface(BgSourceInterface.class), PluginBase.BGSOURCE);
        bgsourceListView.setAdapter(bgsourceDataAdapter);
        setListViewHeightBasedOnChildren(bgsourceListView);
        pumpDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsList(PluginBase.PUMP), PluginBase.PUMP);
        pumpListView.setAdapter(pumpDataAdapter);
        setListViewHeightBasedOnChildren(pumpListView);
        loopDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsList(PluginBase.LOOP), PluginBase.LOOP);
        loopListView.setAdapter(loopDataAdapter);
        setListViewHeightBasedOnChildren(loopListView);
        if (MainApp.getSpecificPluginsList(PluginBase.LOOP).size() == 0)
            loopLabel.setVisibility(View.GONE);
        treatmentsDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsList(PluginBase.TREATMENT), PluginBase.TREATMENT);
        treatmentsListView.setAdapter(treatmentsDataAdapter);
        setListViewHeightBasedOnChildren(treatmentsListView);
        tempsDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsList(PluginBase.TEMPBASAL), PluginBase.TEMPBASAL);
        tempsListView.setAdapter(tempsDataAdapter);
        setListViewHeightBasedOnChildren(tempsListView);
        profileDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsListByInterface(ProfileInterface.class), PluginBase.PROFILE);
        profileListView.setAdapter(profileDataAdapter);
        setListViewHeightBasedOnChildren(profileListView);
        apsDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsList(PluginBase.APS), PluginBase.APS);
        apsListView.setAdapter(apsDataAdapter);
        setListViewHeightBasedOnChildren(apsListView);
        if (MainApp.getSpecificPluginsList(PluginBase.APS).size() == 0)
            apsLabel.setVisibility(View.GONE);
        constraintsDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsListByInterface(ConstraintsInterface.class), PluginBase.CONSTRAINTS);
        constraintsListView.setAdapter(constraintsDataAdapter);
        setListViewHeightBasedOnChildren(constraintsListView);
        generalDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsList(PluginBase.GENERAL), PluginBase.GENERAL);
        generalListView.setAdapter(generalDataAdapter);
        setListViewHeightBasedOnChildren(generalListView);

    }

    /*
     * ConfigBuilderFragment code
     */

    private class PluginCustomAdapter extends ArrayAdapter<PluginBase> {

        private ArrayList<PluginBase> pluginList;
        final private int type;

        public PluginCustomAdapter(Context context, int textViewResourceId,
                                   ArrayList<PluginBase> pluginList, int type) {
            super(context, textViewResourceId, pluginList);
            this.pluginList = new ArrayList<PluginBase>();
            this.pluginList.addAll(pluginList);
            this.type = type;
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
                        plugin.setFragmentEnabled(type, cb.isChecked());
                        plugin.setFragmentVisible(type, cb.isChecked());
                        onEnabledCategoryChanged(plugin, type);
                        configBuilderPlugin.storeSettings();
                        MainApp.bus().post(new EventRefreshGui(true));
                        getPlugin().logPluginStatus();
                    }
                });

                holder.checkboxVisible.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        CheckBox cb = (CheckBox) v;
                        PluginBase plugin = (PluginBase) cb.getTag();
                        plugin.setFragmentVisible(type, cb.isChecked());
                        configBuilderPlugin.storeSettings();
                        MainApp.bus().post(new EventRefreshGui(true));
                        getPlugin().logPluginStatus();
                    }
                });
            } else {
                holder = (PluginViewHolder) convertView.getTag();
            }

            PluginBase plugin = pluginList.get(position);
            holder.name.setText(plugin.getName());
            holder.checkboxEnabled.setChecked(plugin.isEnabled(type));
            holder.checkboxVisible.setChecked(plugin.isVisibleInTabs(type));
            holder.name.setTag(plugin);
            holder.checkboxEnabled.setTag(plugin);
            holder.checkboxVisible.setTag(plugin);

            if (!plugin.canBeHidden(type)) {
                holder.checkboxEnabled.setEnabled(false);
                holder.checkboxVisible.setEnabled(false);
            }

            if (!plugin.isEnabled(type)) {
                holder.checkboxVisible.setEnabled(false);
            }

            // Hide enabled control and force enabled plugin if there is only one plugin available
            if (type == PluginBase.PUMP || type == PluginBase.TREATMENT || type == PluginBase.TEMPBASAL || type == PluginBase.PROFILE)
                if (pluginList.size() < 2) {
                    holder.checkboxEnabled.setEnabled(false);
                    plugin.setFragmentEnabled(type, true);
                    getPlugin().storeSettings();
                }

            // Constraints cannot be disabled
            if (type == PluginBase.CONSTRAINTS)
                holder.checkboxEnabled.setEnabled(false);

            // Hide disabled profiles by default
            if (type == PluginBase.PROFILE) {
                if (!plugin.isEnabled(type)) {
                    holder.checkboxVisible.setEnabled(false);
                    holder.checkboxVisible.setChecked(false);
                } else {
                    holder.checkboxVisible.setEnabled(true);
                }
            }

            // Disable profile control for pump profiles if pump is not enabled
            if (type == PluginBase.PROFILE) {
                if (PumpInterface.class.isAssignableFrom(plugin.getClass())) {
                    if (!plugin.isEnabled(PluginBase.PUMP)) {
                        holder.checkboxEnabled.setEnabled(false);
                        holder.checkboxEnabled.setChecked(false);
                    }
                }
            }

            return convertView;

        }

    }

    void onEnabledCategoryChanged(PluginBase changedPlugin, int type) {
        ArrayList<PluginBase> pluginsInCategory = null;
        switch (type) {
            // Multiple selection allowed
            case PluginBase.GENERAL:
            case PluginBase.CONSTRAINTS:
            case PluginBase.LOOP:
                break;
            // Single selection allowed
            case PluginBase.APS:
                pluginsInCategory = MainApp.getSpecificPluginsListByInterface(APSInterface.class);
                break;
            case PluginBase.PROFILE:
                pluginsInCategory = MainApp.getSpecificPluginsListByInterface(ProfileInterface.class);
                break;
            case PluginBase.BGSOURCE:
                pluginsInCategory = MainApp.getSpecificPluginsListByInterface(BgSourceInterface.class);
                break;
            case PluginBase.TEMPBASAL:
            case PluginBase.TREATMENT:
            case PluginBase.PUMP:
                pluginsInCategory = MainApp.getSpecificPluginsListByInterface(PumpInterface.class);
                break;
        }
        if (pluginsInCategory != null) {
            boolean newSelection = changedPlugin.isEnabled(type);
            if (newSelection) { // new plugin selected -> disable others
                for (PluginBase p : pluginsInCategory) {
                    if (p.getName().equals(changedPlugin.getName())) {
                        // this is new selected
                    } else {
                        p.setFragmentEnabled(type, false);
                        p.setFragmentVisible(type, false);
                    }
                }
            } else { // enable first plugin in list
                if (type == PluginBase.PUMP)
                    MainApp.getSpecificPlugin(VirtualPumpPlugin.class).setFragmentEnabled(type, true);
                else if (type == PluginBase.PROFILE)
                    MainApp.getSpecificPlugin(NSProfilePlugin.class).setFragmentEnabled(type, true);
                else
                    pluginsInCategory.get(0).setFragmentEnabled(type, true);
            }
            setViews();
        }
    }

    /****
     * Method for Setting the Height of the ListView dynamically.
     * *** Hack to fix the issue of not showing all the items of the ListView
     * *** when placed inside a ScrollView
     ****/
    public static void setListViewHeightBasedOnChildren(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter == null)
            return;

        int desiredWidth = View.MeasureSpec.makeMeasureSpec(listView.getWidth(), View.MeasureSpec.UNSPECIFIED);
        int totalHeight = 0;
        View view = null;
        for (int i = 0; i < listAdapter.getCount(); i++) {
            view = listAdapter.getView(i, view, listView);
            if (i == 0)
                view.setLayoutParams(new ViewGroup.LayoutParams(desiredWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

            view.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
            totalHeight += view.getMeasuredHeight();
        }
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
        listView.setLayoutParams(params);
    }

}
