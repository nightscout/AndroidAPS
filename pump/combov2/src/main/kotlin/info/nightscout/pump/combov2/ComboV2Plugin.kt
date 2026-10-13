package info.nightscout.pump.combov2

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.TE
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.androidPermissions.AndroidPermission
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import info.nightscout.comboctl.android.AndroidBluetoothInterface
import info.nightscout.comboctl.base.BasicProgressStage
import info.nightscout.comboctl.base.BluetoothException
import info.nightscout.comboctl.base.BluetoothNotAvailableException
import info.nightscout.comboctl.base.BluetoothNotEnabledException
import info.nightscout.comboctl.base.ComboException
import info.nightscout.comboctl.base.DisplayFrame
import info.nightscout.comboctl.base.NullDisplayFrame
import info.nightscout.comboctl.base.PairingPIN
import info.nightscout.comboctl.main.BasalProfile
import info.nightscout.comboctl.main.QuantityNotChangingException
import info.nightscout.comboctl.main.RTCommandProgressStage
import info.nightscout.comboctl.parser.AlertScreenContent
import info.nightscout.comboctl.parser.AlertScreenException
import info.nightscout.comboctl.parser.BatteryState
import info.nightscout.comboctl.parser.ReservoirState
import info.nightscout.pump.combov2.activities.ComboV2PairingActivity
import info.nightscout.pump.combov2.keys.ComboBooleanKey
import info.nightscout.pump.combov2.keys.ComboIntKey
import info.nightscout.pump.combov2.keys.ComboIntNonKey
import info.nightscout.pump.combov2.keys.ComboIntentKey
import info.nightscout.pump.combov2.keys.ComboLongNonKey
import info.nightscout.pump.combov2.keys.ComboStringNonKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime
import info.nightscout.comboctl.base.BluetoothAddress as ComboCtlBluetoothAddress
import info.nightscout.comboctl.base.LogLevel as ComboCtlLogLevel
import info.nightscout.comboctl.base.Logger as ComboCtlLogger
import info.nightscout.comboctl.base.Tbr as ComboCtlTbr
import info.nightscout.comboctl.main.Pump as ComboCtlPump
import info.nightscout.comboctl.main.PumpManager as ComboCtlPumpManager

internal const val PUMP_ERROR_TIMEOUT_INTERVAL_MSECS = 1000L * 60 * 5

@Singleton
class ComboV2Plugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val context: Context,
    private val rxBus: RxBus,
    private val constraintChecker: ConstraintsChecker,
    sp: SP,
    private val pumpSync: PumpSync,
    private val dateUtil: DateUtil,
    private val uiInteraction: UiInteraction,
    private val androidPermission: AndroidPermission,
    private val config: Config,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>
) :
    PumpPluginBase(
        pluginDescription = PluginDescription()
            .mainType(PluginType.PUMP)
            .fragmentClass(ComboV2Fragment::class.java.name)
            .pluginIcon(R.drawable.ic_combov2)
            .pluginName(R.string.combov2_plugin_name)
            .shortName(R.string.combov2_plugin_shortname)
            .description(R.string.combov2_plugin_description)
            .preferencesId(PluginDescription.PREFERENCE_SCREEN),
        ownPreferences = listOf(
            ComboIntentKey::class.java, ComboIntKey::class.java, ComboBooleanKey::class.java,
            ComboStringNonKey::class.java, ComboIntNonKey::class.java, ComboLongNonKey::class.java
        ),
        aapsLogger, rh, preferences, commandQueue
    ), Pump, PluginConstraints {

    // Coroutine scope and the associated job. All coroutines
    // that are started in this plugin are part of this scope.
    private var pumpCoroutineScopeJob = SupervisorJob()
    private var pumpCoroutineScope = CoroutineScope(Dispatchers.Default + pumpCoroutineScopeJob)

    private val _pumpDescription = PumpDescription()

    private val pumpStateStore = AAPSPumpStateStore(sp)
    private var pumpStateBackup: AAPSPumpStateStore.StatesBackup? = null

    // These are initialized in onStart() and torn down in onStop().
    private var bluetoothInterface: AndroidBluetoothInterface? = null
    private var pumpManager: ComboCtlPumpManager? = null
    private var initializationChangedEventSent = false

    // These are initialized in connect() and torn down in disconnect().
    private var pump: ComboCtlPump? = null
    private var connectionSetupJob: Job? = null
    private var stateAndStatusFlowsDeferred: Deferred<Unit>? = null
    private var pumpUIFlowsDeferred: Deferred<Unit>? = null

    // States for the Pump interface and for the UI.
    private var pumpStatus: ComboCtlPump.Status? = null
    private var lastConnectionTimestamp = 0L
    private var lastComboAlert: AlertScreenContent? = null

    // States for when the pump reports an error. We then want isInitialized()
    // to return false until either the user presses the Refresh button or the
    // pumpErrorTimeoutJob expires. That way, the loop won't run until then,
    // giving the user a chance to handle the error.
    private var pumpErrorObserved = false
    private var pumpErrorTimeoutJob: Job? = null

    // Set to true if a disconnect request came in while the driver
    // was in the Connecting, CheckingPump, or ExecutingCommand
    // state (in other words, while isBusy() was returning true).
    private var disconnectRequestPending = false

    // Set to true in when unpair() starts and back to false in the
    // pumpManager onPumpUnpaired callback. This fixes a race condition
    // that can happen if the user unpairs the pump while AndroidAPS
    // is calling connect().
    private var unpairing = false

    // The current driver state. We use a StateFlow here to
    // allow other components to react to state changes.
    private val _driverStateFlow = MutableStateFlow<DriverState>(DriverState.NotInitialized)

    // If true, the pump was found to be suspended during the connect()
    // call. This is separate from driverStateFlow and driverStateUIFlow.
    // It is set immediately after connect() (while the other two may be
    // set in a separate coroutine), which is important for check
    // inside connect() and checks before commands like deliverTreatment()
    // are run. This is what drives the isSuspended() call, and is _not_
    // to be used for UI update purposes (use driverStateUIFlow for that).
    // Like driverStateUIFlow, this state persists even after disconnecting
    // from the pump. This is necessary, because AAPS may call isSuspended()
    // even when the pump is not connected.
    private var pumpIsSuspended = false

    // The basal profile that is set to be the pump's current profile.
    // If the pump's actual basal profile deviates from this, it is
    // overwritten. This check is performed in checkBasalProfile().
    // In setNewBasalProfile(), this value is changed.
    private var activeBasalProfile: BasalProfile? = null

    // This is used for checking that the correct basal profile is
    // active in the Combo. If not, loop invocation is disallowed.
    // This is _not_ reset by disconnect(). That's on purpose; it
    // is read by isLoopInvocationAllowed(), which is called even
    // if the pump is not connected.
    private var lastActiveBasalProfileNumber: Int? = null

    private var bolusJob: Job? = null

    /*** Public functions and base class & interface overrides ***/

    sealed class DriverState(@Suppress("unused") val label: String) {

        // Initial state when the driver is created.
        data object NotInitialized : DriverState("notInitialized")

        // Driver is disconnected from the pump, or no pump
        // is currently paired. In onStart() the driver state
        // changes from NotInitialized to this.
        data object Disconnected : DriverState("disconnected")

        // Driver is currently connecting to the pump. isBusy()
        // will return true in this state.
        data object Connecting : DriverState("connecting")

        // Driver is running checks on the pump, like verifying
        // that the basal rate is OK, checking for any bolus
        // and TBR activity that AAPS doesn't know about etc.
        // isBusy() will return true in this state.
        data object CheckingPump : DriverState("checkingPump")

        // Driver is connected and ready to execute commands.
        data object Ready : DriverState("ready")

        // Driver is connected, but pump is suspended and
        // cannot currently execute commands. This state is
        // special in that it technically persists even after
        // disconnecting (because the pump remains suspended
        // until the user resumes it, not until the connection
        // is terminated), but it does not persists the same
        // way here (it is replaced by Disconnected after
        // the connection is terminated). This state is used
        // for UI updates (see driverStateUIFlow) and for
        // checks during driver state updates and connection
        // attempts.
        // NOTE: Do not compare against this state to check
        // prior to commands like deliverTreatment() if
        // the pump is currently suspended or not. Use
        // isSuspended() instead. See the pumpIsSuspended
        // documentation for details.
        data object Suspended : DriverState("suspended")

        // Driver is currently executing a command.
        // isBusy() will return true in this state.
        class ExecutingCommand(val description: ComboCtlPump.CommandDescription) : DriverState("executingCommand")
        data object Error : DriverState("error")
    }

    private val driverStateFlow = _driverStateFlow.asStateFlow()

    // Used by ComboV2PairingActivity to launch its own
    // custom activities that have a result.
    var customDiscoveryActivityStartCallback: ((intent: Intent) -> Unit)?
        set(value) {
            bluetoothInterface?.customDiscoveryActivityStartCallback = value
        }
        get() = bluetoothInterface?.customDiscoveryActivityStartCallback

    init {
        ComboCtlLogger.backend = AAPSComboCtlLogger(aapsLogger)
        _pumpDescription.fillFor(PumpType.ACCU_CHEK_COMBO)
    }

    override fun onStart() {
        aapsLogger.info(LTag.PUMP, "Starting combov2 driver")

        super.onStart()

        updateComboCtlLogLevel()

        aapsLogger.debug(LTag.PUMP, "Creating bluetooth interface")
        val newBluetoothInterface = AndroidBluetoothInterface(context)
        bluetoothInterface = newBluetoothInterface

        aapsLogger.info(LTag.PUMP, "Continuing combov2 driver start in coroutine")

        // Continue initialization in a separate coroutine. This allows us to call
        // runWithPermissionCheck(), which will keep trying to run the code block
        // until either the necessary Bluetooth permissions are granted, or the
        // coroutine is cancelled (see onStop() below).
        pumpCoroutineScope.launch {
            try {
                runWithPermissionCheck(
                    context, config, aapsLogger, androidPermission,
                    permissionsToCheckFor = listOf("android.permission.BLUETOOTH_CONNECT")
                ) {
                    aapsLogger.debug(LTag.PUMP, "Setting up bluetooth interface")

                    try {
                        newBluetoothInterface.setup()

                        rxBus.send(EventDismissNotification(Notification.BLUETOOTH_NOT_ENABLED))

                        aapsLogger.debug(LTag.PUMP, "Setting up pump manager")
                        val newPumpManager = ComboCtlPumpManager(newBluetoothInterface, pumpStateStore)
                        newPumpManager.setup {
                            _pairedStateUIFlow.value = false
                            unpairing = false
                        }

                        // UI flows that must have defined values right
                        // at start are initialized here.

                        // The paired state UI flow is special in that it is also
                        // used as the backing store for the isPaired() function,
                        // so setting up that UI state flow equals updating that
                        // paired state.
                        val paired = newPumpManager.getPairedPumpAddresses().isNotEmpty()
                        _pairedStateUIFlow.value = paired

                        pumpManager = newPumpManager
                    } catch (_: BluetoothNotAvailableException) {
                        uiInteraction.addNotification(
                            Notification.BLUETOOTH_NOT_SUPPORTED,
                            text = rh.gs(R.string.combov2_bluetooth_not_supported),
                            level = Notification.URGENT
                        )

                        // Deliberately _not_ setting the driver state here before
                        // exiting this scope. We are essentially aborting the start
                        // since Bluetooth is not supported by the hardware, so the
                        // driver cannot do anything, and therefore cannot leave the
                        // DriverState.NotInitialized state.
                        aapsLogger.error(LTag.PUMP, "combov2 driver start cannot be completed since the hardware does not support Bluetooth")
                        return@runWithPermissionCheck
                    } catch (_: BluetoothNotEnabledException) {
                        uiInteraction.addNotification(
                            Notification.BLUETOOTH_NOT_ENABLED,
                            text = rh.gs(R.string.combov2_bluetooth_disabled),
                            level = Notification.INFO
                        )

                        // If the user currently has Bluetooth disabled, retry until
                        // the user turns it on. AAPS will automatically show a dialog
                        // box which requests the user to enable Bluetooth. Upon
                        // catching this exception, runWithPermissionCheck() will wait
                        // a bit before retrying, so no delay() call is needed here.
                        throw RetryPermissionCheckException()
                    }

                    setDriverState(DriverState.Disconnected)

                    aapsLogger.info(LTag.PUMP, "combov2 driver start complete")

                    // NOTE: EventInitializationChanged is sent in getPumpStatus() .
                }
            } catch (e: CancellationException) {
                aapsLogger.info(LTag.PUMP, "combov2 driver start cancelled")
                throw e
            }
        }
    }

    override fun onStop() {
        aapsLogger.info(LTag.PUMP, "Stopping combov2 driver")

        runBlocking {
            // Cancel any ongoing background coroutines. This includes an ongoing
            // unfinished initialization that still waits for the user to grant
            // Bluetooth permissions. Also join to wait for the coroutines to
            // finish. Otherwise, race conditions can occur, for example, when
            // a coroutine tries to access bluetoothInterface right after it
            // was torn down below.
            pumpCoroutineScopeJob.cancelAndJoin()

            // Normally this should not happen, but to be safe,
            // make sure any running pump instance is disconnected.
            pump?.disconnect()
            pump = null
        }

        pumpManager = null
        bluetoothInterface?.teardown()
        bluetoothInterface = null

        // Set this flag to false here in case an ongoing pairing attempt
        // is somehow aborted inside the interface without the onPumpUnpaired
        // callback being invoked.
        unpairing = false

        setDriverState(DriverState.NotInitialized)

        rxBus.send(EventInitializationChanged())
        initializationChangedEventSent = false

        // The old job and scope were completed. We need new ones.
        pumpCoroutineScopeJob = SupervisorJob()
        pumpCoroutineScope = CoroutineScope(Dispatchers.Default + pumpCoroutineScopeJob)

        super.onStop()

        aapsLogger.info(LTag.PUMP, "combov2 driver stopped")
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)

        // Setup coroutine to enable/disable the pair and unpair
        // preferences depending on the pairing state.
        preferenceFragment.run {
            // We use the fragment's lifecycle instead of the fragment view's, since the latter
            // is initialized in onCreateView(), and we reach this point here _before_ that
            // method is called. In other words, the fragment view does not exist at this point.
            // repeatOnLifecycle() is a utility function that runs its block when the lifecycle
            // starts. If the fragment is destroyed, the code inside - that is, the flow - is
            // cancelled. That way, the UI flow is automatically reconstructed when Android
            // recreates the fragment.
            lifecycle.coroutineScope.launch {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    val pairPref: Preference? = findPreference(ComboIntentKey.PairWithPump.key)
                    val unpairPref: Preference? = findPreference(ComboIntentKey.UnpairPump.key)

                    val isInitiallyPaired = pairedStateUIFlow.value
                    pairPref?.isEnabled = !isInitiallyPaired
                    unpairPref?.isEnabled = isInitiallyPaired

                    pairedStateUIFlow
                        .onEach { isPaired ->
                            pairPref?.isEnabled = !isPaired
                            unpairPref?.isEnabled = isPaired
                        }
                        .launchIn(this)
                }
            }
        }
    }

    override fun isInitialized(): Boolean =
        isPaired() && (driverStateFlow.value != DriverState.NotInitialized) && !pumpErrorObserved

    override fun isSuspended(): Boolean = pumpIsSuspended

    override fun isBusy(): Boolean =
        when (driverStateFlow.value) {
            // DriverState.Connecting is _not_ listed here. Even though the pump
            // is technically busy and unable to execute commands in that state,
            // returning true then causes problems with AAPS' KeepAlive mechanism.
            DriverState.CheckingPump,
            is DriverState.ExecutingCommand -> true

            else                            -> false
        }

    override fun isConnected(): Boolean =
        when (driverStateFlow.value) {
            // NOTE: Even though the Combo is technically already connected by the
            // time the DriverState.CheckingPump state is reached, do not return
            // true then. That's because the pump still tries to issue commands
            // during that state. isBusy() informs about the pump being busy during
            // that state, but that function is not always called before commands
            // are dispatched, so we announce to the queue thread that we aren't
            // connected yet.
            DriverState.Ready,
            DriverState.Suspended,
            is DriverState.ExecutingCommand -> true

            else                            -> false
        }

    override fun isConnecting(): Boolean =
        when (driverStateFlow.value) {
            DriverState.Connecting,
            DriverState.CheckingPump -> true

            else                     -> false
        }

    // There is no corresponding indicator for this
    // in Combo connections, so just return false
    override fun isHandshakeInProgress() = false

    override fun beforeImport() {
        pumpStateBackup = pumpStateStore.createBackup()
        if (pumpStateBackup != null)
            aapsLogger.debug(LTag.PUMP, "Making backup of pump state before importing new configuration")
        else
            aapsLogger.debug(LTag.PUMP, "There is no pump state present; not making any pump state backup before importing new configuration")
    }

    override fun afterImport() {
        val pumpStateExistsInConfig = pumpStateStore.hasAnyPumpState()

        pumpStateBackup?.let { backup ->
            if (pumpStateExistsInConfig)
                aapsLogger.debug(LTag.PUMP, "Restoring pump state backup after importing new configuration, overwriting the existing one from the imported configuration")
            else
                aapsLogger.debug(LTag.PUMP, "Restoring pump state backup after importing new configuration (the configuration does not have a pump state of its own)")

            pumpStateStore.applyBackup(backup)
            pumpStateBackup = null
        } ?: run {
            if (pumpStateExistsInConfig)
                aapsLogger.debug(LTag.PUMP, "There is no pump state backup to restore after importing new configuration; keeping existing one from the imported configuration")
            else
                aapsLogger.debug(LTag.PUMP, "There is no pump state backup to restore after importing new configuration, and the configuration does not have a pump state of its own")
        }
    }

    override fun connect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Connecting to Combo; reason: $reason")

        if (unpairing) {
            aapsLogger.debug(LTag.PUMP, "Aborting connect attempt since we are currently unpairing")
            return
        }

        if (pumpErrorObserved) {
            aapsLogger.debug(LTag.PUMP, "Aborting connect attempt since the pumpErrorObserved flag is set")
            uiInteraction.addNotification(
                Notification.COMBO_PUMP_ALARM,
                text = rh.gs(R.string.combov2_cannot_connect_pump_error_observed),
                level = Notification.NORMAL
            )
            return
        }

        when (driverStateFlow.value) {
            DriverState.Connecting,
            DriverState.CheckingPump,
            DriverState.Ready,
            DriverState.Suspended,
            is DriverState.ExecutingCommand,
            DriverState.Error -> {
                aapsLogger.debug(
                    LTag.PUMP,
                    "Cannot connect while driver is in the ${driverStateFlow.value} state"
                )
                return
            }

            else              -> Unit
        }

        if (!isPaired()) {
            aapsLogger.debug(LTag.PUMP, "Cannot connect since no Combo has been paired")
            return
        }

        // It makes no sense to reach this location with pump
        // being non-null due to the checks above.
        assert(pump == null)

        lastComboAlert = null
        pumpStatus = null

        val bluetoothAddress = when (val address = getBluetoothAddress()) {
            null -> {
                aapsLogger.error(LTag.PUMP, "No Bluetooth address stored - pump state store may be corrupted")
                unpairDueToPumpDataError()
                return
            }

            else -> address
        }

        try {
            val curPumpManager = pumpManager ?: throw Error("Could not get pump manager; this should not happen. Please report this as a bug.")

            val acquiredPump = runBlocking {
                curPumpManager.acquirePump(bluetoothAddress, activeBasalProfile) { event -> handlePumpEvent(event) }
            }

            pump = acquiredPump

            _bluetoothAddressUIFlow.value = bluetoothAddress.toString()
            _serialNumberUIFlow.value = curPumpManager.getPumpID(bluetoothAddress)

            rxBus.send(EventDismissNotification(Notification.BLUETOOTH_NOT_ENABLED))

            // Erase any display frame that may be left over from a previous connection.
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            _displayFrameUIFlow.resetReplayCache()

            stateAndStatusFlowsDeferred = pumpCoroutineScope.async {
                coroutineScope {
                    acquiredPump.stateFlow
                        .onEach { pumpState ->
                            val driverState = when (pumpState) {
                                // The Disconnected pump state is ignored, since the Disconnected
                                // *driver* state is manually set anyway when disconnecting in
                                // in connect() and disconnectInternal(). Passing it to setDriverState()
                                // here would trigger an EventPumpStatusChanged event to be sent over
                                // the rxBus too early, potentially causing a situation where the connect()
                                // call isn't fully done yet, but the queue gets that event and thinks that
                                // it can try to reconnect now.
                                ComboCtlPump.State.Disconnected        -> return@onEach
                                ComboCtlPump.State.Connecting          -> DriverState.Connecting
                                ComboCtlPump.State.CheckingPump        -> DriverState.CheckingPump
                                ComboCtlPump.State.ReadyForCommands    -> DriverState.Ready
                                is ComboCtlPump.State.ExecutingCommand -> DriverState.ExecutingCommand(pumpState.description)
                                ComboCtlPump.State.Suspended           -> DriverState.Suspended
                                is ComboCtlPump.State.Error            -> DriverState.Error
                            }
                            setDriverState(driverState)
                        }
                        .launchIn(this)
                    acquiredPump.statusFlow
                        .onEach { newPumpStatus ->
                            if (newPumpStatus == null)
                                return@onEach

                            _batteryStateUIFlow.value = newPumpStatus.batteryState
                            _reservoirLevelUIFlow.value = ReservoirLevel(
                                newPumpStatus.reservoirState,
                                newPumpStatus.availableUnitsInReservoir
                            )

                            pumpStatus = newPumpStatus
                            updateLevels()

                            // Send the EventRefreshOverview to keep the overview fragment's content
                            // up to date. Other actions like a CommandQueue.readStatus() call trigger
                            // such a refresh, but if the pump status is updated by something else,
                            // a refresh may not happen automatically. This event send call eliminates
                            // that possibility.
                            rxBus.send(EventRefreshOverview("ComboV2 pump status updated"))
                        }
                        .launchIn(this)
                    acquiredPump.lastBolusFlow
                        .onEach { lastBolus ->
                            if (lastBolus == null)
                                return@onEach

                            _lastBolusUIFlow.value = lastBolus
                        }
                        .launchIn(this)
                    acquiredPump.currentTbrFlow
                        .onEach { currentTbr ->
                            _currentTbrUIFlow.value = currentTbr
                        }
                        .launchIn(this)
                }
            }

            setupUiFlows(acquiredPump)

            ////
            // The actual connect procedure begins here.
            ////

            disconnectRequestPending = false
            setDriverState(DriverState.Connecting)

            connectionSetupJob = pumpCoroutineScope.launch {
                var forciblyDisconnectDueToError = false

                try {
                    runWithPermissionCheck(
                        context, config, aapsLogger, androidPermission,
                        permissionsToCheckFor = listOf("android.permission.BLUETOOTH_CONNECT")
                    ) {
                        // Set maxNumAttempts to null to turn off the connection attempt limit inside the connect() call.
                        // The AAPS queue thread will anyway cause the connectionSetupJob to be canceled when its
                        // connection timeout expires, so the Pump class' own connection attempt limiter is redundant.
                        pump?.connect(maxNumAttempts = null)
                    }

                    // No need to set the driver state here, since the pump's stateFlow will announce that.

                    pump?.let {
                        pumpIsSuspended = when (it.stateFlow.value) {
                            ComboCtlPump.State.Suspended,
                            is ComboCtlPump.State.Error -> true

                            else                        -> false
                        }

                        aapsLogger.debug(LTag.PUMP, "Pump is suspended: $pumpIsSuspended")

                        // We can't read the active profile number in the suspended state, since
                        // the Combo's screen does not show any profile number then.
                        if (!isSuspended()) {
                            // Get the active basal profile number. If it is not profile #1, alert
                            // the user. We also keep a copy of that number to be able to disable
                            // loop invocation if this isn't profile #1 (see the implementation of
                            // isLoopInvocationAllowed() below).
                            val activeBasalProfileNumber = it.statusFlow.value?.activeBasalProfileNumber
                            aapsLogger.debug(LTag.PUMP, "Active basal profile number: $activeBasalProfileNumber")
                            if ((activeBasalProfileNumber != null) && (activeBasalProfileNumber != 1)) {
                                uiInteraction.addNotification(
                                    Notification.COMBO_PUMP_ALARM,
                                    text = rh.gs(R.string.combov2_incorrect_active_basal_profile, activeBasalProfileNumber),
                                    level = Notification.URGENT
                                )
                            }
                            lastActiveBasalProfileNumber = activeBasalProfileNumber
                        }

                        // Read the pump's basal profile to know later, when the loop attempts
                        // to set new profile, whether this procedure is redundant or now.
                        if (activeBasalProfile == null) {
                            aapsLogger.debug(
                                LTag.PUMP,
                                "No basal profile specified by pump queue (yet); using the basal profile that got read from the pump"
                            )
                            activeBasalProfile = it.currentBasalProfile
                        }
                        updateBaseBasalRateUI()
                    }
                } catch (e: CancellationException) {
                    // In case of a cancellation, the Pump.connect() call
                    // rolls back any partially started connection and
                    // switches back to the disconnected state automatically.
                    // We just clean up our states here to reflect that the
                    // pump is already disconnected by this point.
                    disconnectRequestPending = false
                    setDriverState(DriverState.Disconnected)
                    // Re-throw to mark this coroutine as cancelled.
                    throw e
                } catch (e: AlertScreenException) {
                    notifyAboutComboAlert(e.alertScreenContent)
                    forciblyDisconnectDueToError = true
                } catch (e: Exception) {
                    uiInteraction.addNotification(
                        Notification.COMBO_PUMP_ALARM,
                        text = rh.gs(R.string.combov2_connection_error, e.message),
                        level = Notification.URGENT
                    )

                    aapsLogger.error(LTag.PUMP, "Exception while connecting: ${e.stackTraceToString()}")

                    forciblyDisconnectDueToError = true
                }

                if (forciblyDisconnectDueToError) {
                    // In case of a connection failure, just disconnect. The command
                    // queue will retry after a while. Repeated failed attempts will
                    // eventually trigger a "pump unreachable" error message.
                    //
                    // Set this to null _before_ disconnecting, since
                    // disconnectInternal() tries to call cancelAndJoin()
                    // on connectionSetupJob, leading to a deadlock.
                    // connectionSetupJob is set to null further below
                    // as well before the executePendingDisconnect()
                    // call, for the same reason. This coroutine is
                    // close to ending anyway, and there won't be any
                    // coroutine suspension happening anymore, so there's
                    // no point in a such cancelAndJoin() call by now.
                    connectionSetupJob = null
                    disconnectInternal(forceDisconnect = true)

                    ToastUtils.showToastInUiThread(context, rh.gs(R.string.combov2_could_not_connect))
                } else {
                    connectionSetupJob = null
                    // In case the pump queue issued a disconnect while the checks
                    // were running inside the connect() call above, do that
                    // postponed disconnect now. (The checks can take a long time
                    // if for example the pump's datetime deviates significantly
                    // from the system's current datetime.)
                    executePendingDisconnect()
                }
            }
        } catch (_: BluetoothNotEnabledException) {
            uiInteraction.addNotification(
                Notification.BLUETOOTH_NOT_ENABLED,
                text = rh.gs(R.string.combov2_bluetooth_disabled),
                level = Notification.INFO
            )
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "Connection failure: $e")
            ToastUtils.showToastInUiThread(context, rh.gs(R.string.combov2_could_not_connect))
            disconnectInternal(forceDisconnect = true)
        }
    }

    override fun disconnect(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Disconnecting from Combo; reason: $reason")
        disconnectInternal(forceDisconnect = false)
    }

    // This is called when (a) the AAPS watchdog is about to toggle
    // Bluetooth (if permission is given by the user) and (b) when
    // the command queue is being emptied. In both cases, the
    // connection attempt must be stopped immediately, which is why
    // forceDisconnect is set to true.
    override fun stopConnecting() {
        aapsLogger.debug(LTag.PUMP, "Stopping connect attempt by (forcibly) disconnecting")
        disconnectInternal(forceDisconnect = true)
    }

    override fun getPumpStatus(reason: String) {
        aapsLogger.debug(LTag.PUMP, "Getting pump status; reason: $reason")

        lastComboAlert = null

        runBlocking {
            try {
                executeCommand {
                    pump?.updateStatus()
                }

                // We send this event here, and not in onStart(), to include
                // the initial pump status update before emitting the event.
                if (!initializationChangedEventSent) {
                    rxBus.send(EventInitializationChanged())
                    initializationChangedEventSent = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        // State and status are automatically updated via the associated flows.
    }

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        if (!isInitialized()) {
            aapsLogger.error(LTag.PUMP, "Cannot set profile since driver is not initialized")

            uiInteraction.addNotification(
                Notification.PROFILE_NOT_SET_NOT_INITIALIZED,
                rh.gs(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set),
                Notification.URGENT
            )

            return pumpEnactResultProvider.get().apply {
                success = false
                enacted = false
                comment = rh.gs(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set)
            }
        }

        val acquiredPump = getAcquiredPump()

        rxBus.send(EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED))
        rxBus.send(EventDismissNotification(Notification.FAILED_UPDATE_PROFILE))

        val pumpEnactResult = pumpEnactResultProvider.get()

        val requestedBasalProfile = profile.toComboCtlBasalProfile()
        aapsLogger.debug(LTag.PUMP, "Basal profile to set: $requestedBasalProfile")

        runBlocking {
            try {
                executeCommand {
                    if (acquiredPump.setBasalProfile(requestedBasalProfile)) {
                        aapsLogger.debug(LTag.PUMP, "Basal profiles are different; new profile set")
                        activeBasalProfile = requestedBasalProfile
                        updateBaseBasalRateUI()

                        uiInteraction.addNotificationValidFor(
                            Notification.PROFILE_SET_OK,
                            rh.gs(app.aaps.core.ui.R.string.profile_set_ok),
                            Notification.INFO,
                            60
                        )
                    } else {
                        aapsLogger.debug(LTag.PUMP, "Basal profiles are equal; did not have to set anything")
                        // Treat this as if the command had been enacted. Setting a basal profile is
                        // an idempotent operation, meaning that setting the exact same profile factors
                        // twice in a row does not actually change anything. Therefore, we can just
                        // completely skip such a redundant set basal profile operation and still get
                        // the exact same result.
                        // Furthermore, it is actually important to also set enacted to true in this case
                        // because even though this _driver_ might know that the Combo uses this profile
                        // already, _AAPS_ might not. A good example is when AAPS is set up the first time
                        // and no profile has been activated. If in this case the profile happens to be
                        // identical to what's already in the Combo, then enacted=false would cause errors,
                        // because AAPS expects the driver to always enact the profile change in this case
                        // (since it thinks that no profile is set yet).
                    }

                    pumpEnactResult.apply {
                        success = true
                        enacted = true
                    }
                }
            } catch (e: CancellationException) {
                // Cancellation is not an error, but it also means
                // that the profile update was not enacted.
                pumpEnactResult.apply {
                    success = true
                    enacted = false
                }
                throw e
            } catch (e: Exception) {
                aapsLogger.error("Exception thrown during basal profile update: $e")

                uiInteraction.addNotification(
                    Notification.FAILED_UPDATE_PROFILE,
                    rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile),
                    Notification.URGENT
                )

                pumpEnactResult.apply {
                    success = false
                    enacted = false
                    comment = rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile)
                }
            }
        }
        return pumpEnactResult
    }

    override fun isThisProfileSet(profile: Profile): Boolean {
        if (!isInitialized())
            return true

        return (activeBasalProfile == profile.toComboCtlBasalProfile())
    }

    override val lastDataTime: Long get() = lastConnectionTimestamp

    @OptIn(ExperimentalTime::class)
    override val lastBolusTime: Long? get() = lastBolusUIFlow.value?.timestamp?.toEpochMilliseconds()
    override val lastBolusAmount: Double? get() = lastBolusUIFlow.value?.bolusAmount?.cctlBolusToIU()
    override val baseBasalRate: Double
        get() {
            val currentHour = DateTime().hourOfDay().get()
            return activeBasalProfile?.get(currentHour)?.cctlBasalToIU() ?: 0.0
        }

    // Store the levels as plain properties. That way, the last reported
    // levels are shown on the UI even when the driver connects to the
    // pump again and resets the current pump state.

    private var _reservoirLevel: Double? = null
    override val reservoirLevel: Double
        get() = _reservoirLevel ?: 0.0

    private var _batteryLevel: Int? = null
    override val batteryLevel: Int?
        get() = _batteryLevel

    private fun updateLevels() {
        pumpStatus?.availableUnitsInReservoir?.let { newLevel ->
            _reservoirLevel?.let { currentLevel ->
                aapsLogger.debug(LTag.PUMP, "Current/new reservoir levels: $currentLevel / $newLevel")
                if (preferences.get(ComboBooleanKey.AutomaticReservoirEntry) && (newLevel > currentLevel)) {
                    aapsLogger.debug(LTag.PUMP, "Auto-inserting reservoir change therapy event")
                    pumpSync.insertTherapyEventIfNewWithTimestamp(
                        timestamp = System.currentTimeMillis(),
                        type = TE.Type.INSULIN_CHANGE,
                        pumpId = null,
                        pumpType = PumpType.ACCU_CHEK_COMBO,
                        pumpSerial = serialNumber()
                    )
                }
            }

            _reservoirLevel = newLevel.toDouble()
        }

        pumpStatus?.batteryState?.let { newState ->
            val newLevel = when (newState) {
                BatteryState.NO_BATTERY   -> 5
                BatteryState.LOW_BATTERY  -> 25
                BatteryState.FULL_BATTERY -> 100
            }

            _batteryLevel?.let { currentLevel ->
                aapsLogger.debug(LTag.PUMP, "Current/new battery levels: $currentLevel / $newLevel")
                if (preferences.get(ComboBooleanKey.AutomaticBatteryEntry) && (newLevel > currentLevel)) {
                    aapsLogger.debug(LTag.PUMP, "Auto-inserting battery change therapy event")
                    pumpSync.insertTherapyEventIfNewWithTimestamp(
                        timestamp = System.currentTimeMillis(),
                        type = TE.Type.PUMP_BATTERY_CHANGE,
                        pumpId = null,
                        pumpType = PumpType.ACCU_CHEK_COMBO,
                        pumpSerial = serialNumber()
                    )
                }
            }

            _batteryLevel = newLevel
        }
    }

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        // Insulin value must be greater than 0
        require(detailedBolusInfo.carbs == 0.0) { detailedBolusInfo.toString() }
        require(detailedBolusInfo.insulin > 0) { detailedBolusInfo.toString() }

        val oldInsulinAmount = detailedBolusInfo.insulin
        detailedBolusInfo.insulin = constraintChecker
            .applyBolusConstraints(ConstraintObject(detailedBolusInfo.insulin, aapsLogger))
            .value()
        aapsLogger.debug(
            LTag.PUMP,
            "Applied bolus constraints:  old insulin amount: $oldInsulinAmount  new: ${detailedBolusInfo.insulin}"
        )

        val acquiredPump = getAcquiredPump()

        val requestedBolusAmount = detailedBolusInfo.insulin.iuToCctlBolus()
        val bolusReason = when (detailedBolusInfo.bolusType) {
            BS.Type.NORMAL  -> ComboCtlPump.StandardBolusReason.NORMAL
            BS.Type.SMB     -> ComboCtlPump.StandardBolusReason.SUPERBOLUS
            BS.Type.PRIMING -> ComboCtlPump.StandardBolusReason.PRIMING_INFUSION_SET
        }

        val pumpEnactResult = pumpEnactResultProvider.get()
        pumpEnactResult.success = false

        if (isSuspended()) {
            aapsLogger.info(LTag.PUMP, "Cannot deliver bolus since the pump is suspended")
            pumpEnactResult.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.combov2_cannot_deliver_pump_suspended)
            }
            return pumpEnactResult
        }

        val bolusProgressJob = pumpCoroutineScope.launch {
            acquiredPump.bolusDeliveryProgressFlow
                .collect { progressReport ->
                    when (progressReport.stage) {
                        is RTCommandProgressStage.DeliveringBolus -> {
                            rxBus.send(EventOverviewBolusProgress(rh, id = detailedBolusInfo.id, percent = (progressReport.overallProgress * 100).toInt()))
                        }

                        BasicProgressStage.Finished               -> {
                            rxBus.send(EventOverviewBolusProgress("Bolus finished, performing post-bolus checks", detailedBolusInfo.id, (progressReport.overallProgress * 100).toInt()))
                        }

                        else                                      -> Unit
                    }
                }
        }

        // Run the delivery in a sub-coroutine to be able
        // to cancel it via stopBolusDelivering().
        val newBolusJob = pumpCoroutineScope.async {
            // NOTE: Above, we take a local reference to the acquired Pump instance,
            // with a check that throws an exception in case the "pump" member is
            // null. This local reference is particularly important inside this
            // coroutine, because the "pump" member is set to null in case of an
            // error or other disconnect reason (see disconnectInternal()). However,
            // we still need to access the last delivered bolus inside this coroutine
            // from the pump's lastBolusFlow, even if an error happened. Accessing
            // it through the "pump" member would then result in an NPE. This is
            // solved by instead accessing the lastBolusFlow through the local
            // "acquiredPump" reference.

            try {
                executeCommand {
                    acquiredPump.deliverBolus(requestedBolusAmount, bolusReason)
                }

                reportFinishedBolus(rh.gs(app.aaps.core.interfaces.R.string.bolus_delivered_successfully, detailedBolusInfo.insulin), detailedBolusInfo.id, pumpEnactResult, succeeded = true)
            } catch (e: CancellationException) {
                // Cancellation is not an error, but it also means
                // that the profile update was not enacted.

                reportFinishedBolus(R.string.combov2_bolus_cancelled, detailedBolusInfo.id, pumpEnactResult, succeeded = true)

                // Rethrowing to finish coroutine cancellation.
                throw e
            } catch (_: ComboCtlPump.BolusCancelledByUserException) {
                aapsLogger.info(LTag.PUMP, "Bolus cancelled via Combo CMD_CANCEL_BOLUS command")

                // This exception is thrown when the bolus is cancelled
                // through a cancel bolus command that was sent to the Combo,
                // and not due to a coroutine cancellation. Like the
                // CancellationException block above, this is not an
                // error, hence the "success = true".

                reportFinishedBolus(R.string.combov2_bolus_cancelled, detailedBolusInfo.id, pumpEnactResult, succeeded = true)
            } catch (_: ComboCtlPump.BolusNotDeliveredException) {
                aapsLogger.error(LTag.PUMP, "Bolus not delivered")
                reportFinishedBolus(R.string.combov2_bolus_not_delivered, detailedBolusInfo.id, pumpEnactResult, succeeded = false)
            } catch (_: ComboCtlPump.UnaccountedBolusDetectedException) {
                aapsLogger.error(LTag.PUMP, "Unaccounted bolus detected")
                reportFinishedBolus(R.string.combov2_unaccounted_bolus_detected_cancelling_bolus, detailedBolusInfo.id, pumpEnactResult, succeeded = false)
            } catch (_: ComboCtlPump.InsufficientInsulinAvailableException) {
                aapsLogger.error(LTag.PUMP, "Insufficient insulin in reservoir")
                reportFinishedBolus(R.string.combov2_insufficient_insulin_in_reservoir, detailedBolusInfo.id, pumpEnactResult, succeeded = false)
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMP, "Exception thrown during bolus delivery: $e")
                reportFinishedBolus(R.string.combov2_bolus_delivery_failed, detailedBolusInfo.id, pumpEnactResult, succeeded = false)
            } finally {
                // The delivery was enacted if even a partial amount was infused.
                acquiredPump.lastBolusFlow.value?.also {
                    pumpEnactResult.enacted = (it.bolusAmount > 0)
                    pumpEnactResult.bolusDelivered = it.bolusAmount.cctlBolusToIU()
                } ?: run {
                    pumpEnactResult.enacted = false
                    pumpEnactResult.bolusDelivered = 0.0
                }
                aapsLogger.debug(
                    LTag.PUMP,
                    "Pump enact result: success ${pumpEnactResult.success} enacted ${pumpEnactResult.enacted} bolusDelivered ${pumpEnactResult.bolusDelivered}"
                )
                bolusJob = null
                bolusProgressJob.cancelAndJoin()
            }
        }

        bolusJob = newBolusJob

        // Do a blocking wait until the bolus coroutine completes or is cancelled.
        // AndroidAPS expects deliverTreatment() calls to block and to be cancellable
        // (via stopBolusDelivering()), so we run a separate bolus coroutine and
        // wait here until it is done.
        runBlocking {
            try {
                aapsLogger.debug(LTag.PUMP, "Waiting for bolus coroutine to finish")
                newBolusJob.join()
                aapsLogger.debug(LTag.PUMP, "Bolus coroutine finished")
            } catch (_: CancellationException) {
                aapsLogger.debug(LTag.PUMP, "Bolus coroutine was cancelled")
            }
        }

        return pumpEnactResult
    }

    override fun stopBolusDelivering() {
        aapsLogger.debug(LTag.PUMP, "Stopping bolus delivery")
        runBlocking {
            bolusJob?.cancelAndJoin()
            bolusJob = null
        }
        aapsLogger.debug(LTag.PUMP, "Bolus delivery stopped")
    }

    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val pumpEnactResult = pumpEnactResultProvider.get()
        pumpEnactResult.isPercent = false

        // Corner case: Current base basal rate is 0 IU. We cannot do
        // anything then, otherwise we get into a division by zero below
        // when converting absoluteRate to a percentage.
        if (baseBasalRate == 0.0) {
            pumpEnactResult.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.combov2_cannot_set_absolute_tbr_if_basal_zero)
            }
            return pumpEnactResult
        }

        // The Combo cannot handle absolute rates directly.
        // We have to convert it to a percentage instead,
        // and the percentage must be an integer multiple
        // of 10, otherwise the Combo won't accept it.

        val percentage = absoluteRate / baseBasalRate * 100
        val roundedPercentage = ((absoluteRate / baseBasalRate * 10).roundToInt() * 10)
        val limitedPercentage = min(roundedPercentage, _pumpDescription.maxTempPercent)

        aapsLogger.debug(LTag.PUMP, "Calculated percentage of $percentage% out of absolute rate $absoluteRate; rounded to: $roundedPercentage%; limited to: $limitedPercentage%")

        val cctlTbrType = when (tbrType) {
            PumpSync.TemporaryBasalType.NORMAL                -> ComboCtlTbr.Type.NORMAL
            PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND -> ComboCtlTbr.Type.EMULATED_COMBO_STOP
            PumpSync.TemporaryBasalType.SUPERBOLUS            -> ComboCtlTbr.Type.SUPERBOLUS

            PumpSync.TemporaryBasalType.PUMP_SUSPEND          -> {
                aapsLogger.error(
                    LTag.PUMP,
                    "PUMP_SUSPEND TBR type produced by AAPS for the TBR initiation even though this is supposed to only be produced by pump drivers"
                )
                pumpEnactResult.apply {
                    success = false
                    enacted = false
                    comment = rh.gs(app.aaps.core.ui.R.string.error)
                }
                return pumpEnactResult
            }
        }

        setTbrInternal(limitedPercentage, durationInMinutes, cctlTbrType, force100Percent = false, pumpEnactResult)

        return pumpEnactResult
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val pumpEnactResult = pumpEnactResultProvider.get()
        pumpEnactResult.isPercent = true

        val roundedPercentage = ((percent + 5) / 10) * 10
        val limitedPercentage = min(roundedPercentage, _pumpDescription.maxTempPercent)
        aapsLogger.debug(LTag.PUMP, "Got percentage of $percent%; rounded to: $roundedPercentage%; limited to: $limitedPercentage%")

        val cctlTbrType = when (tbrType) {
            PumpSync.TemporaryBasalType.NORMAL                -> ComboCtlTbr.Type.NORMAL
            PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND -> ComboCtlTbr.Type.EMULATED_COMBO_STOP
            PumpSync.TemporaryBasalType.SUPERBOLUS            -> ComboCtlTbr.Type.SUPERBOLUS

            PumpSync.TemporaryBasalType.PUMP_SUSPEND          -> {
                aapsLogger.error(
                    LTag.PUMP,
                    "PUMP_SUSPEND TBR type produced by AAPS for the TBR initiation even though this is supposed to only be produced by pump drivers"
                )
                pumpEnactResult.apply {
                    success = false
                    enacted = false
                    comment = rh.gs(app.aaps.core.ui.R.string.error)
                }
                return pumpEnactResult
            }
        }

        setTbrInternal(limitedPercentage, durationInMinutes, cctlTbrType, force100Percent = false, pumpEnactResult)

        return pumpEnactResult
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        val pumpEnactResult = pumpEnactResultProvider.get()
        pumpEnactResult.isPercent = true
        pumpEnactResult.isTempCancel = enforceNew
        setTbrInternal(100, 0, tbrType = ComboCtlTbr.Type.NORMAL, force100Percent = enforceNew, pumpEnactResult)
        return pumpEnactResult
    }

    private fun setTbrInternal(
        percentage: Int,
        durationInMinutes: Int,
        tbrType: ComboCtlTbr.Type,
        force100Percent: Boolean,
        pumpEnactResult: PumpEnactResult
    ) {
        if (isSuspended()) {
            aapsLogger.info(LTag.PUMP, "Cannot set TBR since the pump is suspended")
            pumpEnactResult.apply {
                success = false
                enacted = false
                comment = rh.gs(R.string.combov2_pump_is_suspended)
            }
            return
        }

        val acquiredPump = getAcquiredPump()

        runBlocking {
            try {
                executeCommand {

                    val tbrComment = when (acquiredPump.setTbr(percentage, durationInMinutes, tbrType, force100Percent)) {
                        ComboCtlPump.SetTbrOutcome.SET_NORMAL_TBR                  ->
                            rh.gs(R.string.combov2_setting_tbr_succeeded)

                        ComboCtlPump.SetTbrOutcome.SET_EMULATED_100_TBR            ->
                            rh.gs(R.string.combov2_set_emulated_100_tbr)

                        ComboCtlPump.SetTbrOutcome.LETTING_EMULATED_100_TBR_FINISH ->
                            rh.gs(R.string.combov2_letting_emulated_100_tbr_finish)

                        ComboCtlPump.SetTbrOutcome.IGNORED_REDUNDANT_100_TBR       ->
                            rh.gs(R.string.combov2_ignoring_redundant_100_tbr)
                    }

                    pumpEnactResult.apply {
                        success = true
                        enacted = true
                        comment = tbrComment
                    }
                }
            } catch (e: QuantityNotChangingException) {
                aapsLogger.error(LTag.PUMP, "TBR percentage adjustment hit a limit: $e")
                pumpEnactResult.apply {
                    success = false
                    enacted = false
                    comment = rh.gs(R.string.combov2_hit_unexpected_tbr_limit, e.targetQuantity, e.hitLimitAt)
                }
            } catch (e: ComboCtlPump.UnexpectedTbrStateException) {
                aapsLogger.error(LTag.PUMP, "Setting TBR failed with exception: $e")
                pumpEnactResult.apply {
                    success = false
                    enacted = false
                    comment = rh.gs(R.string.combov2_setting_tbr_failed)
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMP, "Setting TBR failed with exception: $e")
                pumpEnactResult.apply {
                    success = false
                    enacted = false
                    comment = rh.gs(R.string.combov2_setting_tbr_failed)
                }
            }
        }
    }

    // It is currently not known how to program an extended bolus into the Combo.
    // Until that is reverse engineered, inform callers that we can't handle this.

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult =
        createFailurePumpEnactResult(R.string.combov2_extended_bolus_not_supported)

    override fun cancelExtendedBolus(): PumpEnactResult =
        createFailurePumpEnactResult(R.string.combov2_extended_bolus_not_supported)

    override fun updateExtendedJsonStatus(extendedStatus: JSONObject) {
        when (val alert = lastComboAlert) {
            is AlertScreenContent.Warning ->
                extendedStatus.put("WarningCode", alert.code)

            is AlertScreenContent.Error   ->
                extendedStatus.put("ErrorCode", alert.code)

            else                          -> Unit
        }
    }

    override fun manufacturer() = ManufacturerType.Roche

    override fun model() = PumpType.ACCU_CHEK_COMBO

    override fun serialNumber(): String {
        val bluetoothAddress = getBluetoothAddress()
        val curPumpManager = pumpManager
        return if ((bluetoothAddress != null) && (curPumpManager != null))
            curPumpManager.getPumpID(bluetoothAddress)
        else
            rh.gs(R.string.combov2_not_paired)
    }

    override val pumpDescription: PumpDescription
        get() = _pumpDescription

    override fun pumpSpecificShortStatus(veryShort: Boolean): String {
        val lines = mutableListOf<String>()

        val alertCodeString = when (val alert = lastComboAlert) {
            is AlertScreenContent.Warning -> "W${alert.code}"
            is AlertScreenContent.Error   -> "E${alert.code}"
            else                          -> null
        }
        if (alertCodeString != null)
            lines += rh.gs(R.string.combov2_short_status_alert, alertCodeString)

        return lines.joinToString("\n")
    }

    override val isFakingTempsByExtendedBoluses = false

    @OptIn(ExperimentalTime::class)
    override fun loadTDDs(): PumpEnactResult {
        val pumpEnactResult = pumpEnactResultProvider.get()
        val acquiredPump = getAcquiredPump()

        runBlocking {
            try {
                // Map key = timestamp; value = TDD
                val tddMap = mutableMapOf<Long, Int>()

                executeCommand {
                    val tddHistory = acquiredPump.fetchTDDHistory()

                    tddHistory
                        .filter { it.totalDailyAmount >= 1 }
                        .forEach { tddHistoryEntry ->
                            val timestamp = tddHistoryEntry.date.toEpochMilliseconds()
                            tddMap[timestamp] = (tddMap[timestamp] ?: 0) + tddHistoryEntry.totalDailyAmount
                        }
                }

                for (tddEntry in tddMap) {
                    val timestamp = tddEntry.key
                    val totalDailyAmount = tddEntry.value

                    pumpSync.createOrUpdateTotalDailyDose(
                        timestamp,
                        bolusAmount = 0.0,
                        basalAmount = 0.0,
                        totalAmount = totalDailyAmount.cctlBasalToIU(),
                        pumpId = null,
                        pumpType = PumpType.ACCU_CHEK_COMBO,
                        pumpSerial = serialNumber()
                    )
                }

                pumpEnactResult.apply {
                    success = true
                    enacted = true
                }
            } catch (e: CancellationException) {
                pumpEnactResult.apply {
                    success = true
                    enacted = false
                    comment = rh.gs(R.string.combov2_load_tdds_cancelled)
                }
                throw e
            } catch (e: Exception) {
                aapsLogger.error("Exception thrown during TDD retrieval: $e")

                pumpEnactResult.apply {
                    success = false
                    enacted = false
                    comment = rh.gs(R.string.combov2_retrieving_tdds_failed)
                }
            }
        }

        return pumpEnactResult
    }

    override fun canHandleDST() = true

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        aapsLogger.info(LTag.PUMP, "Time, Date and/or TimeZone changed. Time change type = $timeChangeType")

        val reason = when (timeChangeType) {
            TimeChangeType.TimezoneChanged -> rh.gs(R.string.combov2_timezone_changed)
            TimeChangeType.TimeChanged     -> rh.gs(R.string.combov2_datetime_changed)
            TimeChangeType.DSTStarted      -> rh.gs(R.string.combov2_dst_started)
            TimeChangeType.DSTEnded        -> rh.gs(R.string.combov2_dst_ended)
        }
        // Updating pump status implicitly also updates the pump's local datetime,
        // which is what we want after the system datetime/timezone/DST changed.
        commandQueue.readStatus(reason, null)
    }

    fun clearPumpErrorObservedFlag() {
        stopPumpErrorTimeout()
        if (pumpErrorObserved) {
            aapsLogger.info(LTag.PUMP, "Clearing pumpErrorObserved flag")
            pumpErrorObserved = false
        }
    }

    /*** Loop constraints ***/
    // These restrict the function of the loop in case of an event
    // that makes running a loop too risky, for example because something
    // went wrong while bolusing, or because the incorrect basal profile
    // was found to be active.

    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!isSuspended() && (lastActiveBasalProfileNumber != null)) {
            val isAllowed = (lastActiveBasalProfileNumber == 1)
            aapsLogger.info(
                LTag.PUMP,
                "Currently active basal profile: $lastActiveBasalProfileNumber -> loop invocation allowed: $isAllowed"
            )

            if (!isAllowed) {
                value.set(false, rh.gs(R.string.combov2_incorrect_active_basal_profile, lastActiveBasalProfileNumber), this)
            }
        } else {
            aapsLogger.info(
                LTag.PUMP,
                "Cannot currently determine which basal profile is active in the pump"
            )
            // We don't disallow the invocation in this case since the only reasons for lastActiveBasalProfileNumber
            // being null are (1) we are in the initial, uninitialized state (in which case looping won't
            // work anyway) and (2) the pump is currently suspended (which already will not allow for looping).
        }

        return value
    }

    /*** Pairing API ***/

    fun getPairingProgressFlow() =
        pumpManager?.pairingProgressFlow ?: throw IllegalStateException("Attempting access uninitialized pump manager")

    fun resetPairingProgress() = pumpManager?.resetPairingProgress()

    private val _previousPairingAttemptFailedFlow = MutableStateFlow(false)
    val previousPairingAttemptFailedFlow = _previousPairingAttemptFailedFlow.asStateFlow()

    private var pairingJob: Job? = null
    private var pairingPINChannel: Channel<PairingPIN>? = null

    fun startPairing() {
        val discoveryDuration = preferences.get(ComboIntKey.DiscoveryDuration)

        val newPINChannel = Channel<PairingPIN>(capacity = Channel.RENDEZVOUS)
        pairingPINChannel = newPINChannel

        _previousPairingAttemptFailedFlow.value = false

        // Update the log level here in case the user changed it.
        updateComboCtlLogLevel()

        pairingJob = pumpCoroutineScope.async {
            try {
                // Do the pairing attempt within runWithPermissionCheck()
                // since pairing requires Bluetooth permissions.
                val pairingResult = runWithPermissionCheck(
                    context, config, aapsLogger, androidPermission,
                    permissionsToCheckFor = listOf("android.permission.BLUETOOTH_CONNECT")
                ) {
                    try {
                        pumpManager?.pairWithNewPump(discoveryDuration) { newPumpAddress, previousAttemptFailed ->
                            aapsLogger.info(
                                LTag.PUMP,
                                "New pairing PIN request from Combo pump with Bluetooth " +
                                    "address $newPumpAddress (previous attempt failed: $previousAttemptFailed)"
                            )
                            _previousPairingAttemptFailedFlow.value = previousAttemptFailed
                            newPINChannel.receive()
                        } ?: throw IllegalStateException("Attempting to access uninitialized pump manager")
                    } catch (e: BluetoothNotEnabledException) {
                        // If Bluetooth is turned off during pairing, show a toaster message.
                        // Notifications on the AAPS overview fragment are not useful here
                        // because the pairing activity obscures that fragment. So, instead,
                        // alert the user by showing the notification via the toaster.
                        ToastUtils.errorToast(context, app.aaps.core.ui.R.string.ble_not_enabled)
                        ComboCtlPumpManager.PairingResult.ExceptionDuringPairing(e)
                    }
                }

                if (pairingResult !is ComboCtlPumpManager.PairingResult.Success)
                    return@async

                _pairedStateUIFlow.value = true

                // Notify AndroidAPS that this is a new pump and that
                // the history that is associated with any previously
                // paired pump is to be discarded.
                pumpSync.connectNewPump()

                // Schedule a status update, since pairing can take
                // a while. By the time  we reach this point, the queue
                // connection attempt may have reached the timeout,
                // and reading the status is part of what AndroidAPS
                // was trying to do, so do that now.
                // If we reach this point before the timeout, then the
                // queue will contain a pump_driver_changed readstatus
                // command already. The queue will see that and ignore
                // this readStatus() call automatically.
                commandQueue.readStatus(rh.gs(app.aaps.core.ui.R.string.pump_paired), null)
            } finally {
                pairingJob = null
                pairingPINChannel?.close()
                pairingPINChannel = null
            }
        }
    }

    fun cancelPairing() {
        runBlocking {
            aapsLogger.debug(LTag.PUMP, "Cancelling pairing")
            pairingJob?.cancelAndJoin()
            aapsLogger.debug(LTag.PUMP, "Pairing cancelled")
        }
    }

    suspend fun providePairingPIN(pairingPIN: PairingPIN) {
        try {
            pairingPINChannel?.send(pairingPIN)
        } catch (_: ClosedSendChannelException) {
        }
    }

    private fun unpair() {
        if (unpairing)
            return

        val bluetoothAddress = getBluetoothAddress() ?: return

        unpairing = true

        disconnectInternal(forceDisconnect = true)

        runBlocking {
            try {
                val pump = pumpManager?.acquirePump(bluetoothAddress) ?: return@runBlocking
                pump.unpair()
                pumpManager?.releasePump(bluetoothAddress)
            } catch (_: ComboException) {
            } catch (_: BluetoothException) {
            }
        }

        // Reset these states since they are associated
        // with the now unpaired pump.
        lastConnectionTimestamp = 0L
        activeBasalProfile = null
        lastActiveBasalProfileNumber = null

        // Reset the UI flows that are associated with the pump
        // that just got unpaired to prevent the UI from showing
        // information about that now-unpaired pump anymore.
        _currentActivityUIFlow.value = noCurrentActivity()
        _lastConnectionTimestampUIFlow.value = null
        _batteryStateUIFlow.value = null
        _reservoirLevelUIFlow.value = null
        _lastBolusUIFlow.value = null
        _baseBasalRateUIFlow.value = null
        _serialNumberUIFlow.value = ""
        _bluetoothAddressUIFlow.value = ""

        clearPumpErrorObservedFlag()

        // The unpairing variable is set to false in
        // the PumpManager onPumpUnpaired callback.
    }

    /*** User interface flows ***/

    // "UI flows" are hot flows that are meant to be used for showing
    // information about the pump and its current state on the UI.
    // These are kept in the actual plugin class to make sure they
    // are always available, even if no pump is paired (which means
    // that the "pump" variable is set to null and thus its flows
    // are inaccessible).
    //
    // A few UI flows are internally also used for other checks, such
    // as pairedStateUIFlow (which is used internally to verify whether
    // or not the pump is paired).
    //
    // Some UI flows are nullable and have a null initial state to
    // indicate to UIs that they haven't been filled with actual
    // state yet.

    // This is a variant of driverStateFlow that retains the Error
    // and Suspended state even after disconnecting to make sure these
    // states kept being showed to the user post-disconnect.
    // NOTE: Do not rely on this to check prior to a command if the
    // pump is suspended or not, since the driver state UI flow is
    // updated in a separate coroutine, and is _only_ meant for UI
    // updates. Using this for other purposes can cause race conditions
    // to appear, such as when immediately after the Pump.connect() call
    // finishes, the state is checked. Use isSuspended() instead.
    private val _driverStateUIFlow = MutableStateFlow<DriverState>(DriverState.NotInitialized)
    val driverStateUIFlow = _driverStateUIFlow.asStateFlow()

    // "Activity" is not to be confused with the Android Activity class.
    // An "activity" is something that a command does, for example
    // establishing a BT connection, or delivering a bolus, setting
    // a basal rate factor, reading the current pump datetime etc.
    data class CurrentActivityInfo(val description: String, val overallProgress: Double)

    private fun noCurrentActivity() = CurrentActivityInfo("", 0.0)
    private var _currentActivityUIFlow = MutableStateFlow(noCurrentActivity())
    val currentActivityUIFlow = _currentActivityUIFlow.asStateFlow()

    private var _lastConnectionTimestampUIFlow = MutableStateFlow<Long?>(null)
    val lastConnectionTimestampUIFlow = _lastConnectionTimestampUIFlow.asStateFlow()

    private var _batteryStateUIFlow = MutableStateFlow<BatteryState?>(null)
    val batteryStateUIFlow = _batteryStateUIFlow.asStateFlow()

    data class ReservoirLevel(val state: ReservoirState, val availableUnits: Int)

    private var _reservoirLevelUIFlow = MutableStateFlow<ReservoirLevel?>(null)
    val reservoirLevelUIFlow = _reservoirLevelUIFlow.asStateFlow()

    private var _lastBolusUIFlow = MutableStateFlow<ComboCtlPump.LastBolus?>(null)
    val lastBolusUIFlow = _lastBolusUIFlow.asStateFlow()

    private var _currentTbrUIFlow = MutableStateFlow<ComboCtlTbr?>(null)
    val currentTbrUIFlow = _currentTbrUIFlow.asStateFlow()

    private var _baseBasalRateUIFlow = MutableStateFlow<Double?>(null)
    val baseBasalRateUIFlow = _baseBasalRateUIFlow.asStateFlow()

    private var _serialNumberUIFlow = MutableStateFlow("")
    val serialNumberUIFlow = _serialNumberUIFlow.asStateFlow()

    private var _bluetoothAddressUIFlow = MutableStateFlow("")
    val bluetoothAddressUIFlow = _bluetoothAddressUIFlow.asStateFlow()

    private var _pairedStateUIFlow = MutableStateFlow(false)
    val pairedStateUIFlow = _pairedStateUIFlow.asStateFlow()

    // UI flow to show the current RT display frame on the UI. Unlike
    // the other UI flows, this is a SharedFlow, not a StateFlow,
    // since frames aren't "states", and StateFlow filters out duplicates
    // (which isn't useful in this case). The flow is configured such
    // that it never suspends; if its replay cache contains a frame already,
    // that older frame is overwritten. This makes sure the flow always
    // contains the current frame.
    private var _displayFrameUIFlow = MutableSharedFlow<DisplayFrame?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val displayFrameUIFlow = _displayFrameUIFlow.asSharedFlow()

    /*** Misc private functions ***/

    private fun setupUiFlows(acquiredPump: ComboCtlPump) {
        pumpUIFlowsDeferred = pumpCoroutineScope.async {
            try {
                coroutineScope {
                    acquiredPump.connectProgressFlow
                        .onEach { progressReport ->
                            val description = when (val progStage = progressReport.stage) {
                                is BasicProgressStage.EstablishingBtConnection   ->
                                    rh.gs(
                                        R.string.combov2_establishing_bt_connection,
                                        progStage.currentAttemptNr
                                    )

                                BasicProgressStage.PerformingConnectionHandshake -> rh.gs(R.string.combov2_pairing_performing_handshake)
                                else                                             -> ""
                            }
                            _currentActivityUIFlow.value = CurrentActivityInfo(
                                description,
                                progressReport.overallProgress
                            )
                        }
                        .launchIn(this)

                    acquiredPump.setDateTimeProgressFlow
                        .onEach { progressReport ->
                            val description = when (progressReport.stage) {
                                RTCommandProgressStage.SettingDateTimeHour,
                                RTCommandProgressStage.SettingDateTimeMinute -> rh.gs(R.string.combov2_setting_current_pump_time)

                                RTCommandProgressStage.SettingDateTimeYear,
                                RTCommandProgressStage.SettingDateTimeMonth,
                                RTCommandProgressStage.SettingDateTimeDay    -> rh.gs(R.string.combov2_setting_current_pump_date)

                                else                                         -> ""
                            }
                            _currentActivityUIFlow.value = CurrentActivityInfo(
                                description,
                                progressReport.overallProgress
                            )
                        }
                        .launchIn(this)

                    acquiredPump.getBasalProfileFlow
                        .onEach { progressReport ->
                            val description = when (val stage = progressReport.stage) {
                                is RTCommandProgressStage.GettingBasalProfile ->
                                    rh.gs(R.string.combov2_getting_basal_profile, stage.numSetFactors)

                                else                                          -> ""
                            }
                            _currentActivityUIFlow.value = CurrentActivityInfo(
                                description,
                                progressReport.overallProgress
                            )
                        }
                        .launchIn(this)

                    acquiredPump.setBasalProfileFlow
                        .onEach { progressReport ->
                            val description = when (val stage = progressReport.stage) {
                                is RTCommandProgressStage.SettingBasalProfile ->
                                    rh.gs(R.string.combov2_setting_basal_profile, stage.numSetFactors)

                                else                                          -> ""
                            }
                            _currentActivityUIFlow.value = CurrentActivityInfo(
                                description,
                                progressReport.overallProgress
                            )
                        }
                        .launchIn(this)

                    acquiredPump.bolusDeliveryProgressFlow
                        .onEach { progressReport ->
                            val description = when (val stage = progressReport.stage) {
                                is RTCommandProgressStage.DeliveringBolus ->
                                    rh.gs(
                                        R.string.combov2_delivering_bolus,
                                        stage.deliveredImmediateAmount.cctlBolusToIU(),
                                        stage.totalImmediateAmount.cctlBolusToIU()
                                    )

                                else                                      -> ""
                            }
                            _currentActivityUIFlow.value = CurrentActivityInfo(
                                description,
                                progressReport.overallProgress
                            )
                        }
                        .launchIn(this)

                    acquiredPump.parsedDisplayFrameFlow
                        .onEach { parsedDisplayFrame ->
                            _displayFrameUIFlow.emit(
                                parsedDisplayFrame?.displayFrame ?: NullDisplayFrame
                            )
                        }
                        .launchIn(this)

                    launch {
                        while (true) {
                            updateBaseBasalRateUI()
                            val currentMinute = DateTime().minuteOfHour().get()

                            // Calculate how many minutes need to pass until we
                            // reach the next hour and thus the next basal profile
                            // factor becomes active. That way, the amount of UI
                            // refreshes is minimized.
                            // We cap the max waiting period to 58 minutes instead
                            // of 60 to allow for a small tolerance range for cases
                            // when this loop iterates exactly when the current hour
                            // is about to turn.
                            val minutesUntilNextFactor = max((58 - currentMinute), 0)
                            delay(minutesUntilNextFactor * 60 * 1000L)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMP, "Exception thrown in UI flows coroutine scope: $e")
                throw e
            }
        }
    }

    private fun startPumpErrorTimeout() {
        if (pumpErrorTimeoutJob != null)
            return

        pumpErrorTimeoutJob = pumpCoroutineScope.launch {
            delay(PUMP_ERROR_TIMEOUT_INTERVAL_MSECS)
            aapsLogger.info(LTag.PUMP, "Clearing pumpErrorObserved flag after timeout was reached")
            pumpErrorObserved = false
            commandQueue.readStatus(rh.gs(R.string.combov2_refresh_pump_status_after_error), null)
        }
    }

    private fun stopPumpErrorTimeout() {
        pumpErrorTimeoutJob?.cancel()
        pumpErrorTimeoutJob = null
    }

    private fun updateBaseBasalRateUI() {
        val currentHour = DateTime().hourOfDay().get()
        // This sets value to null if no profile is set,
        // which keeps the base basal rate on the UI blank.
        _baseBasalRateUIFlow.value = activeBasalProfile?.get(currentHour)?.cctlBasalToIU()
    }

    @OptIn(ExperimentalTime::class)
    private fun handlePumpEvent(event: ComboCtlPump.Event) {
        aapsLogger.debug(LTag.PUMP, "Handling pump event $event")

        when (event) {
            is ComboCtlPump.Event.BatteryLow           -> {
                uiInteraction.addNotification(
                    Notification.COMBO_PUMP_ALARM,
                    text = rh.gs(R.string.combov2_battery_low_warning),
                    level = Notification.NORMAL
                )
            }

            is ComboCtlPump.Event.ReservoirLow         -> {
                uiInteraction.addNotification(
                    Notification.COMBO_PUMP_ALARM,
                    text = rh.gs(R.string.combov2_reservoir_low_warning),
                    level = Notification.NORMAL
                )
            }

            is ComboCtlPump.Event.QuickBolusInfused    -> {
                pumpSync.syncBolusWithPumpId(
                    event.timestamp.toEpochMilliseconds(),
                    event.bolusAmount.cctlBolusToIU(),
                    BS.Type.NORMAL,
                    event.bolusId,
                    PumpType.ACCU_CHEK_COMBO,
                    serialNumber()
                )
            }

            is ComboCtlPump.Event.StandardBolusInfused -> {
                val bolusType = when (event.standardBolusReason) {
                    ComboCtlPump.StandardBolusReason.NORMAL               -> BS.Type.NORMAL
                    ComboCtlPump.StandardBolusReason.SUPERBOLUS           -> BS.Type.SMB
                    ComboCtlPump.StandardBolusReason.PRIMING_INFUSION_SET -> BS.Type.PRIMING
                }
                pumpSync.syncBolusWithPumpId(
                    event.timestamp.toEpochMilliseconds(),
                    event.bolusAmount.cctlBolusToIU(),
                    bolusType,
                    event.bolusId,
                    PumpType.ACCU_CHEK_COMBO,
                    serialNumber()
                )
            }

            is ComboCtlPump.Event.ExtendedBolusStarted -> {
                pumpSync.syncExtendedBolusWithPumpId(
                    event.timestamp.toEpochMilliseconds(),
                    event.totalBolusAmount.cctlBolusToIU(),
                    event.totalDurationMinutes.toLong() * 60 * 1000,
                    false,
                    event.bolusId,
                    PumpType.ACCU_CHEK_COMBO,
                    serialNumber()
                )
            }

            is ComboCtlPump.Event.ExtendedBolusEnded   -> {
                pumpSync.syncStopExtendedBolusWithPumpId(
                    event.timestamp.toEpochMilliseconds(),
                    event.bolusId,
                    PumpType.ACCU_CHEK_COMBO,
                    serialNumber()
                )
            }

            is ComboCtlPump.Event.TbrStarted           -> {
                aapsLogger.debug(LTag.PUMP, "Pump reports TBR started; expected state according to AAPS: ${pumpSync.expectedPumpState()}")
                val tbrStartTimestampInMs = event.tbr.timestamp.toEpochMilliseconds()
                val tbrType = when (event.tbr.type) {
                    ComboCtlTbr.Type.NORMAL               -> PumpSync.TemporaryBasalType.NORMAL
                    ComboCtlTbr.Type.EMULATED_100_PERCENT -> PumpSync.TemporaryBasalType.NORMAL
                    ComboCtlTbr.Type.SUPERBOLUS           -> PumpSync.TemporaryBasalType.SUPERBOLUS
                    ComboCtlTbr.Type.EMULATED_COMBO_STOP  -> PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND
                    ComboCtlTbr.Type.COMBO_STOPPED        -> PumpSync.TemporaryBasalType.PUMP_SUSPEND
                }
                pumpSync.syncTemporaryBasalWithPumpId(
                    timestamp = tbrStartTimestampInMs,
                    rate = event.tbr.percentage.toDouble(),
                    duration = event.tbr.durationInMinutes.toLong() * 60 * 1000,
                    isAbsolute = false,
                    type = tbrType,
                    pumpId = tbrStartTimestampInMs,
                    pumpType = PumpType.ACCU_CHEK_COMBO,
                    pumpSerial = serialNumber()
                )
            }

            is ComboCtlPump.Event.TbrEnded             -> {
                aapsLogger.debug(LTag.PUMP, "Pump reports TBR ended; expected state according to AAPS: ${pumpSync.expectedPumpState()}")
                val tbrEndTimestampInMs = event.timestampWhenTbrEnded.toEpochMilliseconds()
                pumpSync.syncStopTemporaryBasalWithPumpId(
                    timestamp = tbrEndTimestampInMs,
                    endPumpId = tbrEndTimestampInMs,
                    pumpType = PumpType.ACCU_CHEK_COMBO,
                    pumpSerial = serialNumber()
                )
            }

            is ComboCtlPump.Event.UnknownTbrDetected   -> {
                // Inform about this unknown TBR that was observed (and automatically aborted).
                val remainingDurationString = String.format(
                    Locale.getDefault(),
                    "%02d:%02d",
                    event.remainingTbrDurationInMinutes / 60,
                    event.remainingTbrDurationInMinutes % 60
                )
                uiInteraction.addNotification(
                    Notification.COMBO_UNKNOWN_TBR,
                    text = rh.gs(
                        R.string.combov2_unknown_tbr_detected,
                        event.tbrPercentage,
                        remainingDurationString
                    ),
                    level = Notification.URGENT
                )
            }

            else                                       -> Unit
        }
    }

    // Marked as synchronized since this may get called by a finishing
    // connect operation and by the command queue at the same time.
    @Synchronized private fun disconnectInternal(forceDisconnect: Boolean) {
        // Sometimes, the CommandQueue may decide to call disconnect while the
        // driver is still busy with something, for example because some checks
        // are being performed. Ignore disconnect requests in that case, unless
        // the forceDisconnect flag is set.
        if (!forceDisconnect && isBusy()) {
            disconnectRequestPending = true
            aapsLogger.debug(LTag.PUMP, "Ignoring disconnect request since driver is currently busy")
            return
        }

        if (isDisconnected()) {
            aapsLogger.debug(LTag.PUMP, "Already disconnected")
            return
        }

        // It makes no sense to reach this location with pump
        // being null due to the checks above.
        val pumpToDisconnect = pump
        if (pumpToDisconnect == null) {
            aapsLogger.error(LTag.PUMP, "Current pump is already null")
            return
        }

        // Run these operations in a coroutine to be able to wait
        // until the disconnect really completes and the UI flows
        // are all cancelled & their coroutines finished. Otherwise
        // we can end up with race conditions because the coroutines
        // are still ongoing in the background.
        runBlocking {
            // Disconnecting the pump needs to be done in one of two
            // ways, depending on whether we try to disconnect while
            // the pump is in the Connecting state or not:
            //
            // 1. Pump is in the Connecting state. A disconnectInternal()
            // call then means that we are aborting the ongoing connect
            // attempt. Internally, the pump may be waiting for a blocking
            // Bluetooth device connect procedure to complete.
            // 2. Pump is past the Connecting state. The blocking connect
            // procedure is already over.
            //
            // In case #1, the internal IO loops inside the pump are not
            // yet running. Also, connectionSetupJob.join() won't finish
            // because of the blocking connect procedure. In this case,
            // cancel that coroutine/Job, but don't join yet. Cancel,
            // then disconnect the pump, then join. That way, the blocking
            // Bluetooth connect procedure is aborted (closing a Bluetooth
            // socket usually does that), the connectionSetupJob is unblocked,
            // it can be canceled, and join() can finish. Since there is no
            // IO coroutine running, there won't be any IO errors when
            // disconnecting before joining connectionSetupJob.
            //
            // In case #2, the internal IO loops inside the pump *are*
            // running, so disconnecting before joining is risky. Therefore,
            // in this case, do cancel *and* join connectionSetupJob before
            // actually disconnecting the pump. Otherwise, errors occur, since
            // the connection setup code will try to communicate even though
            // the Pump.disconnect() call shuts down the RFCOMM socket,
            // making all send/receive calls fail.

            if (pumpToDisconnect.stateFlow.value == ComboCtlPump.State.Connecting) {
                // Case #1 from above
                aapsLogger.debug(LTag.PUMP, "Cancelling ongoing connect attempt")
                connectionSetupJob?.cancel()
                pumpToDisconnect.disconnect()
                connectionSetupJob?.join()
            } else {
                // Case #2 from above
                aapsLogger.debug(LTag.PUMP, "Disconnecting Combo (if not disconnected already by a cancelling request)")
                connectionSetupJob?.cancelAndJoin()
                pumpToDisconnect.disconnect()
            }

            aapsLogger.debug(LTag.PUMP, "Combo disconnected; cancelling UI flows coroutine")
            pumpUIFlowsDeferred?.cancelAndJoin()
            aapsLogger.debug(LTag.PUMP, "Cancelling state and status flows coroutine")
            stateAndStatusFlowsDeferred?.cancelAndJoin()
            aapsLogger.debug(LTag.PUMP, "Releasing pump instance back to pump manager")

            getBluetoothAddress()?.let { pumpManager?.releasePump(it) }
        }

        connectionSetupJob = null
        pumpUIFlowsDeferred = null
        stateAndStatusFlowsDeferred = null
        pump = null

        disconnectRequestPending = false

        aapsLogger.debug(LTag.PUMP, "Combo disconnect complete")
        setDriverState(DriverState.Disconnected)
    }

    private fun isPaired() = pairedStateUIFlow.value

    private fun updateComboCtlLogLevel() =
        updateComboCtlLogLevel(preferences.get(ComboBooleanKey.VerboseLogging))

    private fun updateComboCtlLogLevel(enableVerbose: Boolean) {
        aapsLogger.debug(LTag.PUMP, "${if (enableVerbose) "Enabling" else "Disabling"} verbose logging")
        ComboCtlLogger.threshold = if (enableVerbose) ComboCtlLogLevel.VERBOSE else ComboCtlLogLevel.DEBUG
    }

    private fun setDriverState(newState: DriverState) {
        val oldState = _driverStateFlow.value

        if (oldState == newState)
            return

        // Update the last connection timestamp after executing a command.
        // Other components like CommandReadStatus expect the lastDataTime()
        // timestamp to be updated right after a command execution.
        // As a special case, if all we did was to check the pump status,
        // and afterwards disconnected, also update. The CheckingPump
        // state masks multiple command executions.
        if ((oldState is DriverState.ExecutingCommand) || ((oldState == DriverState.CheckingPump) && (newState == DriverState.Disconnected)))
            updateLastConnectionTimestamp()

        // If the pump is suspended, or if an error occurred, we want
        // to show the "suspended" and "error" state labels on the UI
        // even after disconnecting. Otherwise, the user may not see
        // that an error occurred or the pump is suspended.
        val updateUIState = when (newState) {
            DriverState.Disconnected -> {
                when (driverStateUIFlow.value) {
                    DriverState.Error,
                    DriverState.Suspended -> false

                    else                  -> true
                }
            }

            else                     -> true
        }
        if (updateUIState) {
            _driverStateUIFlow.value = newState

            // Also show a notification to alert the user to the fact
            // that the Combo is currently suspended, otherwise this
            // only shows up in the Combo fragment.
            if (newState == DriverState.Suspended) {
                uiInteraction.addNotification(
                    Notification.PUMP_SUSPENDED,
                    text = rh.gs(R.string.combov2_pump_is_suspended),
                    level = Notification.NORMAL
                )
            }
        }

        _driverStateFlow.value = newState

        if (newState == DriverState.Disconnected)
            _currentActivityUIFlow.value = noCurrentActivity()

        aapsLogger.info(LTag.PUMP, "Setting Combo driver state:  old: $oldState  new: $newState")

        when (newState) {
            DriverState.Disconnected -> rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
            DriverState.Connecting   -> rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING))
            // Filter Ready<->Suspended state changes to avoid sending CONNECTED unnecessarily often.
            DriverState.Ready        -> {
                if (oldState != DriverState.Suspended)
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
            }

            DriverState.Suspended    -> {
                if (oldState != DriverState.Ready)
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
            }

            else                     -> Unit
        }
    }

    private fun executePendingDisconnect() {
        if (!disconnectRequestPending)
            return

        aapsLogger.debug(LTag.PUMP, "Executing pending disconnect request")
        disconnectInternal(forceDisconnect = true)
    }

    private fun unpairDueToPumpDataError() {
        disconnectInternal(forceDisconnect = true)
        uiInteraction.addNotificationValidTo(
            id = Notification.PUMP_ERROR,
            date = dateUtil.now(),
            text = rh.gs(R.string.combov2_cannot_access_pump_data),
            level = Notification.URGENT,
            validTo = 0
        )
        unpair()
    }

    // Utility function to run a ComboCtlPump command (deliverBolus for example)
    // and do common checks afterwards (like handling AlertScreenException).
    // IMPORTANT: This disconnects in case of an error, so if any other
    // nontrivial procedure needs to be done for the command in case of an
    // error, do this inside a try-finally block in the block.
    private suspend fun executeCommand(
        block: suspend CoroutineScope.() -> Unit
    ) {
        try {
            coroutineScope {
                block.invoke(this)
            }

            // The AAPS pump command queue may have asked for a disconnect
            // while the command was being executed. Do this postponed
            // disconnect now that we are done with the command.
            executePendingDisconnect()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AlertScreenException) {
            lastComboAlert = e.alertScreenContent

            notifyAboutComboAlert(e.alertScreenContent)

            // Disconnect since we are now in the Error state.
            disconnectInternal(forceDisconnect = true)

            throw e
        } catch (t: Throwable) {
            // Disconnect since we are now in the Error state.
            disconnectInternal(forceDisconnect = true)
            throw t
        }
    }

    private fun updateLastConnectionTimestamp() {
        lastConnectionTimestamp = System.currentTimeMillis()
        _lastConnectionTimestampUIFlow.value = lastConnectionTimestamp
    }

    private fun getAlertDescription(alert: AlertScreenContent) =
        when (alert) {
            is AlertScreenContent.Warning -> {
                val desc = when (alert.code) {
                    4    -> rh.gs(R.string.combov2_warning_4)
                    10   -> rh.gs(R.string.combov2_warning_10)
                    else -> ""
                }

                "${rh.gs(R.string.combov2_warning)} W${alert.code}" +
                    if (desc.isEmpty()) "" else ": $desc"
            }

            is AlertScreenContent.Error   -> {
                val desc = when (alert.code) {
                    1    -> rh.gs(R.string.combov2_error_1)
                    2    -> rh.gs(R.string.combov2_error_2)
                    4    -> rh.gs(R.string.combov2_error_4)
                    5    -> rh.gs(R.string.combov2_error_5)
                    6    -> rh.gs(R.string.combov2_error_6)
                    7    -> rh.gs(R.string.combov2_error_7)
                    8    -> rh.gs(R.string.combov2_error_8)
                    9    -> rh.gs(R.string.combov2_error_9)
                    10   -> rh.gs(R.string.combov2_error_10)
                    11   -> rh.gs(R.string.combov2_error_11)
                    else -> ""
                }

                "${rh.gs(R.string.combov2_error)} E${alert.code}" +
                    if (desc.isEmpty()) "" else ": $desc"
            }

            else                          -> rh.gs(R.string.combov2_unrecognized_alert)
        }

    private fun notifyAboutComboAlert(alert: AlertScreenContent) {
        if (alert is AlertScreenContent.Error) {
            aapsLogger.info(LTag.PUMP, "Error screen observed - setting pumpErrorObserved flag")
            pumpErrorObserved = true
            startPumpErrorTimeout()
        }

        uiInteraction.addNotification(
            Notification.COMBO_PUMP_ALARM,
            text = "${rh.gs(R.string.combov2_combo_alert)}: ${getAlertDescription(alert)}",
            level = if (alert is AlertScreenContent.Warning) Notification.NORMAL else Notification.URGENT
        )
    }

    private fun reportFinishedBolus(status: String, id: Long, pumpEnactResult: PumpEnactResult, succeeded: Boolean) {
        rxBus.send(EventOverviewBolusProgress(rh, percent = 100, id = id))

        pumpEnactResult.apply {
            success = succeeded
            comment = status
        }
    }

    private fun reportFinishedBolus(stringId: Int, id: Long, pumpEnactResult: PumpEnactResult, succeeded: Boolean) =
        reportFinishedBolus(rh.gs(stringId), id, pumpEnactResult, succeeded)

    private fun createFailurePumpEnactResult(comment: Int) =
        pumpEnactResultProvider.get()
            .success(false)
            .enacted(false)
            .comment(comment)

    private fun getBluetoothAddress(): ComboCtlBluetoothAddress? =
        pumpManager?.getPairedPumpAddresses()?.firstOrNull()

    private fun getAcquiredPump() = pump ?: throw Error("There is no currently acquired pump; this should not happen. Please report this as a bug.")

    private fun isDisconnected() =
        when (driverStateFlow.value) {
            DriverState.NotInitialized,
            DriverState.Disconnected -> true

            else                     -> false
        }

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return

        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "combov2_settings"
            title = rh.gs(R.string.combov2_title)
            initialExpandedChildrenCount = 0
            addPreference(
                AdaptiveIntentPreference(
                    ctx = context, intentKey = ComboIntentKey.PairWithPump, title = R.string.combov2_pair_with_pump_title, summary = R.string.combov2_pair_with_pump_summary,
                    intent = Intent(context, ComboV2PairingActivity::class.java)
                )
            )
            addPreference(
                AdaptiveIntentPreference(
                    ctx = context, intentKey = ComboIntentKey.UnpairPump, title = R.string.combov2_unpair_pump_title, summary = R.string.combov2_unpair_pump_summary
                ).apply {
                    onPreferenceClickListener = Preference.OnPreferenceClickListener { preference ->
                        OKDialog.showConfirmation(preference.context, "Confirm pump unpairing", "Do you really want to unpair the pump?", ok = { unpair() })
                        false
                    }
                }
            )
            addPreference(AdaptiveIntPreference(ctx = context, intKey = ComboIntKey.DiscoveryDuration, title = R.string.combov2_discovery_duration))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = ComboBooleanKey.AutomaticReservoirEntry, title = R.string.combov2_automatic_reservoir_entry))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = ComboBooleanKey.AutomaticBatteryEntry, title = R.string.combov2_automatic_battery_entry))
            addPreference(
                AdaptiveSwitchPreference(ctx = context, booleanKey = ComboBooleanKey.VerboseLogging, title = R.string.combov2_verbose_logging).apply {
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                        updateComboCtlLogLevel(newValue as Boolean)
                        true
                    }
                }
            )
        }
    }
}
