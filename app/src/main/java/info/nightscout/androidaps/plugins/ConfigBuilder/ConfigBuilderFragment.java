package info.nightscout.androidaps.plugins.ConfigBuilder;


import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
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

import com.crashlytics.android.answers.CustomEvent;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;
import butterknife.Optional;
import butterknife.Unbinder;
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

    private List<PluginView> pluginViews = new ArrayList<>();

    @BindView(R.id.profile_plugins)
    LinearLayout profilePlugins;
    @BindView(R.id.insulin_plugins)
    LinearLayout insulinPlugins;
    @BindView(R.id.bgsource_plugins)
    LinearLayout bgSourcePlugins;
    @BindView(R.id.pump_plugins)
    LinearLayout pumpPlugins;
    @BindView(R.id.sensitivity_plugins)
    LinearLayout sensitivityPlugins;
    @BindView(R.id.aps_plugins)
    LinearLayout apsPlugins;
    @BindView(R.id.loop_plugins)
    LinearLayout loopPlugins;
    @BindView(R.id.constraints_plugins)
    LinearLayout constraintsPlugins;
    @BindView(R.id.treatments_plugins)
    LinearLayout treatmentsPlugins;
    @BindView(R.id.general_plugins)
    LinearLayout generalPlugins;

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
        for (PluginView pluginView : pluginViews) pluginView.unbind();
        pluginViews.clear();
    }

    @Override
    protected void updateGUI() {
        for (PluginView pluginView : pluginViews) pluginView.update();
    }

    private void createViews() {
        createViewsForPlugins(profilePlugins, MainApp.getSpecificPluginsVisibleInListByInterface(ProfileInterface.class, PluginType.PROFILE));
        createViewsForPlugins(insulinPlugins, MainApp.getSpecificPluginsVisibleInListByInterface(InsulinInterface.class, PluginType.INSULIN));
        createViewsForPlugins(bgSourcePlugins, MainApp.getSpecificPluginsVisibleInListByInterface(BgSourceInterface.class, PluginType.BGSOURCE));
        createViewsForPlugins(pumpPlugins, MainApp.getSpecificPluginsVisibleInList(PluginType.PUMP));
        createViewsForPlugins(sensitivityPlugins, MainApp.getSpecificPluginsVisibleInListByInterface(SensitivityInterface.class, PluginType.SENSITIVITY));
        createViewsForPlugins(apsPlugins, MainApp.getSpecificPluginsVisibleInList(PluginType.APS));
        createViewsForPlugins(loopPlugins, MainApp.getSpecificPluginsVisibleInList(PluginType.LOOP));
        createViewsForPlugins(constraintsPlugins, MainApp.getSpecificPluginsVisibleInListByInterface(ConstraintsInterface.class, PluginType.CONSTRAINTS));
        createViewsForPlugins(treatmentsPlugins, MainApp.getSpecificPluginsVisibleInList(PluginType.TREATMENT));
        createViewsForPlugins(generalPlugins, MainApp.getSpecificPluginsVisibleInList(PluginType.GENERAL));
    }

    private void createViewsForPlugins(LinearLayout parent, List<PluginBase> plugins) {
        for (PluginBase plugin: plugins) {
            PluginView pluginView = new PluginView(plugin);
            parent.addView(pluginView.getBaseView());
            pluginViews.add(pluginView);
        }
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

    class PluginView {

        private Unbinder unbinder;
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

        public PluginView(PluginBase plugin) {
            this.plugin = plugin;
            baseView = (LinearLayout) getLayoutInflater().inflate(R.layout.configbuilder_single_plugin, null);
            unbinder = ButterKnife.bind(this, baseView);
            update();
        }

        public LinearLayout getBaseView() {
            return baseView;
        }

        public void update() {
            enabledExclusive.setVisibility(areMultipleSelectionsAllowed(plugin.getType()) ? View.GONE : View.VISIBLE);
            enabledInclusive.setVisibility(areMultipleSelectionsAllowed(plugin.getType()) ? View.VISIBLE : View.GONE);
            enabledExclusive.setChecked(plugin.isEnabled(plugin.getType()));
            enabledInclusive.setChecked(plugin.isEnabled(plugin.getType()));
            enabledInclusive.setEnabled(!plugin.pluginDescription.alwaysEnabled);
            enabledExclusive.setEnabled(!plugin.pluginDescription.alwaysEnabled);
            pluginName.setText(plugin.getName());
            if (plugin.getDescription() == null) pluginDescription.setVisibility(View.GONE);
            else {
                pluginDescription.setVisibility(View.VISIBLE);
                pluginDescription.setText(plugin.getDescription());
            }
            pluginPreferences.setVisibility(plugin.getPreferencesId() == -1 || !plugin.isEnabled(plugin.getType()) ? View.GONE : View.VISIBLE);
            pluginVisibility.setVisibility(plugin.hasFragment() ? View.VISIBLE : View.INVISIBLE);
            pluginVisibility.setEnabled(!(plugin.pluginDescription.neverVisible || plugin.pluginDescription.alwayVisible) && plugin.isEnabled(plugin.getType()));
            pluginVisibility.setChecked(plugin.isFragmentVisible());
        }

        @OnClick(R.id.plugin_visibility)
        void onVisibilityChanged() {
            plugin.setFragmentVisible(plugin.getType(), pluginVisibility.isChecked());
            ConfigBuilderPlugin.getPlugin().storeSettings("CheckedCheckboxVisible");
            MainApp.bus().post(new EventRefreshGui());
            ConfigBuilderPlugin.getPlugin().logPluginStatus();
        }

        @OnClick({R.id.plugin_enabled_exclusive, R.id.plugin_enabled_inclusive})
        void onEnabledChanged() {
            boolean enabled = enabledExclusive.getVisibility() == View.VISIBLE ? enabledExclusive.isChecked() : enabledInclusive.isChecked();
            plugin.setPluginEnabled(plugin.getType(), enabled);
            plugin.setFragmentVisible(plugin.getType(), enabled);
            processOnEnabledCategoryChanged(plugin, plugin.getType());
            updateGUI();
            ConfigBuilderPlugin.getPlugin().storeSettings("CheckedCheckboxEnabled");
            MainApp.bus().post(new EventRefreshGui());
            MainApp.bus().post(new EventConfigBuilderChange());
            ConfigBuilderPlugin.getPlugin().logPluginStatus();
            FabricPrivacy.getInstance().logCustom(new CustomEvent("ConfigurationChange"));
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

    }

}
