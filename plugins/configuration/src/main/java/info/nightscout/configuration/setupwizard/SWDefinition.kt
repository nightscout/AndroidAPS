package info.nightscout.configuration.setupwizard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import dagger.android.HasAndroidInjector
import info.nightscout.configuration.R
import info.nightscout.configuration.setupwizard.elements.SWBreak
import info.nightscout.configuration.setupwizard.elements.SWButton
import info.nightscout.configuration.setupwizard.elements.SWEditEncryptedPassword
import info.nightscout.configuration.setupwizard.elements.SWEditIntNumber
import info.nightscout.configuration.setupwizard.elements.SWEditNumber
import info.nightscout.configuration.setupwizard.elements.SWEditNumberWithUnits
import info.nightscout.configuration.setupwizard.elements.SWEditString
import info.nightscout.configuration.setupwizard.elements.SWFragment
import info.nightscout.configuration.setupwizard.elements.SWHtmlLink
import info.nightscout.configuration.setupwizard.elements.SWInfoText
import info.nightscout.configuration.setupwizard.elements.SWPlugin
import info.nightscout.configuration.setupwizard.elements.SWPreference
import info.nightscout.configuration.setupwizard.elements.SWRadioButton
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.core.utils.CryptoUtil
import info.nightscout.core.utils.isRunningTest
import info.nightscout.interfaces.AndroidPermission
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.ConfigBuilder
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.constraints.Objectives
import info.nightscout.interfaces.maintenance.ImportExportPrefs
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.OmnipodDash
import info.nightscout.interfaces.pump.OmnipodEros
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventPumpStatusChanged
import info.nightscout.rx.events.EventSWRLStatus
import info.nightscout.rx.events.EventSWSyncStatus
import info.nightscout.rx.events.EventSWUpdate
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SWDefinition @Inject constructor(
    private val injector: HasAndroidInjector,
    private val rxBus: RxBus,
    private val context: Context,
    private val rh: ResourceHelper,
    private val sp: SP,
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
    private val screens: MutableList<SWScreen>  = ArrayList()

    fun getScreens(): List<SWScreen> {
        if (screens.isEmpty()) {
            if (config.APS) swDefinitionFull()
            else if (config.PUMPCONTROL) swDefinitionPumpControl()
            else if (config.NSCLIENT) swDefinitionNSClient()
        }
        return screens
    }

    private fun add(newScreen: SWScreen?): SWDefinition {
        if (newScreen != null) screens.add(newScreen)
        return this
    }

    private val screenSetupWizard get() = SWScreen(injector, R.string.nav_setupwizard)
        .add(
            SWInfoText(injector)
                .label(R.string.welcometosetupwizard)
        )
    private val screenEula get() = SWScreen(injector, R.string.end_user_license_agreement)
        .skippable(false)
        .add(
            SWInfoText(injector)
                .label(R.string.end_user_license_agreement_text)
        )
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
                    .option(R.array.unitsArray, R.array.unitsValues)
                    .preferenceId(R.string.key_units).label(R.string.units)
                    .comment(R.string.setupwizard_units_prompt)
            )
            .validator { sp.contains(R.string.key_units) }

    private val displaySettings
        get() = SWScreen(injector, R.string.display_settings)
            .skippable(false)
            .add(
                SWEditNumberWithUnits(injector, Constants.LOW_MARK * Constants.MGDL_TO_MMOLL, 3.0, 8.0)
                    .preferenceId(R.string.key_low_mark)
                    .updateDelay(5)
                    .label(R.string.low_mark)
                    .comment(R.string.low_mark_comment)
            )
            .add(SWBreak(injector))
            .add(
                SWEditNumberWithUnits(injector, Constants.HIGH_MARK * Constants.MGDL_TO_MMOLL, 5.0, 20.0)
                    .preferenceId(R.string.key_high_mark)
                    .updateDelay(5)
                    .label(R.string.high_mark)
                    .comment(R.string.high_mark_comment)
            )

    private val screenPermissionWindow
        get() = SWScreen(injector, R.string.permission)
            .skippable(false)
            .add(
            SWInfoText(injector)
                .label(rh.gs(R.string.need_system_window_permission))
        )
        .add(SWBreak(injector))
        .add(SWButton(injector)
                 .text(R.string.askforpermission)
                 .visibility { !Settings.canDrawOverlays(activity) }
                 .action { activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.packageName))) })
        .visibility { !Settings.canDrawOverlays(activity) }
        .validator { Settings.canDrawOverlays(activity) }

    private val screenPermissionBattery get() = SWScreen(injector, R.string.permission)
        .skippable(false)
        .add(
            SWInfoText(injector)
                .label(rh.gs(R.string.need_whitelisting, rh.gs(config.appName)))
        )
        .add(SWBreak(injector))
        .add(SWButton(injector)
                 .text(R.string.askforpermission)
                 .visibility { androidPermission.permissionNotGranted(context, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) }
                 .action { androidPermission.askForPermission(activity, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) })
        .visibility { androidPermission.permissionNotGranted(activity, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) }
        .validator { !androidPermission.permissionNotGranted(activity, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) }

    private val screenPermissionBt get() = SWScreen(injector, R.string.permission)
        .skippable(false)
        .add(
            SWInfoText(injector)
                .label(rh.gs(R.string.need_location_permission))
        )
        .add(SWBreak(injector))
        .add(SWButton(injector)
                 .text(R.string.askforpermission)
                 .visibility { androidPermission.permissionNotGranted(activity, Manifest.permission.ACCESS_FINE_LOCATION) }
                 .action { androidPermission.askForPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) })
        .visibility { androidPermission.permissionNotGranted(activity, Manifest.permission.ACCESS_FINE_LOCATION) }
        .validator { !androidPermission.permissionNotGranted(activity, Manifest.permission.ACCESS_FINE_LOCATION) }

    private val screenPermissionStore get() = SWScreen(injector, R.string.permission)
        .skippable(false)
        .add(
            SWInfoText(injector)
                .label(rh.gs(R.string.need_storage_permission))
        )
        .add(SWBreak(injector))
        .add(SWButton(injector)
                 .text(R.string.askforpermission)
                 .visibility { androidPermission.permissionNotGranted(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                 .action { androidPermission.askForPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) })
        .visibility { androidPermission.permissionNotGranted(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) }
        .validator { !androidPermission.permissionNotGranted(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) }

    private val screenImport get() = SWScreen(injector, R.string.import_setting)
        .add(
            SWInfoText(injector)
                .label(R.string.storedsettingsfound)
        )
        .add(SWBreak(injector))
        .add(SWButton(injector)
                 .text(R.string.import_setting)
                 .action { importExportPrefs.importSharedPreferences(activity) })
        .visibility { importExportPrefs.prefsFileExists() && !androidPermission.permissionNotGranted(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) }

    private val screenNsClient get() = SWScreen(injector, R.string.configbuilder_sync)
        .skippable(true)
        .add(
            SWPlugin(injector, this)
                .option(PluginType.SYNC, R.string.configbuilder_sync_description)
                .makeVisible(false)
                .label(R.string.configbuilder_insulin)
        )
        .add(SWBreak(injector))
        .add(
            SWInfoText(injector)
                .label(R.string.syncinfotext)
        )
        .add(SWBreak(injector))
        .add(
            SWEventListener(injector, EventSWSyncStatus::class.java)
                .label(R.string.status)
                .initialStatus(activePlugin.firstActiveSync?.status ?: "")
        )
        .validator { activePlugin.firstActiveSync?.connected == true && activePlugin.firstActiveSync?.hasWritePermission == true }

    private val screenPatientName get() = SWScreen(injector, R.string.patient_name)
        .skippable(true)
        .add(
            SWInfoText(injector)
                .label(R.string.patient_name_summary)
        )
        .add(
            SWEditString(injector)
                .validator(SWTextValidator(String::isNotEmpty))
                .preferenceId(R.string.key_patient_name)
        )

    private val privacy get() = SWScreen(injector, R.string.privacy_settings)
        .skippable(true)
        .add(
            SWInfoText(injector)
                .label(R.string.privacy_summary)
        )
        .add(
            SWPreference(injector, this)
                .option(R.xml.pref_datachoices)
        )

    private val screenMasterPassword get() = SWScreen(injector, R.string.master_password)
        .skippable(false)
        .add(
            SWInfoText(injector)
                .label(R.string.master_password)
        )
        .add(
            SWEditEncryptedPassword(injector, cryptoUtil)
                .preferenceId(R.string.key_master_password)
        )
        .add(SWBreak(injector))
        .add(
            SWInfoText(injector)
                .label(R.string.master_password_summary)
        )
        .validator { !cryptoUtil.checkPassword("", sp.getString(R.string.key_master_password, "")) }

    private val screenAge get() = SWScreen(injector, R.string.patient_type)
        .skippable(false)
        .add(SWBreak(injector))
        .add(
            SWRadioButton(injector)
                .option(R.array.ageArray, R.array.ageValues)
                .preferenceId(R.string.key_age)
                .label(R.string.patient_type)
                .comment(R.string.patient_age_summary)
        )
        .add(SWBreak(injector))
        .add(
            SWEditNumber(injector, 3.0, 0.1, 25.0)
                .preferenceId(R.string.key_treatmentssafety_maxbolus)
                .updateDelay(5)
                .label(R.string.max_bolus_title)
                .comment(R.string.common_values)
        )
        .add(
            SWEditIntNumber(injector, 48, 1, 100)
                .preferenceId(R.string.key_treatmentssafety_maxcarbs)
                .updateDelay(5)
                .label(R.string.max_carbs_title)
                .comment(R.string.common_values)
        )
        .validator {
            sp.contains(R.string.key_age)
                && sp.getDouble(R.string.key_treatmentssafety_maxbolus, 0.0) > 0
                && sp.getInt(R.string.key_treatmentssafety_maxcarbs, 0) > 0
        }

    private val screenInsulin get() = SWScreen(injector, R.string.configbuilder_insulin)
        .skippable(false)
        .add(
            SWPlugin(injector, this)
                .option(PluginType.INSULIN, R.string.configbuilder_insulin_description)
                .makeVisible(false)
                .label(R.string.configbuilder_insulin)
        )
        .add(SWBreak(injector))
        .add(
            SWInfoText(injector)
                .label(R.string.diawarning)
        )

    private val screenBgSource
        get() = SWScreen(injector, R.string.configbuilder_bgsource)
            .skippable(false)
            .add(
                SWPlugin(injector, this)
                    .option(PluginType.BGSOURCE, R.string.configbuilder_bgsource_description)
                    .label(R.string.configbuilder_bgsource)
            )
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
                    ?.let { ProfileSealed.Pure(it).isValid("StartupWizard", activePlugin.activePump, config, rh, rxBus, hardLimits, false).isValid }
                    ?: false
            }
            .visibility { (activePlugin.activeProfileSource as PluginBase).isEnabled() }

    private val screenProfileSwitch
        get() = SWScreen(injector, R.string.careportal_profileswitch)
        .skippable(false)
        .add(
            SWInfoText(injector)
                .label(R.string.profileswitch_ismissing)
        )
        .add(SWButton(injector)
                 .text(R.string.doprofileswitch)
                 .action { uiInteraction.runProfileSwitchDialog(activity.supportFragmentManager) })
        .validator { profileFunction.getRequestedProfile() != null }
        .visibility { profileFunction.getRequestedProfile() == null }

    private val screenPump get() = SWScreen(injector, R.string.configbuilder_pump)
        .skippable(false)
        .add(
            SWPlugin(injector, this)
                .option(PluginType.PUMP, R.string.configbuilder_pump_description)
                .label(R.string.configbuilder_pump)
        )
        .add(SWBreak(injector))
        .add(SWInfoText(injector)
                 .label(R.string.setupwizard_pump_pump_not_initialized)
                 .visibility { !isPumpInitialized() })
        .add( // Omnipod Eros only
            SWInfoText(injector)
                .label(R.string.setupwizard_pump_waiting_for_riley_link_connection)
                .visibility {
                    val activePump = activePlugin.activePump
                    activePump is OmnipodEros && !activePump.isRileyLinkReady()
                })
        .add( // Omnipod Eros only
            SWEventListener(injector, EventSWRLStatus::class.java)
                .label(R.string.setupwizard_pump_riley_link_status)
                .visibility { activePlugin.activePump is OmnipodEros })
        .add(SWButton(injector)
                 .text(R.string.readstatus)
                 .action { commandQueue.readStatus(rh.gs(R.string.clicked_connect_to_pump), null) }
                 .visibility {
                     // Hide for Omnipod, because as we don't require a Pod to be paired in the setup wizard,
                     // Getting the status might not be possible
                     activePlugin.activePump !is OmnipodEros && activePlugin.activePump !is OmnipodDash
                 })
        .add(SWEventListener(injector, EventPumpStatusChanged::class.java)
                 .visibility { activePlugin.activePump !is OmnipodEros && activePlugin.activePump !is OmnipodDash })
        .validator { isPumpInitialized() }

    private fun isPumpInitialized(): Boolean {
        val activePump = activePlugin.activePump

        // For Omnipod, activating a Pod can be done after setup through the Omnipod fragment
        // For the Eros model, consider the pump initialized when a RL has been configured successfully
        // For Dash model, consider the pump setup without any extra conditions
        return activePump.isInitialized()
            || (activePump is OmnipodEros && activePump.isRileyLinkReady())
            || activePump is OmnipodDash
    }

    private val screenAps get() = SWScreen(injector, R.string.configbuilder_aps)
        .skippable(false)
        .add(
            SWInfoText(injector)
                .label(R.string.setupwizard_aps_description)
        )
        .add(SWBreak(injector))
        .add(
            SWPlugin(injector, this)
                .option(PluginType.APS, R.string.configbuilder_aps_description)
                .label(R.string.configbuilder_aps)
        )
        .add(SWBreak(injector))
        .add(
            SWHtmlLink(injector)
                .label("https://wiki.aaps.app")
        )
        .add(SWBreak(injector))

    private val screenApsMode get() = SWScreen(injector, R.string.apsmode_title)
        .skippable(false)
        .add(
            SWRadioButton(injector)
                .option(R.array.aps_modeArray, R.array.aps_modeValues)
                .preferenceId(R.string.key_aps_mode).label(R.string.apsmode_title)
                .comment(R.string.setupwizard_preferred_aps_mode)
        )
        .validator { sp.contains(R.string.key_aps_mode) }

    private val screenLoop get() = SWScreen(injector, R.string.configbuilder_loop)
        .skippable(false)
        .add(
            SWInfoText(injector)
                .label(R.string.setupwizard_loop_description)
        )
        .add(SWBreak(injector))
        .add(SWButton(injector)
                 .text(R.string.enableloop)
                 .action {
                     configBuilder.performPluginSwitch(loop as PluginBase, true, PluginType.LOOP)
                     rxBus.send(EventSWUpdate(true))
                 }
                 .visibility { !loop.isEnabled() })
        .validator { loop.isEnabled() }
        .visibility { !loop.isEnabled() && config.APS }

    private val screenSensitivity get() = SWScreen(injector, R.string.configbuilder_sensitivity)
        .skippable(false)
        .add(
            SWInfoText(injector)
                .label(R.string.setupwizard_sensitivity_description)
        )
        .add(
            SWHtmlLink(injector)
                .label(R.string.setupwizard_sensitivity_url)
        )
        .add(SWBreak(injector))
        .add(
            SWPlugin(injector, this)
                .option(PluginType.SENSITIVITY, R.string.configbuilder_sensitivity_description)
                .label(R.string.configbuilder_sensitivity)
        )

    private val getScreenObjectives get() = SWScreen(injector, R.string.objectives)
        .skippable(false)
        .add(
            SWInfoText(injector)
                .label(R.string.startobjective)
        )
        .add(SWBreak(injector))
        .add(
            SWFragment(injector, this)
                .add(activity.supportFragmentManager.fragmentFactory.instantiate(ClassLoader.getSystemClassLoader(), (activePlugin.activeObjectives as PluginBase).pluginDescription.fragmentClass!!))
                //.add(ObjectivesFragment())
        )
        .validator { activePlugin.activeObjectives?.isStarted(Objectives.FIRST_OBJECTIVE) ?: false}
        .visibility { config.APS && !(activePlugin.activeObjectives?.isStarted(Objectives.FIRST_OBJECTIVE) ?: false) }

    private fun swDefinitionFull() = // List all the screens here
        add(screenSetupWizard)
            //.add(screenLanguage)
            .add(screenEula)
            .add(if (isRunningTest()) null else screenPermissionBattery) // cannot mock ask battery optimization
            .add(screenPermissionWindow)
            .add(screenPermissionBt)
            .add(screenPermissionStore)
            .add(screenMasterPassword)
            .add(screenImport)
            .add(privacy)
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
            .add(if (isRunningTest()) null else screenPermissionBattery) // cannot mock ask battery optimization
            .add(screenPermissionWindow)
            .add(screenPermissionBt)
            .add(screenPermissionStore)
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
            .add(if (isRunningTest()) null else screenPermissionBattery) // cannot mock ask battery optimization
            .add(screenPermissionWindow)
            .add(screenPermissionStore)
            .add(screenMasterPassword)
            .add(screenImport)
            .add(screenUnits)
            .add(displaySettings)
            .add(screenNsClient)
            //.add(screenBgSource)
            .add(screenPatientName)
}