package info.nightscout.androidaps.setupwizard;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.PreferencesActivity;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventConfigBuilderChange;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.Careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.ConstraintsObjectives.ObjectivesFragment;
import info.nightscout.androidaps.plugins.ConstraintsObjectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.NSClientPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.events.EventNSClientStatus;
import info.nightscout.androidaps.plugins.ProfileLocal.LocalProfileFragment;
import info.nightscout.androidaps.plugins.ProfileLocal.LocalProfilePlugin;
import info.nightscout.androidaps.plugins.ProfileNS.NSProfileFragment;
import info.nightscout.androidaps.plugins.ProfileNS.NSProfilePlugin;
import info.nightscout.androidaps.plugins.ProfileSimple.SimpleProfileFragment;
import info.nightscout.androidaps.plugins.ProfileSimple.SimpleProfilePlugin;
import info.nightscout.androidaps.setupwizard.elements.SWBreak;
import info.nightscout.androidaps.setupwizard.elements.SWButton;
import info.nightscout.androidaps.setupwizard.elements.SWFragment;
import info.nightscout.androidaps.setupwizard.elements.SWHtmlLink;
import info.nightscout.androidaps.setupwizard.elements.SWInfotext;
import info.nightscout.androidaps.setupwizard.elements.SWPlugin;
import info.nightscout.androidaps.setupwizard.elements.SWRadioButton;
import info.nightscout.androidaps.setupwizard.elements.SWEditString;
import info.nightscout.androidaps.setupwizard.elements.SWEditUrl;
import info.nightscout.androidaps.setupwizard.events.EventSWLabel;
import info.nightscout.androidaps.setupwizard.events.EventSWUpdate;
import info.nightscout.utils.AndroidPermission;
import info.nightscout.utils.ImportExportPrefs;
import info.nightscout.utils.LocaleHelper;
import info.nightscout.utils.PasswordProtection;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

public class SWDefinition {
    private static Logger log = LoggerFactory.getLogger(SWDefinition.class);

    private String packageName;

    private AppCompatActivity activity;
    private List<SWScreen> screens = new ArrayList<>();

    public void setActivity(AppCompatActivity activity) {
        this.activity = activity;
        packageName = activity.getPackageName();
    }

    public AppCompatActivity getActivity() {
        return activity;
    }

    public List<SWScreen> getScreens() {
        return screens;
    }

    SWDefinition add(SWScreen newScreen) {
        screens.add(newScreen);
        return this;
    }

    SWDefinition() {
        if (BuildConfig.FLAVOR.equals("full"))
            SWDefinitionFull();
        else if (BuildConfig.FLAVOR.equals("nsclient"))
            SWDefinitionNSClient();
    }

    private void SWDefinitionFull() {
        // List all the screens here
        add(new SWScreen(R.string.nav_setupwizard)
                .add(new SWInfotext()
                        .label(R.string.welcometosetupwizard))
                .add(new SWButton()
                        .text(R.string.nav_import)
                        .action(() -> ImportExportPrefs.importSharedPreferences(getActivity()))
                        .visibility(ImportExportPrefs.file::exists))
                .add(new SWInfotext()
                        .label(R.string.backupismissing)
                        .visibility(() -> !ImportExportPrefs.file.exists()))
        )
        .add(new SWScreen(R.string.language)
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
        .add(new SWScreen(R.string.end_user_license_agreement)
                .skippable(false)
                .add(new SWInfotext()
                        .label(R.string.end_user_license_agreement_text))
                .add(new SWBreak())
                .add(new SWButton()
                        .text(R.string.end_user_license_agreement_i_understand)
                        .visibility(() -> !SP.getBoolean(R.string.key_i_understand, false))
                        .action(() -> {
                            SP.putBoolean(R.string.key_i_understand, true);
                            MainApp.bus().post(new EventSWUpdate(false));
                        }))
                .visibility(() -> !SP.getBoolean(R.string.key_i_understand, false))
                .validator(() -> SP.getBoolean(R.string.key_i_understand, false))
        )
        .add(new SWScreen(R.string.permission)
                .skippable(false)
                .add(new SWInfotext()
                        .label(String.format(MainApp.gs(R.string.needwhitelisting), MainApp.gs(R.string.app_name))))
                .add(new SWBreak())
                .add(new SWButton()
                        .text(R.string.askforpermission)
                        .visibility(() -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AndroidPermission.checkForPermission(getActivity(), Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
                        .action(() -> AndroidPermission.askForPermission(getActivity(), Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, AndroidPermission.CASE_BATTERY)))
                .visibility(() -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AndroidPermission.checkForPermission(getActivity(), Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
                .validator(() -> !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AndroidPermission.checkForPermission(getActivity(), Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)))
        )
        .add(new SWScreen(R.string.permission)
                .skippable(false)
                .add(new SWInfotext()
                        .label(MainApp.gs(R.string.needlocationpermission)))
                .add(new SWBreak())
                .add(new SWButton()
                        .text(R.string.askforpermission)
                        .visibility(() -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AndroidPermission.checkForPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION))
                        .action(() -> AndroidPermission.askForPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION, AndroidPermission.CASE_LOCATION)))
                .visibility(() -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AndroidPermission.checkForPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION))
                .validator(() -> !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AndroidPermission.checkForPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION)))
        )
        .add(new SWScreen(R.string.permission)
                .skippable(false)
                .add(new SWInfotext()
                        .label(MainApp.gs(R.string.needstoragepermission)))
                .add(new SWBreak())
                .add(new SWButton()
                        .text(R.string.askforpermission)
                        .visibility(() -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AndroidPermission.checkForPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE))
                        .action(() -> AndroidPermission.askForPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE, AndroidPermission.CASE_STORAGE)))
                .visibility(() -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AndroidPermission.checkForPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE))
                .validator(() -> !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AndroidPermission.checkForPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)))
        )
        .add(new SWScreen(R.string.nsclientinternal_title)
                .skippable(true)
                .add(new SWInfotext()
                        .label(R.string.nsclientinfotext))
                .add(new SWBreak())
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
                .add(new SWEditUrl()
                        .preferenceId(R.string.key_nsclientinternal_url)
                        .label(R.string.nsclientinternal_url_title)
                        .comment(R.string.nsclientinternal_url_dialogmessage))
                .add(new SWEditString()
                        .validator(text -> text.length() >= 12)
                        .preferenceId(R.string.key_nsclientinternal_api_secret)
                        .label(R.string.nsclientinternal_secret_dialogtitle)
                        .comment(R.string.nsclientinternal_secret_dialogmessage))
                .add(new SWBreak())
                .add(new SWEventListener(this)
                        .label(R.string.status)
                        .initialStatus(NSClientPlugin.getPlugin().status)
                        .listener(new Object() {
                            @Subscribe
                            public void onEventNSClientStatus(EventNSClientStatus event) {
                                MainApp.bus().post(new EventSWLabel(event.status));
                            }
                        })
                )
                .add(new SWBreak())
                .validator(() -> NSClientPlugin.getPlugin().nsClientService != null && NSClientPlugin.getPlugin().nsClientService.isConnected && NSClientPlugin.getPlugin().nsClientService.hasWriteAuth)
                .visibility(() -> !(NSClientPlugin.getPlugin().nsClientService != null && NSClientPlugin.getPlugin().nsClientService.isConnected && NSClientPlugin.getPlugin().nsClientService.hasWriteAuth))
        )
        .add(new SWScreen(R.string.patientage)
                .skippable(false)
                .add(new SWInfotext()
                        .label(R.string.patientage_summary))
                .add(new SWBreak())
                .add(new SWRadioButton()
                        .option(R.array.ageArray, R.array.ageValues)
                        .preferenceId(R.string.key_age)
                        .label(R.string.patientage)
                        .comment(R.string.patientage_summary))
                .validator(() -> SP.contains(R.string.key_age))
        )
        .add(new SWScreen(R.string.configbuilder_insulin)
                .skippable(false)
                .add(new SWInfotext()
                        .label(MainApp.gs(R.string.rapid_acting_oref) + ": " + MainApp.gs(R.string.fastactinginsulincomment)))
                .add(new SWInfotext()
                        .label(MainApp.gs(R.string.ultrarapid_oref) + ": " + MainApp.gs(R.string.ultrafastactinginsulincomment)))
                .add(new SWInfotext()
                        .label(MainApp.gs(R.string.free_peak_oref) + ": " + MainApp.gs(R.string.free_peak_oref_description)))
                .add(new SWBreak())
                .add(new SWInfotext()
                        .label(R.string.diawarning))
                .add(new SWBreak())
                .add(new SWPlugin()
                        .option(PluginType.INSULIN)
                        .makeVisible(false)
                        .label(R.string.configbuilder_insulin))
                .add(new SWBreak())
                .add(new SWButton()
                        .text(R.string.insulinsourcesetup)
                        .action(() -> {
                            final PluginBase plugin = (PluginBase) MainApp.getConfigBuilder().getActiveInsulin();
                            PasswordProtection.QueryPassword(activity, R.string.settings_password, "settings_password", () -> {
                                Intent i = new Intent(activity, PreferencesActivity.class);
                                i.putExtra("id", plugin.getPreferencesId());
                                activity.startActivity(i);
                            }, null);
                        })
                        .visibility(() -> MainApp.getConfigBuilder().getActiveInsulin()!= null  && ((PluginBase) MainApp.getConfigBuilder().getActiveInsulin()).getPreferencesId() > 0))
                .validator(() -> MainApp.getConfigBuilder().getActiveInsulin() != null)
        )
        .add(new SWScreen(R.string.configbuilder_bgsource)
                .skippable(false)
                .add(new SWInfotext()
                        .label(R.string.setupwizard_bgsource_description))
                .add(new SWPlugin()
                        .option(PluginType.BGSOURCE)
                        .label(R.string.configbuilder_bgsource))
                .add(new SWBreak())
                .add(new SWButton()
                        .text(R.string.bgsourcesetup)
                        .action(() -> {
                            final PluginBase plugin = (PluginBase) MainApp.getConfigBuilder().getActiveBgSource();
                            PasswordProtection.QueryPassword(activity, R.string.settings_password, "settings_password", () -> {
                                Intent i = new Intent(activity, PreferencesActivity.class);
                                i.putExtra("id", plugin.getPreferencesId());
                                activity.startActivity(i);
                            }, null);
                        })
                        .visibility(() -> MainApp.getConfigBuilder().getActiveBgSource()!= null  && ((PluginBase) MainApp.getConfigBuilder().getActiveBgSource()).getPreferencesId() > 0))
                .validator(() -> MainApp.getConfigBuilder().getActiveBgSource() != null)
        )
        .add(new SWScreen(R.string.configbuilder_profile)
                        .skippable(false)
                        .add(new SWInfotext()
                                .label(R.string.setupwizard_profile_description))
                        .add(new SWBreak())
                        .add(new SWPlugin()
                                .option(PluginType.PROFILE)
                                .label(R.string.configbuilder_profile))
                        .validator(() -> MainApp.getConfigBuilder().getActiveProfileInterface() != null)
                )
        .add(new SWScreen(R.string.nsprofile)
                        .skippable(false)
                        .add(new SWInfotext()
                                .label(R.string.adjustprofileinns))
                        .add(new SWFragment(this)
                                .add(new NSProfileFragment()))
                        .validator(() -> NSProfilePlugin.getPlugin().getProfile() != null && NSProfilePlugin.getPlugin().getProfile().getDefaultProfile().isValid("StartupWizard"))
                        .visibility(() -> NSProfilePlugin.getPlugin().isEnabled(PluginType.PROFILE))
                )
        .add(new SWScreen(R.string.localprofile)
                        .skippable(false)
                        .add(new SWFragment(this)
                                .add(new LocalProfileFragment()))
                        .validator(() -> LocalProfilePlugin.getPlugin().getProfile() != null && LocalProfilePlugin.getPlugin().getProfile().getDefaultProfile().isValid("StartupWizard"))
                        .visibility(() -> LocalProfilePlugin.getPlugin().isEnabled(PluginType.PROFILE))
                )
        .add(new SWScreen(R.string.simpleprofile)
                        .skippable(false)
                        .add(new SWFragment(this)
                                .add(new SimpleProfileFragment()))
                        .validator(() -> SimpleProfilePlugin.getPlugin().getProfile() != null && SimpleProfilePlugin.getPlugin().getProfile().getDefaultProfile().isValid("StartupWizard"))
                        .visibility(() -> SimpleProfilePlugin.getPlugin().isEnabled(PluginType.PROFILE))
                )
        .add(new SWScreen(R.string.profileswitch)
                        .skippable(false)
                        .add(new SWInfotext()
                                .label(R.string.profileswitch_ismissing))
                        .add(new SWButton()
                                .text(R.string.profileswitch)
                                .action(() -> {
                                    NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
                                    final OptionsToShow profileswitch = CareportalFragment.PROFILESWITCHDIRECT;
                                    profileswitch.executeProfileSwitch = true;
                                    newDialog.setOptions(profileswitch, R.string.careportal_profileswitch);
                                    newDialog.show(getActivity().getSupportFragmentManager(), "NewNSTreatmentDialog");
                                }))
                        .validator(() -> MainApp.getConfigBuilder().getProfile() != null)
                        .visibility(() -> MainApp.getConfigBuilder().getProfile() == null)
                )
        .add(new SWScreen(R.string.configbuilder_pump)
                .skippable(false)
                .add(new SWPlugin()
                        .option(PluginType.PUMP)
                        .label(R.string.configbuilder_pump))
                .add(new SWButton()
                        .text(R.string.pumpsetup)
                        .action(() -> {
                            final PluginBase plugin = (PluginBase) MainApp.getConfigBuilder().getActivePump();
                            PasswordProtection.QueryPassword(activity, R.string.settings_password, "settings_password", () -> {
                                Intent i = new Intent(activity, PreferencesActivity.class);
                                i.putExtra("id", plugin.getPreferencesId());
                                activity.startActivity(i);
                            }, null);
                        })
                        .visibility(() -> ((PluginBase) MainApp.getConfigBuilder().getActivePump()).getPreferencesId() > 0))
                .add(new SWButton()
                        .text(R.string.readstatus)
                        .action(() -> ConfigBuilderPlugin.getCommandQueue().readStatus("Clicked connect to pump", null))
                        .visibility(() -> MainApp.getConfigBuilder().getActivePump() != null))
                .add(new SWEventListener(this)
                        .listener(new Object() {
                            @Subscribe
                            public void onEventPumpStatusChanged(EventPumpStatusChanged event) {
                                MainApp.bus().post(new EventSWLabel(event.textStatus()));
                            }
                        })
                )
                .validator(() -> MainApp.getConfigBuilder().getActivePump() != null && MainApp.getConfigBuilder().getActivePump().isInitialized())
        )
        .add(new SWScreen(R.string.configbuilder_aps)
                .skippable(false)
                .add(new SWInfotext()
                        .label(R.string.setupwizard_aps_description))
                .add(new SWBreak())
                .add(new SWHtmlLink()
                        .label("https://openaps.readthedocs.io/en/latest/"))
                .add(new SWBreak())
                .add(new SWPlugin()
                        .option(PluginType.APS)
                        .label(R.string.configbuilder_aps))
                .add(new SWButton()
                        .text(R.string.apssetup)
                        .action(() -> {
                            final PluginBase plugin = (PluginBase) MainApp.getConfigBuilder().getActiveAPS();
                            PasswordProtection.QueryPassword(activity, R.string.settings_password, "settings_password", () -> {
                                Intent i = new Intent(activity, PreferencesActivity.class);
                                i.putExtra("id", plugin.getPreferencesId());
                                activity.startActivity(i);
                            }, null);
                        })
                        .visibility(() -> MainApp.getConfigBuilder().getActiveAPS() != null && ((PluginBase) MainApp.getConfigBuilder().getActiveAPS()).getPreferencesId() > 0))
                .validator(() -> MainApp.getConfigBuilder().getActiveAPS() != null)
        )
        .add(new SWScreen(R.string.configbuilder_loop)
                .skippable(false)
                .add(new SWInfotext()
                        .label(R.string.setupwizard_loop_description))
                .add(new SWBreak())
                .add(new SWButton()
                        .text(R.string.enableloop)
                        .action(() -> {
                            LoopPlugin.getPlugin().setPluginEnabled(PluginType.LOOP, true);
                            LoopPlugin.getPlugin().setFragmentVisible(PluginType.LOOP, true);
                            ConfigBuilderFragment.processOnEnabledCategoryChanged(LoopPlugin.getPlugin(), PluginType.LOOP);
                            ConfigBuilderPlugin.getPlugin().storeSettings("SetupWizard");
                            MainApp.bus().post(new EventConfigBuilderChange());
                            MainApp.bus().post(new EventSWUpdate(true));
                        })
                        .visibility(() -> !LoopPlugin.getPlugin().isEnabled(PluginType.LOOP)))
                .validator(() -> LoopPlugin.getPlugin().isEnabled(PluginType.LOOP))
                .visibility(() -> !LoopPlugin.getPlugin().isEnabled(PluginType.LOOP))
        )
        .add(new SWScreen(R.string.configbuilder_sensitivity)
                .skippable(false)
                .add(new SWInfotext()
                        .label(R.string.setupwizard_sensitivity_description))
                .add(new SWHtmlLink()
                        .label(R.string.setupwizard_sensitivity_url))
                .add(new SWBreak())
                .add(new SWPlugin()
                        .option(PluginType.SENSITIVITY)
                        .label(R.string.configbuilder_sensitivity))
                .add(new SWBreak())
                .add(new SWButton()
                        .text(R.string.sensitivitysetup)
                        .action(() -> {
                            final PluginBase plugin = (PluginBase) MainApp.getConfigBuilder().getActiveSensitivity();
                            PasswordProtection.QueryPassword(activity, R.string.settings_password, "settings_password", () -> {
                                Intent i = new Intent(activity, PreferencesActivity.class);
                                i.putExtra("id", plugin.getPreferencesId());
                                activity.startActivity(i);
                            }, null);
                        })
                        .visibility(() -> MainApp.getConfigBuilder().getActiveSensitivity() != null && ((PluginBase) MainApp.getConfigBuilder().getActiveSensitivity()).getPreferencesId() > 0))
                .validator(() -> MainApp.getConfigBuilder().getActiveSensitivity() != null)
        )
        .add(new SWScreen(R.string.objectives)
                .skippable(false)
                .add(new SWInfotext()
                        .label(R.string.setupwizard_objectives_description))
                .add(new SWButton()
                        .text(R.string.enableobjectives)
                        .action(() -> {
                            ObjectivesPlugin.getPlugin().setPluginEnabled(PluginType.CONSTRAINTS, true);
                            ObjectivesPlugin.getPlugin().setFragmentVisible(PluginType.CONSTRAINTS, true);
                            ConfigBuilderFragment.processOnEnabledCategoryChanged(ObjectivesPlugin.getPlugin(), PluginType.CONSTRAINTS);
                            ConfigBuilderPlugin.getPlugin().storeSettings("SetupWizard");
                            MainApp.bus().post(new EventConfigBuilderChange());
                            MainApp.bus().post(new EventSWUpdate(true));
                        })
                        .visibility(() -> !ObjectivesPlugin.getPlugin().isFragmentVisible()))
                .validator(() -> ObjectivesPlugin.getPlugin().isEnabled(PluginType.CONSTRAINTS))
                .visibility(() -> !ObjectivesPlugin.getPlugin().isFragmentVisible())
        )
        .add(new SWScreen(R.string.objectives)
                        .skippable(false)
                        .add(new SWInfotext()
                                .label(R.string.startobjective))
                        .add(new SWBreak())
                        .add(new SWFragment(this)
                                .add(new ObjectivesFragment()))
                        .validator(() -> ObjectivesPlugin.getPlugin().objectives.get(0).isStarted())
                        .visibility(() -> !ObjectivesPlugin.getPlugin().objectives.get(0).isStarted())
                )
        ;
    }

    private void SWDefinitionNSClient() {
        // List all the screens here
        add(new SWScreen(R.string.nav_setupwizard)
                .add(new SWInfotext()
                        .label(R.string.welcometosetupwizard))
                .add(new SWButton()
                        .text(R.string.nav_import)
                        .action(() -> ImportExportPrefs.importSharedPreferences(getActivity()))
                        .visibility(ImportExportPrefs.file::exists))
                .add(new SWInfotext()
                        .label(R.string.backupismissing)
                        .visibility(() -> !ImportExportPrefs.file.exists()))
        )
        .add(new SWScreen(R.string.language)
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
        .add(new SWScreen(R.string.end_user_license_agreement)
                .skippable(false)
                .add(new SWInfotext()
                        .label(R.string.end_user_license_agreement_text))
                .add(new SWBreak())
                .add(new SWButton()
                        .text(R.string.end_user_license_agreement_i_understand)
                        .visibility(() -> !SP.getBoolean(R.string.key_i_understand, false))
                        .action(() -> {
                            SP.putBoolean(R.string.key_i_understand, true);
                            MainApp.bus().post(new EventSWUpdate(false));
                        }))
                .visibility(() -> !SP.getBoolean(R.string.key_i_understand, false))
                .validator(() -> SP.getBoolean(R.string.key_i_understand, false))
        )
        .add(new SWScreen(R.string.permission)
                .skippable(false)
                .add(new SWInfotext()
                        .label(String.format(MainApp.gs(R.string.needwhitelisting), MainApp.gs(R.string.app_name))))
                .add(new SWBreak())
                .add(new SWButton()
                        .text(R.string.askforpermission)
                        .visibility(() -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AndroidPermission.checkForPermission(getActivity(), Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
                        .action(() -> AndroidPermission.askForPermission(getActivity(), Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, AndroidPermission.CASE_BATTERY)))
                .visibility(() -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AndroidPermission.checkForPermission(getActivity(), Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
                .validator(() -> !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AndroidPermission.checkForPermission(getActivity(), Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)))
        )
        .add(new SWScreen(R.string.permission)
                .skippable(false)
                .add(new SWInfotext()
                        .label(MainApp.gs(R.string.needstoragepermission)))
                .add(new SWBreak())
                .add(new SWButton()
                        .text(R.string.askforpermission)
                        .visibility(() -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AndroidPermission.checkForPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE))
                        .action(() -> AndroidPermission.askForPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE, AndroidPermission.CASE_STORAGE)))
                .visibility(() -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AndroidPermission.checkForPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE))
                .validator(() -> !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AndroidPermission.checkForPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)))
        )
        .add(new SWScreen(R.string.nsclientinternal_title)
                .skippable(true)
                .add(new SWInfotext()
                        .label(R.string.nsclientinfotext))
                .add(new SWBreak())
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
                .add(new SWEditUrl()
                        .preferenceId(R.string.key_nsclientinternal_url)
                        .label(R.string.nsclientinternal_url_title)
                        .comment(R.string.nsclientinternal_url_dialogmessage))
                .add(new SWEditString()
                        .validator(text -> text.length() >= 12)
                        .preferenceId(R.string.key_nsclientinternal_api_secret)
                        .label(R.string.nsclientinternal_secret_dialogtitle)
                        .comment(R.string.nsclientinternal_secret_dialogmessage))
                .add(new SWBreak())
                .add(new SWEventListener(this)
                        .label(R.string.status)
                        .initialStatus(NSClientPlugin.getPlugin().status)
                        .listener(new Object() {
                            @Subscribe
                            public void onEventNSClientStatus(EventNSClientStatus event) {
                                MainApp.bus().post(new EventSWLabel(event.status));
                            }
                        })
                )
                .add(new SWBreak())
                .validator(() -> NSClientPlugin.getPlugin().nsClientService != null && NSClientPlugin.getPlugin().nsClientService.isConnected && NSClientPlugin.getPlugin().nsClientService.hasWriteAuth)
                .visibility(() -> !(NSClientPlugin.getPlugin().nsClientService != null && NSClientPlugin.getPlugin().nsClientService.isConnected && NSClientPlugin.getPlugin().nsClientService.hasWriteAuth))
        )
        .add(new SWScreen(R.string.patientage)
                .skippable(false)
                .add(new SWInfotext()
                        .label(R.string.patientage_summary))
                .add(new SWBreak())
                .add(new SWRadioButton()
                        .option(R.array.ageArray, R.array.ageValues)
                        .preferenceId(R.string.key_age)
                        .label(R.string.patientage)
                        .comment(R.string.patientage_summary))
                .validator(() -> SP.contains(R.string.key_age))
        )
        .add(new SWScreen(R.string.configbuilder_insulin)
                .skippable(false)
                .add(new SWInfotext()
                        .label(MainApp.gs(R.string.rapid_acting_oref) + ": " + MainApp.gs(R.string.fastactinginsulincomment)))
                .add(new SWInfotext()
                        .label(MainApp.gs(R.string.ultrarapid_oref) + ": " + MainApp.gs(R.string.ultrafastactinginsulincomment)))
                .add(new SWInfotext()
                        .label(MainApp.gs(R.string.free_peak_oref) + ": " + MainApp.gs(R.string.free_peak_oref_description)))
                .add(new SWBreak())
                .add(new SWInfotext()
                        .label(R.string.diawarning))
                .add(new SWBreak())
                .add(new SWPlugin()
                        .option(PluginType.INSULIN)
                        .makeVisible(false)
                        .label(R.string.configbuilder_insulin))
                .add(new SWBreak())
                .add(new SWButton()
                        .text(R.string.insulinsourcesetup)
                        .action(() -> {
                            final PluginBase plugin = (PluginBase) MainApp.getConfigBuilder().getActiveInsulin();
                            PasswordProtection.QueryPassword(activity, R.string.settings_password, "settings_password", () -> {
                                Intent i = new Intent(activity, PreferencesActivity.class);
                                i.putExtra("id", plugin.getPreferencesId());
                                activity.startActivity(i);
                            }, null);
                        })
                        .visibility(() -> MainApp.getConfigBuilder().getActiveInsulin()!= null  && ((PluginBase) MainApp.getConfigBuilder().getActiveInsulin()).getPreferencesId() > 0))
                .validator(() -> MainApp.getConfigBuilder().getActiveInsulin() != null)
        )
        .add(new SWScreen(R.string.configbuilder_sensitivity)
                .skippable(false)
                .add(new SWInfotext()
                        .label(R.string.setupwizard_sensitivity_description))
                .add(new SWHtmlLink()
                        .label(R.string.setupwizard_sensitivity_url))
                .add(new SWBreak())
                .add(new SWPlugin()
                        .option(PluginType.SENSITIVITY)
                        .label(R.string.configbuilder_sensitivity))
                .add(new SWBreak())
                .add(new SWButton()
                        .text(R.string.sensitivitysetup)
                        .action(() -> {
                            final PluginBase plugin = (PluginBase) MainApp.getConfigBuilder().getActiveSensitivity();
                            PasswordProtection.QueryPassword(activity, R.string.settings_password, "settings_password", () -> {
                                Intent i = new Intent(activity, PreferencesActivity.class);
                                i.putExtra("id", plugin.getPreferencesId());
                                activity.startActivity(i);
                            }, null);
                        })
                        .visibility(() -> MainApp.getConfigBuilder().getActiveSensitivity() != null && ((PluginBase) MainApp.getConfigBuilder().getActiveSensitivity()).getPreferencesId() > 0))
                .validator(() -> MainApp.getConfigBuilder().getActiveSensitivity() != null)
        )
        ;
    }

}