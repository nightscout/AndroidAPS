package info.nightscout.androidaps.plugins.ConfigBuilder;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.crashlytics.android.answers.CustomEvent;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.PreferencesActivity;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventConfigBuilderChange;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.interfaces.APSInterface;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.SensitivityInterface;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.Insulin.InsulinOrefRapidActingPlugin;
import info.nightscout.androidaps.plugins.ProfileNS.NSProfilePlugin;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.SensitivityOref0.SensitivityOref0Plugin;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.PasswordProtection;


public class ConfigBuilderFragment extends SubscriberFragment {

    @BindView(R.id.configbuilder_insulinlistview)
    ListView insulinListView;
    @BindView(R.id.configbuilder_sensitivitylistview)
    ListView sensitivityListView;
    @BindView(R.id.configbuilder_bgsourcelistview)
    ListView bgsourceListView;
    @BindView(R.id.configbuilder_bgsourcelabel)
    TextView bgsourceLabel;
    @BindView(R.id.configbuilder_pumplistview)
    ListView pumpListView;
    @BindView(R.id.configbuilder_pumplabel)
    TextView pumpLabel;
    @BindView(R.id.configbuilder_looplistview)
    ListView loopListView;
    @BindView(R.id.configbuilder_looplabel)
    TextView loopLabel;
    @BindView(R.id.configbuilder_treatmentslistview)
    ListView treatmentsListView;
    @BindView(R.id.configbuilder_treatmentslabel)
    TextView treatmentsLabel;
    @BindView(R.id.configbuilder_profilelistview)
    ListView profileListView;
    @BindView(R.id.configbuilder_profilelabel)
    TextView profileLabel;
    @BindView(R.id.configbuilder_apslistview)
    ListView apsListView;
    @BindView(R.id.configbuilder_apslabel)
    TextView apsLabel;
    @BindView(R.id.configbuilder_constraintslistview)
    ListView constraintsListView;
    @BindView(R.id.configbuilder_constraintslabel)
    TextView constraintsLabel;
    @BindView(R.id.configbuilder_generallistview)
    ListView generalListView;

    @BindView(R.id.configbuilder_mainlayout)
    LinearLayout mainLayout;
    @BindView(R.id.configbuilder_unlock)
    Button unlock;

    PluginCustomAdapter insulinDataAdapter = null;
    PluginCustomAdapter sensivityDataAdapter = null;
    PluginCustomAdapter bgsourceDataAdapter = null;
    PluginCustomAdapter pumpDataAdapter = null;
    PluginCustomAdapter loopDataAdapter = null;
    PluginCustomAdapter treatmentDataAdapter = null;
    PluginCustomAdapter profileDataAdapter = null;
    PluginCustomAdapter apsDataAdapter = null;
    PluginCustomAdapter constraintsDataAdapter = null;
    PluginCustomAdapter generalDataAdapter = null;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.configbuilder_fragment, container, false);

            unbinder = ButterKnife.bind(this, view);

            if (PasswordProtection.isLocked("settings_password"))
                mainLayout.setVisibility(View.GONE);
            else
                unlock.setVisibility(View.GONE);
            return view;
        } catch (Exception e) {
            FabricPrivacy.logException(e);
        }

        return null;
    }

    @OnClick(R.id.configbuilder_unlock)
    public void onClickUnlock() {
        PasswordProtection.QueryPassword(getContext(), R.string.settings_password, "settings_password", () -> {
            mainLayout.setVisibility(View.VISIBLE);
            unlock.setVisibility(View.GONE);
        }, null);
    }


    @Override
    protected void updateGUI() {

        insulinDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsVisibleInListByInterface(InsulinInterface.class, PluginType.INSULIN), PluginType.INSULIN);
        insulinListView.setAdapter(insulinDataAdapter);
        setListViewHeightBasedOnChildren(insulinListView);
        bgsourceDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsVisibleInListByInterface(BgSourceInterface.class, PluginType.BGSOURCE), PluginType.BGSOURCE);
        bgsourceListView.setAdapter(bgsourceDataAdapter);
        if (MainApp.getSpecificPluginsVisibleInList(PluginType.BGSOURCE).size() == 0)
            bgsourceLabel.setVisibility(View.GONE);
        setListViewHeightBasedOnChildren(bgsourceListView);
        pumpDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsVisibleInList(PluginType.PUMP), PluginType.PUMP);
        pumpListView.setAdapter(pumpDataAdapter);
        if (MainApp.getSpecificPluginsVisibleInList(PluginType.PUMP).size() == 0 || Config.NSCLIENT || Config.G5UPLOADER) {
            pumpLabel.setVisibility(View.GONE);
            pumpListView.setVisibility(View.GONE);
        }
        setListViewHeightBasedOnChildren(pumpListView);
        loopDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsVisibleInList(PluginType.LOOP), PluginType.LOOP);
        loopListView.setAdapter(loopDataAdapter);
        setListViewHeightBasedOnChildren(loopListView);
        if (MainApp.getSpecificPluginsVisibleInList(PluginType.LOOP).size() == 0)
            loopLabel.setVisibility(View.GONE);
        treatmentDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsVisibleInList(PluginType.TREATMENT), PluginType.TREATMENT);
        treatmentsListView.setAdapter(treatmentDataAdapter);
        setListViewHeightBasedOnChildren(treatmentsListView);
        if (MainApp.getSpecificPluginsVisibleInList(PluginType.TREATMENT).size() == 0)
            treatmentsLabel.setVisibility(View.GONE);
        profileDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsVisibleInListByInterface(ProfileInterface.class, PluginType.PROFILE), PluginType.PROFILE);
        profileListView.setAdapter(profileDataAdapter);
        if (MainApp.getSpecificPluginsVisibleInList(PluginType.PROFILE).size() == 0)
            profileLabel.setVisibility(View.GONE);
        setListViewHeightBasedOnChildren(profileListView);
        apsDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsVisibleInList(PluginType.APS), PluginType.APS);
        apsListView.setAdapter(apsDataAdapter);
        setListViewHeightBasedOnChildren(apsListView);
        if (MainApp.getSpecificPluginsVisibleInList(PluginType.APS).size() == 0)
            apsLabel.setVisibility(View.GONE);
        sensivityDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsVisibleInListByInterface(SensitivityInterface.class, PluginType.SENSITIVITY), PluginType.SENSITIVITY);
        sensitivityListView.setAdapter(sensivityDataAdapter);
        setListViewHeightBasedOnChildren(sensitivityListView);
        constraintsDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsVisibleInListByInterface(ConstraintsInterface.class, PluginType.CONSTRAINTS), PluginType.CONSTRAINTS);
        constraintsListView.setAdapter(constraintsDataAdapter);
        setListViewHeightBasedOnChildren(constraintsListView);
        if (MainApp.getSpecificPluginsVisibleInList(PluginType.CONSTRAINTS).size() == 0)
            constraintsLabel.setVisibility(View.GONE);
        generalDataAdapter = new PluginCustomAdapter(getContext(), R.layout.configbuilder_simpleitem, MainApp.getSpecificPluginsVisibleInList(PluginType.GENERAL), PluginType.GENERAL);
        generalListView.setAdapter(generalDataAdapter);
        setListViewHeightBasedOnChildren(generalListView);
    }

    /*
     * ConfigBuilderFragment code
     */

    private class PluginCustomAdapter extends ArrayAdapter<PluginBase> {

        private ArrayList<PluginBase> pluginList;
        final private PluginType type;

        PluginCustomAdapter(Context context, int textViewResourceId,
                            ArrayList<PluginBase> pluginList, PluginType type) {
            super(context, textViewResourceId, pluginList);
            this.pluginList = new ArrayList<>();
            this.pluginList.addAll(pluginList);
            this.type = type;
        }

        private class PluginViewHolder {
            TextView name;
            CheckBox checkboxEnabled;
            CheckBox checkboxVisible;
            ImageView settings;
        }

        @NonNull
        @Override
        public View getView(int position, View view, @NonNull ViewGroup parent) {

            PluginViewHolder holder;
            PluginBase plugin = pluginList.get(position);

            if (view == null) {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.configbuilder_simpleitem, null);

                holder = new PluginViewHolder();
                holder.name = (TextView) view.findViewById(R.id.configbuilder_simpleitem_name);
                holder.checkboxEnabled = (CheckBox) view.findViewById(R.id.configbuilder_simpleitem_checkboxenabled);
                holder.checkboxVisible = (CheckBox) view.findViewById(R.id.configbuilder_simpleitem_checkboxvisible);
                holder.settings = (ImageView) view.findViewById(R.id.configbuilder_simpleitem_settings);

                if (plugin.isEnabled(type) && plugin.getPreferencesId() != -1)
                    holder.settings.setVisibility(View.VISIBLE);
                else
                    holder.settings.setVisibility(View.INVISIBLE);

                view.setTag(holder);

                holder.checkboxEnabled.setOnClickListener(v -> {
                    CheckBox cb = (CheckBox) v;
                    PluginBase plugin1 = (PluginBase) cb.getTag();
                    plugin1.setPluginEnabled(type, cb.isChecked());
                    plugin1.setFragmentVisible(type, cb.isChecked());
                    onEnabledCategoryChanged(plugin1, type);
                    ConfigBuilderPlugin.getPlugin().storeSettings("CheckedCheckboxEnabled");
                    MainApp.bus().post(new EventRefreshGui());
                    MainApp.bus().post(new EventConfigBuilderChange());
                    ConfigBuilderPlugin.getPlugin().logPluginStatus();
                    FabricPrivacy.getInstance().logCustom(new CustomEvent("ConfigurationChange"));
                });

                holder.checkboxVisible.setOnClickListener(v -> {
                    CheckBox cb = (CheckBox) v;
                    PluginBase plugin12 = (PluginBase) cb.getTag();
                    plugin12.setFragmentVisible(type, cb.isChecked());
                    ConfigBuilderPlugin.getPlugin().storeSettings("CheckedCheckboxVisible");
                    MainApp.bus().post(new EventRefreshGui());
                    ConfigBuilderPlugin.getPlugin().logPluginStatus();
                });

                holder.settings.setOnClickListener(v -> {
                    final PluginBase plugin13 = (PluginBase) v.getTag();
                    PasswordProtection.QueryPassword(getContext(), R.string.settings_password, "settings_password", () -> {
                        Intent i = new Intent(getContext(), PreferencesActivity.class);
                        i.putExtra("id", plugin13.getPreferencesId());
                        startActivity(i);
                    }, null);
                });

                holder.name.setOnLongClickListener(v -> {
                    final PluginBase plugin14 = (PluginBase) v.getTag();
                    PasswordProtection.QueryPassword(getContext(), R.string.settings_password, "settings_password", () -> {
                        Intent i = new Intent(getContext(), PreferencesActivity.class);
                        i.putExtra("id", plugin14.getPreferencesId());
                        startActivity(i);
                    }, null);
                    return false;
                });

            } else {
                holder = (PluginViewHolder) view.getTag();
            }

            holder.name.setText(plugin.getName());
            holder.checkboxEnabled.setChecked(plugin.isEnabled(type));
            holder.checkboxVisible.setChecked(plugin.isFragmentVisible());
            holder.name.setTag(plugin);
            holder.checkboxEnabled.setTag(plugin);
            holder.checkboxVisible.setTag(plugin);
            holder.settings.setTag(plugin);

            if (plugin.pluginDescription.alwaysEnabled) {
                holder.checkboxEnabled.setEnabled(false);
            }

           if (plugin.pluginDescription.alwayVisible) {
                holder.checkboxEnabled.setEnabled(false);
            }

            if (!plugin.isEnabled(type)) {
                holder.checkboxVisible.setEnabled(false);
            }

            if (!plugin.hasFragment()) {
                holder.checkboxVisible.setVisibility(View.INVISIBLE);
            }

            // Hide enabled control and force enabled plugin if there is only one plugin available
            if (type == PluginType.INSULIN || type == PluginType.PUMP || type == PluginType.SENSITIVITY)
                if (pluginList.size() < 2) {
                    holder.checkboxEnabled.setEnabled(false);
                    plugin.setPluginEnabled(type, true);
                    ConfigBuilderPlugin.getPlugin().storeSettings("ForceEnable");
                }

            // Constraints cannot be disabled
            if (type == PluginType.CONSTRAINTS)
                holder.checkboxEnabled.setEnabled(false);

            // Hide disabled profiles by default
            if (type == PluginType.PROFILE) {
                if (!plugin.isEnabled(type)) {
                    holder.checkboxVisible.setEnabled(false);
                    holder.checkboxVisible.setChecked(false);
                } else {
                    holder.checkboxVisible.setEnabled(true);
                }
            }

            // Disable profile control for pump profiles if pump is not enabled
            if (type == PluginType.PROFILE) {
                if (PumpInterface.class.isAssignableFrom(plugin.getClass())) {
                    if (!plugin.isEnabled(PluginType.PUMP)) {
                        holder.checkboxEnabled.setEnabled(false);
                        holder.checkboxEnabled.setChecked(false);
                    }
                }
            }

            if (plugin.isEnabled(type)) {
                view.setBackgroundColor(MainApp.sResources.getColor(R.color.configBuilderSelectedBackground));
            }

            return view;

        }

    }

    void onEnabledCategoryChanged(PluginBase changedPlugin, PluginType type) {
        ArrayList<PluginBase> pluginsInCategory = null;
        switch (type) {
            // Multiple selection allowed
            case GENERAL:
            case CONSTRAINTS:
            case LOOP:
                break;
            // Single selection allowed
            case INSULIN:
                pluginsInCategory = MainApp.getSpecificPluginsListByInterface(InsulinInterface.class);
                break;
            case SENSITIVITY:
                pluginsInCategory = MainApp.getSpecificPluginsListByInterface(SensitivityInterface.class);
                break;
            case APS:
                pluginsInCategory = MainApp.getSpecificPluginsListByInterface(APSInterface.class);
                break;
            case PROFILE:
                pluginsInCategory = MainApp.getSpecificPluginsListByInterface(ProfileInterface.class);
                break;
            case BGSOURCE:
                pluginsInCategory = MainApp.getSpecificPluginsListByInterface(BgSourceInterface.class);
                break;
            case TREATMENT:
            case PUMP:
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
                        p.setPluginEnabled(type, false);
                        p.setFragmentVisible(type, false);
                    }
                }
            } else { // enable first plugin in list
                if (type == PluginType.PUMP)
                    VirtualPumpPlugin.getPlugin().setPluginEnabled(type, true);
                else if (type == PluginType.INSULIN)
                    InsulinOrefRapidActingPlugin.getPlugin().setPluginEnabled(type, true);
                else if (type == PluginType.SENSITIVITY)
                    SensitivityOref0Plugin.getPlugin().setPluginEnabled(type, true);
                else if (type == PluginType.PROFILE)
                    NSProfilePlugin.getPlugin().setPluginEnabled(type, true);
                else
                    pluginsInCategory.get(0).setPluginEnabled(type, true);
            }
            updateGUI();
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
