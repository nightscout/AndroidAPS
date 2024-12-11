package app.aaps.plugins.configuration.setupwizard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.androidPermissions.AndroidPermission
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.Medtrum
import app.aaps.core.interfaces.pump.OmnipodDash
import app.aaps.core.interfaces.pump.OmnipodEros
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAAPSDirectorySelected
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventSWRLStatus
import app.aaps.core.interfaces.rx.events.EventSWSyncStatus
import app.aaps.core.interfaces.rx.events.EventSWUpdate
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.utils.isRunningTest
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import app.aaps.plugins.configuration.setupwizard.elements.SWBreak
import app.aaps.plugins.configuration.setupwizard.elements.SWButton
import app.aaps.plugins.configuration.setupwizard.elements.SWEditEncryptedPassword
import app.aaps.plugins.configuration.setupwizard.elements.SWEditIntNumber
import app.aaps.plugins.configuration.setupwizard.elements.SWEditNumber
import app.aaps.plugins.configuration.setupwizard.elements.SWEditNumberWithUnits
import app.aaps.plugins.configuration.setupwizard.elements.SWEditString
import app.aaps.plugins.configuration.setupwizard.elements.SWFragment
import app.aaps.plugins.configuration.setupwizard.elements.SWHtmlLink
import app.aaps.plugins.configuration.setupwizard.elements.SWInfoText
import app.aaps.plugins.configuration.setupwizard.elements.SWPlugin
import app.aaps.plugins.configuration.setupwizard.elements.SWRadioButton
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SWDefinition @Inject constructor(
    private val injector: HasAndroidInjector,
    private val rxBus: RxBus,
    private val context: Context,
    private val rh: ResourceHelper,
    private val sp: SP,
    private val preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val commandQueue: CommandQueue,
    private val configBuilder: ConfigBuilder,
    private val loop: Loop,
    private val importExportPrefs: ImportExportPrefs,
    private val androidPermission: AndroidPermission,
    private val cryptoUtil: CryptoUtil,
    private val config: Config,
    private val hardLimits: HardLimits,
    private val uiInteraction: UiInteraction
) {

    lateinit var activity: AppCompatActivity
    private val screens: MutableList<SWScreen> = ArrayList()

    fun getScreens(): List<SWScreen> {
        if (screens.isEmpty()) {
            when {
                config.APS -> swDefinitionFull()
                config.PUMPCONTROL -> swDefinitionPumpControl()
                config.NSCLIENT -> swDefinitionNSClient()
            }
        }
        return screens
    }

    private fun add(newScreen: SWScreen?): SWDefinition {
        if (newScreen != null) screens.add(newScreen)
        return this
    }

    private val screenSetupWizard
        get() = SWScreen(injector, R.string.welcome)
            .add(SWInfoText(injector).label(R.string.welcometosetupwizard))

    private val screenEula
        get() = SWScreen(injector, R.string.end_user_license_agreement)
            .skippable(false)
            .add(SWInfoText(injector).label(R.string.end_user_license_agreement_text))
            .add(SWBreak(injector))
            .add(
                SWButton(injector)
                    .text(R.string.end_user_license_agreement_i_understand)
                    .visibility { !sp.getBoolean(R.string.key_i_understand, false) }
                    .action {
                        sp.putBoolean(R.string.key_i_understand, true)
                        rxBus.send(EventSWUpdate(false))
                    })
            .visibility { !sp.getBoolean(R.string.key_i_understand, false) }
            .validator { sp.getBoolean(R.string.key_i_understand, false) }

    private val screenUnits
        get() = SWScreen(injector, R.string.units)
            .skippable(false)
            .add(
                SWRadioButton(injector)
                    .option(uiInteraction.unitsEntries, uiInteraction.unitsValues)
                    .preference(StringKey.GeneralUnits.key).label(R.string.units)
                    .comment(R.string.setupwizard_units_prompt)
            )
            .validator { preferences.getIfExists(StringKey.GeneralUnits) != null }

    private val displaySettings
        get() = SWScreen(injector, R.string.display_settings)
            .skippable(false)
            .add(
                SWEditNumberWithUnits(injector, UnitDoubleKey.OverviewLowMark.defaultValue * Constants.MGDL_TO_MMOLL, 3.0, 8.0)
                    .preference(UnitDoubleKey.OverviewLowMark)
                    .updateDelay(5)
                    .label(R.string.low_mark)
                    .comment(R.string.low_mark_comment)
            )
            .add(SWBreak(injector))
            .add(
                SWEditNumberWithUnits(injector, UnitDoubleKey.OverviewHighMark.defaultValue * Constants.MGDL_TO_MMOLL, 5.0, 20.0)
                    .preference(UnitDoubleKey.OverviewHighMark)
                    .updateDelay(5)
                    .label(R.string.high_mark)
                    .comment(R.string.high_mark_comment)
            )

    private val screenPermissionWindow
        get() = SWScreen(injector, R.string.permission)
            .skippable(false)
            .add(SWInfoText(injector).label(rh.gs(R.string.need_system_window_permission)))
            .add(SWButton(injector)
                     .text(R.string.askforpermission)
                     .visibility { !Settings.canDrawOverlays(activity) }
                     .action { activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.packageName))) })
            .add(SWBreak(injector))
            .add(SWInfoText(injector).label(rh.gs(R.string.need_whitelisting, rh.gs(config.appName))))
            .add(SWButton(injector)
                     .text(R.string.askforpermission)
                     .visibility { androidPermission.permissionNotGranted(context, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) }
                     .action { androidPermission.askForPermission(activity, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) })
            .add(SWBreak(injector))
            .add(SWInfoText(injector).label(rh.gs(R.string.need_storage_permission)))
            .add(SWButton(injector)
                     .text(R.string.askforpermission)
                     .visibility { androidPermission.permissionNotGranted(activity, Manifest.permission.READ_EXTERNAL_STORAGE) }
                     .action { androidPermission.askForPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) })
            .add(SWBreak(injector))
            .add(SWInfoText(injector).label(rh.gs(R.string.select_aaps_directory)))
            .add(SWButton(injector)
                     .text(R.string.aaps_directory)
                     .visibility { preferences.getIfExists(StringKey.AapsDirectoryUri) == null }
                     .action { (activity as DaggerAppCompatActivityWithResult).accessTree?.launch(null) })
            .add(SWBreak(injector))
            .add(SWEventListener(injector, EventAAPSDirectorySelected::class.java).label(app.aaps.core.ui.R.string.settings).initialStatus(preferences.get(StringKey.AapsDirectoryUri)))
            .add(SWBreak(injector))
            .visibility {
                !Settings.canDrawOverlays(activity) ||
                    androidPermission.permissionNotGranted(activity, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) ||
                    androidPermission.permissionNotGranted(activity, Manifest.permission.READ_EXTERNAL_STORAGE) ||
                    preferences.getIfExists(StringKey.AapsDirectoryUri) == null
            }
            .validator {
                Settings.canDrawOverlays(activity) &&
                    !androidPermission.permissionNotGranted(activity, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) &&
                    !androidPermission.permissionNotGranted(activity, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                    preferences.getIfExists(StringKey.AapsDirectoryUri) != null
            }

    private val screenPermissionBt
        get() = SWScreen(injector, R.string.permission)
            .skippable(false)
            .add(SWInfoText(injector).label(rh.gs(R.string.need_location_permission)))
            .add(SWBreak(injector))
            .add(SWButton(injector)
                     .text(R.string.askforpermission)
                     .visibility { androidPermission.permissionNotGranted(activity, Manifest.permission.ACCESS_FINE_LOCATION) }
                     .action { androidPermission.askForPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) })
            .add(SWBreak(injector))
            .add(SWInfoText(injector).label(rh.gs(R.string.need_background_location_permission)))
            .add(SWBreak(injector))
            .add(SWButton(injector)
                     .text(R.string.askforpermission)
                     .visibility { androidPermission.permissionNotGranted(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
                     .action { androidPermission.askForPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) })
            .visibility { androidPermission.permissionNotGranted(activity, Manifest.permission.ACCESS_FINE_LOCATION) || androidPermission.permissionNotGranted(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
            .validator { !androidPermission.permissionNotGranted(activity, Manifest.permission.ACCESS_FINE_LOCATION) && !androidPermission.permissionNotGranted(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) }

    private val screenImport
        get() = SWScreen(injector, R.string.import_setting)
            .add(SWInfoText(injector).label(R.string.storedsettingsfound))
            .add(SWBreak(injector))
            .add(SWButton(injector).text(R.string.import_setting).action { importExportPrefs.importSharedPreferences(activity) })
            .visibility { importExportPrefs.prefsFileExists() && !androidPermission.permissionNotGranted(activity, Manifest.permission.READ_EXTERNAL_STORAGE) }

    private val screenNsClient
        get() = SWScreen(injector, R.string.configbuilder_sync)
            .skippable(true)
            .add(SWPlugin(injector, this).option(PluginType.SYNC, R.string.configbuilder_sync_description))
            .add(SWBreak(injector))
            .add(SWInfoText(injector).label(R.string.syncinfotext))
            .add(SWBreak(injector))
            .add(SWEventListener(injector, EventSWSyncStatus::class.java).label(R.string.status).initialStatus(activePlugin.activeNsClient?.status ?: ""))
            .validator { activePlugin.activeNsClient?.connected == true && activePlugin.activeNsClient?.hasWritePermission == true }

    private val screenPatientName
        get() = SWScreen(injector, R.string.patient_name)
            .skippable(true)
            .add(SWInfoText(injector).label(R.string.patient_name_summary))
            .add(SWEditString(injector).validator(String::isNotEmpty).preference(StringKey.GeneralPatientName))

    private val screenMasterPassword
        get() = SWScreen(injector, app.aaps.core.ui.R.string.master_password)
            .skippable(false)
            .add(SWInfoText(injector).label(app.aaps.core.ui.R.string.master_password))
            .add(SWEditEncryptedPassword(injector, cryptoUtil).preference(StringKey.ProtectionMasterPassword))
            .add(SWBreak(injector))
            .add(SWInfoText(injector).label(R.string.master_password_summary))
            .validator { !cryptoUtil.checkPassword("", preferences.get(StringKey.ProtectionMasterPassword)) }

    private val screenAge
        get() = SWScreen(injector, app.aaps.core.ui.R.string.patient_type)
            .skippable(false)
            .add(SWBreak(injector))
            .add(
                SWRadioButton(injector)
                    .option(hardLimits.ageEntries(), hardLimits.ageEntryValues())
                    .preference(StringKey.SafetyAge.key)
                    .label(app.aaps.core.ui.R.string.patient_type)
                    .comment(app.aaps.core.ui.R.string.patient_age_summary)
            )
            .add(SWBreak(injector))
            .add(
                SWEditNumber(injector, 3.0, 0.1, 25.0)
                    .preference(DoubleKey.SafetyMaxBolus)
                    .updateDelay(5)
                    .label(app.aaps.core.ui.R.string.max_bolus_title)
                    .comment(R.string.common_values)
            )
            .add(
                SWEditIntNumber(injector, 48, 1, 100)
                    .preference(IntKey.SafetyMaxCarbs)
                    .updateDelay(5)
                    .label(app.aaps.core.ui.R.string.max_carbs_title)
                    .comment(R.string.common_values)
            )
            .validator {
                preferences.getIfExists(StringKey.SafetyAge) != null
                    && preferences.get(DoubleKey.SafetyMaxBolus) > 0
                    && preferences.get(IntKey.SafetyMaxCarbs) > 0
            }

    private val screenInsulin
        get() = SWScreen(injector, app.aaps.core.ui.R.string.configbuilder_insulin)
            .skippable(false)
            .add(SWPlugin(injector, this).option(PluginType.INSULIN, R.string.configbuilder_insulin_description))
            .add(SWBreak(injector))
            .add(SWInfoText(injector).label(R.string.diawarning))

    private val screenBgSource
        get() = SWScreen(injector, R.string.configbuilder_bgsource)
            .skippable(false)
            .add(SWPlugin(injector, this).option(PluginType.BGSOURCE, R.string.configbuilder_bgsource_description))
            .add(SWBreak(injector))

    private val screenLocalProfile
        get() = SWScreen(injector, R.string.profile)
            .skippable(false)
            .add(
                SWFragment(injector, this)
                    .add(
                        activity.supportFragmentManager.fragmentFactory.instantiate(
                            ClassLoader.getSystemClassLoader(),
                            (activePlugin.activeProfileSource as PluginBase).pluginDescription.fragmentClass!!
                        )
                    )
                //.add(ProfileFragment())
            )
            .validator {
                activePlugin.activeProfileSource.profile?.getDefaultProfile()
                    ?.let { ProfileSealed.Pure(it, activePlugin).isValid("StartupWizard", activePlugin.activePump, config, rh, rxBus, hardLimits, false).isValid } == true
            }
            .visibility { (activePlugin.activeProfileSource as PluginBase).isEnabled() }

    private val screenProfileSwitch
        get() = SWScreen(injector, app.aaps.core.ui.R.string.careportal_profileswitch)
            .skippable(false)
            .add(SWInfoText(injector).label(app.aaps.core.ui.R.string.profileswitch_ismissing))
            .add(SWButton(injector)
                     .text(R.string.doprofileswitch)
                     .action { uiInteraction.runProfileSwitchDialog(activity.supportFragmentManager) })
            .validator { profileFunction.getRequestedProfile() != null }
            .visibility { profileFunction.getRequestedProfile() == null }

    private val screenPump
        get() = SWScreen(injector, R.string.configbuilder_pump)
            .skippable(false)
            .add(SWPlugin(injector, this).option(PluginType.PUMP, R.string.configbuilder_pump_description))
            .add(SWBreak(injector))
            .add(SWInfoText(injector).label(R.string.setupwizard_pump_pump_not_initialized).visibility { !isPumpInitialized() })
            .add( // Omnipod Eros only
                SWInfoText(injector)
                    .label(R.string.setupwizard_pump_waiting_for_riley_link_connection)
                    .visibility { activePlugin.activePump.let { it is OmnipodEros && !it.isRileyLinkReady() } }
            )
            .add( // Omnipod Eros only
                SWEventListener(injector, EventSWRLStatus::class.java)
                    .label(R.string.setupwizard_pump_riley_link_status)
                    .visibility { activePlugin.activePump is OmnipodEros })
            .add(SWButton(injector)
                     .text(R.string.readstatus)
                     .action { commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.clicked_connect_to_pump), null) }
                     .visibility {
                         // Hide for Omnipod and Medtrum, because as we don't require a Pod/Patch to be paired in the setup wizard,
                         // Getting the status might not be possible
                         activePlugin.activePump !is OmnipodEros && activePlugin.activePump !is OmnipodDash && activePlugin.activePump !is Medtrum
                     })
            .add(SWEventListener(injector, EventPumpStatusChanged::class.java)
                     .visibility { activePlugin.activePump !is OmnipodEros && activePlugin.activePump !is OmnipodDash && activePlugin.activePump !is Medtrum })
            .validator { isPumpInitialized() }

    private fun isPumpInitialized(): Boolean {
        val activePump = activePlugin.activePump

        // For Omnipod and Medtrum, activating a Pod/Patch can be done after setup through the pump fragment
        // For the Eros, consider the pump initialized when a RL has been configured successfully
        // For all others, consider the pump setup without any extra conditions
        return activePump.isInitialized()
            || (activePump is OmnipodEros && activePump.isRileyLinkReady())
            || activePump is OmnipodDash
            || activePump is Medtrum
    }

    private val screenAps
        get() = SWScreen(injector, R.string.configbuilder_aps)
            .skippable(false)
            .add(SWInfoText(injector).label(R.string.setupwizard_aps_description))
            .add(SWBreak(injector))
            .add(SWPlugin(injector, this).option(PluginType.APS, R.string.configbuilder_aps_description))
            .add(SWBreak(injector))
            .add(SWHtmlLink(injector).label("https://wiki.aaps.app"))
            .add(SWBreak(injector))

    private val screenApsMode
        get() = SWScreen(injector, R.string.apsmode_title)
            .skippable(false)
            .add(
                SWRadioButton(injector)
                    .option(loop.entries(), loop.entryValues())
                    .preference(StringKey.LoopApsMode.key).label(R.string.apsmode_title)
                    .comment(R.string.setupwizard_preferred_aps_mode)
            )
            .validator { preferences.getIfExists(StringKey.LoopApsMode) != null }

    private val screenLoop
        get() = SWScreen(injector, R.string.configbuilder_loop)
            .skippable(false)
            .add(SWInfoText(injector).label(R.string.setupwizard_loop_description))
            .add(SWBreak(injector))
            .add(SWButton(injector)
                     .text(app.aaps.core.ui.R.string.enableloop)
                     .action {
                         configBuilder.performPluginSwitch(loop as PluginBase, true, PluginType.LOOP)
                         rxBus.send(EventSWUpdate(true))
                     }
                     .visibility { !loop.isEnabled() })
            .validator { loop.isEnabled() }
            .visibility { !loop.isEnabled() && config.APS }

    private val screenSensitivity
        get() = SWScreen(injector, R.string.configbuilder_sensitivity)
            .skippable(false)
            .add(SWInfoText(injector).label(R.string.setupwizard_sensitivity_description))
            .add(SWHtmlLink(injector).label(R.string.setupwizard_sensitivity_url))
            .add(SWBreak(injector))
            .add(SWPlugin(injector, this).option(PluginType.SENSITIVITY, R.string.configbuilder_sensitivity_description))

    private val getScreenObjectives
        get() = SWScreen(injector, app.aaps.core.ui.R.string.objectives)
            .skippable(false)
            .add(SWInfoText(injector).label(R.string.startobjective))
            .add(SWBreak(injector))
            .add(
                SWFragment(injector, this)
                    .add(
                        activity.supportFragmentManager.fragmentFactory.instantiate(
                            ClassLoader.getSystemClassLoader(),
                            (activePlugin.activeObjectives as PluginBase).pluginDescription.fragmentClass!!
                        )
                    )
                //.add(ObjectivesFragment())
            )
            .validator { activePlugin.activeObjectives?.isStarted(Objectives.FIRST_OBJECTIVE) == true }
            .visibility { config.APS && activePlugin.activeObjectives?.isStarted(Objectives.FIRST_OBJECTIVE) == false }

    private fun swDefinitionFull() = // List all the screens here
        add(screenSetupWizard)
            //.add(screenLanguage)
            .add(screenEula)
            .add(if (isRunningTest()) null else screenPermissionWindow)  // cannot mock ask battery optimization
            .add(screenPermissionBt)
            .add(screenMasterPassword)
            .add(screenImport)
            .add(screenUnits)
            .add(displaySettings)
            .add(screenNsClient)
            .add(screenPatientName)
            .add(screenAge)
            .add(screenInsulin)
            .add(screenBgSource)
            .add(screenLocalProfile)
            .add(screenProfileSwitch)
            .add(screenPump)
            .add(screenAps)
            .add(screenApsMode)
            .add(screenLoop)
            .add(screenSensitivity)
            .add(getScreenObjectives)

    private fun swDefinitionPumpControl() = // List all the screens here
        add(screenSetupWizard)
            //.add(screenLanguage)
            .add(screenEula)
            .add(if (isRunningTest()) null else screenPermissionWindow) // cannot mock ask battery optimization
            .add(screenPermissionBt)
            .add(screenMasterPassword)
            .add(screenImport)
            .add(screenUnits)
            .add(displaySettings)
            .add(screenNsClient)
            .add(screenPatientName)
            .add(screenAge)
            .add(screenInsulin)
            .add(screenBgSource)
            .add(screenLocalProfile)
            .add(screenProfileSwitch)
            .add(screenPump)
            .add(screenSensitivity)

    private fun swDefinitionNSClient() = // List all the screens here
        add(screenSetupWizard)
            //.add(screenLanguage)
            .add(screenEula)
            .add(if (isRunningTest()) null else screenPermissionWindow) // cannot mock ask battery optimization
            .add(screenMasterPassword)
            .add(screenImport)
            .add(screenUnits)
            .add(displaySettings)
            .add(screenNsClient)
            //.add(screenBgSource)
            .add(screenPatientName)
}