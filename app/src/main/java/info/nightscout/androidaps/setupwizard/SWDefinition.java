package info.nightscout.androidaps.setupwizard;

import android.Manifest;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.activities.PreferencesActivity;
import info.nightscout.androidaps.events.EventConfigBuilderChange;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesFragment;
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.general.maintenance.ImportExportPrefs;
import info.nightscout.androidaps.plugins.general.nsclient.NSClientPlugin;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientStatus;
import info.nightscout.androidaps.plugins.general.nsclient.services.NSClientService;
import info.nightscout.androidaps.dialogs.ProfileSwitchDialog;
import info.nightscout.androidaps.plugins.profile.local.LocalProfileFragment;
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin;
import info.nightscout.androidaps.plugins.profile.ns.NSProfileFragment;
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin;
import info.nightscout.androidaps.setupwizard.elements.SWBreak;
import info.nightscout.androidaps.setupwizard.elements.SWButton;
import info.nightscout.androidaps.setupwizard.elements.SWEditNumberWithUnits;
import info.nightscout.androidaps.setupwizard.elements.SWEditString;
import info.nightscout.androidaps.setupwizard.elements.SWEditUrl;
import info.nightscout.androidaps.setupwizard.elements.SWFragment;
import info.nightscout.androidaps.setupwizard.elements.SWHtmlLink;
import info.nightscout.androidaps.setupwizard.elements.SWInfotext;
import info.nightscout.androidaps.setupwizard.elements.SWPlugin;
import info.nightscout.androidaps.setupwizard.elements.SWRadioButton;
import info.nightscout.androidaps.setupwizard.events.EventSWUpdate;
import info.nightscout.androidaps.utils.AndroidPermission;
import info.nightscout.androidaps.utils.LocaleHelper;
import info.nightscout.androidaps.utils.PasswordProtection;
import info.nightscout.androidaps.utils.SP;

import static info.nightscout.androidaps.utils.EspressoTestHelperKt.isRunningTest;

public class SWDefinition {
    private AppCompatActivity activity;
    private List<SWScreen> screens = new ArrayList<>();

    public void setActivity(AppCompatActivity activity) {
        this.activity = activity;
    }

    public AppCompatActivity getActivity() {
        return activity;
    }

    List<SWScreen> getScreens() {
        return screens;
    }

    private SWDefinition add(SWScreen newScreen) {
        if (newScreen != null) screens.add(newScreen);
        return this;
    }

    SWDefinition() {
        if (Config.APS)
            SWDefinitionFull();
        else if (Config.PUMPCONTROL)
            SWDefinitionPumpControl();
        else if (Config.NSCLIENT)
            SWDefinitionNSClient();
    }

    private SWScreen screenSetupWizard = new SWScreen(R.string.nav_setupwizard)
            .add(new SWInfotext()
                    .label(R.string.welcometosetupwizard));

    private SWScreen screenLanguage = new SWScreen(R.string.language)
            .skippable(false)
            .add(new SWRadioButton()
                    .option(R.array.languagesArray, R.array.languagesValues)
                    .preferenceId(R.string.key_language).label(R.string.language)
                    .comment(R.string.setupwizard_language_prompt))
            .validator(() -> {
                LocaleHelper.INSTANCE.update(MainApp.instance().getApplicationContext());
                return SP.contains(R.string.key_language);
            });

    private SWScreen screenEula = new SWScreen(R.string.end_user_license_agreement)
            .skippable(false)
            .add(new SWInfotext()
                    .label(R.string.end_user_license_agreement_text))
            .add(new SWBreak())
            .add(new SWButton()
                    .text(R.string.end_user_license_agreement_i_understand)
                    .visibility(() -> !SP.getBoolean(R.string.key_i_understand, false))
                    .action(() -> {
                        SP.putBoolean(R.string.key_i_understand, true);
                        RxBus.INSTANCE.send(new EventSWUpdate(false));
                    }))
            .visibility(() -> !SP.getBoolean(R.string.key_i_understand, false))
            .validator(() -> SP.getBoolean(R.string.key_i_understand, false));

    private SWScreen screenUnits = new SWScreen(R.string.units)
            .skippable(false)
            .add(new SWRadioButton()
                    .option(R.array.unitsArray, R.array.unitsValues)
                    .preferenceId(R.string.key_units).label(R.string.units)
                    .comment(R.string.setupwizard_units_prompt))
            .validator(() -> SP.contains(R.string.key_units));

    private SWScreen displaySettings = new SWScreen(R.string.wear_display_settings)
            .skippable(false)
            .add(new SWEditNumberWithUnits(Constants.LOWMARK * Constants.MGDL_TO_MMOLL, 3d, 8d)
                    .preferenceId(R.string.key_low_mark)
                    .updateDelay(5)
                    .label(R.string.low_mark)
                    .comment(R.string.low_mark_comment))
            .add(new SWBreak())
            .add(new SWEditNumberWithUnits(Constants.HIGHMARK * Constants.MGDL_TO_MMOLL, 5d, 20d)
                    .preferenceId(R.string.key_high_mark)
                    .updateDelay(5)
                    .label(R.string.high_mark)
                    .comment(R.string.high_mark_comment));

    private SWScreen screenPermissionBattery = new SWScreen(R.string.permission)
            .skippable(false)
            .add(new SWInfotext()
                    .label(String.format(MainApp.gs(R.string.needwhitelisting), MainApp.gs(R.string.app_name))))
            .add(new SWBreak())
            .add(new SWButton()
                    .text(R.string.askforpermission)
                    .visibility(() -> AndroidPermission.permissionNotGranted(getActivity(), Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
                    .action(() -> AndroidPermission.askForPermission(getActivity(), Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, AndroidPermission.CASE_BATTERY)))
            .visibility(() -> AndroidPermission.permissionNotGranted(getActivity(), Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
            .validator(() -> !(AndroidPermission.permissionNotGranted(getActivity(), Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)));

    private SWScreen screenPermissionBt = new SWScreen(R.string.permission)
            .skippable(false)
            .add(new SWInfotext()
                    .label(MainApp.gs(R.string.needlocationpermission)))
            .add(new SWBreak())
            .add(new SWButton()
                    .text(R.string.askforpermission)
                    .visibility(() -> AndroidPermission.permissionNotGranted(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION))
                    .action(() -> AndroidPermission.askForPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION, AndroidPermission.CASE_LOCATION)))
            .visibility(() -> AndroidPermission.permissionNotGranted(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION))
            .validator(() -> !(AndroidPermission.permissionNotGranted(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION)));

    private SWScreen screenPermissionStore = new SWScreen(R.string.permission)
            .skippable(false)
            .add(new SWInfotext()
                    .label(MainApp.gs(R.string.needstoragepermission)))
            .add(new SWBreak())
            .add(new SWButton()
                    .text(R.string.askforpermission)
                    .visibility(() -> AndroidPermission.permissionNotGranted(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    .action(() -> AndroidPermission.askForPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE, AndroidPermission.CASE_STORAGE)))
            .visibility(() -> AndroidPermission.permissionNotGranted(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE))
            .validator(() -> !(AndroidPermission.permissionNotGranted(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)));

    private SWScreen screenImport = new SWScreen(R.string.nav_import)
            .add(new SWInfotext()
                    .label(R.string.storedsettingsfound))
            .add(new SWBreak())
            .add(new SWButton()
                    .text(R.string.nav_import)
                    .action(() -> ImportExportPrefs.importSharedPreferences(getActivity())))
            .visibility(() -> ImportExportPrefs.file.exists() && !(AndroidPermission.permissionNotGranted(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)));

    private SWScreen screenNsClient = new SWScreen(R.string.nsclientinternal_title)
            .skippable(true)
            .add(new SWInfotext()
                    .label(R.string.nsclientinfotext))
            .add(new SWBreak())
            .add(new SWButton()
                    .text(R.string.enable_nsclient)
                    .action(() -> {
                        NSClientPlugin.getPlugin().setPluginEnabled(PluginType.GENERAL, true);
                        NSClientPlugin.getPlugin().setFragmentVisible(PluginType.GENERAL, true);
                        ConfigBuilderPlugin.getPlugin().processOnEnabledCategoryChanged(NSClientPlugin.getPlugin(), PluginType.GENERAL);
                        ConfigBuilderPlugin.getPlugin().storeSettings("SetupWizard");
                        RxBus.INSTANCE.send(new EventConfigBuilderChange());
                        RxBus.INSTANCE.send(new EventSWUpdate(true));
                    })
                    .visibility(() -> !NSClientPlugin.getPlugin().isEnabled(PluginType.GENERAL)))
            .add(new SWEditUrl()
                    .preferenceId(R.string.key_nsclientinternal_url)
                    .updateDelay(5)
                    .label(R.string.nsclientinternal_url_title)
                    .comment(R.string.nsclientinternal_url_dialogmessage))
            .add(new SWEditString()
                    .validator(text -> text.length() >= 12)
                    .preferenceId(R.string.key_nsclientinternal_api_secret)
                    .updateDelay(5)
                    .label(R.string.nsclientinternal_secret_dialogtitle)
                    .comment(R.string.nsclientinternal_secret_dialogmessage))
            .add(new SWBreak())
            .add(new SWEventListener(this, EventNSClientStatus.class)
                    .label(R.string.status)
                    .initialStatus(NSClientPlugin.getPlugin().status)
            )
            .add(new SWBreak())
            .validator(() -> NSClientPlugin.getPlugin().nsClientService != null && NSClientService.isConnected && NSClientService.hasWriteAuth)
            .visibility(() -> !(NSClientPlugin.getPlugin().nsClientService != null && NSClientService.isConnected && NSClientService.hasWriteAuth));

    private SWScreen screenAge = new SWScreen(R.string.patientage)
            .skippable(false)
            .add(new SWBreak())
            .add(new SWRadioButton()
                    .option(R.array.ageArray, R.array.ageValues)
                    .preferenceId(R.string.key_age)
                    .label(R.string.patientage)
                    .comment(R.string.patientage_summary))
            .validator(() -> SP.contains(R.string.key_age));

    private SWScreen screenInsulin = new SWScreen(R.string.configbuilder_insulin)
            .skippable(false)
            .add(new SWPlugin()
                    .option(PluginType.INSULIN, R.string.configbuilder_insulin_description)
                    .makeVisible(false)
                    .label(R.string.configbuilder_insulin))
            .add(new SWBreak())
            .add(new SWInfotext()
                    .label(R.string.diawarning))
            .add(new SWBreak())
            .add(new SWButton()
                    .text(R.string.insulinsourcesetup)
                    .action(() -> {
                        final PluginBase plugin = (PluginBase) ConfigBuilderPlugin.getPlugin().getActiveInsulin();
                        if (plugin != null) {
                            PasswordProtection.QueryPassword(activity, R.string.settings_password, "settings_password", () -> {
                                Intent i = new Intent(activity, PreferencesActivity.class);
                                i.putExtra("id", plugin.getPreferencesId());
                                activity.startActivity(i);
                            }, null);
                        }
                    })
                    .visibility(() -> ConfigBuilderPlugin.getPlugin().getActiveInsulin() != null && ((PluginBase) ConfigBuilderPlugin.getPlugin().getActiveInsulin()).getPreferencesId() > 0))
            .validator(() -> ConfigBuilderPlugin.getPlugin().getActiveInsulin() != null);

    private SWScreen screenBgSource = new SWScreen(R.string.configbuilder_bgsource)
            .skippable(false)
            .add(new SWPlugin()
                    .option(PluginType.BGSOURCE, R.string.configbuilder_bgsource_description)
                    .label(R.string.configbuilder_bgsource))
            .add(new SWBreak())
            .add(new SWButton()
                    .text(R.string.bgsourcesetup)
                    .action(() -> {
                        final PluginBase plugin = (PluginBase) ConfigBuilderPlugin.getPlugin().getActiveBgSource();
                        if (plugin != null) {
                            PasswordProtection.QueryPassword(activity, R.string.settings_password, "settings_password", () -> {
                                Intent i = new Intent(activity, PreferencesActivity.class);
                                i.putExtra("id", plugin.getPreferencesId());
                                activity.startActivity(i);
                            }, null);
                        }
                    })
                    .visibility(() -> ConfigBuilderPlugin.getPlugin().getActiveBgSource() != null && ((PluginBase) ConfigBuilderPlugin.getPlugin().getActiveBgSource()).getPreferencesId() > 0))
            .validator(() -> ConfigBuilderPlugin.getPlugin().getActiveBgSource() != null);

    private SWScreen screenProfile = new SWScreen(R.string.configbuilder_profile)
            .skippable(false)
            .add(new SWInfotext()
                    .label(R.string.setupwizard_profile_description))
            .add(new SWBreak())
            .add(new SWPlugin()
                    .option(PluginType.PROFILE, R.string.configbuilder_profile_description)
                    .label(R.string.configbuilder_profile))
            .validator(() -> ConfigBuilderPlugin.getPlugin().getActiveProfileInterface() != null);

    private SWScreen screenNsProfile = new SWScreen(R.string.nsprofile)
            .skippable(false)
            .add(new SWInfotext()
                    .label(R.string.adjustprofileinns))
            .add(new SWFragment(this)
                    .add(new NSProfileFragment()))
            .validator(() -> NSProfilePlugin.getPlugin().getProfile() != null && NSProfilePlugin.getPlugin().getProfile().getDefaultProfile() != null && NSProfilePlugin.getPlugin().getProfile().getDefaultProfile().isValid("StartupWizard"))
            .visibility(() -> NSProfilePlugin.getPlugin().isEnabled(PluginType.PROFILE));

    private SWScreen screenLocalProfile = new SWScreen(R.string.localprofile)
            .skippable(false)
            .add(new SWFragment(this)
                    .add(new LocalProfileFragment()))
            .validator(() -> LocalProfilePlugin.INSTANCE.getProfile() != null && LocalProfilePlugin.INSTANCE.getProfile().getDefaultProfile() != null && LocalProfilePlugin.INSTANCE.getProfile().getDefaultProfile().isValid("StartupWizard"))
            .visibility(() -> LocalProfilePlugin.INSTANCE.isEnabled(PluginType.PROFILE));

    private SWScreen screenProfileSwitch = new SWScreen(R.string.careportal_profileswitch)
            .skippable(false)
            .add(new SWInfotext()
                    .label(R.string.profileswitch_ismissing))
            .add(new SWButton()
                    .text(R.string.doprofileswitch)
                    .action(() -> {
                        new ProfileSwitchDialog().show(getActivity().getSupportFragmentManager(), "SetupWizard");
                    }))
            .validator(() -> ProfileFunctions.getInstance().getProfile() != null)
            .visibility(() -> ProfileFunctions.getInstance().getProfile() == null);

    private SWScreen screenPump = new SWScreen(R.string.configbuilder_pump)
            .skippable(false)
            .add(new SWPlugin()
                    .option(PluginType.PUMP, R.string.configbuilder_pump_description)
                    .label(R.string.configbuilder_pump))
            .add(new SWBreak())
            .add(new SWButton()
                    .text(R.string.pumpsetup)
                    .action(() -> {
                        final PluginBase plugin = (PluginBase) ConfigBuilderPlugin.getPlugin().getActivePump();
                        if (plugin != null) {
                            PasswordProtection.QueryPassword(activity, R.string.settings_password, "settings_password", () -> {
                                Intent i = new Intent(activity, PreferencesActivity.class);
                                i.putExtra("id", plugin.getPreferencesId());
                                activity.startActivity(i);
                            }, null);
                        }
                    })
                    .visibility(() -> (ConfigBuilderPlugin.getPlugin().getActivePump() != null && ((PluginBase) ConfigBuilderPlugin.getPlugin().getActivePump()).getPreferencesId() > 0)))
            .add(new SWButton()
                    .text(R.string.readstatus)
                    .action(() -> ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("Clicked connect to pump", null))
                    .visibility(() -> ConfigBuilderPlugin.getPlugin().getActivePump() != null))
            .add(new SWEventListener(this, EventPumpStatusChanged.class))
            .validator(() -> ConfigBuilderPlugin.getPlugin().getActivePump() != null && ConfigBuilderPlugin.getPlugin().getActivePump().isInitialized());

    private SWScreen screenAps = new SWScreen(R.string.configbuilder_aps)
            .skippable(false)
            .add(new SWInfotext()
                    .label(R.string.setupwizard_aps_description))
            .add(new SWBreak())
            .add(new SWHtmlLink()
                    .label("https://openaps.readthedocs.io/en/latest/"))
            .add(new SWBreak())
            .add(new SWPlugin()
                    .option(PluginType.APS, R.string.configbuilder_aps_description)
                    .label(R.string.configbuilder_aps))
            .add(new SWButton()
                    .text(R.string.apssetup)
                    .action(() -> {
                        final PluginBase plugin = (PluginBase) ConfigBuilderPlugin.getPlugin().getActiveAPS();
                        if (plugin != null) {
                            PasswordProtection.QueryPassword(activity, R.string.settings_password, "settings_password", () -> {
                                Intent i = new Intent(activity, PreferencesActivity.class);
                                i.putExtra("id", plugin.getPreferencesId());
                                activity.startActivity(i);
                            }, null);
                        }
                    })
                    .visibility(() -> ConfigBuilderPlugin.getPlugin().getActiveAPS() != null && ((PluginBase) ConfigBuilderPlugin.getPlugin().getActiveAPS()).getPreferencesId() > 0))
            .validator(() -> ConfigBuilderPlugin.getPlugin().getActiveAPS() != null)
            .visibility(() -> Config.APS);

    private SWScreen screenApsMode = new SWScreen(R.string.apsmode_title)
            .skippable(false)
            .add(new SWRadioButton()
                    .option(R.array.aps_modeArray, R.array.aps_modeValues)
                    .preferenceId(R.string.key_aps_mode).label(R.string.apsmode_title)
                    .comment(R.string.setupwizard_preferred_aps_mode))
            .validator(() -> SP.contains(R.string.key_aps_mode));

    private SWScreen screenLoop = new SWScreen(R.string.configbuilder_loop)
            .skippable(false)
            .add(new SWInfotext()
                    .label(R.string.setupwizard_loop_description))
            .add(new SWBreak())
            .add(new SWButton()
                    .text(R.string.enableloop)
                    .action(() -> {
                        LoopPlugin.getPlugin().setPluginEnabled(PluginType.LOOP, true);
                        LoopPlugin.getPlugin().setFragmentVisible(PluginType.LOOP, true);
                        ConfigBuilderPlugin.getPlugin().processOnEnabledCategoryChanged(LoopPlugin.getPlugin(), PluginType.LOOP);
                        ConfigBuilderPlugin.getPlugin().storeSettings("SetupWizard");
                        RxBus.INSTANCE.send(new EventConfigBuilderChange());
                        RxBus.INSTANCE.send(new EventSWUpdate(true));
                    })
                    .visibility(() -> !LoopPlugin.getPlugin().isEnabled(PluginType.LOOP)))
            .validator(() -> LoopPlugin.getPlugin().isEnabled(PluginType.LOOP))
            .visibility(() -> !LoopPlugin.getPlugin().isEnabled(PluginType.LOOP) && Config.APS);

    private SWScreen screenSensitivity = new SWScreen(R.string.configbuilder_sensitivity)
            .skippable(false)
            .add(new SWInfotext()
                    .label(R.string.setupwizard_sensitivity_description))
            .add(new SWHtmlLink()
                    .label(R.string.setupwizard_sensitivity_url))
            .add(new SWBreak())
            .add(new SWPlugin()
                    .option(PluginType.SENSITIVITY, R.string.configbuilder_sensitivity_description)
                    .label(R.string.configbuilder_sensitivity))
            .add(new SWBreak())
            .add(new SWButton()
                    .text(R.string.sensitivitysetup)
                    .action(() -> {
                        final PluginBase plugin = (PluginBase) ConfigBuilderPlugin.getPlugin().getActiveSensitivity();
                        if (plugin != null) {
                            PasswordProtection.QueryPassword(activity, R.string.settings_password, "settings_password", () -> {
                                Intent i = new Intent(activity, PreferencesActivity.class);
                                i.putExtra("id", plugin.getPreferencesId());
                                activity.startActivity(i);
                            }, null);
                        }
                    })
                    .visibility(() -> ConfigBuilderPlugin.getPlugin().getActiveSensitivity() != null && ((PluginBase) ConfigBuilderPlugin.getPlugin().getActiveSensitivity()).getPreferencesId() > 0))
            .validator(() -> ConfigBuilderPlugin.getPlugin().getActiveSensitivity() != null);

    private SWScreen getScreenObjectives = new SWScreen(R.string.objectives)
            .skippable(false)
            .add(new SWInfotext()
                    .label(R.string.startobjective))
            .add(new SWBreak())
            .add(new SWFragment(this)
                    .add(new ObjectivesFragment()))
            .validator(() -> ObjectivesPlugin.INSTANCE.getObjectives().get(ObjectivesPlugin.INSTANCE.getFIRST_OBJECTIVE()).isStarted())
            .visibility(() -> !ObjectivesPlugin.INSTANCE.getObjectives().get(ObjectivesPlugin.INSTANCE.getFIRST_OBJECTIVE()).isStarted() && Config.APS);

    private void SWDefinitionFull() {
        // List all the screens here
        add(screenSetupWizard)
                .add(screenLanguage)
                .add(screenEula)
                .add(isRunningTest() ? null : screenPermissionBattery) // cannot mock ask battery optimalization
                .add(screenPermissionBt)
                .add(screenPermissionStore)
                .add(screenImport)
                .add(screenUnits)
                .add(displaySettings)
                .add(screenNsClient)
                .add(screenAge)
                .add(screenInsulin)
                .add(screenBgSource)
                .add(screenProfile)
                .add(screenNsProfile)
                .add(screenLocalProfile)
                .add(screenProfileSwitch)
                .add(screenPump)
                .add(screenAps)
                .add(screenApsMode)
                .add(screenLoop)
                .add(screenSensitivity)
                .add(getScreenObjectives)
        ;
    }

    private void SWDefinitionPumpControl() {
        // List all the screens here
        add(screenSetupWizard)
                .add(screenLanguage)
                .add(screenEula)
                .add(isRunningTest() ? null : screenPermissionBattery) // cannot mock ask battery optimalization
                .add(screenPermissionBt)
                .add(screenPermissionStore)
                .add(screenImport)
                .add(screenUnits)
                .add(displaySettings)
                .add(screenNsClient)
                .add(screenAge)
                .add(screenInsulin)
                .add(screenBgSource)
                .add(screenProfile)
                .add(screenNsProfile)
                .add(screenLocalProfile)
                .add(screenProfileSwitch)
                .add(screenPump)
                .add(screenSensitivity)
        ;
    }

    private void SWDefinitionNSClient() {
        // List all the screens here
        add(screenSetupWizard)
                .add(screenLanguage)
                .add(screenEula)
                .add(isRunningTest() ? null : screenPermissionBattery) // cannot mock ask battery optimalization
                .add(screenPermissionStore)
                .add(screenImport)
                .add(screenUnits)
                .add(displaySettings)
                .add(screenNsClient)
                .add(screenBgSource)
                .add(screenAge)
                .add(screenInsulin)
                .add(screenSensitivity)
        ;
    }
}