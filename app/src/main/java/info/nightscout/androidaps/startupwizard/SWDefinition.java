package info.nightscout.androidaps.startupwizard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventConfigBuilderChange;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.NSClientPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.androidaps.startupwizard.events.EventSWUpdate;
import info.nightscout.utils.LocaleHelper;
import info.nightscout.utils.SP;
//Needed for pump validation

public class SWDefinition {
    private static Logger log = LoggerFactory.getLogger(SWDefinition.class);
    private static SWDefinition swDefinition = null;

    public static SWDefinition getInstance() {
        if (swDefinition == null)
            swDefinition = new SWDefinition();
        return swDefinition;
    }

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
        add(new SWScreen(R.string.language)
                .skippable(false)
                .add(new SWRadioButton()
                        .option(R.array.languagesArray, R.array.languagesValues)
                        .preferenceId(R.string.key_language).label(R.string.language)
                        .comment(R.string.setupwizard_language_prompt))
                .validator(() -> {
                    String lang = SP.getString("language", "en");
                    LocaleHelper.setLocale(MainApp.instance().getApplicationContext(), lang);
                    return SP.contains(R.string.key_language);
                })
        )
        .add(new SWScreen(R.string.nsclientinternal_title)
                .skippable(true)
                .add(new SWUrl()
                        .preferenceId(R.string.key_nsclientinternal_url)
                        .label(R.string.nsclientinternal_url_title)
                        .comment(R.string.nsclientinternal_url_dialogmessage))
                .add(new SWString()
                        .preferenceId(R.string.key_nsclientinternal_api_secret)
                        .label(R.string.nsclientinternal_secret_dialogtitle)
                        .comment(R.string.nsclientinternal_secret_dialogmessage))
                .add(new SWButton()
                        .text(R.string.enable_nsclient)
                        .action(() -> {
                            NSClientPlugin.getPlugin().setPluginEnabled(PluginType.GENERAL, true);
                            NSClientPlugin.getPlugin().setFragmentVisible(PluginType.GENERAL, true);
                            ConfigBuilderFragment.processOnEnabledCategoryChanged(NSClientPlugin.getPlugin(), PluginType.GENERAL);
                            ConfigBuilderPlugin.getPlugin().storeSettings("SetupWizard");
                            MainApp.bus().post(new EventConfigBuilderChange());
                            MainApp.bus().post(new EventSWUpdate(true));
                        })
                        .visibility(() -> !NSClientPlugin.getPlugin().isEnabled(PluginType.GENERAL)))
                .validator(() -> NSClientPlugin.getPlugin().nsClientService != null && NSClientPlugin.getPlugin().nsClientService.isConnected && NSClientPlugin.getPlugin().nsClientService.hasWriteAuth)
        )
        .add(new SWScreen(R.string.configbuilder_pump)
                .skippable(true)

                .add(new SWPlugin()
                        .option(PluginType.PUMP)
                        .label(R.string.configbuilder_pump)
                        .comment(R.string.configbuilder_pump))
                .validator(() -> MainApp.getSpecificPluginsList(PluginType.PUMP) != null)
        )
        .add(new SWScreen(R.string.setupwizard_pump_test)
                    .skippable(true)
                // Adding DanaR specific fields
                    .add(DanaRPlugin.getPlugin().isEnabled(PluginType.PUMP)?new SWString()
                            .preferenceId(R.string.key_danar_bt_name)
                            .label(R.string.danar_bt_name_title)
                            .comment(R.string.danar_bt_name_title) : new SWItem(SWItem.Type.STRING)
                    )
                    .add(DanaRPlugin.getPlugin().isEnabled(PluginType.PUMP)?new SWString()
                        .preferenceId(R.string.key_danar_password)
                        .label(R.string.danar_password_title)
                        .comment(R.string.danar_password_title): new SWItem(SWItem.Type.STRING)
                    )
                // Virtual pump
                    .add(VirtualPumpPlugin.getPlugin().isEnabled(PluginType.PUMP)?
                            new SWRadioButton()
                                .option(R.array.trueFalseArray, R.array.trueFalseValues)
                                .preferenceId(R.string.key_virtualpump_uploadstatus)
                                .label(R.string.virtualpump_uploadstatus_title)
                                .comment(R.string.virtualpump_uploadstatus_title): new SWItem(SWItem.Type.STRING)
                    )
                .validator(() -> MainApp.getSpecificPluginsList(PluginType.PUMP) != null)
            )

        .add(new SWScreen(R.string.patientage)
                .skippable(false)
                .add(new SWRadioButton()
                        .option(R.array.ageArray, R.array.ageValues)
                        .preferenceId(R.string.key_age)
                        .label(R.string.patientage)
                        .comment(R.string.patientage_summary))
                .validator(() -> SP.contains(R.string.key_age))
        )
        .add(new SWScreen(R.string.configbuilder_insulin)
                .skippable(false)
                .add(new SWPlugin()
                        .option(PluginType.INSULIN)
                        .label(R.string.configbuilder_insulin)
                        .comment(R.string.configbuilder_insulin))
                .validator(() -> MainApp.getSpecificPluginsList(PluginType.INSULIN) != null)
        )
        .add(new SWScreen(R.string.configbuilder_bgsource)
                .skippable(false)
                .add(new SWPlugin()
                        .option(PluginType.BGSOURCE)
                        .label(R.string.configbuilder_bgsource)
                        .comment(R.string.configbuilder_bgsource))
                .validator(() -> MainApp.getSpecificPluginsList(PluginType.BGSOURCE) != null)
        )
        .add(new SWScreen(R.string.configbuilder_aps)
                .skippable(false)
                .add(new SWPlugin()
                        .option(PluginType.APS)
                        .label(R.string.configbuilder_aps)
                        .comment(R.string.configbuilder_aps))
                .validator(() -> MainApp.getSpecificPluginsList(PluginType.APS) != null)
        )
        ;
    }

}
