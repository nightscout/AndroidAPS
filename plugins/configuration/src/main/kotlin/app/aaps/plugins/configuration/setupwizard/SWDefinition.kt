package app.aaps.plugins.configuration.setupwizard

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.Medtrum
import app.aaps.core.interfaces.pump.OmnipodDash
import app.aaps.core.interfaces.pump.OmnipodEros
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventSWRLStatus
import app.aaps.core.interfaces.rx.events.EventSWSyncStatus
import app.aaps.core.interfaces.rx.events.EventSWUpdate
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.setupwizard.elements.SWBreak
import app.aaps.plugins.configuration.setupwizard.elements.SWButton
import app.aaps.plugins.configuration.setupwizard.elements.SWEditEncryptedPassword
import app.aaps.plugins.configuration.setupwizard.elements.SWEditIntNumber
import app.aaps.plugins.configuration.setupwizard.elements.SWEditNumber
import app.aaps.plugins.configuration.setupwizard.elements.SWEditNumberWithUnits
import app.aaps.plugins.configuration.setupwizard.elements.SWEditString
import app.aaps.plugins.configuration.setupwizard.elements.SWHtmlLink
import app.aaps.plugins.configuration.setupwizard.elements.SWInfoText
import app.aaps.plugins.configuration.setupwizard.elements.SWPermissions
import app.aaps.plugins.configuration.setupwizard.elements.SWPlugin
import app.aaps.plugins.configuration.setupwizard.elements.SWRadioButton
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SWDefinition @Inject constructor(
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val localProfileManager: LocalProfileManager,
    private val commandQueue: CommandQueue,
    private val fileListProvider: FileListProvider,
    private val cryptoUtil: CryptoUtil,
    private val config: Config,
    private val hardLimits: HardLimits,
    private val uiInteraction: UiInteraction,
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
    private val swHtmlLinkProvider: Provider<SWHtmlLink>,
    private val swInfoTextProvider: Provider<SWInfoText>,
    private val swPermissionsProvider: Provider<SWPermissions>,
    private val swPluginProvider: Provider<SWPlugin>,
    private val swRadioButtonProvider: Provider<SWRadioButton>
) {

    var onImportSettings: (() -> Unit)? = null
    var onPluginPreferences: ((pluginId: String) -> Unit)? = null
    var onSetMasterPassword: (() -> Unit)? = null
    var onManageInsulin: (() -> Unit)? = null
    var onManageProfile: (() -> Unit)? = null
    var onProfileSwitch: (() -> Unit)? = null
    var onRunObjectives: (() -> Unit)? = null
    var onRequestDirectoryAccess: (() -> Unit)? = null
    var onRequestPermission: ((PermissionGroup) -> Unit)? = null
    var permissionItems: (() -> List<Pair<PermissionGroup, Boolean>>)? = null
    var isDirectoryAccessGranted: (() -> Boolean)? = null
    private val disposable = CompositeDisposable()
    private val screens: MutableList<SWScreen> = ArrayList()

    private fun pluginOption(pType: PluginType, @androidx.annotation.StringRes description: Int): SWPlugin =
        swPluginProvider.get()
            .option(pType, description)
            .onPreferences { pluginId -> onPluginPreferences?.invoke(pluginId) }

    fun getScreens(): List<SWScreen> {
        if (screens.isEmpty()) {
            when {
                config.APS -> swDefinitionFull()
                config.PUMPCONTROL -> swDefinitionPumpControl()
                config.AAPSCLIENT -> swDefinitionNSClient()
            }
            disposable += rxBus
                .toObservable(EventConfigBuilderChange::class.java)
                .observeOn(aapsSchedulers.main)
                .subscribe { rxBus.send(EventSWUpdate(true)) }
        }
        return screens
    }

    private fun add(newScreen: SWScreen?): SWDefinition {
        if (newScreen != null) screens.add(newScreen)
        return this
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
            .add(swInfoTextProvider.get().label(R.string.setupwizard_units_prompt))
            .add(
                swRadioButtonProvider.get()
                    .preference(StringKey.GeneralUnits)
            )
            .validator { preferences.get(StringKey.GeneralUnits).isNotEmpty() }

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

    private val screenPermissions
        get() = swScreenProvider.get().with(R.string.setupwizard_permissions)
            .skippable(true)
            .add(swInfoTextProvider.get().label(R.string.setupwizard_permissions_info))
            .add(swBreakProvider.get())
            .add(swPermissionsProvider.get().with(this))

    private val screenImport
        get() = swScreenProvider.get().with(app.aaps.core.ui.R.string.import_setting)
            .add(swInfoTextProvider.get().label(R.string.storedsettingsfound))
            .add(swBreakProvider.get())
            .add(swButtonProvider.get().text(app.aaps.core.ui.R.string.import_setting).action {
                onImportSettings?.invoke()
            })
            .visibility { fileListProvider.listPreferenceFiles().isNotEmpty() }

    private val screenNsClient
        get() = swScreenProvider.get().with(app.aaps.core.ui.R.string.configbuilder_sync)
            .skippable(true)
            .add(pluginOption(PluginType.SYNC, R.string.configbuilder_sync_description))
            .add(swBreakProvider.get())
            .add(swInfoTextProvider.get().label(R.string.syncinfotext))
            .add(swBreakProvider.get())
            .add(swEventListenerProvider.get().with(EventSWSyncStatus::class.java).label(R.string.status).initialStatus(activePlugin.activeNsClient?.status ?: ""))
            .validator { activePlugin.activeNsClient?.connected == true && activePlugin.activeNsClient?.hasWritePermission == true }

    private val screenPatientName
        get() = swScreenProvider.get().with(app.aaps.core.keys.R.string.pref_title_patient_name)
            .skippable(true)
            .add(swInfoTextProvider.get().label(app.aaps.core.keys.R.string.pref_summary_patient_name))
            .add(swEditStringProvider.get().validator(String::isNotEmpty).preference(StringKey.GeneralPatientName))

    private val screenMasterPassword
        get() = swScreenProvider.get().with(app.aaps.core.keys.R.string.master_password)
            .skippable(false)
            .add(swEditEncryptedPasswordProvider.get().preference(StringKey.ProtectionMasterPassword).onSetPassword { onSetMasterPassword?.invoke() })
            .add(swBreakProvider.get())
            .add(swInfoTextProvider.get().label(R.string.master_password_summary))
            .validator { !cryptoUtil.checkPassword("", preferences.get(StringKey.ProtectionMasterPassword)) }

    private val screenAge
        get() = swScreenProvider.get().with(app.aaps.core.ui.R.string.patient_type)
            .skippable(false)
            .add(swInfoTextProvider.get().label(app.aaps.core.ui.R.string.patient_age_summary))
            .add(
                swRadioButtonProvider.get()
                    .option(hardLimits.ageEntries(), hardLimits.ageEntryValues())
                    .preference(StringKey.SafetyAge)
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
                preferences.get(StringKey.SafetyAge).isNotEmpty()
                    && preferences.get(DoubleKey.SafetyMaxBolus) > 0
                    && preferences.get(IntKey.SafetyMaxCarbs) > 0
            }

    private val screenInsulin
        get() = swScreenProvider.get().with(app.aaps.core.ui.R.string.configbuilder_insulin)
            .skippable(false)
            .add(swInfoTextProvider.get().label(R.string.diawarning))
            .add(swBreakProvider.get())
            .add(swButtonProvider.get().text(app.aaps.core.ui.R.string.configbuilder_insulin).action { onManageInsulin?.invoke() })

    private val screenBgSource
        get() = swScreenProvider.get().with(app.aaps.core.ui.R.string.configbuilder_bgsource)
            .skippable(false)
            .add(pluginOption(PluginType.BGSOURCE, R.string.configbuilder_bgsource_description))
            .add(swBreakProvider.get())

    private val screenProfile
        get() = swScreenProvider.get().with(app.aaps.core.ui.R.string.profile)
            .skippable(false)
            .add(swInfoTextProvider.get().label(R.string.setupwizard_profile_info))
            .add(swBreakProvider.get())
            .add(swButtonProvider.get().text(app.aaps.core.ui.R.string.profile).action { onManageProfile?.invoke() })
            .validator { localProfileManager.numOfProfiles > 0 && localProfileManager.isValid() }

    private val screenProfileSwitch
        get() = swScreenProvider.get().with(app.aaps.core.ui.R.string.careportal_profileswitch)
            .skippable(false)
            .add(swInfoTextProvider.get().label(app.aaps.core.ui.R.string.profileswitch_ismissing))
            .add(
                swButtonProvider.get()
                    .text(R.string.doprofileswitch)
                    .action { onProfileSwitch?.invoke() })
            .validator { runBlocking { profileFunction.getRequestedProfile() } != null }
            .visibility { runBlocking { profileFunction.getRequestedProfile() } == null }

    private val screenPump
        get() = swScreenProvider.get().with(app.aaps.core.ui.R.string.configbuilder_pump)
            .skippable(false)
            .add(pluginOption(PluginType.PUMP, R.string.configbuilder_pump_description))
            .add(swBreakProvider.get())
            .add(swInfoTextProvider.get().label(R.string.setupwizard_pump_pump_not_initialized).visibility { !isPumpInitialized() })
            .add( // Omnipod Eros only
                swInfoTextProvider.get()
                    .label(R.string.setupwizard_pump_waiting_for_riley_link_connection)
                    .visibility { activePlugin.activePumpInternal.let { it is OmnipodEros && !it.isRileyLinkReady() } }
            )
            .add( // Omnipod Eros only
                swEventListenerProvider.get().with(EventSWRLStatus::class.java)
                    .label(R.string.setupwizard_pump_riley_link_status)
                    .visibility { activePlugin.activePumpInternal is OmnipodEros })
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
                swEventListenerProvider.get().with(EventPumpStatusChanged::class.java)
                    .visibility { activePlugin.activePumpInternal !is OmnipodEros && activePlugin.activePumpInternal !is OmnipodDash && activePlugin.activePumpInternal !is Medtrum })
            .validator { isPumpInitialized() }

    private fun isPumpInitialized(): Boolean {
        val activePump = activePlugin.activePumpInternal

        // For Omnipod and Medtrum, activating a Pod/Patch can be done after set up through the pump fragment
        // For the Eros, consider the pump initialized when a RL has been configured successfully
        // For all others, consider the pump setup without any extra conditions
        return activePump.isInitialized()
            || (activePump is OmnipodEros && activePump.isRileyLinkReady())
            || activePump is OmnipodDash
            || activePump is Medtrum
    }

    private val screenAps
        get() = swScreenProvider.get().with(app.aaps.core.ui.R.string.configbuilder_aps)
            .skippable(false)
            .add(swInfoTextProvider.get().label(R.string.setupwizard_aps_description))
            .add(swBreakProvider.get())
            .add(pluginOption(PluginType.APS, R.string.configbuilder_aps_description))
            .add(swBreakProvider.get())
            .add(swHtmlLinkProvider.get().label("https://wiki.aaps.app"))
            .add(swBreakProvider.get())

    private val screenSensitivity
        get() = swScreenProvider.get().with(app.aaps.core.ui.R.string.configbuilder_sensitivity)
            .skippable(false)
            .add(swInfoTextProvider.get().label(R.string.setupwizard_sensitivity_description))
            .add(swHtmlLinkProvider.get().label(R.string.setupwizard_sensitivity_url))
            .add(swBreakProvider.get())
            .add(pluginOption(PluginType.SENSITIVITY, R.string.configbuilder_sensitivity_description))

    private val getScreenObjectives
        get() = swScreenProvider.get().with(app.aaps.core.ui.R.string.objectives)
            .skippable(false)
            .add(swInfoTextProvider.get().label(R.string.startobjective))
            .add(swBreakProvider.get())
            .add(swButtonProvider.get().text(R.string.open_objectives).action { onRunObjectives?.invoke() })
            .validator { activePlugin.activeObjectives?.isStarted(Objectives.FIRST_OBJECTIVE) == true }
            .visibility { config.APS && activePlugin.activeObjectives?.allAccomplished == false }

    private fun swDefinitionFull() = // List all the screens here
        add(screenSetupWizard)
            .add(screenEula)
            .add(screenPermissions)
            .add(screenMasterPassword)
            .add(screenImport)
            .add(screenUnits)
            .add(displaySettings)

            .add(screenNsClient)
            .add(screenPatientName)
            .add(screenAge)
            .add(screenInsulin)
            .add(screenBgSource)

            .add(screenProfile)
            .add(screenProfileSwitch)
            .add(screenPump)
            .add(screenAps)
            .add(screenSensitivity)
            .add(getScreenObjectives)

    private fun swDefinitionPumpControl() = // List all the screens here
        add(screenSetupWizard)
            .add(screenEula)
            .add(screenPermissions)
            .add(screenMasterPassword)
            .add(screenImport)
            .add(screenUnits)
            .add(displaySettings)

            .add(screenNsClient)
            .add(screenPatientName)
            .add(screenAge)
            .add(screenInsulin)
            .add(screenBgSource)

            .add(screenProfile)
            .add(screenProfileSwitch)
            .add(screenPump)
            .add(screenSensitivity)

    private fun swDefinitionNSClient() = // List all the screens here
        add(screenSetupWizard)
            .add(screenEula)
            .add(screenPermissions)
            .add(screenMasterPassword)
            .add(screenImport)
            .add(screenUnits)
            .add(displaySettings)

            .add(screenNsClient)
            //.add(screenBgSource)
            .add(screenPatientName)
}