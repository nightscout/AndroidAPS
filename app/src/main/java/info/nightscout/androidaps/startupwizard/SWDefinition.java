package info.nightscout.androidaps.startupwizard;

import android.content.Context;
import android.content.Intent;
import android.widget.RadioButton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.interfaces.BgSourceInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.NSClientPlugin;
import info.nightscout.androidaps.plugins.Source.SourceNSClientPlugin;
import info.nightscout.utils.LocaleHelper;
import info.nightscout.utils.SP;

public class SWDefinition {
    private static Logger log = LoggerFactory.getLogger(SWDefinition.class);
    private static SWDefinition swDefinition = null;
    public static SWDefinition getInstance() {
        if (swDefinition == null)
            swDefinition = new SWDefinition();
        return swDefinition;
    }
    android.content.Context context = MainApp.instance().getApplicationContext();

    static List<SWScreen> screens = new ArrayList<>();

    public static List<SWScreen> getScreens() {
        return screens;
    }

    SWDefinition add(SWScreen newScreen) {
        screens.add(newScreen);
        return this;
    }



    SWDefinition() {
        // List all the screens here
        // todo: SWValidator ?!?
        add(new SWScreen(R.string.language)
        .skippable(false)
        .add(new SWRadioButton().option(R.array.languagesArray, R.array.languagesValues).preferenceId(R.string.key_language).label(R.string.language).comment(R.string.setupwizard_language_prompt))
        .validator(() -> {
            LocaleHelper.setLocale(context, SP.getString(R.string.key_language, "en"));
            MainApp.bus().post(new EventRefreshGui(true));
            if(!SP.getString(R.string.key_language, "en").equals(LocaleHelper.getLanguage(context))) {
                Intent intent = new Intent(MainApp.instance().getApplicationContext(), SetupWizardActivity.class);
                intent.putExtra("WIZZARDPAGE", 0);
                SetupWizardActivity.instance().finish();
                SetupWizardActivity.instance().startActivity(intent);
            }
            return SP.contains(R.string.key_language);}
        ))
        .add(new SWScreen(R.string.nsclientinternal_title)
                .skippable(true)
                .add(new SWUrl().preferenceId(R.string.key_nsclientinternal_url).label(R.string.nsclientinternal_url_title).comment(R.string.nsclientinternal_url_dialogmessage))
                .add(new SWString().preferenceId(R.string.key_nsclientinternal_api_secret).label(R.string.nsclientinternal_secret_dialogtitle).comment(R.string.nsclientinternal_secret_dialogmessage))
                .validator(() -> {
                    // Check for the correct locale set and restart if not
                    log.debug("key is: "+SP.getString(R.string.key_language,"unset")+" Locale is "+LocaleHelper.getLanguage(context));
                    if(!SP.getString(R.string.key_language, "en").equals(LocaleHelper.getLanguage(context))) {
                        LocaleHelper.setLocale(context, SP.getString(R.string.key_language, "en"));
                        MainApp.bus().post(new EventRefreshGui(true));
                        Intent intent = new Intent(MainApp.instance().getApplicationContext(), SetupWizardActivity.class);
                        intent.putExtra("WIZZARDPAGE", 0);
                        SetupWizardActivity.instance().finish();
                        SetupWizardActivity.instance().startActivity(intent);
                    }
                    return NSClientPlugin.getPlugin().nsClientService.isConnected && NSClientPlugin.getPlugin().nsClientService.hasWriteAuth;
                })
        )
        .add(new SWScreen(R.string.patientage)
                .skippable(false)
                .add(new SWRadioButton().option(R.array.ageArray, R.array.ageValues).preferenceId(R.string.key_age).label(R.string.patientage).comment(R.string.patientage_summary))
                .validator(() -> {
                    return SP.contains(R.string.key_age);
                })
        )
        .add(new SWScreen(R.string.configbuilder_insulin)
                .skippable(false)
                .add(new SWRadioButton().option(R.array.insulinArray, R.array.insulinValues).preferenceId(R.string.configbuilder_insulin).label(R.string.configbuilder_insulin).comment(R.string.configbuilder_insulin))
                .validator(() -> {
                    ArrayList<PluginBase> pluginsInCategory = MainApp.getSpecificPluginsList(PluginType.INSULIN);
                    ConfigBuilderPlugin configBuilder = ConfigBuilderPlugin.getPlugin();
                    BgSourceInterface activeSource = configBuilder.getActiveBgSource();
                    if (activeSource == null) {
                        activeSource = SourceNSClientPlugin.getPlugin();
                        configBuilder.setPluginEnabled(PluginType.INSULIN, true);
                    }
                    // new plugin selected -> disable others
                    for (PluginBase p : pluginsInCategory) {
                        log.debug("Name is: "+ p.getClass().getSimpleName() + " setting is: "+SP.getString(R.string.configbuilder_insulin, "InsulinOrefRapidActingPlugin"));
                        if (p.getClass().getSimpleName().equals(SP.getString(R.string.configbuilder_insulin, "InsulinOrefRapidActingPlugin"))) {
                            // this is new selected
                            p.setPluginEnabled(PluginType.INSULIN, true);
                            p.setFragmentVisible(PluginType.INSULIN, true);
                            String settingVisible = "ConfigBuilder_" + PluginType.INSULIN.name() + "_" + p.getClass().getSimpleName() + "_Visible";
                            String settingEnabled = "ConfigBuilder_" + PluginType.INSULIN.name() + "_" + p.getClass().getSimpleName() + "_Enabled";
                            log.debug("Setting to: "+settingEnabled);
                            SP.putBoolean(settingEnabled, true);
                            SP.putBoolean(settingVisible, true);
                        } else {
                            String settingEnabled = "ConfigBuilder_" + PluginType.INSULIN.name() + "_" + p.getName() + "_Enabled";
                            log.debug("Disable: "+settingEnabled);
                            p.setPluginEnabled(PluginType.INSULIN, false);
                            p.setFragmentVisible(PluginType.INSULIN, false);
                        }
                    }

                    String settingVisible = "ConfigBuilder_" + PluginType.INSULIN.name() + "_" + SP.getString(R.string.configbuilder_insulin,"InsulinOrefRapidActingPlugin") + "_Visible";
                    SP.putBoolean(settingVisible, true);
                    return SP.contains(R.string.configbuilder_insulin);
                })
            )
        .add(new SWScreen(R.string.configbuilder_aps)
                .skippable(false)
                .add(new SWRadioButton().option(R.array.apsArray, R.array.apsValues).preferenceId(R.string.configbuilder_aps).label(R.string.configbuilder_aps).comment(R.string.configbuilder_aps))
                .validator(() -> {
                    ArrayList<PluginBase> pluginsInCategory = MainApp.getSpecificPluginsList(PluginType.APS);
                    ConfigBuilderPlugin configBuilder = ConfigBuilderPlugin.getPlugin();
                    BgSourceInterface activeSource = configBuilder.getActiveBgSource();
                    if (activeSource == null) {
                        activeSource = SourceNSClientPlugin.getPlugin();
                        configBuilder.setPluginEnabled(PluginType.APS, true);
                    }
                    // new plugin selected -> disable others
                    for (PluginBase p : pluginsInCategory) {
                        log.debug("Name is: "+ p.getClass().getSimpleName() + " setting is: "+SP.getString(R.string.configbuilder_aps, "OpenAPSMA"));
                        if (p.getClass().getSimpleName().equals(SP.getString(R.string.configbuilder_aps, "OpenAPSMA"))) {
                            // this is new selected
                            p.setPluginEnabled(PluginType.APS, true);
                            p.setFragmentVisible(PluginType.APS, true);
                            String settingVisible = "ConfigBuilder_" + PluginType.APS.name() + "_" + p.getClass().getSimpleName() + "_Visible";
                            String settingEnabled = "ConfigBuilder_" + PluginType.APS.name() + "_" + p.getClass().getSimpleName() + "_Enabled";
                            log.debug("Setting to: "+settingEnabled);
                            SP.putBoolean(settingEnabled, true);
                            SP.putBoolean(settingVisible, true);
                        } else {
                            String settingEnabled = "ConfigBuilder_" + PluginType.APS.name() + "_" + p.getName() + "_Enabled";
                            log.debug("Disable: "+settingEnabled);
                            p.setPluginEnabled(PluginType.APS, false);
                            p.setFragmentVisible(PluginType.APS, false);
                        }
                    }

                    String settingVisible = "ConfigBuilder_" + PluginType.APS.name() + "_" + SP.getString(R.string.configbuilder_aps,"OpenAPSMA") + "_Visible";
                    SP.putBoolean(settingVisible, true);
                    return SP.contains(settingVisible);
                })
        )
        .add(new SWScreen(R.string.configbuilder_bgsource)
                .skippable(false)
                .add(new SWRadioButton().option(R.array.BGSourceArray, R.array.BGSourceValues).preferenceId(R.string.configbuilder_bgsource).label(R.string.configbuilder_bgsource).comment(R.string.configbuilder_bgsource))
                .validator(() -> {
                    ArrayList<PluginBase> pluginsInCategory = MainApp.getSpecificPluginsList(PluginType.BGSOURCE);
                    ConfigBuilderPlugin configBuilder = ConfigBuilderPlugin.getPlugin();
                    BgSourceInterface activeSource = configBuilder.getActiveBgSource();
                    if (activeSource == null) {
                        activeSource = SourceNSClientPlugin.getPlugin();
                        configBuilder.setPluginEnabled(PluginType.BGSOURCE, true);
                    }
                    // new plugin selected -> disable others
                    for (PluginBase p : pluginsInCategory) {
                        log.debug("Name is: "+ p.getClass().getSimpleName() + " setting is: "+SP.getString(R.string.configbuilder_bgsource, "SourceNSClient"));
                        if (p.getClass().getSimpleName().equals(SP.getString(R.string.configbuilder_bgsource, "SourceNSClient"))) {
                            // this is new selected
                            p.setPluginEnabled(PluginType.BGSOURCE, true);
                            p.setFragmentVisible(PluginType.BGSOURCE, true);
                            String settingVisible = "ConfigBuilder_" + PluginType.BGSOURCE.name() + "_" + p.getClass().getSimpleName() + "_Visible";
                            String settingEnabled = "ConfigBuilder_" + PluginType.BGSOURCE.name() + "_" + p.getClass().getSimpleName() + "_Enabled";
                            log.debug("Setting to: "+settingEnabled);
                            SP.putBoolean(settingEnabled, true);
                            SP.putBoolean(settingVisible, true);
                        } else {
                            String settingEnabled = "ConfigBuilder_" + PluginType.BGSOURCE.name() + "_" + p.getName() + "_Enabled";
                            log.debug("Disable: "+settingEnabled);
                            p.setPluginEnabled(PluginType.BGSOURCE, false);
                            p.setFragmentVisible(PluginType.BGSOURCE, false);
                        }
                    }

                    String settingVisible = "ConfigBuilder_" + PluginType.BGSOURCE.name() + "_" + SP.getString(R.string.configbuilder_bgsource,"SourceNSClientPlugin") + "_Visible";
                    SP.putBoolean(settingVisible, true);
                    return SP.contains(R.string.configbuilder_bgsource);
                })
        )
        ;
    }

}
