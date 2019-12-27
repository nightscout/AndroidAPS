package info.nightscout.androidaps.activities;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import javax.inject.Inject;

import dagger.android.AndroidInjection;
import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSMA.OpenAPSMAPlugin;
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin;
import info.nightscout.androidaps.plugins.constraints.safety.SafetyPlugin;
import info.nightscout.androidaps.plugins.general.automation.AutomationPlugin;
import info.nightscout.androidaps.plugins.general.careportal.CareportalPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.NSClientPlugin;
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin;
import info.nightscout.androidaps.plugins.general.tidepool.TidepoolPlugin;
import info.nightscout.androidaps.plugins.general.wear.WearPlugin;
import info.nightscout.androidaps.plugins.general.xdripStatusline.StatuslinePlugin;
import info.nightscout.androidaps.plugins.insulin.InsulinOrefFreePeakPlugin;
import info.nightscout.androidaps.plugins.pump.combo.ComboPlugin;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin;
import info.nightscout.androidaps.plugins.pump.danaRv2.DanaRv2Plugin;
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityAAPSPlugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref0Plugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin;
import info.nightscout.androidaps.plugins.sensitivity.SensitivityWeightedAveragePlugin;
import info.nightscout.androidaps.plugins.source.SourceDexcomPlugin;

/**
 * Created by adrian on 2019-12-23.
 */
public class MyPreferenceFragment extends PreferenceFragment implements HasAndroidInjector {
    private Integer id;

    @Inject
    DispatchingAndroidInjector<Object> androidInjector;

    @Inject
    InsulinOrefFreePeakPlugin insulinOrefFreePeakPlugin;

    @Inject
    SmsCommunicatorPlugin smsCommunicatorPlugin;

    @Override
    public void setArguments(Bundle args) {
        super.setArguments(args);
        id = args.getInt("id");
    }

    void addPreferencesFromResourceIfEnabled(PluginBase p, PluginType type) {
        if (p.isEnabled(type) && p.getPreferencesId() != -1)
            addPreferencesFromResource(p.getPreferencesId());
    }

    @Override
    public AndroidInjector<Object> androidInjector() {
        return androidInjector;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        AndroidInjection.inject(this);
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null && savedInstanceState.containsKey("id")) {
            id = savedInstanceState.getInt("id");
        }

        AndroidInjection.inject(this);

        if (id != -1) {
            addPreferencesFromResource(id);
        } else {

            if (!Config.NSCLIENT) {
                addPreferencesFromResource(R.xml.pref_password);
            }
            addPreferencesFromResource(R.xml.pref_general);
            addPreferencesFromResource(R.xml.pref_age);

            addPreferencesFromResource(R.xml.pref_overview);

            addPreferencesFromResourceIfEnabled(SourceDexcomPlugin.INSTANCE, PluginType.BGSOURCE);
            addPreferencesFromResourceIfEnabled(CareportalPlugin.getPlugin(), PluginType.GENERAL);
            addPreferencesFromResourceIfEnabled(SafetyPlugin.getPlugin(), PluginType.CONSTRAINTS);
            if (Config.APS) {
                addPreferencesFromResourceIfEnabled(LoopPlugin.getPlugin(), PluginType.LOOP);
                addPreferencesFromResourceIfEnabled(OpenAPSMAPlugin.getPlugin(), PluginType.APS);
                addPreferencesFromResourceIfEnabled(OpenAPSAMAPlugin.getPlugin(), PluginType.APS);
                addPreferencesFromResourceIfEnabled(OpenAPSSMBPlugin.getPlugin(), PluginType.APS);
            }

            addPreferencesFromResourceIfEnabled(SensitivityAAPSPlugin.getPlugin(), PluginType.SENSITIVITY);
            addPreferencesFromResourceIfEnabled(SensitivityWeightedAveragePlugin.getPlugin(), PluginType.SENSITIVITY);
            addPreferencesFromResourceIfEnabled(SensitivityOref0Plugin.getPlugin(), PluginType.SENSITIVITY);
            addPreferencesFromResourceIfEnabled(SensitivityOref1Plugin.getPlugin(), PluginType.SENSITIVITY);

            if (Config.PUMPDRIVERS) {
                addPreferencesFromResourceIfEnabled(DanaRPlugin.getPlugin(), PluginType.PUMP);
                addPreferencesFromResourceIfEnabled(DanaRKoreanPlugin.getPlugin(), PluginType.PUMP);
                addPreferencesFromResourceIfEnabled(DanaRv2Plugin.getPlugin(), PluginType.PUMP);
                addPreferencesFromResourceIfEnabled(DanaRSPlugin.getPlugin(), PluginType.PUMP);
                addPreferencesFromResourceIfEnabled(LocalInsightPlugin.getPlugin(), PluginType.PUMP);
                addPreferencesFromResourceIfEnabled(ComboPlugin.getPlugin(), PluginType.PUMP);
                addPreferencesFromResourceIfEnabled(MedtronicPumpPlugin.getPlugin(), PluginType.PUMP);

                if (DanaRPlugin.getPlugin().isEnabled(PluginType.PROFILE)
                        || DanaRKoreanPlugin.getPlugin().isEnabled(PluginType.PROFILE)
                        || DanaRv2Plugin.getPlugin().isEnabled(PluginType.PROFILE)
                        || DanaRSPlugin.getPlugin().isEnabled(PluginType.PROFILE)) {
                    addPreferencesFromResource(R.xml.pref_danarprofile);
                }
            }

            if (!Config.NSCLIENT) {
                addPreferencesFromResourceIfEnabled(VirtualPumpPlugin.getPlugin(), PluginType.PUMP);
            }

            addPreferencesFromResourceIfEnabled(insulinOrefFreePeakPlugin, PluginType.INSULIN);

            addPreferencesFromResourceIfEnabled(NSClientPlugin.getPlugin(), PluginType.GENERAL);
            addPreferencesFromResourceIfEnabled(TidepoolPlugin.INSTANCE, PluginType.GENERAL);
            addPreferencesFromResourceIfEnabled(smsCommunicatorPlugin, PluginType.GENERAL);
            addPreferencesFromResourceIfEnabled(AutomationPlugin.INSTANCE, PluginType.GENERAL);

            addPreferencesFromResource(R.xml.pref_others);
            addPreferencesFromResource(R.xml.pref_datachoices);

            addPreferencesFromResourceIfEnabled(WearPlugin.getPlugin(), PluginType.GENERAL);
            addPreferencesFromResourceIfEnabled(StatuslinePlugin.getPlugin(), PluginType.GENERAL);
        }

        PreferencesActivity.initSummary(getPreferenceScreen());

        for (PluginBase plugin : MainApp.getPluginsList()) {
            plugin.preprocessPreferences(this);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("id", id);
    }
}
