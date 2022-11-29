package info.nightscout.comboctl.main

import info.nightscout.comboctl.base.BasicProgressStage
import info.nightscout.comboctl.base.BluetoothAddress
import info.nightscout.comboctl.base.BluetoothInterface
import info.nightscout.comboctl.base.ComboException
import info.nightscout.comboctl.base.Constants
import info.nightscout.comboctl.base.LogLevel
import info.nightscout.comboctl.base.Logger
import info.nightscout.comboctl.base.PairingPIN
import info.nightscout.comboctl.base.ProgressReporter
import info.nightscout.comboctl.base.PumpIO
import info.nightscout.comboctl.base.PumpStateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = Logger.get("PumpManager")

/**
 * Manager class for acquiring and creating [Pump] instances.
 *
 * This is the main class for accessing pumps. It manages a list
 * of paired pumps and handles discovery and pairing. Applications
 * use this class as the primary ComboCtl interface, along with [Pump].
 * [bluetoothInterface] is used for device discovery and for creating
 * Bluetooth device instances that are then passed to newly paired
 * [Pump] instances. [pumpStateStore] contains the pump states for
 * all paired pumps.
 *
 * Before an instance of this class can actually be used, [setup]
 * must be called.
 */
class PumpManager(
    private val bluetoothInterface: BluetoothInterface,
    private val pumpStateStore: PumpStateStore
) {
    // Coroutine mutex. This is used to prevent race conditions while
    // accessing acquiredPumps and the pumpStateStore. The mutex is needed
    // when acquiring pumps (accesses the store and the acquiredPumps map),
    // releasing pumps (accesses the acquiredPumps map), when a new pump
    // is found during discovery (accesses the store), and when a pump is
    // unpaired (accesses the store).
    // Note that a coroutine mutex is rather slow. But since the calls
    // that use it are not used very often, this is not an issue.
    private val pumpStateAccessMutex = Mutex()

    // List of Pump instances acquired by acquirePump() calls.
    private val acquiredPumps = mutableMapOf<BluetoothAddress, Pump>()

    /**
     * Stage for when discovery is aborted due to an error.
     */
    object DiscoveryError : BasicProgressStage.Aborted("discoveryError")

    /**
     * Exception thrown when an attempt is made to acquire an already acquired pump.
     *
     * Pumps cannot be acquired multiple times simulatenously. This is a safety
     * measure to prevent multiple [Pump] instances from accessing the same pump,
     * which would lead to undefined behavior (partially also because this would
     * cause chaos in [pumpStateStore]). See [PumpManager.acquirePump] for more.
     *
     * @param pumpAddress Bluetooth address of the pump that was already acquired.
     */
    class PumpAlreadyAcquiredException(val pumpAddress: BluetoothAddress) :
        ComboException("Pump with address $pumpAddress was already acquired")

    /**
     * Exception thrown when a pump has not been paired and a function requires a paired pump.
     *
     * @param pumpAddress Bluetooth address of the pump that's not paired.
     */
    class PumpNotPairedException(val pumpAddress: BluetoothAddress) :
        ComboException("Pump with address $pumpAddress has not been paired")

    /**
     * Possible results from a [pairWithNewPump] call.
     */
    sealed class PairingResult {
        data class Success(
            val bluetoothAddress: BluetoothAddress,
            val pumpID: String
        ) : PairingResult()

        class ExceptionDuringPairing(val exception: Exception) : PairingResult()
        object DiscoveryManuallyStopped : PairingResult()
        object DiscoveryError : PairingResult()
        object DiscoveryTimeout : PairingResult()
    }

    init {
        logger(LogLevel.INFO) { "Pump manager started" }

        // Install a filter to make sure we only ever get notified about Combo pumps.
        bluetoothInterface.deviceFilterCallback = { deviceAddress -> isCombo(deviceAddress) }
    }

    /**
     * Sets up this PumpManager instance.
     *
     * Once this is called, the [onPumpUnpaired] callback will be invoked
     * whenever a pump is unpaired (this includes unpairing via the system's
     * Bluetooth settings). Once this is invoked, the states associated with
     * the unpaired pump will already have been wiped from the pump state store.
     * That callback is mainly useful for UI updates. Note however that this
     * callback is typically called from some background thread. Switching to
     * a different context with [kotlinx.coroutines.withContext] may be necessary.
     *
     * This also checks the available states in the pump state store and compares
     * this with the list of paired device addresses returned by the
     * [BluetoothInterface.getPairedDeviceAddresses] function to check for pumps
     * that may have been unpaired while ComboCtl was not running. This makes sure
     * that there are no stale states inside the store which otherwise would impact
     * the event handling and cause other IO issues (especially in future pairing
     * attempts). If that check reveals states in [pumpStateStore] that do not
     * have a corresponding device in the list of paired device addresses, then
     * those stale pump states are erased.
     *
     * This must be called before using [pairWithNewPump] or [acquirePump].
     *
     * @param onPumpUnpaired Callback for when a previously paired pump is unpaired.
     *        This is typically called from some background thread. Switching to
     *        a different context with [withContext] may be necessary.
     */
    fun setup(onPumpUnpaired: (pumpAddress: BluetoothAddress) -> Unit = { }) {
        bluetoothInterface.onDeviceUnpaired = { deviceAddress ->
            onPumpUnpaired(deviceAddress)
            // Explicitly wipe the pump state to make sure that no stale pump state remains.
            pumpStateStore.deletePumpState(deviceAddress)
        }

        val pairedDeviceAddresses = bluetoothInterface.getPairedDeviceAddresses()
        logger(LogLevel.DEBUG) { "${pairedDeviceAddresses.size} paired Bluetooth device(s)" }

        val availablePumpStates = pumpStateStore.getAvailablePumpStateAddresses()
        logger(LogLevel.DEBUG) { "${availablePumpStates.size} available pump state(s)" }

        for (deviceAddress in pairedDeviceAddresses) {
            logger(LogLevel.DEBUG) { "Paired Bluetooth device: $deviceAddress" }
        }

        for (pumpStateAddress in availablePumpStates) {
            logger(LogLevel.DEBUG) { "Got state for pump with address $pumpStateAddress" }
        }

        // We need to keep the list of paired devices and the list of pump states in sync
        // to keep the pairing process working properly (avoiding pairing attempts when
        // at Bluetooth level the pump is already paired but at the pump state store level
        // it isn't) and to prevent incorrect connection attempts from happening
        // (avoiding connection attempts when at Bluetooth level the pump isn't paired
        // but at the pump state level it seems like it is).

        // Check for pump states that correspond to those pumps that are no longer paired.
        // This can happen if the user unpaired the Combo in the Bluetooth settings
        // while the pump manager wasn't running.
        for (pumpStateAddress in availablePumpStates) {
            val pairedDevicePresent = pairedDeviceAddresses.contains(pumpStateAddress)
            if (!pairedDevicePresent) {
                logger(LogLevel.DEBUG) { "There is no paired device for pump state with address $pumpStateAddress; deleting state" }
                pumpStateStore.deletePumpState(pumpStateAddress)
            }
        }
    }

    private val pairingProgressReporter = ProgressReporter(
        listOf(
            BasicProgressStage.ScanningForPumpStage::class,
            BasicProgressStage.EstablishingBtConnection::class,
            BasicProgressStage.PerformingConnectionHandshake::class,
            BasicProgressStage.ComboPairingKeyAndPinRequested::class,
            BasicProgressStage.ComboPairingFinishing::class
        ),
        Unit
    )

    /**
     * [kotlinx.coroutines.flow.StateFlow] for reporting progress during the [pairWithNewPump] call.
     *
     * See the [ProgressReporter] documentation for details.
     */
    val pairingProgressFlow = pairingProgressReporter.progressFlow

    /**
     * Resets the state of the [pairingProgressFlow] back to [BasicProgressStage.Idle].
     *
     * This is useful in case the user wants to try again to pair with a pump.
     * By resetting the state, it is easier to manage UI elements when such
     * a pairing retry is attempted, especially if the UI orients itself on
     * the stage field of the [pairingProgressFlow] value, which is
     * a [info.nightscout.comboctl.base.ProgressReport].
     */
    fun resetPairingProgress() = pairingProgressReporter.reset()

    /**
     * Starts device discovery and pairs with a pump once one is discovered.
     *
     * This function suspends the calling coroutine until a device is found,
     * the coroutine is cancelled, an error happens during discovery, or
     * discovery timeouts.
     *
     * This manages the Bluetooth device discovery and the pairing
     * process with new pumps. Once an unpaired pump is discovered,
     * the Bluetooth implementation pairs with it, using the
     * [Constants.BT_PAIRING_PIN] PIN code (not to be confused with
     * the 10-digit Combo PIN).
     *
     * When the Bluetooth-level pairing is done, additional processing is
     * necessary: The Combo-level pairing must be performed, which also sets
     * up a state in the [PumpStateStore] for the discovered pump.
     * [onPairingPIN] is called when the Combo-level pairing process
     * reaches a point where the user must be asked for the 10-digit PIN.
     *
     * Note that [onPairingPIN] is called by a coroutine that is run on
     * a different thread than the one that called this function . With
     * some UI frameworks like JavaFX, it is invalid to operate UI controls
     * in coroutines that are not associated with a particular UI coroutine
     * context. Consider using [kotlinx.coroutines.withContext] in
     * [onPairingPIN] for this reason.
     *
     * Before the pairing starts, this function compares the list of paired
     * Bluetooth device addresses with the list of pump states in the
     * [pumpStateStore]. This is similar to the checks done in [setup], except
     * it is reversed: Each paired device that has no corresponding pump
     * state in the [pumpStateStore] is unpaired before the new pairing begins.
     * This is useful to prevent situations where a Combo isn't actually paired,
     * but [pairWithNewPump] doesn't detect them, because at a Bluetooth level,
     * that Combo _is_ still paired (that is, the OS has it listed among its
     * paired devices).
     *
     * @param discoveryDuration How long the discovery shall go on,
     *   in seconds. Must be a value between 1 and 300.
     * @param onPairingPIN Suspending block that asks the user for
     *   the 10-digit pairing PIN during the pairing process.
     * @throws info.nightscout.comboctl.base.BluetoothException if discovery
     *   fails due to an underlying Bluetooth issue.
     */
    suspend fun pairWithNewPump(
        discoveryDuration: Int,
        onPairingPIN: suspend (newPumpAddress: BluetoothAddress, previousAttemptFailed: Boolean) -> PairingPIN
    ): PairingResult {
        val deferred = CompletableDeferred<PairingResult>()

        lateinit var result: PairingResult

        // Before doing the actual pairing, unpair devices that have no corresponding pump state.

        val pairedDeviceAddresses = bluetoothInterface.getPairedDeviceAddresses()
        logger(LogLevel.DEBUG) { "${pairedDeviceAddresses.size} paired Bluetooth device(s)" }

        val availablePumpStates = pumpStateStore.getAvailablePumpStateAddresses()
        logger(LogLevel.DEBUG) { "${availablePumpStates.size} available pump state(s)" }

        // Check for paired pumps that have no corresponding state. This can happen if
        // the state was deleted and the application crashed before it could unpair the
        // pump, or if some other application paired the pump. Those devices get unpaired.
        for (pairedDeviceAddress in pairedDeviceAddresses) {
            val pumpStatePresent = availablePumpStates.contains(pairedDeviceAddress)
            if (!pumpStatePresent) {
                if (isCombo(pairedDeviceAddress)) {
                    logger(LogLevel.DEBUG) { "There is no pump state for paired pump with address $pairedDeviceAddresses; unpairing" }
                    val bluetoothDevice = bluetoothInterface.getDevice(pairedDeviceAddress)
                    bluetoothDevice.unpair()
                }
            }
        }

        // Unpairing unknown devices done. Actual pairing of a new pump continues now.

        pairingProgressReporter.reset(Unit)

        // Spawn an internal coroutine scope since we need to launch new coroutines during discovery & pairing.
        coroutineScope {
            val thisScope = this
            try {
                pairingProgressReporter.setCurrentProgressStage(BasicProgressStage.ScanningForPumpStage)

                bluetoothInterface.startDiscovery(
                    sdpServiceName = Constants.BT_SDP_SERVICE_NAME,
                    sdpServiceProvider = "ComboCtl SDP service",
                    sdpServiceDescription = "ComboCtl",
                    btPairingPin = Constants.BT_PAIRING_PIN,
                    discoveryDuration = discoveryDuration,
                    onDiscoveryStopped = { reason ->
                        when (reason) {
                            BluetoothInterface.DiscoveryStoppedReason.MANUALLY_STOPPED ->
                                deferred.complete(PairingResult.DiscoveryManuallyStopped)
                            BluetoothInterface.DiscoveryStoppedReason.DISCOVERY_ERROR ->
                                deferred.complete(PairingResult.DiscoveryError)
                            BluetoothInterface.DiscoveryStoppedReason.DISCOVERY_TIMEOUT ->
                                deferred.complete(PairingResult.DiscoveryTimeout)
                        }
                    },
                    onFoundNewPairedDevice = { deviceAddress ->
                        thisScope.launch {
                            pumpStateAccessMutex.withLock {
                                try {
                                    logger(LogLevel.DEBUG) { "Found pump with address $deviceAddress" }

                                    if (pumpStateStore.hasPumpState(deviceAddress)) {
                                        logger(LogLevel.DEBUG) { "Skipping added pump since it has already been paired" }
                                    } else {
                                        performPairing(deviceAddress, onPairingPIN, pairingProgressReporter)

                                        val pumpID = pumpStateStore.getInvariantPumpData(deviceAddress).pumpID
                                        logger(LogLevel.DEBUG) { "Paired pump with address $deviceAddress ; pump ID = $pumpID" }

                                        deferred.complete(PairingResult.Success(deviceAddress, pumpID))
                                    }
                                } catch (e: Exception) {
                                    logger(LogLevel.ERROR) { "Caught exception while pairing to pump with address $deviceAddress: $e" }
                                    deferred.completeExceptionally(e)
                                    throw e
                                }
                            }
                        }
                    }
                )

                result = deferred.await()
            } catch (e: CancellationException) {
                logger(LogLevel.DEBUG) { "Pairing cancelled" }
                pairingProgressReporter.setCurrentProgressStage(BasicProgressStage.Cancelled)
                throw e
            } catch (e: Exception) {
                pairingProgressReporter.setCurrentProgressStage(BasicProgressStage.Error(e))
                result = PairingResult.ExceptionDuringPairing(e)
                throw e
            } finally {
                bluetoothInterface.stopDiscovery()
            }
        }

        when (result) {
            // Report Finished/Aborted _after_ discovery was stopped
            // (otherwise it isn't really finished/aborted yet).
            is PairingResult.Success ->
                pairingProgressReporter.setCurrentProgressStage(BasicProgressStage.Finished)
            is PairingResult.DiscoveryTimeout ->
                pairingProgressReporter.setCurrentProgressStage(BasicProgressStage.Timeout)
            is PairingResult.DiscoveryManuallyStopped ->
                pairingProgressReporter.setCurrentProgressStage(BasicProgressStage.Cancelled)
            is PairingResult.DiscoveryError ->
                pairingProgressReporter.setCurrentProgressStage(DiscoveryError)
            // The other cases are covered by the catch clauses above.
            else -> Unit
        }

        return result
    }

    /**
     * Returns a set of Bluetooth addresses of the paired pumps.
     *
     * This equals the list of addresses of all the pump states in the
     * [PumpStateStore] assigned to this PumpManager instance.
     */
    fun getPairedPumpAddresses() = pumpStateStore.getAvailablePumpStateAddresses()

    /**
     * Returns the ID of the paired pump with the given address.
     *
     * @return String with the pump ID.
     * @throws PumpStateDoesNotExistException if no pump state associated with
     *   the given address exists in the store.
     * @throws PumpStateStoreAccessException if accessing the data fails
     *   due to an error that occurred in the underlying implementation.
     */
    fun getPumpID(pumpAddress: BluetoothAddress) =
        pumpStateStore.getInvariantPumpData(pumpAddress).pumpID

    /**
     * Acquires a Pump instance for a pump with the given Bluetooth address.
     *
     * Pumps can only be acquired once at a time. This is a safety measure to
     * prevent multiple [Pump] instances from accessing the same pump, which
     * would lead to undefined behavior. An acquired pump must be un-acquired
     * by calling [releasePump]. Attempting to acquire an already acquired
     * pump is an error and will cause this function to throw an exception
     * ([PumpAlreadyAcquiredException]).
     *
     * The pump must have been paired before it can be acquired. If this is
     * not done, an [PumpNotPairedException] is thrown.
     *
     * For details about [initialBasalProfile] and [onEvent], consult the
     * [Pump] documentation.
     *
     * @param pumpAddress Bluetooth address of the pump to acquire.
     * @param initialBasalProfile Basal profile to use as the initial profile,
     *   or null if no initial profile shall be used.
     * @param onEvent Callback to inform caller about events that happen
     *   during a connection, like when the battery is going low, or when
     *   a TBR started.
     * @throws PumpAlreadyAcquiredException if the pump was already acquired.
     * @throws PumpNotPairedException if the pump was not yet paired.
     * @throws info.nightscout.comboctl.base.BluetoothException if getting
     *   a [info.nightscout.comboctl.base.BluetoothDevice] for this pump fails.
     */
    suspend fun acquirePump(
        pumpAddress: BluetoothAddress,
        initialBasalProfile: BasalProfile? = null,
        onEvent: (event: Pump.Event) -> Unit = { }
    ) =
        pumpStateAccessMutex.withLock {
            if (acquiredPumps.contains(pumpAddress))
                throw PumpAlreadyAcquiredException(pumpAddress)

            logger(LogLevel.DEBUG) { "Getting Pump instance for pump $pumpAddress" }

            if (!pumpStateStore.hasPumpState(pumpAddress))
                throw PumpNotPairedException(pumpAddress)

            val bluetoothDevice = bluetoothInterface.getDevice(pumpAddress)

            val pump = Pump(bluetoothDevice, pumpStateStore, initialBasalProfile, onEvent)

            acquiredPumps[pumpAddress] = pump

            pump // Return the Pump instance
        }

    /**
     * Releases (= un-acquires) a previously acquired pump with the given address.
     *
     * If no such pump was previously acquired, this function does nothing.
     *
     * @param acquiredPumpAddress Bluetooth address of the pump to release.
     */
    suspend fun releasePump(acquiredPumpAddress: BluetoothAddress) {
        pumpStateAccessMutex.withLock {
            if (!acquiredPumps.contains(acquiredPumpAddress)) {
                logger(LogLevel.DEBUG) { "A pump with address $acquiredPumpAddress wasn't previously acquired; ignoring call" }
                return@withLock
            }

            acquiredPumps.remove(acquiredPumpAddress)
        }
    }

    // Filter for Combo devices based on their address.
    // The first 3 bytes of a Combo are always the same.
    private fun isCombo(deviceAddress: BluetoothAddress) =
        (deviceAddress[0] == 0x00.toByte()) &&
                (deviceAddress[1] == 0x0E.toByte()) &&
                (deviceAddress[2] == 0x2F.toByte())

    private suspend fun performPairing(
        pumpAddress: BluetoothAddress,
        onPairingPIN: suspend (newPumpAddress: BluetoothAddress, previousAttemptFailed: Boolean) -> PairingPIN,
        progressReporter: ProgressReporter<Unit>?
    ) {
        // NOTE: Pairing can be aborted either by calling stopDiscovery()
        // or by cancelling the coroutine that runs this functions.

        logger(LogLevel.DEBUG) { "About to perform pairing with pump $pumpAddress" }

        val bluetoothDevice = bluetoothInterface.getDevice(pumpAddress)
        logger(LogLevel.DEBUG) { "Got Bluetooth device instance for pump" }

        val pumpIO = PumpIO(pumpStateStore, bluetoothDevice, onNewDisplayFrame = {}, onPacketReceiverException = {})

        if (pumpIO.isPaired()) {
            logger(LogLevel.INFO) { "Not pairing discovered pump $pumpAddress since it is already paired" }
            return
        }

        logger(LogLevel.DEBUG) { "Pump instance ready for pairing" }

        pumpIO.performPairing(bluetoothInterface.getAdapterFriendlyName(), progressReporter, onPairingPIN)

        logger(LogLevel.DEBUG) { "Successfully paired with pump $pumpAddress" }
    }
}
