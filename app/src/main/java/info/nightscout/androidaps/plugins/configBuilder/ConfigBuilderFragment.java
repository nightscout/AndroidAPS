package info.nightscout.androidaps.plugins.configBuilder;


import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.activities.PreferencesActivity;
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
import info.nightscout.androidaps.plugins.common.SubscriberFragment;
import info.nightscout.androidaps.plugins.insulin.InsulinOrefRapidActingPlugin;
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref0Plugin;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.PasswordProtection;


public class ConfigBuilderFragment extends SubscriberFragment {

    private List<PluginViewHolder> pluginViewHolders = new ArrayList<>();

    @BindView(R.id.categories)
    LinearLayout categories;

    @BindView(R.id.main_layout)
    ScrollView mainLayout;
    @BindView(R.id.unlock)
    Button unlock;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.configbuilder_fragment, container, false);
            unbinder = ButterKnife.bind(this, view);

            if (PasswordProtection.isLocked("settings_password"))
                mainLayout.setVisibility(View.GONE);
            else unlock.setVisibility(View.GONE);

            createViews();

            return view;
        } catch (Exception e) {
            FabricPrivacy.logException(e);
        }

        return null;
    }

    @OnClick(R.id.unlock)
    void onClickUnlock() {
        PasswordProtection.QueryPassword(getContext(), R.string.settings_password, "settings_password", () -> {
            mainLayout.setVisibility(View.VISIBLE);
            unlock.setVisibility(View.GONE);
        }, null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        for (PluginViewHolder pluginViewHolder : pluginViewHolders) pluginViewHolder.unbind();
        pluginViewHolders.clear();
    }

    @Override
    protected void updateGUI() {
        for (PluginViewHolder pluginViewHolder : pluginViewHolders) pluginViewHolder.update();
    }

    private void createViews() {
        createViewsForPlugins(R.string.configbuilder_profile, R.string.configbuilder_profile_description, PluginType.PROFILE, MainApp.getSpecificPluginsVisibleInListByInterface(ProfileInterface.class, PluginType.PROFILE));
        createViewsForPlugins(R.string.configbuilder_insulin, R.string.configbuilder_insulin_description, PluginType.INSULIN, MainApp.getSpecificPluginsVisibleInListByInterface(InsulinInterface.class, PluginType.INSULIN));
        createViewsForPlugins(R.string.configbuilder_bgsource, R.string.configbuilder_bgsource_description, PluginType.BGSOURCE, MainApp.getSpecificPluginsVisibleInListByInterface(BgSourceInterface.class, PluginType.BGSOURCE));
        createViewsForPlugins(R.string.configbuilder_pump, R.string.configbuilder_pump_description, PluginType.PUMP, MainApp.getSpecificPluginsVisibleInList(PluginType.PUMP));
        createViewsForPlugins(R.string.configbuilder_sensitivity, R.string.configbuilder_sensitivity_description, PluginType.SENSITIVITY, MainApp.getSpecificPluginsVisibleInListByInterface(SensitivityInterface.class, PluginType.SENSITIVITY));
        createViewsForPlugins(R.string.configbuilder_aps, R.string.configbuilder_aps_description, PluginType.APS, MainApp.getSpecificPluginsVisibleInList(PluginType.APS));
        createViewsForPlugins(R.string.configbuilder_loop, R.string.configbuilder_loop_description, PluginType.LOOP, MainApp.getSpecificPluginsVisibleInList(PluginType.LOOP));
        createViewsForPlugins(R.string.constraints, R.string.configbuilder_constraints_description, PluginType.CONSTRAINTS, MainApp.getSpecificPluginsVisibleInListByInterface(ConstraintsInterface.class, PluginType.CONSTRAINTS));
        createViewsForPlugins(R.string.configbuilder_treatments, R.string.configbuilder_treatments_description, PluginType.TREATMENT, MainApp.getSpecificPluginsVisibleInList(PluginType.TREATMENT));
        createViewsForPlugins(R.string.configbuilder_general, R.string.configbuilder_general_description, PluginType.GENERAL, MainApp.getSpecificPluginsVisibleInList(PluginType.GENERAL));
    }

    private void createViewsForPlugins(@StringRes int title, @StringRes int description, PluginType pluginType, List<PluginBase> plugins) {
        if (plugins.size() == 0) return;
        LinearLayout parent = (LinearLayout) getLayoutInflater().inflate(R.layout.configbuilder_single_category, null);
        ((TextView) parent.findViewById(R.id.category_title)).setText(MainApp.gs(title));
        ((TextView) parent.findViewById(R.id.category_description)).setText(MainApp.gs(description));
        LinearLayout pluginContainer = parent.findViewById(R.id.category_plugins);
        for (PluginBase plugin: plugins) {
            PluginViewHolder pluginViewHolder = new PluginViewHolder(pluginType, plugin);
            pluginContainer.addView(pluginViewHolder.getBaseView());
            pluginViewHolders.add(pluginViewHolder);
        }
        categories.addView(parent);
    }

    private boolean areMultipleSelectionsAllowed(PluginType type) {
        return type == PluginType.GENERAL || type == PluginType.CONSTRAINTS ||type == PluginType.LOOP;
    }

    public static void processOnEnabledCategoryChanged(PluginBase changedPlugin, PluginType type) {
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
        }
    }

    public class PluginViewHolder {

        private Unbinder unbinder;
        private PluginType pluginType;
        private PluginBase plugin;

        LinearLayout baseView;
        @BindView(R.id.plugin_enabled_exclusive)
        RadioButton enabledExclusive;
        @BindView(R.id.plugin_enabled_inclusive)
        CheckBox enabledInclusive;
        @BindView(R.id.plugin_name)
        TextView pluginName;
        @BindView(R.id.plugin_description)
        TextView pluginDescription;
        @BindView(R.id.plugin_preferences)
        ImageButton pluginPreferences;
        @BindView(R.id.plugin_visibility)
        CheckBox pluginVisibility;

        public PluginViewHolder(PluginType pluginType, PluginBase plugin) {
            this.pluginType = pluginType;
            this.plugin = plugin;
            baseView = (LinearLayout) getLayoutInflater().inflate(R.layout.configbuilder_single_plugin, null);
            unbinder = ButterKnife.bind(this, baseView);
            update();
        }

        public LinearLayout getBaseView() {
            return baseView;
        }

        public void update() {
            enabledExclusive.setVisibility(areMultipleSelectionsAllowed(pluginType) ? View.GONE : View.VISIBLE);
            enabledInclusive.setVisibility(areMultipleSelectionsAllowed(pluginType) ? View.VISIBLE : View.GONE);
            enabledExclusive.setChecked(plugin.isEnabled(pluginType));
            enabledInclusive.setChecked(plugin.isEnabled(pluginType));
            enabledInclusive.setEnabled(!plugin.pluginDescription.alwaysEnabled);
            enabledExclusive.setEnabled(!plugin.pluginDescription.alwaysEnabled);
            pluginName.setText(plugin.getName());
            if (plugin.getDescription() == null) pluginDescription.setVisibility(View.GONE);
            else {
                pluginDescription.setVisibility(View.VISIBLE);
                pluginDescription.setText(plugin.getDescription());
            }
            pluginPreferences.setVisibility(plugin.getPreferencesId() == -1 || !plugin.isEnabled(pluginType) ? View.INVISIBLE : View.VISIBLE);
            pluginVisibility.setVisibility(plugin.hasFragment() ? View.VISIBLE : View.INVISIBLE);
            pluginVisibility.setEnabled(!(plugin.pluginDescription.neverVisible || plugin.pluginDescription.alwayVisible) && plugin.isEnabled(pluginType));
            pluginVisibility.setChecked(plugin.isFragmentVisible());
        }

        @OnClick(R.id.plugin_visibility)
        void onVisibilityChanged() {
            plugin.setFragmentVisible(pluginType, pluginVisibility.isChecked());
            ConfigBuilderPlugin.getPlugin().storeSettings("CheckedCheckboxVisible");
            MainApp.bus().post(new EventRefreshGui());
            ConfigBuilderPlugin.getPlugin().logPluginStatus();
        }

        @OnClick({R.id.plugin_enabled_exclusive, R.id.plugin_enabled_inclusive})
        void onEnabledChanged() {
            plugin.switchAllowed(new PluginSwitcher(), getActivity());
        }

        @OnClick(R.id.plugin_preferences)
        void onPluginPreferencesClicked() {
            PasswordProtection.QueryPassword(getContext(), R.string.settings_password, "settings_password", () -> {
                Intent i = new Intent(getContext(), PreferencesActivity.class);
                i.putExtra("id", plugin.getPreferencesId());
                startActivity(i);
            }, null);
        }

        public void unbind() {
            unbinder.unbind();
        }

        public class PluginSwitcher {
            public void invoke() {
                boolean enabled = enabledExclusive.getVisibility() == View.VISIBLE ? enabledExclusive.isChecked() : enabledInclusive.isChecked();
                plugin.setPluginEnabled(pluginType, enabled);
                plugin.setFragmentVisible(pluginType, enabled);
                processOnEnabledCategoryChanged(plugin, pluginType);
                updateGUI();
                ConfigBuilderPlugin.getPlugin().storeSettings("CheckedCheckboxEnabled");
                MainApp.bus().post(new EventRefreshGui());
                MainApp.bus().post(new EventConfigBuilderChange());
                ConfigBuilderPlugin.getPlugin().logPluginStatus();
            }

            public void cancel(){
                updateGUI();
            }
        }
    }
}
