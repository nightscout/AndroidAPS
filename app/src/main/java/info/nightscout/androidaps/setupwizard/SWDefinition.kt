package info.nightscout.androidaps.setupwizard

import android.Manifest
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.PreferencesActivity
import info.nightscout.androidaps.dialogs.ProfileSwitchDialog
import info.nightscout.androidaps.events.EventConfigBuilderChange
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesFragment
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesPlugin
import info.nightscout.androidaps.plugins.general.maintenance.ImportExportPrefs
import info.nightscout.androidaps.plugins.general.nsclient.NSClientPlugin
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientStatus
import info.nightscout.androidaps.plugins.general.nsclient.services.NSClientService
import info.nightscout.androidaps.plugins.profile.local.LocalProfileFragment
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.profile.ns.NSProfileFragment
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin
import info.nightscout.androidaps.setupwizard.elements.*
import info.nightscout.androidaps.setupwizard.events.EventSWUpdate
import info.nightscout.androidaps.utils.AndroidPermission
import info.nightscout.androidaps.utils.LocaleHelper.update
import info.nightscout.androidaps.utils.PasswordProtection
import info.nightscout.androidaps.utils.isRunningTest
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SWDefinition @Inject constructor(
    private val rxBus: RxBusWrapper,
    private val mainApp: MainApp,
    private val resourceHelper: ResourceHelper,
    private val sp: SP,
    private val profileFunction: ProfileFunction,
    private val localProfilePlugin: LocalProfilePlugin,
    private val configBuilderPlugin: ConfigBuilderPlugin,
    private val objectivesPlugin: ObjectivesPlugin,
    private val loopPlugin: LoopPlugin
) {

    var activity: AppCompatActivity? = null
    private val screens: MutableList<SWScreen> = ArrayList()

    fun getScreens(): List<SWScreen> {
        return screens
    }

    private fun add(newScreen: SWScreen?): SWDefinition {
        if (newScreen != null) screens.add(newScreen)
        return this
    }

    private val screenSetupWizard = SWScreen(R.string.nav_setupwizard)
        .add(SWInfotext()
            .label(R.string.welcometosetupwizard))
    private val screenLanguage = SWScreen(R.string.language)
        .skippable(false)
        .add(SWRadioButton()
            .option(R.array.languagesArray, R.array.languagesValues)
            .preferenceId(R.string.key_language).label(R.string.language)
            .comment(R.string.setupwizard_language_prompt))
        .validator {
            update(mainApp)
            sp.contains(R.string.key_language)
        }
    private val screenEula = SWScreen(R.string.end_user_license_agreement)
        .skippable(false)
        .add(SWInfotext()
            .label(R.string.end_user_license_agreement_text))
        .add(SWBreak())
        .add(SWButton()
            .text(R.string.end_user_license_agreement_i_understand)
            .visibility { !sp.getBoolean(R.string.key_i_understand, false) }
            .action {
                sp.putBoolean(R.string.key_i_understand, true)
                rxBus.send(EventSWUpdate(false))
            })
        .visibility { !sp.getBoolean(R.string.key_i_understand, false) }
        .validator { sp.getBoolean(R.string.key_i_understand, false) }
    private val screenUnits = SWScreen(R.string.units)
        .skippable(false)
        .add(SWRadioButton()
            .option(R.array.unitsArray, R.array.unitsValues)
            .preferenceId(R.string.key_units).label(R.string.units)
            .comment(R.string.setupwizard_units_prompt))
        .validator { sp.contains(R.string.key_units) }
    private val displaySettings = SWScreen(R.string.wear_display_settings)
        .skippable(false)
        .add(SWEditNumberWithUnits(Constants.LOWMARK * Constants.MGDL_TO_MMOLL, 3.0, 8.0)
            .preferenceId(R.string.key_low_mark)
            .updateDelay(5)
            .label(R.string.low_mark)
            .comment(R.string.low_mark_comment))
        .add(SWBreak())
        .add(SWEditNumberWithUnits(Constants.HIGHMARK * Constants.MGDL_TO_MMOLL, 5.0, 20.0)
            .preferenceId(R.string.key_high_mark)
            .updateDelay(5)
            .label(R.string.high_mark)
            .comment(R.string.high_mark_comment))
    private val screenPermissionBattery = SWScreen(R.string.permission)
        .skippable(false)
        .add(SWInfotext()
            .label(resourceHelper.gs(R.string.needwhitelisting, resourceHelper.gs(R.string.app_name))))
        .add(SWBreak())
        .add(SWButton()
            .text(R.string.askforpermission)
            .visibility { AndroidPermission.permissionNotGranted(activity, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) }
            .action { AndroidPermission.askForPermission(activity, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, AndroidPermission.CASE_BATTERY) })
        .visibility { AndroidPermission.permissionNotGranted(activity, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) }
        .validator { !AndroidPermission.permissionNotGranted(activity, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) }
    private val screenPermissionBt = SWScreen(R.string.permission)
        .skippable(false)
        .add(SWInfotext()
            .label(resourceHelper.gs(R.string.needlocationpermission)))
        .add(SWBreak())
        .add(SWButton()
            .text(R.string.askforpermission)
            .visibility { AndroidPermission.permissionNotGranted(activity, Manifest.permission.ACCESS_FINE_LOCATION) }
            .action { AndroidPermission.askForPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION, AndroidPermission.CASE_LOCATION) })
        .visibility { AndroidPermission.permissionNotGranted(activity, Manifest.permission.ACCESS_FINE_LOCATION) }
        .validator { !AndroidPermission.permissionNotGranted(activity, Manifest.permission.ACCESS_FINE_LOCATION) }
    private val screenPermissionStore = SWScreen(R.string.permission)
        .skippable(false)
        .add(SWInfotext()
            .label(resourceHelper.gs(R.string.needstoragepermission)))
        .add(SWBreak())
        .add(SWButton()
            .text(R.string.askforpermission)
            .visibility { AndroidPermission.permissionNotGranted(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) }
            .action { AndroidPermission.askForPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE, AndroidPermission.CASE_STORAGE) })
        .visibility { AndroidPermission.permissionNotGranted(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) }
        .validator { !AndroidPermission.permissionNotGranted(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) }
    private val screenImport = SWScreen(R.string.nav_import)
        .add(SWInfotext()
            .label(R.string.storedsettingsfound))
        .add(SWBreak())
        .add(SWButton()
            .text(R.string.nav_import)
            .action { ImportExportPrefs.importSharedPreferences(activity) })
        .visibility { ImportExportPrefs.file.exists() && !AndroidPermission.permissionNotGranted(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) }
    private val screenNsClient = SWScreen(R.string.nsclientinternal_title)
        .skippable(true)
        .add(SWInfotext()
            .label(R.string.nsclientinfotext))
        .add(SWBreak())
        .add(SWButton()
            .text(R.string.enable_nsclient)
            .action {
                NSClientPlugin.getPlugin().setPluginEnabled(PluginType.GENERAL, true)
                NSClientPlugin.getPlugin().setFragmentVisible(PluginType.GENERAL, true)
                configBuilderPlugin.processOnEnabledCategoryChanged(NSClientPlugin.getPlugin(), PluginType.GENERAL)
                configBuilderPlugin.storeSettings("SetupWizard")
                rxBus.send(EventConfigBuilderChange())
                rxBus.send(EventSWUpdate(true))
            }
            .visibility { !NSClientPlugin.getPlugin().isEnabled(PluginType.GENERAL) })
        .add(SWEditUrl()
            .preferenceId(R.string.key_nsclientinternal_url)
            .updateDelay(5)
            .label(R.string.nsclientinternal_url_title)
            .comment(R.string.nsclientinternal_url_dialogmessage))
        .add(SWEditString()
            .validator { text: String -> text.length >= 12 }
            .preferenceId(R.string.key_nsclientinternal_api_secret)
            .updateDelay(5)
            .label(R.string.nsclientinternal_secret_dialogtitle)
            .comment(R.string.nsclientinternal_secret_dialogmessage))
        .add(SWBreak())
        .add(SWEventListener(this, EventNSClientStatus::class.java)
            .label(R.string.status)
            .initialStatus(NSClientPlugin.getPlugin().status)
        )
        .add(SWBreak())
        .validator { NSClientPlugin.getPlugin().nsClientService != null && NSClientService.isConnected && NSClientService.hasWriteAuth }
        .visibility { !(NSClientPlugin.getPlugin().nsClientService != null && NSClientService.isConnected && NSClientService.hasWriteAuth) }
    private val screenAge = SWScreen(R.string.patientage)
        .skippable(false)
        .add(SWBreak())
        .add(SWRadioButton()
            .option(R.array.ageArray, R.array.ageValues)
            .preferenceId(R.string.key_age)
            .label(R.string.patientage)
            .comment(R.string.patientage_summary))
        .validator { sp.contains(R.string.key_age) }
    private val screenInsulin = SWScreen(R.string.configbuilder_insulin)
        .skippable(false)
        .add(SWPlugin()
            .option(PluginType.INSULIN, R.string.configbuilder_insulin_description)
            .makeVisible(false)
            .label(R.string.configbuilder_insulin))
        .add(SWBreak())
        .add(SWInfotext()
            .label(R.string.diawarning))
        .add(SWBreak())
        .add(SWButton()
            .text(R.string.insulinsourcesetup)
            .action {
                val plugin = configBuilderPlugin.activeInsulin as PluginBase?
                if (plugin != null) {
                    PasswordProtection.QueryPassword(activity, R.string.settings_password, "settings_password", Runnable {
                        val i = Intent(activity, PreferencesActivity::class.java)
                        i.putExtra("id", plugin.preferencesId)
                        activity?.startActivity(i)
                    }, null)
                }
            }
            .visibility { configBuilderPlugin.activeInsulin != null && (configBuilderPlugin.activeInsulin as PluginBase?)!!.preferencesId > 0 })
        .validator { configBuilderPlugin.activeInsulin != null }
    private val screenBgSource = SWScreen(R.string.configbuilder_bgsource)
        .skippable(false)
        .add(SWPlugin()
            .option(PluginType.BGSOURCE, R.string.configbuilder_bgsource_description)
            .label(R.string.configbuilder_bgsource))
        .add(SWBreak())
        .add(SWButton()
            .text(R.string.bgsourcesetup)
            .action {
                val plugin = configBuilderPlugin.activeBgSource as PluginBase?
                if (plugin != null) {
                    PasswordProtection.QueryPassword(activity, R.string.settings_password, "settings_password", Runnable {
                        val i = Intent(activity, PreferencesActivity::class.java)
                        i.putExtra("id", plugin.preferencesId)
                        activity!!.startActivity(i)
                    }, null)
                }
            }
            .visibility { configBuilderPlugin.activeBgSource != null && (configBuilderPlugin.activeBgSource as PluginBase?)!!.preferencesId > 0 })
        .validator { configBuilderPlugin.activeBgSource != null }
    private val screenProfile = SWScreen(R.string.configbuilder_profile)
        .skippable(false)
        .add(SWInfotext()
            .label(R.string.setupwizard_profile_description))
        .add(SWBreak())
        .add(SWPlugin()
            .option(PluginType.PROFILE, R.string.configbuilder_profile_description)
            .label(R.string.configbuilder_profile))
        .validator { configBuilderPlugin.activeProfileInterface != null }
    private val screenNsProfile = SWScreen(R.string.nsprofile)
        .skippable(false)
        .add(SWInfotext()
            .label(R.string.adjustprofileinns))
        .add(SWFragment(this)
            .add(NSProfileFragment()))
        .validator { NSProfilePlugin.getPlugin().profile != null && NSProfilePlugin.getPlugin().profile!!.getDefaultProfile() != null && NSProfilePlugin.getPlugin().profile!!.getDefaultProfile()!!.isValid("StartupWizard") }
        .visibility { NSProfilePlugin.getPlugin().isEnabled(PluginType.PROFILE) }
    private val screenLocalProfile = SWScreen(R.string.localprofile)
        .skippable(false)
        .add(SWFragment(this)
            .add(LocalProfileFragment()))
        .validator { localProfilePlugin.getProfile()?.getDefaultProfile()?.isValid("StartupWizard") == true }
        .visibility { localProfilePlugin.isEnabled(PluginType.PROFILE) }
    private val screenProfileSwitch = SWScreen(R.string.careportal_profileswitch)
        .skippable(false)
        .add(SWInfotext()
            .label(R.string.profileswitch_ismissing))
        .add(SWButton()
            .text(R.string.doprofileswitch)
            .action { ProfileSwitchDialog().show(activity!!.supportFragmentManager, "SetupWizard") })
        .validator { profileFunction.getProfile() != null }
        .visibility { profileFunction.getProfile() == null }
    private val screenPump = SWScreen(R.string.configbuilder_pump)
        .skippable(false)
        .add(SWPlugin()
            .option(PluginType.PUMP, R.string.configbuilder_pump_description)
            .label(R.string.configbuilder_pump))
        .add(SWBreak())
        .add(SWButton()
            .text(R.string.pumpsetup)
            .action {
                val plugin = configBuilderPlugin.activePump as PluginBase?
                if (plugin != null) {
                    PasswordProtection.QueryPassword(activity, R.string.settings_password, "settings_password", Runnable {
                        val i = Intent(activity, PreferencesActivity::class.java)
                        i.putExtra("id", plugin.preferencesId)
                        activity!!.startActivity(i)
                    }, null)
                }
            }
            .visibility { configBuilderPlugin.activePump != null && (configBuilderPlugin.activePump as PluginBase?)!!.preferencesId > 0 })
        .add(SWButton()
            .text(R.string.readstatus)
            .action { configBuilderPlugin.commandQueue.readStatus("Clicked connect to pump", null) }
            .visibility { configBuilderPlugin.activePump != null })
        .add(SWEventListener(this, EventPumpStatusChanged::class.java))
        .validator { configBuilderPlugin.activePump != null && configBuilderPlugin.activePump!!.isInitialized }
    private val screenAps = SWScreen(R.string.configbuilder_aps)
        .skippable(false)
        .add(SWInfotext()
            .label(R.string.setupwizard_aps_description))
        .add(SWBreak())
        .add(SWHtmlLink()
            .label("https://openaps.readthedocs.io/en/latest/"))
        .add(SWBreak())
        .add(SWPlugin()
            .option(PluginType.APS, R.string.configbuilder_aps_description)
            .label(R.string.configbuilder_aps))
        .add(SWButton()
            .text(R.string.apssetup)
            .action {
                val plugin = configBuilderPlugin.activeAPS as PluginBase?
                if (plugin != null) {
                    PasswordProtection.QueryPassword(activity, R.string.settings_password, "settings_password", Runnable {
                        val i = Intent(activity, PreferencesActivity::class.java)
                        i.putExtra("id", plugin.preferencesId)
                        activity!!.startActivity(i)
                    }, null)
                }
            }
            .visibility { configBuilderPlugin.activeAPS != null && (configBuilderPlugin.activeAPS as PluginBase?)!!.preferencesId > 0 })
        .validator { configBuilderPlugin.activeAPS != null }
        .visibility { Config.APS }
    private val screenApsMode = SWScreen(R.string.apsmode_title)
        .skippable(false)
        .add(SWRadioButton()
            .option(R.array.aps_modeArray, R.array.aps_modeValues)
            .preferenceId(R.string.key_aps_mode).label(R.string.apsmode_title)
            .comment(R.string.setupwizard_preferred_aps_mode))
        .validator { sp.contains(R.string.key_aps_mode) }
    private val screenLoop = SWScreen(R.string.configbuilder_loop)
        .skippable(false)
        .add(SWInfotext()
            .label(R.string.setupwizard_loop_description))
        .add(SWBreak())
        .add(SWButton()
            .text(R.string.enableloop)
            .action {
                loopPlugin.setPluginEnabled(PluginType.LOOP, true)
                loopPlugin.setFragmentVisible(PluginType.LOOP, true)
                configBuilderPlugin.processOnEnabledCategoryChanged(loopPlugin, PluginType.LOOP)
                configBuilderPlugin.storeSettings("SetupWizard")
                rxBus.send(EventConfigBuilderChange())
                rxBus.send(EventSWUpdate(true))
            }
            .visibility { !loopPlugin.isEnabled(PluginType.LOOP) })
        .validator { loopPlugin.isEnabled(PluginType.LOOP) }
        .visibility { !loopPlugin.isEnabled(PluginType.LOOP) && Config.APS }
    private val screenSensitivity = SWScreen(R.string.configbuilder_sensitivity)
        .skippable(false)
        .add(SWInfotext()
            .label(R.string.setupwizard_sensitivity_description))
        .add(SWHtmlLink()
            .label(R.string.setupwizard_sensitivity_url))
        .add(SWBreak())
        .add(SWPlugin()
            .option(PluginType.SENSITIVITY, R.string.configbuilder_sensitivity_description)
            .label(R.string.configbuilder_sensitivity))
        .add(SWBreak())
        .add(SWButton()
            .text(R.string.sensitivitysetup)
            .action {
                val plugin = configBuilderPlugin.activeSensitivity as PluginBase?
                if (plugin != null) {
                    PasswordProtection.QueryPassword(activity, R.string.settings_password, "settings_password", Runnable {
                        val i = Intent(activity, PreferencesActivity::class.java)
                        i.putExtra("id", plugin.preferencesId)
                        activity!!.startActivity(i)
                    }, null)
                }
            }
            .visibility { configBuilderPlugin.activeSensitivity != null && (configBuilderPlugin.activeSensitivity as PluginBase?)!!.preferencesId > 0 })
        .validator { configBuilderPlugin.activeSensitivity != null }
    private val getScreenObjectives = SWScreen(R.string.objectives)
        .skippable(false)
        .add(SWInfotext()
            .label(R.string.startobjective))
        .add(SWBreak())
        .add(SWFragment(this)
            .add(ObjectivesFragment()))
        .validator { objectivesPlugin.objectives[objectivesPlugin.FIRST_OBJECTIVE].isStarted }
        .visibility { !objectivesPlugin.objectives[objectivesPlugin.FIRST_OBJECTIVE].isStarted && Config.APS }

    private fun SWDefinitionFull() { // List all the screens here
        add(screenSetupWizard)
            .add(screenLanguage)
            .add(screenEula)
            .add(if (isRunningTest()) null else screenPermissionBattery) // cannot mock ask battery optimalization
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
    }

    private fun SWDefinitionPumpControl() { // List all the screens here
        add(screenSetupWizard)
            .add(screenLanguage)
            .add(screenEula)
            .add(if (isRunningTest()) null else screenPermissionBattery) // cannot mock ask battery optimalization
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
    }

    private fun SWDefinitionNSClient() { // List all the screens here
        add(screenSetupWizard)
            .add(screenLanguage)
            .add(screenEula)
            .add(if (isRunningTest()) null else screenPermissionBattery) // cannot mock ask battery optimalization
            .add(screenPermissionStore)
            .add(screenImport)
            .add(screenUnits)
            .add(displaySettings)
            .add(screenNsClient)
            .add(screenBgSource)
            .add(screenAge)
            .add(screenInsulin)
            .add(screenSensitivity)
    }

    init {
        if (Config.APS) SWDefinitionFull() else if (Config.PUMPCONTROL) SWDefinitionPumpControl() else if (Config.NSCLIENT) SWDefinitionNSClient()
    }
}