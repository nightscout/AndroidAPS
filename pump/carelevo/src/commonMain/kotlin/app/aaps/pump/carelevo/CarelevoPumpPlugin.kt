package app.aaps.pump.carelevo

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs
import app.aaps.core.keys.interfaces.withEntries
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.icons.IcPluginCarelevo
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.ble.commands.BasalProgramCommand
import app.aaps.pump.carelevo.ble.data.BleState
import app.aaps.pump.carelevo.ble.data.BondingState
import app.aaps.pump.carelevo.ble.data.DeviceModuleState
import app.aaps.pump.carelevo.ble.data.NotificationState
import app.aaps.pump.carelevo.ble.data.PeripheralConnectionState
import app.aaps.pump.carelevo.ble.data.ServiceDiscoverState
import app.aaps.pump.carelevo.command.CarelevoActivationExecutor
import app.aaps.pump.carelevo.command.CmdTimeZoneUpdate
import app.aaps.pump.carelevo.command.CmdUpdateBuzzer
import app.aaps.pump.carelevo.command.CmdUpdateExpiredThreshold
import app.aaps.pump.carelevo.command.CmdUpdateLowInsulinNotice
import app.aaps.pump.carelevo.command.CmdUpdateMaxBolus
import app.aaps.pump.carelevo.common.CarelevoAlarmNotifier
import app.aaps.pump.carelevo.common.CarelevoAppForeground
import app.aaps.pump.carelevo.common.CarelevoBleReadyCheck
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.keys.CarelevoBooleanPreferenceKey
import app.aaps.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import app.aaps.pump.carelevo.common.keys.CarelevoStringNonKey
import app.aaps.pump.carelevo.common.latest
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.compose.CarelevoComposeContent
import app.aaps.pump.carelevo.coordinator.CarelevoBasalProfileUpdateCoordinator
import app.aaps.pump.carelevo.coordinator.CarelevoBolusCoordinator
import app.aaps.pump.carelevo.coordinator.CarelevoConnectionCoordinator
import app.aaps.pump.carelevo.coordinator.CarelevoTempBasalCoordinator
import app.aaps.pump.carelevo.di.CarelevoCentral
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.type.AlarmType.Companion.isCritical
import app.aaps.pump.carelevo.ext.transformNotificationStringResources
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

// Registered as a pump plugin in CarelevoRegistration, which each platform includes once it has the Carelevo ports.
@SingleIn(AppScope::class)
class CarelevoPumpPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    override val rh: TextResolver,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val fabricPrivacy: FabricPrivacy,
    private val profileFunction: ProfileFunction,
    @CarelevoCentral private val central: BleCentral,
    private val appForeground: CarelevoAppForeground,
    private val protectionCheck: ProtectionCheck,
    private val bleReadyCheck: CarelevoBleReadyCheck,
    private val iconsProvider: IconsProvider,
    private val config: Config,
    private val uiInteraction: UiInteraction,
    private var pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val carelevoPatch: CarelevoPatch,

    private val carelevoAlarmNotifier: CarelevoAlarmNotifier,
    private val basalProfileUpdateCoordinator: CarelevoBasalProfileUpdateCoordinator,
    private val bolusCoordinator: CarelevoBolusCoordinator,
    private val tempBasalCoordinator: CarelevoTempBasalCoordinator,
    private val connectionCoordinator: CarelevoConnectionCoordinator,
    private val activationExecutor: CarelevoActivationExecutor,
    notificationManager: NotificationManager
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .composeContent { _ ->
            CarelevoComposeContent(
                aapsLogger = aapsLogger,
                carelevoAlarmNotifier = carelevoAlarmNotifier,
                protectionCheck = protectionCheck,
                bleReadyCheck = bleReadyCheck,
                iconsProvider = iconsProvider,
                config = config
            )
        }
        .icon(IcPluginCarelevo)
        .pluginName(CarelevoStrings.carelevo)
        .description(CarelevoStrings.carelevo_description),
    ownPreferences = CarelevoBooleanPreferenceKey.entries + CarelevoIntPreferenceKey.entries + CarelevoStringNonKey.entries,
    aapsLogger, rh, preferences, commandQueue, notificationManager
), Pump {

    /** Where the plugin collects its streams. A test sets an unconfined dispatcher. */
    internal var dispatcher: CoroutineDispatcher = aapsIoDispatcher

    // Critical alarms escalate through the shared AAPS full-screen alarm (uiInteraction.runAlarm) —
    // see handleAlarms. This dedups that escalation to once per still-active alarm id: the alarms
    // stream re-emits on every reconnect/snapshot poll while the condition remains, and runAlarm must
    // not re-fire (re-ring/re-launch the activity) for an alarm the patch is still reporting as active,
    // only for one that is newly seen. An id drops out (and can re-fire later) once it's no longer in
    // the incoming alarm list — resolved, or acknowledged.
    private val globallyAlarmedIds = mutableSetOf<String>()

    private var _pumpType: PumpType = PumpType.CAREMEDI_CARELEVO
    private val _pumpDescription = PumpDescription().fillFor(_pumpType)

    private var scope: CoroutineScope? = null

    // Auto-resume watchdog cadence + safety margin: poll this often, and reconcile once the stop window has
    // elapsed plus this margin (so the patch has actually auto-resumed before AAPS clears its suspend).
    private val autoResumePollMs = 30_000L
    private val autoResumeMarginMs = 30_000L

    // The coroutine BLE session every pump op runs over. Field-injected — its @Inject constructor
    // holds no eager BLE state.
    @Inject lateinit var bleSession: CarelevoBleSession

    override suspend fun onStart() {
        super.onStart()

        applyDefaultCageThresholdsIfNeeded()
        registerPreferenceChangeObserver()
        initializeOnStart()
        followBluetoothAdapter()
        startAlarmObserving()
        startReconnectAlarmSnapshotObserving()
        startAutoResumeWatchdog()
        // Consume patch-pushed frames (alarms, stop/basal-restart reports) whenever a session is open.
        bleSession.unsolicitedHandler = carelevoPatch::onUnsolicited
    }

    override suspend fun onStop() {
        super.onStop()
        aapsLogger.debug(LTag.PUMP, "onStop called")
        bleSession.unsolicitedHandler = null

        // onStart/onStop run as separate, unjoined pluginScope coroutines (PluginBase), so a fast
        // disable→re-enable can overlap them. Tear down only the generation captured here, and clear
        // the field only if it still points at that generation — never clobber a scope that a
        // concurrent onStart may have just re-created. The scope also ends the Bluetooth and foreground follows.
        val scopeToCancel = scope

        scopeToCancel?.cancel()
        if (scope === scopeToCancel) scope = null

        carelevoAlarmNotifier.stopObserving()
    }

    private fun registerPreferenceChangeObserver() {
        val newScope = CoroutineScope(dispatcher + SupervisorJob())
        scope = newScope

        // Settings pushes go through the queue (connect-before-execute) like every other patch op — a
        // direct fire-and-forget write would silently fail while the pump idle-disconnects between commands.
        preferences.observe(DoubleKey.SafetyMaxBolus)
            .drop(1)
            .onEach { commandQueue.customCommand(CmdUpdateMaxBolus(preferences.get(DoubleKey.SafetyMaxBolus))) }
            .launchIn(newScope)

        preferences.observe(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS)
            .drop(1)
            .onEach {
                val hours = preferences.get(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS)
                commandQueue.customCommand(CmdUpdateExpiredThreshold(hours))
            }
            .launchIn(newScope)

        preferences.observe(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS)
            .drop(1)
            .onEach {
                val amountUnits = preferences.get(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS)
                // Zero = reminder off; skip enqueuing so the pump isn't reconnected just to no-op.
                if (amountUnits != 0) commandQueue.customCommand(CmdUpdateLowInsulinNotice(amountUnits))
            }
            .launchIn(newScope)

        preferences.observe(CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER)
            .drop(1)
            .onEach {
                val on = preferences.get(CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER)
                commandQueue.customCommand(CmdUpdateBuzzer(on))
            }
            .launchIn(newScope)

        // Deferred settings sync: when the patch is booted, push a max-bolus / low-insulin setting that the patch may
        // not have. That is a change that could not reach it (changed while offline, or during a bolus), which left a
        // needXSyncPatch flag on the stored user settings, or a preference that differs from the stored value
        // because it changed while this driver did not run or no patch was there (also a settings import).
        combine(carelevoPatch.patchState, carelevoPatch.infusionInfo, carelevoPatch.userSettingInfo) { state, infusion, setting ->
            computeSettingsSyncNeed(state, infusion, setting)
        }
            .distinctUntilChanged()
            .onEach { need ->
                newScope.launch {
                    need.maxBolusDose?.let { commandQueue.customCommand(CmdUpdateMaxBolus(it)) }
                    need.lowInsulinAmountUnits?.let { commandQueue.customCommand(CmdUpdateLowInsulinNotice(it)) }
                }
            }
            .launchIn(newScope)
    }

    /**
     * Auto-resume watchdog. A timed pump-stop makes the patch auto-resume at the stop duration, but with
     * per-op BLE sessions there is no live link to receive the patch's stop-report push, and a 0x91 status
     * read does not carry the suspend flag — so `isStopped` would stay latched until an app-initiated resume
     * (issue #4993).
     *
     * Implemented as a resilient periodic check rather than a one-shot timer, so it is robust to the failure
     * modes a single fire-and-forget timer has:
     *  - Each tick is independently guarded, so a transient reconcile failure neither kills the watchdog
     *    (it simply retries next tick) nor is permanent.
     *  - Elapsed time is measured from a DURABLE anchor ([CarelevoBasalInfusionInfoDomainModel.updatedAt],
     *    persisted at stop time and untouched by 0x91 status reads), so an app restart mid-stop still
     *    resumes on schedule instead of re-arming a full window.
     *  - A status read that momentarily resurrects `isStopped` is re-cleared on the next tick, because the
     *    anchor is stable.
     */
    private fun startAutoResumeWatchdog() {
        scope?.launch {
            while (isActive) {
                try {
                    checkAutoResume()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    aapsLogger.error(LTag.PUMPCOMM, "auto-resume check failed (will retry)", e)
                }
                delay(autoResumePollMs)
            }
        }
    }

    /** One watchdog tick: reconcile a timed stop whose window has elapsed. Idempotent — safe to call repeatedly. */
    private suspend fun checkAutoResume() {
        val patchInfo = carelevoPatch.patchInfo.latest ?: return
        if (patchInfo.isStopped != true) return
        val stopMinutes = patchInfo.stopMinutes ?: return
        if (stopMinutes <= 0) return
        // Durable stop-start anchor: basalInfusionInfo.updatedAt is set at persistStopped, survives an app
        // restart (persisted), and is not moved by 0x91 status reads (which only rewrite patchInfo). Skip
        // until infusion info is loaded.
        val stopStartedAt = carelevoPatch.infusionInfo.latest?.basalInfusionInfo?.updatedAt?.toEpochMilliseconds() ?: return
        val elapsedMs = Clock.System.now().toEpochMilliseconds() - stopStartedAt
        if (elapsedMs < stopMinutes * 60_000L + autoResumeMarginMs) return
        aapsLogger.info(LTag.PUMPCOMM, "auto-resume: ${stopMinutes}min stop elapsed (${elapsedMs / 1000}s) -> reconcile")
        carelevoPatch.reconcileAutoResumed()
        commandQueue.readStatus("Carelevo auto-resume")
    }

    private data class SettingsSyncNeed(val maxBolusDose: Double?, val lowInsulinAmountUnits: Int?)

    /**
     * Which patch settings still need pushing, only when the patch is booted. A setting is pushed when its flag is set
     * or when its preference differs from the stored value. The value pushed is always the preference: it is what
     * the user set last. Reads the two preferences and changes nothing.
     */
    private fun computeSettingsSyncNeed(
        state: PatchState?,
        infusion: CarelevoInfusionInfoDomainModel?,
        setting: CarelevoUserSettingInfoDomainModel?
    ): SettingsSyncNeed {
        if (state !is PatchState.ConnectedBooted || setting == null) return SettingsSyncNeed(null, null)
        // Max-bolus is a device safety cap — do not re-push mid-bolus (mirrors the use case's own guard).
        // "No bolus running" requires BOTH channels idle (the deleted observeSyncPatch had this as `||`,
        // which was true during a single-channel bolus and triggered a spurious mid-bolus reconnect).
        val noBolusRunning = infusion?.extendBolusInfusionInfo == null && infusion?.immeBolusInfusionInfo == null
        val maxBolus = preferences.get(DoubleKey.SafetyMaxBolus)
        val maxBolusDose = if (noBolusRunning && (setting.needMaxBolusDoseSyncPatch || setting.maxBolusDose != maxBolus)) maxBolus else null
        // Zero = reminder off. It is never sent, as in the preference observer.
        val lowInsulin = preferences.get(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS)
        val lowInsulinAmountUnits =
            if (lowInsulin != 0 && (setting.needLowInsulinNoticeAmountSyncPatch || setting.lowInsulinNoticeAmount != lowInsulin)) lowInsulin else null
        return SettingsSyncNeed(maxBolusDose, lowInsulinAmountUnits)
    }

    private fun initializeOnStart() {
        // Run initialization directly on start instead of gating it on EventAppInitialized.
        // onStart() is now a suspend function launched fire-and-forget, so the (non-replayed)
        // EventAppInitialized could fire before this subscription was registered — leaving the
        // patch uninitialized and appearing deactivated after an app update (dev fixed the same
        // race in eopatch). initPatchOnce() does not depend on other plugins; the basal profile is
        // applied best-effort here and, if not yet available, is set later via setNewBasalProfile().
        val activeScope = scope ?: return
        activeScope.launch {
            aapsLogger.debug(LTag.PUMPCOMM, "onStart: 1) initPatchOnce waiting")
            // A start that fails or takes longer than 5 s does not stop the rest.
            try {
                withTimeoutOrNull(INIT_WAIT) { carelevoPatch.initPatchOnce().await() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            aapsLogger.debug(LTag.PUMPCOMM, "onStart: 1) initPatchOnce completed")
            try {
                val profile = profileFunction.getProfile()
                if (profile != null) {
                    carelevoPatch.setProfile(profile)
                    aapsLogger.debug(LTag.PUMPCOMM, "onStart: 3) setProfile done: $profile")
                } else {
                    aapsLogger.debug(LTag.PUMPCOMM, "onStart: 3) profile not ready, deferring to setNewBasalProfile")
                }
                aapsLogger.debug(LTag.PUMPCOMM, "onStart: ALL COMPLETE")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMPCOMM, "onStart: chain error", e)
            }
        }

        carelevoPatch.patchInfo
            .onEach {
                // No patch record: no reservoir to tell, which is not an empty one.
                _reservoirLevel.value = it?.insulinRemain?.let(::PumpInsulin)
                _batteryLevel.value = 0
            }
            .catch { fabricPrivacy.logException(it) }
            .launchIn(activeScope)
    }

    /**
     * The Bluetooth adapter state is the only btState source (adapter on/off; there is no resting link to track).
     * It comes from the driver's own central, so the emulated patch, whose central is always on, is not stopped
     * by the phone's Bluetooth being off.
     */
    private fun followBluetoothAdapter() {
        // Seeded at once, so isBluetoothEnabled()/patchState resolve before the first change arrives.
        carelevoPatch.onBluetoothStateChanged(adapterBleState(central.adapterState.value))
        scope?.let { activeScope ->
            central.adapterState
                .onEach { state ->
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Carelevo Bluetooth adapter: $state")
                    carelevoPatch.onBluetoothStateChanged(adapterBleState(state))
                }
                .launchIn(activeScope)
        }
    }

    /** Adapter-level state only — bond/discovery/notification fields are per-session now and never tracked. */
    private fun adapterBleState(adapter: AdapterState): BleState = BleState(
        isEnabled = when (adapter) {
            AdapterState.ON          -> DeviceModuleState.DEVICE_STATE_ON
            // iOS says UNKNOWN for a moment after the start. That is not "off", so no Bluetooth alarm comes from it.
            AdapterState.UNKNOWN     -> DeviceModuleState.DEVICE_STATE_TURNING_ON
            // No permission, or no Bluetooth at all: the patch cannot be reached, as with Bluetooth off.
            AdapterState.OFF,
            AdapterState.UNAUTHORIZED,
            AdapterState.UNSUPPORTED -> DeviceModuleState.DEVICE_STATE_OFF
        },
        isBonded = BondingState.BOND_NONE,
        isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_NONE,
        isConnected = PeripheralConnectionState.CONN_STATE_NONE,
        isNotificationEnabled = NotificationState.NOTIFICATION_NONE
    )

    private fun applyDefaultCageThresholdsIfNeeded() {
        if (preferences.get(CarelevoBooleanPreferenceKey.CARELEVO_CAGE_DEFAULT_APPLIED)) return

        preferences.put(IntKey.OverviewCageWarning, 96)
        preferences.put(IntKey.OverviewCageCritical, 168)
        preferences.put(CarelevoBooleanPreferenceKey.CARELEVO_CAGE_DEFAULT_APPLIED, true)
    }

    private fun startAlarmObserving() {
        aapsLogger.debug(LTag.NOTIFICATION, "startAlarmObserving:: onStart")

        // Each time AAPS comes to the front, and once right away when it is in front already.
        scope?.let { activeScope ->
            appForeground.inForeground()
                .filter { it }
                .onEach {
                    aapsLogger.debug(LTag.NOTIFICATION, "Foreground transition -> refresh alarms")
                    carelevoAlarmNotifier.refreshAlarms()
                }
                .launchIn(activeScope)
        }

        carelevoAlarmNotifier.startObserving { alarms ->
            aapsLogger.debug(LTag.NOTIFICATION, "observe alarms size=${alarms.size}, $alarms")
            handleAlarms(alarms)
        }
    }

    private fun startReconnectAlarmSnapshotObserving() {
        scope?.let { activeScope ->
            bleSession.connected
                .drop(1)
                .onEach { connected ->
                    if (connected) refreshActiveAlarmSnapshots()
                }
                .launchIn(activeScope)
        }
    }

    private suspend fun refreshActiveAlarmSnapshots() {
        val address = carelevoPatch.getPatchInfoAddress() ?: run {
            aapsLogger.warn(LTag.PUMPCOMM, "active alarm snapshot skipped: no patch address")
            return
        }
        try {
            // readActiveAlarmSnapshots itself is bounded and, on timeout, does NOT tear the held link
            // down (see CarelevoBleSession — this protocol only exists on newer firmware, so an older
            // patch simply never answering must not disrupt whatever the queue runs next). It signals
            // that "no answer" case as an empty list rather than throwing.
            val snapshots = bleSession.readActiveAlarmSnapshots(address)
            if (snapshots.isEmpty()) {
                aapsLogger.warn(LTag.PUMPCOMM, "active alarm snapshot skipped: no response (older firmware?)")
                return
            }
            carelevoPatch.applyActiveAlarmSnapshots(snapshots)
            aapsLogger.info(
                LTag.PUMPCOMM,
                "active alarm snapshot OK tiers=${snapshots.map { it.tier }} infusing=${snapshots.firstOrNull()?.infusing}"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            aapsLogger.error(LTag.PUMPCOMM, "active alarm snapshot FAILED", e)
        }
    }

    /**
     * Two escalation surfaces, same shape EOPatch's `AlarmManager.showNotification` uses (a critical
     * alarm gets BOTH, every other alarm gets only the second):
     * - [uiInteraction.runAlarm]: the shared AAPS full-screen alarm — wakes/unlocks the screen and
     *   rings, so a critical patch failure (occlusion, out of insulin, patch error, …) isn't missed
     *   while the phone is locked or the user is elsewhere in the app. Fire-and-forget: its own OK
     *   button only silences the sound (it's shared UI with no notion of a CareLevo alarm to clear).
     * - [CarelevoAlarmNotifier.showTopNotification]: the persistent notification card, for every
     *   alarm regardless of severity. Its action button is the one that actually clears the alarm
     *   (wired to [app.aaps.pump.carelevo.presentation.model.AlarmEvent.ClearAlarm] in
     *   [CarelevoAlarmNotifier.showTopNotification] itself) — so silencing the full-screen alarm is
     *   not the same as acknowledging it; the card is what the user confirms on.
     *
     * The full-screen text says so and names the card's button, because the alarm descriptions say
     * "Tap the [OK] button" and the full-screen alarm has its own button that does not clear anything.
     */
    private fun handleAlarms(alarms: List<CarelevoAlarmInfo>) {
        aapsLogger.debug(LTag.NOTIFICATION, "startAlarmObserving handleAlarms:: $alarms")
        globallyAlarmedIds.retainAll(alarms.map { it.alarmId }.toSet())

        // filter-with-add: keeps only the not-yet-escalated critical alarms AND marks them escalated,
        // so a condition the snapshot keeps re-reporting rings once rather than on every reconnect.
        val fresh = alarms.filter { it.alarmType.isCritical() && globallyAlarmedIds.add(it.alarmId) }
        fresh.firstOrNull()?.let { alarm ->
            val (title, _, button) = alarm.cause.transformNotificationStringResources()
            uiInteraction.runAlarm(
                status = rh.gs(CarelevoStrings.carelevo_alarm_screen_text, rh.gs(title), rh.gs(button)),
                title = rh.gs(CarelevoStrings.carelevo),
                sound = AlarmSound.ERROR
            )
        }

        carelevoAlarmNotifier.showTopNotification(alarms)
    }

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "carelevo_settings",
        title = CarelevoStrings.carelevo,
        items = listOf(
            // Labels come from the translated unit format templates. Building them as "$it U" or
            // "$it h" in code would put text where no translator can reach it.
            CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_REMINDER_UNITS.withEntries(
                (20..50 step 5).associateWith { CoreUiStrings.units_format_insulin_int.withArgs(it) }
            ),
            CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS.withEntries(
                (24..167 step 1).associateWith { CoreUiStrings.units_format_hours.withArgs(it) }
            ),
            CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER
        ),
        icon = pluginDescription.icon
    )

    // A patch is "configured" once activation has persisted a patch record (patchInfo present) — the
    // same signal isInitialized() gates on first, so isInitialized() implies isConfigured() and the
    // contract invariant !isConfigured() => !isInitialized() holds by construction. Intentionally
    // independent of BLE: an attached-but-disconnected patch is still configured and may be delivering.
    override fun isConfigured(): Boolean =
        carelevoPatch.patchInfo.latest != null

    override fun isInitialized(): Boolean {
        return connectionCoordinator.isInitialized()
    }

    override fun isSuspended(): Boolean {
        // Real delivery-suspend (pump stopped by the user), NOT the BLE connection state. Otherwise, a
        // normal idle disconnect (NotConnectedBooted, now that disconnect() actually disconnects) would
        // be reported as suspended and surface as an error/suspended icon on the overview.
        return carelevoPatch.patchInfo.latest?.isStopped ?: false
    }

    override fun isBusy(): Boolean {
        return false
    }

    override fun isConnected(): Boolean {
        return connectionCoordinator.isConnected()
    }

    override fun isConnecting(): Boolean {
        return connectionCoordinator.isConnecting()
    }

    override fun isHandshakeInProgress(): Boolean {
        return false
    }

    override fun connect(reason: String) {
        connectionCoordinator.connect(reason)
    }

    override fun disconnect(reason: String) {
        connectionCoordinator.disconnect(reason)
    }

    override fun stopConnecting() {
        connectionCoordinator.stopConnecting()
    }

    override suspend fun getPumpStatus(reason: String) = readInfusionInfo()

    /**
     * Status read over the [app.aaps.pump.carelevo.ble.BleClient] stack: read Infusion Info (0x31 → 0x91)
     * on the session's own connection and persist it through `carelevoPatch.applyInfusionInfoReport` —
     * reservoir/pump-state/totals all refresh. Runs on
     * the QueueWorker thread, blocked inside this status read.
     */
    private suspend fun readInfusionInfo() {
        val address = carelevoPatch.getPatchInfoAddress() ?: run {
            aapsLogger.warn(LTag.PUMPCOMM, "newBle.readInfusionInfo skipped: no patch address")
            return
        }
        try {
            val info = bleSession.readInfusionInfo(address)
            carelevoPatch.applyInfusionInfoReport(
                runningMinutes = info.runningMinutes,
                remains = info.insulinRemaining,
                infusedTotalBasalAmount = info.infusedTotalBasalAmount,
                infusedTotalBolusAmount = info.infusedTotalBolusAmount,
                pumpStateRaw = info.pumpStateRaw,
                modeRaw = info.modeRaw
            )
            _lastDataTime.value = Clock.System.now().toEpochMilliseconds()
            aapsLogger.info(
                LTag.PUMPCOMM,
                "newBle.readInfusionInfo OK remains=${info.insulinRemaining} basal=${info.infusedTotalBasalAmount} " +
                    "bolus=${info.infusedTotalBolusAmount} pumpState=${info.pumpStateRaw} mode=${info.modeRaw} running=${info.runningMinutes}"
            )
            // A bolus the app did not record (it stopped during the bolus) is recorded here. The queue reads the status
            // when the app starts ("Pump driver changed"), so this runs seconds after a restart.
            bolusCoordinator.recoverLeftoverBolus(info, serialNumber())
            tempBasalCoordinator.endTempBasalThePatchDoesNotRun(info, serialNumber())
            bolusCoordinator.endExtendedBolusThePatchDoesNotRun(info, serialNumber())
        } catch (e: Throwable) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.readInfusionInfo FAILED", e)
        }
    }

    override suspend fun setNewBasalProfile(profile: PumpProfile): PumpEnactResult {
        aapsLogger.debug(LTag.PUMP, "setNewBasalProfile called - ${carelevoPatch.resolvePatchState()}")
        _lastDataTime.value = Clock.System.now().toEpochMilliseconds()
        // PROFILE_SET_OK / FAILED_UPDATE_PROFILE are posted centrally by the CommandQueue from the returned
        // success/enacted (unified across pumps) — this method only returns the right values.
        val result = when (carelevoPatch.resolvePatchState()) {
            is PatchState.NotConnectedNotBooting -> {
                // No active patch yet — store the profile for when a patch is activated. A deferred write,
                // not an actual change, so enacted=false (no PROFILE_SET_OK); success=true keeps the
                // not-ready case out of the failure alarm (matches the other queue-managed pumps).
                carelevoPatch.setProfile(profile)
                pumpEnactResultProvider().success(true).enacted(false)
            }

            else                                 -> {
                // Patch present. setNewBasalProfile runs on the queue worker AFTER the queue guaranteed a
                // fully-connected link, so this is the live push path even if the cached patchState briefly
                // reads NotConnectedBooted. updateBasalProfile returns the real success/enacted result.
                updateBasalProfile(profile)
            }
        }
        aapsLogger.debug(LTag.PUMP, "result success=${result.success} enacted=${result.enacted} comment=${result.comment}")
        return result
    }

    private suspend fun updateBasalProfile(profile: Profile): PumpEnactResult {
        return basalProfileUpdateCoordinator.updateBasalProfile(
            profile = profile,
            cancelExtendedBolus = {
                bolusCoordinator.cancelExtendedBolus(
                    serialNumber = serialNumber(),
                    onLastDataUpdated = { _lastDataTime.value = Clock.System.now().toEpochMilliseconds() }
                )
            },
            cancelTempBasal = {
                tempBasalCoordinator.cancelTempBasal(
                    serialNumber = serialNumber(),
                    onLastDataUpdated = { _lastDataTime.value = Clock.System.now().toEpochMilliseconds() }
                )
            },
            onProfileUpdated = { updatedProfile ->
                _lastDataTime.value = Clock.System.now().toEpochMilliseconds()
                carelevoPatch.setProfile(updatedProfile)
            }
        )
    }

    /**
     * Compared with the program the patch accepted, which is stored and survives a restart. The profile held in memory
     * is the running one after a restart, and that holds the basal the patch gives, rounded (#2454). It never matched
     * the requested profile, so every restart set the basal again and cancelled the running temp basal. Before a
     * program was accepted (no patch yet) the profile held in memory is used.
     *
     * A patch that is being activated counts as set: the activation's last step programs the basal, from the profile
     * AAPS asks for then. Before, the queue's profile check wrote a program to a real patch during the activation,
     * before its needle was in (2026-11-18).
     */
    override fun isThisProfileSet(profile: PumpProfile): Boolean {
        if (carelevoPatch.patchInfo.latest?.activationComplete == false) return true
        return basalProfileUpdateCoordinator.isAccepted(profile) ?: carelevoPatch.checkIsSameProfile(profile)
    }

    /**
     * The 24 hourly rates the patch runs: the program it accepted, each rate rounded to 0.01 U/h as it is sent (#2454).
     * The patch cannot report its program, so this is the one stored after the patch accepted it. null before a
     * program was accepted: the profile counts as it was sent.
     */
    override fun deliveredBasal(): List<Profile.ProfileValue>? =
        basalProfileUpdateCoordinator.acceptedBasalSegments()
            ?.sortedBy { it.startTime }
            ?.map { Profile.ProfileValue(it.startTime * SECONDS_PER_HOUR, BasalProgramCommand.sentSpeed(it.speed)) }

    // Activation ops (safety check, …) are queued so they get the CommandQueue's managed
    // connect-before-execute / reconnect lifecycle instead of a direct BLE call.
    override fun executeCustomCommand(customCommand: CustomCommand): PumpEnactResult? =
        activationExecutor.execute(customCommand)

    private val _lastDataTime = MutableStateFlow(0L)
    override val lastDataTime: StateFlow<Long> = _lastDataTime.asStateFlow()

    override val lastBolusTime: StateFlow<Long?>
        get() = bolusCoordinator.lastBolusTime

    override val lastBolusAmount: StateFlow<PumpInsulin?>
        get() = bolusCoordinator.lastBolusAmount

    // What the patch really gives in this hour, as the running profile holds it - not the rate it was asked for (#2454).
    override val baseBasalRate: PumpRate
        get() {
            val secondsOfDay = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).let { it.hour * SECONDS_PER_HOUR + it.minute * 60 }
            val delivered = deliveredBasal()?.lastOrNull { it.timeAsSeconds <= secondsOfDay }?.value
            return PumpRate(delivered ?: carelevoPatch.profile.latest?.getBasal() ?: 0.0)
        }

    private val _reservoirLevel = MutableStateFlow<PumpInsulin?>(null)
    override val reservoirLevel: StateFlow<PumpInsulin?> = _reservoirLevel

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    override val batteryLevel: StateFlow<Int?> = _batteryLevel

    // start imme bolus infusion
    override suspend fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        return bolusCoordinator.deliverTreatment(
            detailedBolusInfo = detailedBolusInfo,
            serialNumber = serialNumber(),
            onLastDataUpdated = { _lastDataTime.value = Clock.System.now().toEpochMilliseconds() },
            onStatusRefreshRequested = {
                scope?.launch(start = CoroutineStart.UNDISPATCHED) { commandQueue.readStatus("Carelevo bolus completion") }
            },
            onAlarmsRefreshRequested = ::refreshActiveAlarmSnapshots
        )
    }

    // cancel imme bolus
    override fun stopBolusDelivering() {
        bolusCoordinator.cancelImmediateBolus(
            serialNumber = serialNumber(),
            onLastDataUpdated = { _lastDataTime.value = Clock.System.now().toEpochMilliseconds() },
            onStatusRefreshRequested = {
                scope?.launch(start = CoroutineStart.UNDISPATCHED) { commandQueue.readStatus("Carelevo bolus cancel") }
            }
        )
    }

    // enforceNew is intentionally ignored on all three TBR entry points: with per-op sessions there
    // is no resting link whose state a "keep the running TBR" optimization could trust, so Carelevo
    // always re-programs the requested TBR (the Pump contract explicitly allows this; the flag is
    // only an optimization hint).
    override suspend fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        return tempBasalCoordinator.setTempBasalAbsolute(
            absoluteRate = absoluteRate,
            durationInMinutes = durationInMinutes,
            tbrType = tbrType,
            serialNumber = serialNumber(),
            onLastDataUpdated = { _lastDataTime.value = Clock.System.now().toEpochMilliseconds() }
        )
    }

    override suspend fun setTempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        return tempBasalCoordinator.setTempBasalPercent(
            percent = percent,
            durationInMinutes = durationInMinutes,
            tbrType = tbrType,
            serialNumber = serialNumber(),
            onLastDataUpdated = { _lastDataTime.value = Clock.System.now().toEpochMilliseconds() }
        )
    }

    override suspend fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        return tempBasalCoordinator.cancelTempBasal(
            serialNumber = serialNumber(),
            onLastDataUpdated = { _lastDataTime.value = Clock.System.now().toEpochMilliseconds() }
        )
    }

    override suspend fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        return bolusCoordinator.setExtendedBolus(
            insulin = insulin,
            durationInMinutes = durationInMinutes,
            serialNumber = serialNumber()
        )
    }

    override suspend fun cancelExtendedBolus(): PumpEnactResult {
        return bolusCoordinator.cancelExtendedBolus(
            serialNumber = serialNumber(),
            onLastDataUpdated = { _lastDataTime.value = Clock.System.now().toEpochMilliseconds() }
        )
    }

    override fun manufacturer(): ManufacturerType {
        return ManufacturerType.CareMedi
    }

    override fun model(): PumpType {
        return PumpType.CAREMEDI_CARELEVO
    }

    override fun serialNumber(): String {
        return carelevoPatch.patchInfo.latest?.manufactureNumber ?: ""
    }

    override val pumpDescription: PumpDescription
        get() = _pumpDescription

    override val isFakingTempsByExtendedBoluses: Boolean
        get() = false

    override suspend fun loadTDDs(): PumpEnactResult {
        return pumpEnactResultProvider()
    }

    override fun canHandleDST(): Boolean {
        return false
    }

    override suspend fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        super.timezoneOrDSTChanged(timeChangeType)
        // Route through the queue (connect-before-execute) like every other patch op. The pump
        // idle-disconnects between commands, so a direct fire-and-forget write would silently fail
        // while resting. Skip ONLY when no patch is active; if the patch is present but its insulinRemain
        // is not yet known (e.g. before the first status read after reconnect) still push with 0 rather
        // than dropping the clock update (original `?: 0` semantics).
        val patchInfo = carelevoPatch.patchInfo.latest ?: return
        val insulin = patchInfo.insulinRemain?.toInt() ?: 0
        val result = commandQueue.customCommand(CmdTimeZoneUpdate(insulinAmount = insulin))
        if (result.success) _lastDataTime.value = Clock.System.now().toEpochMilliseconds()
    }

    private companion object {

        /** How long the start waits for the patch streams before it applies the profile. */
        val INIT_WAIT = 5.seconds

        const val SECONDS_PER_HOUR = 3600
    }
}
