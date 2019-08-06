package info.nightscout.androidaps.plugins.configBuilder;

import android.content.Intent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.activities.PreferencesActivity;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.utils.PasswordProtection;

public class PluginViewHolder {

    private Unbinder unbinder;
    private PluginType pluginType;
    private PluginBase plugin;
    private ConfigBuilderFragment fragment;

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

    public PluginViewHolder(ConfigBuilderFragment fragment, PluginType pluginType, PluginBase plugin) {
        this.pluginType = pluginType;
        this.plugin = plugin;
        this.fragment = fragment;
        baseView = (LinearLayout) fragment.getLayoutInflater().inflate(R.layout.configbuilder_single_plugin, null);
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
        pluginVisibility.setEnabled(!(plugin.pluginDescription.neverVisible || plugin.pluginDescription.alwaysVisible) && plugin.isEnabled(pluginType));
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
        plugin.switchAllowed(enabledExclusive.getVisibility() == View.VISIBLE ? enabledExclusive.isChecked() : enabledInclusive.isChecked(), fragment.getActivity());
    }

    @OnClick(R.id.plugin_preferences)
    void onPluginPreferencesClicked() {
        PasswordProtection.QueryPassword(fragment.getContext(), R.string.settings_password, "settings_password", () -> {
            Intent i = new Intent(fragment.getContext(), PreferencesActivity.class);
            i.putExtra("id", plugin.getPreferencesId());
            fragment.startActivity(i);
        }, null);
    }

    public void unbind() {
        unbinder.unbind();
    }

    private boolean areMultipleSelectionsAllowed(PluginType type) {
        return type == PluginType.GENERAL || type == PluginType.CONSTRAINTS ||type == PluginType.LOOP;
    }

}
