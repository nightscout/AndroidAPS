package app.aaps.pump.carelevo

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
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
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.withEntries
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.ui.compose.icons.IcPluginCarelevo
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.pump.carelevo.ble.CarelevoBleSession
import app.aaps.pump.carelevo.ble.data.BleState
import app.aaps.pump.carelevo.ble.data.BondingState
import app.aaps.pump.carelevo.ble.data.DeviceModuleState
import app.aaps.pump.carelevo.ble.data.DeviceModuleState.Companion.codeToDeviceResult
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
import app.aaps.pump.carelevo.common.CarelevoObserveReceiver
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.common.keys.CarelevoBooleanPreferenceKey
import app.aaps.pump.carelevo.common.keys.CarelevoIntPreferenceKey
import app.aaps.pump.carelevo.common.model.PatchState
import app.aaps.pump.carelevo.compose.CarelevoComposeContent
import app.aaps.pump.carelevo.coordinator.CarelevoBasalProfileUpdateCoordinator
import app.aaps.pump.carelevo.coordinator.CarelevoBolusCoordinator
import app.aaps.pump.carelevo.coordinator.CarelevoConnectionCoordinator
import app.aaps.pump.carelevo.coordinator.CarelevoSettingsCoordinator
import app.aaps.pump.carelevo.coordinator.CarelevoTempBasalCoordinator
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.type.AlarmType.Companion.isCritical
import app.aaps.pump.carelevo.ext.transformNotificationStringResources
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull

@Singleton
class CarelevoPumpPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val aapsSchedulers: AapsSchedulers,
    private val sp: SP,
    private val fabricPrivacy: FabricPrivacy,
    private val profileFunction: ProfileFunction,
    private val context: Context,
    private val protectionCheck: ProtectionCheck,
    private val blePreCheck: BlePreCheck,
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
    private val settingsCoordinator: CarelevoSettingsCoordinator,
    private val activationExecutor: CarelevoActivationExecutor
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .composeContent { _ ->
            CarelevoComposeContent(
                aapsLogger = aapsLogger,
                carelevoAlarmNotifier = carelevoAlarmNotifier,
                protectionCheck = protectionCheck,
                blePreCheck = blePreCheck,
                iconsProvider = iconsProvider,
                config = config
            )
        }
        .icon(IcPluginCarelevo)
        .pluginName(R.string.carelevo)
        .shortName(R.string.carelevo_shortname)
        .description(R.string.carelevo_description),
    ownPreferences = listOf(CarelevoBooleanPreferenceKey::class.java, CarelevoIntPreferenceKey::class.java),
    aapsLogger, rh, preferences, commandQueue
), Pump {

    private var bleReceiverDisposable: Disposable? = null
    private val pluginDisposable = CompositeDisposable()

    private var _pumpType: PumpType = PumpType.CAREMEDI_CARELEVO
    private val _pumpDescription = PumpDescription().fillFor(_pumpType)

    private var scope: CoroutineScope? = null
    private var lifecycleObserver: LifecycleEventObserver? = null

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
        registerBleReceiverIfNeeded()
        startAlarmObserving()
        startAutoResumeWatchdog()
        // Consume patch-pushed frames (alarms, stop/basal-restart reports) whenever a session is open.
        bleSession.unsolicitedHandler = carelevoPatch::onUnsolicited
    }

    override suspend fun onStop() {
        super.onStop()
        aapsLogger.debug(LTag.PUMP, "onStop called")
        bleSession.unsolicitedHandler = null
        settingsCoordinator.clearUserSettings(pluginDisposable)
        pluginDisposable.clear()

        // onStart/onStop run as separate, unjoined pluginScope coroutines (PluginBase), so a fast
        // disable→re-enable can overlap them. Tear down only the generation captured here, and clear
        // a field only if it still points at that generation — never clobber a scope/observer that a
        // concurrent onStart may have just re-created (this fn suspends at withContext below).
        val scopeToCancel = scope
        val observerToRemove = lifecycleObserver

        scopeToCancel?.cancel()
        if (scope === scopeToCancel) scope = null

        carelevoAlarmNotifier.stopObserving()

        observerToRemove?.let { observer ->
            withContext(Dispatchers.Main) {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
            }
        }
        if (lifecycleObserver === observerToRemove) lifecycleObserver = null
    }

    private fun registerPreferenceChangeObserver() {
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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
                val hours = sp.getInt(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS.key, 0)
                commandQueue.customCommand(CmdUpdateExpiredThreshold(hours))
            }
            .launchIn(newScope)

        preferences.observe(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS)
            .drop(1)
            .onEach {
                val hours = sp.getInt(CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS.key, 0)
                // Zero = reminder off; skip enqueuing so the pump isn't reconnected just to no-op.
                if (hours != 0) commandQueue.customCommand(CmdUpdateLowInsulinNotice(hours))
            }
            .launchIn(newScope)

        preferences.observe(CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER)
            .drop(1)
            .onEach {
                val on = sp.getBoolean(CarelevoBooleanPreferenceKey.CARELEVO_BUZZER_REMINDER.key, false)
                commandQueue.customCommand(CmdUpdateBuzzer(on))
            }
            .launchIn(newScope)

        // Deferred settings-sync recovery: a max-bolus / low-insulin change that couldn't reach the patch
        // (changed while offline, or during a bolus) leaves a needXSyncPatch flag on the stored user
        // settings; when the patch is booted again, push it through the queue. The combiner is PURE and the
        // enqueue runs on IO.
        pluginDisposable += Observable.combineLatest(
            carelevoPatch.patchState,
            carelevoPatch.infusionInfo,
            carelevoPatch.userSettingInfo
        ) { state, infusion, setting ->
            computeSettingsSyncNeed(state.getOrNull(), infusion.getOrNull(), setting.getOrNull())
        }
            .distinctUntilChanged()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe { need ->
                newScope.launch {
                    need.maxBolusDose?.let { commandQueue.customCommand(CmdUpdateMaxBolus(it)) }
                    need.lowInsulinHours?.let { commandQueue.customCommand(CmdUpdateLowInsulinNotice(it)) }
                }
            }
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
        val patchInfo = carelevoPatch.patchInfo.value?.getOrNull() ?: return
        if (patchInfo.isStopped != true) return
        val stopMinutes = patchInfo.stopMinutes ?: return
        if (stopMinutes <= 0) return
        // Durable stop-start anchor: basalInfusionInfo.updatedAt is set at persistStopped, survives an app
        // restart (persisted), and is not moved by 0x91 status reads (which only rewrite patchInfo). Skip
        // until infusion info is loaded.
        val stopStartedAt = carelevoPatch.infusionInfo.value?.getOrNull()?.basalInfusionInfo?.updatedAt?.millis ?: return
        val elapsedMs = System.currentTimeMillis() - stopStartedAt
        if (elapsedMs < stopMinutes * 60_000L + autoResumeMarginMs) return
        aapsLogger.info(LTag.PUMPCOMM, "auto-resume: ${stopMinutes}min stop elapsed (${elapsedMs / 1000}s) -> reconcile")
        carelevoPatch.reconcileAutoResumed()
        commandQueue.readStatus("Carelevo auto-resume")
    }

    private data class SettingsSyncNeed(val maxBolusDose: Double?, val lowInsulinHours: Int?)

    /** Pure: which deferred patch settings still need pushing (flag set AND the patch is booted). */
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
        val maxBolusDose = if (setting.needMaxBolusDoseSyncPatch && noBolusRunning) setting.maxBolusDose ?: 0.0 else null
        val lowInsulinHours = if (setting.needLowInsulinNoticeAmountSyncPatch) setting.lowInsulinNoticeAmount ?: 0 else null
        return SettingsSyncNeed(maxBolusDose, lowInsulinHours)
    }

    private fun initializeOnStart() {
        // Run initialization directly on start instead of gating it on EventAppInitialized.
        // onStart() is now a suspend function launched fire-and-forget, so the (non-replayed)
        // EventAppInitialized could fire before this subscription was registered — leaving the
        // patch uninitialized and appearing deactivated after an app update (dev fixed the same
        // race in eopatch). initPatchOnce() does not depend on other plugins; the basal profile is
        // applied best-effort here and, if not yet available, is set later via setNewBasalProfile().
        pluginDisposable += carelevoPatch.initPatchOnce()
            .subscribeOn(aapsSchedulers.io)
            .timeout(5, TimeUnit.SECONDS)
            .onErrorComplete()
            .doOnSubscribe { aapsLogger.debug(LTag.PUMPCOMM, "onStart: 1) initPatchOnce waiting") }
            .doOnComplete { aapsLogger.debug(LTag.PUMPCOMM, "onStart: 1) initPatchOnce completed") }
            .andThen(
                Completable.fromAction {
                    val profile = runBlocking { profileFunction.getProfile() }
                    if (profile != null) {
                        carelevoPatch.setProfile(profile)
                        aapsLogger.debug(LTag.PUMPCOMM, "onStart: 3) setProfile done: $profile")
                    } else {
                        aapsLogger.debug(LTag.PUMPCOMM, "onStart: 3) profile not ready, deferring to setNewBasalProfile")
                    }
                }
            )
            .subscribe(
                { aapsLogger.debug(LTag.PUMPCOMM, "onStart: ALL COMPLETE") },
                { e -> aapsLogger.error(LTag.PUMPCOMM, "onStart: chain error", e) }
            )

        pluginDisposable += carelevoPatch.patchInfo
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           _reservoirLevel.value = PumpInsulin(it.getOrNull()?.insulinRemain ?: 0.0)
                           _batteryLevel.value = 0
                       }, fabricPrivacy::logException)
    }

    private fun registerBleReceiverIfNeeded() {
        if (bleReceiverDisposable?.isDisposed == false) return

        // Seed the adapter state so isBluetoothEnabled()/patchState resolve before the first broadcast —
        // the receiver is now the ONLY btState source (adapter on/off; there is no resting link to track).
        carelevoPatch.onBluetoothStateChanged(currentAdapterBleState())

        // The emulated patch has no radio, so the state seeded above is the whole truth. Subscribing
        // anyway would let the host's adapter being switched off report OFF and kill a live emulated
        // session — the one thing emulation is supposed to be immune to.
        if (isEmulating) return

        bleReceiverDisposable = CarelevoObserveReceiver(context, createBluetoothIntentFilter())
            .subscribe { intent ->
                aapsLogger.debug(LTag.PUMPBTCOMM, "CarelevoObserveReceiver called: ${intent.action}")
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val value = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (value in setOf(BluetoothAdapter.STATE_ON, BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_ON, BluetoothAdapter.STATE_TURNING_OFF)) {
                        carelevoPatch.onBluetoothStateChanged(adapterBleState(value.codeToDeviceResult()))
                    }
                }
            }

        bleReceiverDisposable?.let { pluginDisposable.add(it) }
    }

    /**
     * The emulated patch is reachable regardless of the host adapter, so report ON without asking it.
     * Without this the whole driver — every `isBluetoothEnabled()` gate in the coordinators and view
     * models — refuses before a request ever reaches the emulated transport. Mirrors the way Dana
     * skips its BLE pre-check while emulating; Dana needs no equivalent here only because it never
     * reads the adapter's enabled state at all.
     */
    private fun currentAdapterBleState(): BleState {
        val enabled = isEmulating ||
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
        return adapterBleState(if (enabled) DeviceModuleState.DEVICE_STATE_ON else DeviceModuleState.DEVICE_STATE_OFF)
    }

    private val isEmulating: Boolean get() = config.isEnabled(ExternalOptions.EMULATE_CARELEVO)

    /** Adapter-level state only — bond/discovery/notification fields are per-session now and never tracked. */
    private fun adapterBleState(enabled: DeviceModuleState): BleState = BleState(
        isEnabled = enabled,
        isBonded = BondingState.BOND_NONE,
        isServiceDiscovered = ServiceDiscoverState.DISCOVER_STATE_NONE,
        isConnected = PeripheralConnectionState.CONN_STATE_NONE,
        isNotificationEnabled = NotificationState.NOTIFICATION_NONE
    )

    private fun createBluetoothIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
    }

    private fun applyDefaultCageThresholdsIfNeeded() {
        if (sp.getBoolean(CarelevoBooleanPreferenceKey.CARELEVO_CAGE_DEFAULT_APPLIED.key, false)) return

        sp.edit {
            putInt(IntKey.OverviewCageWarning.key, 96)
            putInt(IntKey.OverviewCageCritical.key, 168)
            putBoolean(CarelevoBooleanPreferenceKey.CARELEVO_CAGE_DEFAULT_APPLIED.key, true)
        }
    }

    private suspend fun startAlarmObserving() {
        aapsLogger.debug(LTag.NOTIFICATION, "startAlarmObserving:: onStart")

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                aapsLogger.debug(LTag.NOTIFICATION, "Foreground transition -> refresh alarms")
                carelevoAlarmNotifier.refreshAlarms()
            }
        }
        lifecycleObserver = observer
        withContext(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        }

        carelevoAlarmNotifier.startObserving { alarms ->
            aapsLogger.debug(LTag.NOTIFICATION, "observe alarms size=${alarms.size}, $alarms")
            handleAlarms(alarms)
        }
    }

    // Critical alarms already escalated to the global alarm (so a re-emission of the same list
    // doesn't re-fire the sound). Pruned to the currently-active set on every pass.
    private val globallyAlarmedIds = mutableSetOf<String>()

    private fun handleAlarms(alarms: List<CarelevoAlarmInfo>) {
        aapsLogger.debug(LTag.NOTIFICATION, "startAlarmObserving handleAlarms:: $alarms")
        globallyAlarmedIds.retainAll(alarms.map { it.alarmId }.toSet())
        if (alarms.isEmpty()) return

        val critical = alarms.filter { it.alarmType.isCritical() }
        if (critical.isNotEmpty()) {
            // filter-with-add: keeps only the not-yet-escalated alarms AND marks them escalated.
            val fresh = critical.filter { globallyAlarmedIds.add(it.alarmId) }
            if (carelevoAlarmNotifier.alarmHostActive) {
                // The in-app host is mounted — it presents the full-screen alarm and starts the
                // sound itself (CarelevoAlarmHost/CarelevoAlarmViewModel).
                aapsLogger.debug(LTag.NOTIFICATION, "critical alarm handled by compose host")
            } else if (fresh.isNotEmpty()) {
                // No in-app surface (backgrounded, or user on another screen): fire the global AAPS
                // alarm (sound + full-screen intent) — a critical patch alarm must NEVER depend on
                // the user having the Carelevo screen open.
                val first = fresh.first()
                uiInteraction.runAlarm(
                    status = rh.gs(first.cause.transformNotificationStringResources().first),
                    title = rh.gs(R.string.carelevo),
                    soundId = CoreUiR.raw.error
                )
            }
        } else {
            carelevoAlarmNotifier.showTopNotification(alarms)
        }
    }

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "carelevo_settings",
        titleResId = R.string.carelevo,
        items = listOf(
            CarelevoIntPreferenceKey.CARELEVO_LOW_INSULIN_EXPIRATION_REMINDER_HOURS.withEntries(
                (20..50 step 5).associateWith { "$it U" }
            ),
            CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS.withEntries(
                (24..167 step 1).associateWith { "$it ${rh.gs(app.aaps.core.interfaces.R.string.hours)}" }
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
        carelevoPatch.patchInfo.value?.getOrNull() != null

    override fun isInitialized(): Boolean {
        return connectionCoordinator.isInitialized()
    }

    override fun isSuspended(): Boolean {
        // Real delivery-suspend (pump stopped by the user), NOT the BLE connection state. Otherwise, a
        // normal idle disconnect (NotConnectedBooted, now that disconnect() actually disconnects) would
        // be reported as suspended and surface as an error/suspended icon on the overview.
        return carelevoPatch.patchInfo.value?.getOrNull()?.isStopped ?: false
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
            _lastDataTime.value = System.currentTimeMillis()
            aapsLogger.info(
                LTag.PUMPCOMM,
                "newBle.readInfusionInfo OK remains=${info.insulinRemaining} basal=${info.infusedTotalBasalAmount} " +
                    "bolus=${info.infusedTotalBolusAmount} pumpState=${info.pumpStateRaw} mode=${info.modeRaw} running=${info.runningMinutes}"
            )
        } catch (e: Throwable) {
            aapsLogger.error(LTag.PUMPCOMM, "newBle.readInfusionInfo FAILED", e)
        }
    }

    override suspend fun setNewBasalProfile(profile: PumpProfile): PumpEnactResult {
        aapsLogger.debug(LTag.PUMP, "setNewBasalProfile called - ${carelevoPatch.resolvePatchState()}")
        _lastDataTime.value = System.currentTimeMillis()
        // PROFILE_SET_OK / FAILED_UPDATE_PROFILE are posted centrally by the CommandQueue from the returned
        // success/enacted (unified across pumps) — this method only returns the right values.
        val result = when (carelevoPatch.resolvePatchState()) {
            is PatchState.NotConnectedNotBooting -> {
                // No active patch yet — store the profile for when a patch is activated. A deferred write,
                // not an actual change, so enacted=false (no PROFILE_SET_OK); success=true keeps the
                // not-ready case out of the failure alarm (matches the other queue-managed pumps).
                carelevoPatch.setProfile(profile)
                pumpEnactResultProvider.get().success(true).enacted(false)
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

    private fun updateBasalProfile(profile: Profile): PumpEnactResult {
        return basalProfileUpdateCoordinator.updateBasalProfile(
            profile = profile,
            cancelExtendedBolus = {
                bolusCoordinator.cancelExtendedBolus(
                    serialNumber = serialNumber(),
                    onLastDataUpdated = { _lastDataTime.value = System.currentTimeMillis() }
                )
            },
            cancelTempBasal = {
                tempBasalCoordinator.cancelTempBasal(
                    serialNumber = serialNumber(),
                    onLastDataUpdated = { _lastDataTime.value = System.currentTimeMillis() }
                )
            },
            onProfileUpdated = { updatedProfile ->
                _lastDataTime.value = System.currentTimeMillis()
                carelevoPatch.setProfile(updatedProfile)
            }
        )
    }

    override fun isThisProfileSet(profile: PumpProfile): Boolean {
        return carelevoPatch.checkIsSameProfile(profile)
    }

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

    override val baseBasalRate: PumpRate
        get() = PumpRate(carelevoPatch.profile.value?.getOrNull()?.getBasal() ?: 0.0)

    private val _reservoirLevel = MutableStateFlow(PumpInsulin(0.0))
    override val reservoirLevel: StateFlow<PumpInsulin> = _reservoirLevel

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    override val batteryLevel: StateFlow<Int?> = _batteryLevel

    // start imme bolus infusion
    override suspend fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        return bolusCoordinator.deliverTreatment(
            detailedBolusInfo = detailedBolusInfo,
            serialNumber = serialNumber(),
            onLastDataUpdated = { _lastDataTime.value = System.currentTimeMillis() },
            pluginDisposable = pluginDisposable
        )
    }

    // cancel imme bolus
    override fun stopBolusDelivering() {
        bolusCoordinator.cancelImmediateBolus(
            serialNumber = serialNumber(),
            onLastDataUpdated = { _lastDataTime.value = System.currentTimeMillis() }
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
            onLastDataUpdated = { _lastDataTime.value = System.currentTimeMillis() }
        )
    }

    override suspend fun setTempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        return tempBasalCoordinator.setTempBasalPercent(
            percent = percent,
            durationInMinutes = durationInMinutes,
            tbrType = tbrType,
            serialNumber = serialNumber(),
            onLastDataUpdated = { _lastDataTime.value = System.currentTimeMillis() }
        )
    }

    override suspend fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        return tempBasalCoordinator.cancelTempBasal(
            serialNumber = serialNumber(),
            onLastDataUpdated = { _lastDataTime.value = System.currentTimeMillis() }
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
            onLastDataUpdated = { _lastDataTime.value = System.currentTimeMillis() }
        )
    }

    override fun manufacturer(): ManufacturerType {
        return ManufacturerType.CareMedi
    }

    override fun model(): PumpType {
        return PumpType.CAREMEDI_CARELEVO
    }

    override fun serialNumber(): String {
        return carelevoPatch.patchInfo.value?.getOrNull()?.manufactureNumber ?: ""
    }

    override val pumpDescription: PumpDescription
        get() = _pumpDescription

    override val isFakingTempsByExtendedBoluses: Boolean
        get() = false

    override suspend fun loadTDDs(): PumpEnactResult {
        return pumpEnactResultProvider.get()
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
        val patchInfo = carelevoPatch.patchInfo.value?.getOrNull() ?: return
        val insulin = patchInfo.insulinRemain?.toInt() ?: 0
        val result = commandQueue.customCommand(CmdTimeZoneUpdate(insulinAmount = insulin))
        if (result.success) _lastDataTime.value = System.currentTimeMillis()
    }
}
