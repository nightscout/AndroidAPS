package app.aaps.plugins.configuration.setupwizard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.androidPermissions.AndroidPermission
import app.aaps.core.interfaces.configuration.Config
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
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventAAPSDirectorySelected
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventSWRLStatus
import app.aaps.core.interfaces.rx.events.EventSWSyncStatus
import app.aaps.core.interfaces.rx.events.EventSWUpdate
import app.aaps.core.interfaces.rx.events.EventStatus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.utils.isRunningTest
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import app.aaps.plugins.configuration.configBuilder.events.EventConfigBuilderUpdateGui
import app.aaps.plugins.configuration.maintenance.MaintenancePlugin
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
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SWDefinition @Inject constructor(
    private val rxBus: RxBus,
    private val context: Context,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val commandQueue: CommandQueue,
    private val importExportPrefs: ImportExportPrefs,
    private val androidPermission: AndroidPermission,
    private val cryptoUtil: CryptoUtil,
    private val config: Config,
    private val hardLimits: HardLimits,
    private val uiInteraction: UiInteraction,
    private val maintenancePlugin: MaintenancePlugin,
    private val aapsSchedulers: AapsSchedulers,
    private val swScreenProvider: Provider<SWScreen>,
    private val swEventListenerProvider: Provider<SWEventListener>,
    private val swBreakProvider: Provider<SWBreak>,
    private val swButtonProvider: Provider<SWButton>,
    private val swEditEncryptedPasswordProvider: Provider<SWEditEncryptedPassword>,
    private val swEditIntNumberProvider: Provider<SWEditIntNumber>,
    private val swEditNumberProvider: Provider<SWEditNumber>,
    private val swEditNumberWithUnitsProvider: Provider<SWEditNumberWithUnits>,
    private val swEditStringProvider: Provider<SWEditString>,
    private val swFragmentProvider: Provider<SWFragment>,
    private val swHtmlLinkProvider: Provider<SWHtmlLink>,
    private val swInfoTextProvider: Provider<SWInfoText>,
    private val swPluginProvider: Provider<SWPlugin>,
    private val swRadioButtonProvider: Provider<SWRadioButton>
) {

    var activity: AppCompatActivity? = null
    private val disposable = CompositeDisposable()
    private val screens: MutableList<SWScreen> = ArrayList()

    private fun requireActivity() = activity ?: error("Activity is null")

    fun getScreens(): List<SWScreen> {
        if (screens.isEmpty()) {
            when {
                config.APS -> swDefinitionFull()
                config.PUMPCONTROL -> swDefinitionPumpControl()
                config.AAPSCLIENT -> swDefinitionNSClient()
            }
            disposable += rxBus
                .toObservable(EventConfigBuilderUpdateGui::class.java)
                .observeOn(aapsSchedulers.main)
                .subscribe { (activity as SetupWizardActivity).prepareLayout() }
        }
        return screens
    }

    private fun add(newScreen: SWScreen?): SWDefinition {
        if (newScreen != null) screens.add(newScreen)
        return this
    }

    val listeners = ArrayList<SWEventListener>()
    fun addListener(listener: SWEventListener) {
        listeners.add(listener)
        disposable += rxBus
            .toObservable(listener.clazz)
            .observeOn(aapsSchedulers.main)
            .subscribe { event ->
                processListeners(event as EventStatus)
            }
    }

    private fun processListeners(event: Event) {
        if (event is EventStatus)
            listeners.forEach { it.updateFromEvent(event, requireActivity()) }
    }

    private val screenSetupWizard
        get() = swScreenProvider.get().with(R.string.welcome)
            .add(swInfoTextProvider.get().label(R.string.welcometosetupwizard))

    private val screenEula
        get() = swScreenProvider.get().with(R.string.end_user_license_agreement)
            .skippable(false)
            .add(swInfoTextProvider.get().label(R.string.end_user_license_agreement_text))
            .add(swBreakProvider.get())
            .add(
                swButtonProvider.get()
                    .text(R.string.end_user_license_agreement_i_understand)
                    .visibility { !preferences.get(BooleanNonKey.SetupWizardIUnderstand) }
                    .action {
                        preferences.put(BooleanNonKey.SetupWizardIUnderstand, true)
                        rxBus.send(EventSWUpdate(false))
                    })
            .visibility { !preferences.get(BooleanNonKey.SetupWizardIUnderstand) }
            .validator { preferences.get(BooleanNonKey.SetupWizardIUnderstand) }

    private val screenUnits
        get() = swScreenProvider.get().with(R.string.units)
            .skippable(false)
            .add(
                swRadioButtonProvider.get()
                    .option(uiInteraction.unitsEntries, uiInteraction.unitsValues)
                    .preference(StringKey.GeneralUnits).label(R.string.units)
                    .comment(R.string.setupwizard_units_prompt)
            )
            .validator { preferences.getIfExists(StringKey.GeneralUnits) != null }

    private val displaySettings
        get() = swScreenProvider.get().with(R.string.display_settings)
            .skippable(false)
            .add(
                swEditNumberWithUnitsProvider.get()
                    .preference(UnitDoubleKey.OverviewLowMark)
                    .updateDelay(5)
                    .label(R.string.low_mark)
                    .comment(R.string.low_mark_comment)
            )
            .add(swBreakProvider.get())
            .add(
                swEditNumberWithUnitsProvider.get()
                    .preference(UnitDoubleKey.OverviewHighMark)
                    .updateDelay(5)
                    .label(R.string.high_mark)
                    .comment(R.string.high_mark_comment)
            )

    private val screenPermissionWindow
        get() = swScreenProvider.get().with(R.string.permission)
            .skippable(false)
            .add(swInfoTextProvider.get().label(rh.gs(R.string.need_system_window_permission)))
            .add(
                swButtonProvider.get()
                     .text(R.string.askforpermission)
                    .visibility { !Settings.canDrawOverlays(requireActivity()) }
                    .action { requireActivity().startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, ("package:" + requireActivity().packageName).toUri())) })
            .add(swBreakProvider.get())
            .add(swInfoTextProvider.get().label(rh.gs(R.string.need_whitelisting, rh.gs(config.appName))))
            .add(
                swButtonProvider.get()
                     .text(R.string.askforpermission)
                     .visibility { androidPermission.permissionNotGranted(context, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) }
                    .action { androidPermission.askForPermission(requireActivity(), Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) })
            .add(swBreakProvider.get())
            .add(swInfoTextProvider.get().label(rh.gs(R.string.need_storage_permission)))
            .add(
                swButtonProvider.get()
                     .text(R.string.askforpermission)
                    .visibility { androidPermission.permissionNotGranted(requireActivity(), Manifest.permission.READ_EXTERNAL_STORAGE) }
                    .action { androidPermission.askForPermission(requireActivity(), Manifest.permission.READ_EXTERNAL_STORAGE) })
            .add(swBreakProvider.get())
            .add(swInfoTextProvider.get().label(rh.gs(R.string.select_aaps_directory)))
            .add(
                swButtonProvider.get()
                     .text(R.string.aaps_directory)
                     .visibility { preferences.getIfExists(StringKey.AapsDirectoryUri) == null }
                    .action { maintenancePlugin.selectAapsDirectory(requireActivity() as DaggerAppCompatActivityWithResult) })
            .add(swBreakProvider.get())
            .add(swEventListenerProvider.get().with(EventAAPSDirectorySelected::class.java, this).label(app.aaps.core.ui.R.string.settings).initialStatus(preferences.get(StringKey.AapsDirectoryUri)))
            .add(swBreakProvider.get())
            .visibility {
                !Settings.canDrawOverlays(requireActivity()) ||
                    androidPermission.permissionNotGranted(requireActivity(), Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) ||
                    androidPermission.permissionNotGranted(requireActivity(), Manifest.permission.READ_EXTERNAL_STORAGE) ||
                    preferences.getIfExists(StringKey.AapsDirectoryUri) == null
            }
            .validator {
                Settings.canDrawOverlays(requireActivity()) &&
                    !androidPermission.permissionNotGranted(requireActivity(), Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) &&
                    !androidPermission.permissionNotGranted(requireActivity(), Manifest.permission.READ_EXTERNAL_STORAGE) &&
                    preferences.getIfExists(StringKey.AapsDirectoryUri) != null
            }

    private val screenPermissionBt
        get() = swScreenProvider.get().with(R.string.permission)
            .skippable(false)
            .add(swInfoTextProvider.get().label(rh.gs(R.string.need_location_permission)))
            .add(swBreakProvider.get())
            .add(
                swButtonProvider.get()
                     .text(R.string.askforpermission)
                    .visibility { androidPermission.permissionNotGranted(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) }
                    .action { androidPermission.askForPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) })
            .add(swBreakProvider.get())
            .add(swInfoTextProvider.get().label(rh.gs(R.string.need_background_location_permission)))
            .add(swBreakProvider.get())
            .add(
                swButtonProvider.get()
                     .text(R.string.askforpermission)
                    .visibility { androidPermission.permissionNotGranted(requireActivity(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
                    .action { androidPermission.askForPermission(requireActivity(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) })
            .visibility { androidPermission.permissionNotGranted(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) || androidPermission.permissionNotGranted(requireActivity(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
            .validator { !androidPermission.permissionNotGranted(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) && !androidPermission.permissionNotGranted(requireActivity(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) }

    private val screenImport
        get() = swScreenProvider.get().with(R.string.import_setting)
            .add(swInfoTextProvider.get().label(R.string.storedsettingsfound))
            .add(swBreakProvider.get())
            .add(swButtonProvider.get().text(R.string.import_setting).action { importExportPrefs.importSharedPreferences(requireActivity()) })
            .visibility { importExportPrefs.prefsFileExists() && !androidPermission.permissionNotGranted(requireActivity(), Manifest.permission.READ_EXTERNAL_STORAGE) }

    private val screenNsClient
        get() = swScreenProvider.get().with(R.string.configbuilder_sync)
            .skippable(true)
            .add(swPluginProvider.get().option(PluginType.SYNC, R.string.configbuilder_sync_description))
            .add(swBreakProvider.get())
            .add(swInfoTextProvider.get().label(R.string.syncinfotext))
            .add(swBreakProvider.get())
            .add(swEventListenerProvider.get().with(EventSWSyncStatus::class.java, this).label(R.string.status).initialStatus(activePlugin.activeNsClient?.status ?: ""))
            .validator { activePlugin.activeNsClient?.connected == true && activePlugin.activeNsClient?.hasWritePermission == true }

    private val screenPatientName
        get() = swScreenProvider.get().with(R.string.patient_name)
            .skippable(true)
            .add(swInfoTextProvider.get().label(R.string.patient_name_summary))
            .add(swEditStringProvider.get().validator(String::isNotEmpty).preference(StringKey.GeneralPatientName))

    private val screenMasterPassword
        get() = swScreenProvider.get().with(app.aaps.core.ui.R.string.master_password)
            .skippable(false)
            .add(swInfoTextProvider.get().label(app.aaps.core.ui.R.string.master_password))
            .add(swEditEncryptedPasswordProvider.get().preference(StringKey.ProtectionMasterPassword))
            .add(swBreakProvider.get())
            .add(swInfoTextProvider.get().label(R.string.master_password_summary))
            .validator { !cryptoUtil.checkPassword("", preferences.get(StringKey.ProtectionMasterPassword)) }

    private val screenAge
        get() = swScreenProvider.get().with(app.aaps.core.ui.R.string.patient_type)
            .skippable(false)
            .add(swBreakProvider.get())
            .add(
                swRadioButtonProvider.get()
                    .option(hardLimits.ageEntries(), hardLimits.ageEntryValues())
                    .preference(StringKey.SafetyAge)
                    .label(app.aaps.core.ui.R.string.patient_type)
                    .comment(app.aaps.core.ui.R.string.patient_age_summary)
            )
            .add(swBreakProvider.get())
            .add(
                swEditNumberProvider.get()
                    .preference(DoubleKey.SafetyMaxBolus)
                    .updateDelay(5)
                    .label(app.aaps.core.ui.R.string.max_bolus_title)
                    .comment(R.string.common_values)
            )
            .add(
                swEditIntNumberProvider.get()
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
        get() = swScreenProvider.get().with(app.aaps.core.ui.R.string.configbuilder_insulin)
            .skippable(false)
            .add(swPluginProvider.get().option(PluginType.INSULIN, R.string.configbuilder_insulin_description))
            .add(swBreakProvider.get())
            .add(swInfoTextProvider.get().label(R.string.diawarning))

    private val screenBgSource
        get() = swScreenProvider.get().with(R.string.configbuilder_bgsource)
            .skippable(false)
            .add(swPluginProvider.get().option(PluginType.BGSOURCE, R.string.configbuilder_bgsource_description))
            .add(swBreakProvider.get())

    private val screenLocalProfile
        get() = swScreenProvider.get().with(R.string.profile)
            .skippable(false)
            .add(swFragmentProvider.get().with((activePlugin.activeProfileSource as PluginBase).pluginDescription.fragmentClass!!))
            .validator {
                activePlugin.activeProfileSource.profile?.getDefaultProfile()
                    ?.let { ProfileSealed.Pure(it, activePlugin).isValid("StartupWizard", activePlugin.activePump, config, rh, rxBus, hardLimits, false).isValid } == true
            }
            .visibility { (activePlugin.activeProfileSource as PluginBase).isEnabled() }

    private val screenProfileSwitch
        get() = swScreenProvider.get().with(app.aaps.core.ui.R.string.careportal_profileswitch)
            .skippable(false)
            .add(swInfoTextProvider.get().label(app.aaps.core.ui.R.string.profileswitch_ismissing))
            .add(
                swButtonProvider.get()
                     .text(R.string.doprofileswitch)
                    .action { uiInteraction.runProfileSwitchDialog(requireActivity().supportFragmentManager) })
            .validator { profileFunction.getRequestedProfile() != null }
            .visibility { profileFunction.getRequestedProfile() == null }

    private val screenPump
        get() = swScreenProvider.get().with(R.string.configbuilder_pump)
            .skippable(false)
            .add(swPluginProvider.get().option(PluginType.PUMP, R.string.configbuilder_pump_description))
            .add(swBreakProvider.get())
            .add(swInfoTextProvider.get().label(R.string.setupwizard_pump_pump_not_initialized).visibility { !isPumpInitialized() })
            .add( // Omnipod Eros only
                swInfoTextProvider.get()
                    .label(R.string.setupwizard_pump_waiting_for_riley_link_connection)
                    .visibility { activePlugin.activePump.let { it is OmnipodEros && !it.isRileyLinkReady() } }
            )
            .add( // Omnipod Eros only
                swEventListenerProvider.get().with(EventSWRLStatus::class.java, this)
                    .label(R.string.setupwizard_pump_riley_link_status)
                    .visibility { activePlugin.activePump is OmnipodEros })
            .add(
                swButtonProvider.get()
                     .text(R.string.readstatus)
                     .action { commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.clicked_connect_to_pump), null) }
                     .visibility {
                         // Hide for Omnipod and Medtrum, because as we don't require a Pod/Patch to be paired in the setup wizard,
                         // Getting the status might not be possible
                         activePlugin.activePump !is OmnipodEros && activePlugin.activePump !is OmnipodDash && activePlugin.activePump !is Medtrum
                     })
            .add(
                swEventListenerProvider.get().with(EventPumpStatusChanged::class.java, this)
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
        get() = swScreenProvider.get().with(R.string.configbuilder_aps)
            .skippable(false)
            .add(swInfoTextProvider.get().label(R.string.setupwizard_aps_description))
            .add(swBreakProvider.get())
            .add(swPluginProvider.get().option(PluginType.APS, R.string.configbuilder_aps_description))
            .add(swBreakProvider.get())
            .add(swHtmlLinkProvider.get().label("https://wiki.aaps.app"))
            .add(swBreakProvider.get())

    private val screenSensitivity
        get() = swScreenProvider.get().with(R.string.configbuilder_sensitivity)
            .skippable(false)
            .add(swInfoTextProvider.get().label(R.string.setupwizard_sensitivity_description))
            .add(swHtmlLinkProvider.get().label(R.string.setupwizard_sensitivity_url))
            .add(swBreakProvider.get())
            .add(swPluginProvider.get().option(PluginType.SENSITIVITY, R.string.configbuilder_sensitivity_description))

    private val getScreenObjectives
        get() = swScreenProvider.get().with(app.aaps.core.ui.R.string.objectives)
            .skippable(false)
            .add(swInfoTextProvider.get().label(R.string.startobjective))
            .add(swBreakProvider.get())
            .add(swFragmentProvider.get().with((activePlugin.activeObjectives as PluginBase).pluginDescription.fragmentClass!!))
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