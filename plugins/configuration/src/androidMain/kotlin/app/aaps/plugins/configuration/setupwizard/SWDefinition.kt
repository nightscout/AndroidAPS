package app.aaps.plugins.configuration.setupwizard

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.configuration.ConfigurationStrings
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.pump.Medtrum
import app.aaps.core.interfaces.pump.OmnipodDash
import app.aaps.core.interfaces.pump.OmnipodEros
import app.aaps.core.interfaces.pump.comment
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventSWRLStatus
import app.aaps.core.interfaces.rx.events.EventSWSyncStatus
import app.aaps.core.interfaces.rx.events.EventSWUpdate
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.plugins.configuration.setupwizard.elements.SWBreak
import app.aaps.plugins.configuration.setupwizard.elements.SWButton
import app.aaps.plugins.configuration.setupwizard.elements.SWEditEncryptedPassword
import app.aaps.plugins.configuration.setupwizard.elements.SWEditIntNumber
import app.aaps.plugins.configuration.setupwizard.elements.SWEditNumber
import app.aaps.plugins.configuration.setupwizard.elements.SWEditNumberWithUnits
import app.aaps.plugins.configuration.setupwizard.elements.SWEditString
import app.aaps.plugins.configuration.setupwizard.elements.SWHtmlLink
import app.aaps.plugins.configuration.setupwizard.elements.SWInfoText
import app.aaps.plugins.configuration.setupwizard.elements.SWPairingStatus
import app.aaps.plugins.configuration.setupwizard.elements.SWPermissions
import app.aaps.plugins.configuration.setupwizard.elements.SWPlugin
import app.aaps.plugins.configuration.setupwizard.elements.SWRadioButton
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@SingleIn(AppScope::class)
class SWDefinition @Inject constructor(
    @ApplicationScope private val appScope: CoroutineScope,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val profileRepository: ProfileRepository,
    private val commandQueue: CommandQueue,
    private val fileListProvider: FileListProvider,
    private val cryptoUtil: CryptoUtil,
    private val config: Config,
    private val hardLimits: HardLimits,
    private val nsClient: NsClient,
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
    private val swPairingStatusProvider: Provider<SWPairingStatus>,
    private val swPermissionsProvider: Provider<SWPermissions>,
    private val swPluginProvider: Provider<SWPlugin>,
    private val swRadioButtonProvider: Provider<SWRadioButton>
) {

    var onImportSettings: (() -> Unit)? = null
    var onPluginPreferences: ((pluginId: String) -> Unit)? = null
    var onPluginOpen: ((pluginId: String) -> Unit)? = null
    var onSetMasterPassword: (() -> Unit)? = null
    var onManageInsulin: (() -> Unit)? = null
    var onManageProfile: (() -> Unit)? = null
    var onProfileSwitch: (() -> Unit)? = null
    var onOpenAuthorizedClients: (() -> Unit)? = null
    var onPairWithMaster: (() -> Unit)? = null
    var onOpenNsReceiveSettings: (() -> Unit)? = null
    var onRunObjectives: (() -> Unit)? = null
    var onRequestDirectoryAccess: (() -> Unit)? = null
    var onRequestPermission: ((PermissionGroup) -> Unit)? = null
    var permissionItems: (() -> List<Pair<PermissionGroup, Boolean>>)? = null
    var isDirectoryAccessGranted: (() -> Boolean)? = null
    private val screens: MutableList<SWScreen> = ArrayList()

    private fun pluginOption(pType: PluginType, description: TextRef): SWPlugin =
        swPluginProvider()
            .option(pType, description)
            .onPreferences { pluginId -> onPluginPreferences?.invoke(pluginId) }
            .onOpenPlugin { pluginId -> onPluginOpen?.invoke(pluginId) }

    fun getScreens(): List<SWScreen> {
        if (screens.isEmpty()) {
            when {
                config.APS -> swDefinitionFull()
                config.PUMPCONTROL -> swDefinitionPumpControl()
                config.AAPSCLIENT -> swDefinitionNSClient()
            }
            // appScope rather than a new one: the CompositeDisposable this replaces was never cleared,
            // so this subscription was already app-lifetime. Plain onEach/launchIn rather than
            // collectResilient because there is no logger here and the body is a bus send that cannot
            // meaningfully fail - the Rx version had no error handler either.
            rxBus.toFlow(EventConfigBuilderChange::class)
                .onEach { rxBus.send(EventSWUpdate(true)) }
                .launchIn(appScope)
        }
        return screens
    }

    private fun add(newScreen: SWScreen?): SWDefinition {
        if (newScreen != null) screens.add(newScreen)
        return this
    }

    private val screenSetupWizard
        get() = swScreenProvider().with(ConfigurationStrings.welcome)
            .add(swInfoTextProvider().label(ConfigurationStrings.welcometosetupwizard))

    private val screenEula
        get() = swScreenProvider().with(ConfigurationStrings.end_user_license_agreement)
            .skippable(false)
            .add(swInfoTextProvider().label(ConfigurationStrings.end_user_license_agreement_text))
            .add(swBreakProvider())
            .add(
                swButtonProvider()
                    .text(ConfigurationStrings.end_user_license_agreement_i_understand)
                    .visibility { !preferences.get(BooleanNonKey.SetupWizardIUnderstand) }
                    .action {
                        preferences.put(BooleanNonKey.SetupWizardIUnderstand, true)
                        rxBus.send(EventSWUpdate(false))
                    })
            .visibility { !preferences.get(BooleanNonKey.SetupWizardIUnderstand) }
            .validator { preferences.get(BooleanNonKey.SetupWizardIUnderstand) }

    private val screenUnits
        get() = swScreenProvider().with(ConfigurationStrings.units)
            .skippable(false)
            .add(swInfoTextProvider().label(ConfigurationStrings.setupwizard_units_prompt))
            .add(
                swRadioButtonProvider()
                    .preference(StringKey.GeneralUnits)
            )
            .validator { preferences.get(StringKey.GeneralUnits).isNotEmpty() }

    private val displaySettings
        get() = swScreenProvider().with(ConfigurationStrings.display_settings)
            .skippable(false)
            .add(
                swEditNumberWithUnitsProvider()
                    .preference(UnitDoubleKey.OverviewLowMark)
                    .updateDelay(5)
                    .label(ConfigurationStrings.low_mark)
                    .comment(ConfigurationStrings.low_mark_comment)
            )
            .add(swBreakProvider())
            .add(
                swEditNumberWithUnitsProvider()
                    .preference(UnitDoubleKey.OverviewHighMark)
                    .updateDelay(5)
                    .label(ConfigurationStrings.high_mark)
                    .comment(ConfigurationStrings.high_mark_comment)
            )

    private val screenPermissions
        get() = swScreenProvider().with(ConfigurationStrings.setupwizard_permissions)
            .skippable(true)
            .add(swInfoTextProvider().label(ConfigurationStrings.setupwizard_permissions_info))
            .add(swBreakProvider())
            .add(swPermissionsProvider().with(this))

    private val screenImport
        get() = swScreenProvider().with(CoreUiStrings.import_setting)
            .add(swInfoTextProvider().label(ConfigurationStrings.storedsettingsfound))
            .add(swBreakProvider())
            .add(swButtonProvider().text(CoreUiStrings.import_setting).action {
                onImportSettings?.invoke()
            })
            .visibility { fileListProvider.listPreferenceFiles().isNotEmpty() }

    private val screenNsClient
        get() = swScreenProvider().with(CoreUiStrings.configbuilder_sync)
            .skippable(true)
            .add(pluginOption(PluginType.SYNC, ConfigurationStrings.configbuilder_sync_description))
            .add(swBreakProvider())
            .add(swInfoTextProvider().label(ConfigurationStrings.syncinfotext))
            .add(swBreakProvider())
            .add(swEventListenerProvider().with(EventSWSyncStatus::class).label(ConfigurationStrings.status_label).initialStatus(nsClient.status))
            .validator { nsClient.connected && nsClient.hasWritePermission }

    // Master side: explain the paired client-control channel, open the pairing (Authorized clients) screen,
    // and offer the old "accept data from NS" settings (now off by default).
    private val screenClientControl
        get() = swScreenProvider().with(ConfigurationStrings.setupwizard_client_control_title)
            .skippable(true)
            .add(swInfoTextProvider().label(ConfigurationStrings.setupwizard_client_control_info))
            .add(swPairingStatusProvider())
            .add(swBreakProvider())
            .add(
                swButtonProvider()
                    .text(CoreUiStrings.authorized_clients_manage_label)
                    .visibility { preferences.get(BooleanKey.NsClient3UseWs) }
                    .action { onOpenAuthorizedClients?.invoke() }
            )
            .add(swInfoTextProvider().label(ConfigurationStrings.setupwizard_pairing_ws_warning).visibility { !preferences.get(BooleanKey.NsClient3UseWs) })
            .add(swBreakProvider())
            .add(swInfoTextProvider().label(ConfigurationStrings.setupwizard_ns_receive_info))
            .add(
                swButtonProvider()
                    .text(ConfigurationStrings.setupwizard_open_ns_receive_settings)
                    .action { onOpenNsReceiveSettings?.invoke() }
            )

    // Client side: explain pairing with a master and open the "Pair with master" (PIN entry) screen.
    private val screenPairWithMaster
        get() = swScreenProvider().with(CoreUiStrings.pair_with_master_manage_label)
            .skippable(true)
            .add(swInfoTextProvider().label(ConfigurationStrings.setupwizard_pair_with_master_info))
            .add(swBreakProvider())
            .add(
                swButtonProvider()
                    .text(CoreUiStrings.pair_with_master_manage_label)
                    .visibility { preferences.get(BooleanKey.NsClient3UseWs) }
                    .action { onPairWithMaster?.invoke() }
            )
            .add(swBreakProvider())
            .add(swPairingStatusProvider())
            .add(swInfoTextProvider().label(ConfigurationStrings.setupwizard_pairing_ws_warning).visibility { !preferences.get(BooleanKey.NsClient3UseWs) })

    private val screenPatientName
        get() = swScreenProvider().with(StringKey.GeneralPatientName.title)
            .skippable(true)
            .add(swInfoTextProvider().label(StringKey.GeneralPatientName.summary!!))
            .add(swEditStringProvider().validator(String::isNotEmpty).preference(StringKey.GeneralPatientName))

    private val screenMasterPassword
        get() = swScreenProvider().with(StringKey.ProtectionMasterPassword.title)
            .skippable(false)
            .add(swEditEncryptedPasswordProvider().preference(StringKey.ProtectionMasterPassword).onSetPassword { onSetMasterPassword?.invoke() })
            .add(swBreakProvider())
            .add(swInfoTextProvider().label(ConfigurationStrings.master_password_summary))
            .validator { !cryptoUtil.checkPassword("", preferences.get(StringKey.ProtectionMasterPassword)) }

    private val screenAge
        get() = swScreenProvider().with(CoreUiStrings.patient_type)
            .skippable(false)
            .add(swInfoTextProvider().label(CoreUiStrings.patient_age_summary))
            .add(
                swRadioButtonProvider()
                    .option(hardLimits.ageEntries(), hardLimits.ageEntryValues())
                    .preference(StringKey.SafetyAge)
            )
            .add(swBreakProvider())
            .add(
                swEditNumberProvider()
                    .preference(DoubleKey.SafetyMaxBolus)
                    .updateDelay(5)
                    .label(CoreUiStrings.max_bolus_title)
                    .comment(ConfigurationStrings.common_values)
            )
            .add(
                swEditIntNumberProvider()
                    .preference(IntKey.SafetyMaxCarbs)
                    .updateDelay(5)
                    .label(CoreUiStrings.max_carbs_title)
                    .comment(ConfigurationStrings.common_values)
            )
            .validator {
                preferences.get(StringKey.SafetyAge).isNotEmpty()
                    && preferences.get(DoubleKey.SafetyMaxBolus) > 0
                    && preferences.get(IntKey.SafetyMaxCarbs) > 0
            }

    private val screenInsulin
        get() = swScreenProvider().with(CoreUiStrings.configbuilder_insulin)
            .skippable(false)
            .add(swInfoTextProvider().label(ConfigurationStrings.diawarning))
            .add(swBreakProvider())
            .add(swButtonProvider().text(CoreUiStrings.configbuilder_insulin).action { onManageInsulin?.invoke() })

    private val screenBgSource
        get() = swScreenProvider().with(CoreUiStrings.configbuilder_bgsource)
            .skippable(false)
            .add(pluginOption(PluginType.BGSOURCE, ConfigurationStrings.configbuilder_bgsource_description))
            .add(swBreakProvider())

    private val screenProfile
        get() = swScreenProvider().with(CoreUiStrings.profile)
            .skippable(false)
            .add(swInfoTextProvider().label(ConfigurationStrings.setupwizard_profile_info))
            .add(swBreakProvider())
            .add(swButtonProvider().text(CoreUiStrings.profile).action { onManageProfile?.invoke() })
            .validator { profileRepository.profiles.value.let { it.isNotEmpty() && it.all { p -> profileRepository.validateStructured(p).isEmpty() } } }

    private val screenProfileSwitch
        get() = swScreenProvider().with(CoreUiStrings.careportal_profileswitch)
            .skippable(false)
            .add(swInfoTextProvider().label(CoreUiStrings.profileswitch_ismissing))
            .add(
                swButtonProvider()
                    .text(ConfigurationStrings.doprofileswitch)
                    .action { onProfileSwitch?.invoke() })
            .validator { runBlocking { profileFunction.getRequestedProfile() } != null }
            .visibility { runBlocking { profileFunction.getRequestedProfile() } == null }

    private val screenPump
        get() = swScreenProvider().with(CoreUiStrings.configbuilder_pump)
            .skippable(false)
            .add(pluginOption(PluginType.PUMP, ConfigurationStrings.configbuilder_pump_description))
            .add(swBreakProvider())
            .add(swInfoTextProvider().label(ConfigurationStrings.setupwizard_pump_pump_not_initialized).visibility { !isPumpInitialized() })
            .add( // Omnipod Eros only
                swInfoTextProvider()
                    .label(ConfigurationStrings.setupwizard_pump_waiting_for_riley_link_connection)
                    .visibility { activePlugin.activePumpInternal.let { it is OmnipodEros && !it.isRileyLinkReady() } }
            )
            .add( // Omnipod Eros only
                swEventListenerProvider().with(EventSWRLStatus::class)
                    .label(ConfigurationStrings.setupwizard_pump_riley_link_status)
                    .visibility { activePlugin.activePumpInternal is OmnipodEros })
            .add(
                swButtonProvider()
                    .text(ConfigurationStrings.readstatus)
                    .action { appScope.launch { commandQueue.readStatus(rh.gs(CoreUiStrings.clicked_connect_to_pump)) } }
                    .visibility {
                        // Hide for Omnipod and Medtrum, because as we don't require a Pod/Patch to be paired in the setup wizard,
                        // Getting the status might not be possible
                        activePlugin.activePump !is OmnipodEros && activePlugin.activePump !is OmnipodDash && activePlugin.activePump !is Medtrum
                    })
            .add(
                swEventListenerProvider().with(EventPumpStatusChanged::class)
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
        get() = swScreenProvider().with(CoreUiStrings.configbuilder_aps)
            .skippable(false)
            .add(swInfoTextProvider().label(ConfigurationStrings.setupwizard_aps_description))
            .add(swBreakProvider())
            .add(pluginOption(PluginType.APS, ConfigurationStrings.configbuilder_aps_description))
            .add(swBreakProvider())
            .add(swHtmlLinkProvider().label("https://wiki.aaps.app"))
            .add(swBreakProvider())

    private val screenSensitivity
        get() = swScreenProvider().with(CoreUiStrings.configbuilder_sensitivity)
            .skippable(false)
            .add(swInfoTextProvider().label(ConfigurationStrings.setupwizard_sensitivity_description))
            .add(swHtmlLinkProvider().label(ConfigurationStrings.setupwizard_sensitivity_url))
            .add(swBreakProvider())
            .add(pluginOption(PluginType.SENSITIVITY, ConfigurationStrings.configbuilder_sensitivity_description))

    private val getScreenObjectives
        get() = swScreenProvider().with(CoreUiStrings.objectives)
            .skippable(false)
            .add(swInfoTextProvider().label(ConfigurationStrings.startobjective))
            .add(swBreakProvider())
            .add(swButtonProvider().text(ConfigurationStrings.open_objectives).action { onRunObjectives?.invoke() })
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
            .add(screenClientControl)
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
            .add(screenPairWithMaster)
            //.add(screenBgSource)
            .add(screenPatientName)
}