package info.nightscout.comboctl.base

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val logger = Logger.get("PumpIO")

private object PumpIOConstants {
    const val MAX_NUM_REGULAR_CONNECTION_ATTEMPTS = 3
    const val NONCE_INCREMENT = 500
}

/**
 * Callback used during pairing for asking the user for the 10-digit PIN.
 *
 * This is passed to [PumpIO.performPairing] when pairing.
 *
 * [previousAttemptFailed] is useful for showing in a GUI that the
 * previously entered PIN seems to be wrong and that the user needs
 * to try again.
 *
 * If the user wants to cancel the pairing instead of entering the
 * PIN, cancelling the coroutine where [PumpIO.performPairing] runs
 * is sufficient.
 *
 * @param previousAttemptFailed true if the user was already asked for
 *        the PIN and the KEY_RESPONSE authentication failed.
 */
typealias PairingPINCallback = suspend (previousAttemptFailed: Boolean) -> PairingPIN

/**
 * Class for high-level Combo pump IO.
 *
 * This implements high level IO actions on top of [TransportLayer.IO].
 * It takes care of pairing, connection setup, remote terminal (RT)
 * commands and RT display reception, and also supports the Combo's
 * command mode. Basically, this class' public API reflects what the
 * user can directly do with the pump (press RT buttons like UP or
 * DOWN, send a command mode bolus etc.).
 *
 * For initiating the Combo pairing, the [performPairing] function is
 * available. This must not be used if a connection was already established
 * via [connect] - pairing is only possible in the disconnected state.
 *
 * For initiating a regular connection, use [connect]. Do not call
 * [connect] again until after disconnecting with [disconnect]. Also
 * see the remarks above about pairing and connecting at the same time.
 *
 * The Combo regularly sends new display frames when running in the
 * REMOTE_TERMINAL (RT) mode. These frames come as the payload of
 * RT_DISPLAY packets. This class reads those packets and extracts the
 * partial frames (the packets only contain portions of a frame, not
 * a full frame). Once enough parts were gathered to assemble a full
 * frame, the frame is emitted via [onNewDisplayFrame]. This callback
 * must not block, since this would otherwise block the dataflow in the
 * internal IO code. This callback is mainly meant for passing the
 * frame to something like a flow or a channel. If its argument is
 * null, it means that there's no frame available. This happens at
 * the beginning in [connect] and during the [switchMode] call.
 *
 * To handle IO at the transport layer, this uses [TransportLayer.IO]
 * internally.
 *
 * In regular connections, the Combo needs "heartbeats" to periodically
 * let it know that the client still exists. If too much time passes since
 * the last heartbeat, the Combo terminates the connection. Each mode has a
 * different type of heartbeat: RT mode has RT_KEEP_ALIVE commands, command
 * mode has CMD_PING commands. To periodically send these, this class runs
 * separate coroutines with loops inside that send these commands. Only one
 * of these two heartbeats are active, depending on the current mode of the
 * Combo.
 * Note that other commands sent to the Combo _also_ count as heartbeats,
 * so RT_KEEP_ALIVE / CMD_PING only need to be sent by the internal heartbeat
 * if no other command has been sent for a while now.
 * In some cases (typically unit tests), a regular connection without
 * a heartbeat is needed. [connect] accepts an argument to start
 * without one for this purpose.
 *
 * The supplied [pumpStateStore] is used during pairing and regular
 * connections. During pairing, a new pump state is set up for the
 * pump that is being paired. It is during pairing that the invariant
 * portion of the pump state is written. During regular (= not pairing)
 * connections, the invariant part is read, not written.
 *
 * This class accesses the pump state in a thread safe manner, ensuring
 * that no two threads access the pump state at the same time. See
 * [PumpStateStore] for details about thread safety.
 *
 * @param pumpStateStore Pump state store to use.
 * @param bluetoothDevice [BluetoothDevice] object to use for
 *   Bluetooth I/O. Must be in a disconnected state when
 *   assigned to this instance.
 * @param onNewDisplayFrame Callback to invoke whenever a new RT
 *   [DisplayFrame] was received.
 * @param onPacketReceiverException Callback to invoked whenever an
 *   exception is thrown inside the transport layer's receiver loop.
 *   This is useful for automatic reconnecting.
 */
class PumpIO(
    private val pumpStateStore: PumpStateStore,
    private val bluetoothDevice: BluetoothDevice,
    private val onNewDisplayFrame: (displayFrame: DisplayFrame?) -> Unit,
    private val onPacketReceiverException: (e: TransportLayer.PacketReceiverException) -> Unit
) {
    // Mutex to synchronize sendPacketWithResponse and sendPacketWithoutResponse calls.
    private val sendPacketMutex = Mutex()

    // RT sequence number. Used in outgoing RT packets.
    private var currentRTSequence: Int = 0

    // Pass IO through the FramedComboIO class since the Combo
    // sends packets in a framed form (See [ComboFrameParser]
    // and [List<Byte>.toComboFrame] for details).
    private val framedComboIO = FramedComboIO(bluetoothDevice)

    private var initialMode: Mode? = null

    private var transportLayerIO = TransportLayer.IO(
        pumpStateStore, bluetoothDevice.address, framedComboIO
    ) { packetReceiverException ->
        // If the packet receiver fails, close the barrier to wake
        // up any caller that is waiting on it.
        rtButtonConfirmationBarrier.close(packetReceiverException)
        // Also forward the exception to the associated callback.
        onPacketReceiverException(packetReceiverException)
    }

    private var internalScopeJob: Job? = null
    private var internalScope: CoroutineScope? = null

    // Job representing the coroutine that runs the CMD ping heartbeat.
    private var cmdPingHeartbeatJob: Job? = null
    // Job representing the coroutine that runs the RT keep-alive heartbeat.
    private var rtKeepAliveHeartbeatJob: Job? = null

    // Members associated with long-pressing buttons in RT mode.
    // Long-pressing is implemented by repeatedly sending RT_BUTTON_STATUS
    // messages until the user "releases" the buttons.
    // (We use a list of Button values in case multiple buttons are being
    // held down at the same time.)
    // We use a Deferred instance instead of Job to be able to catch
    // and store exceptions & rethrow them later.
    private var currentLongRTPressJob: Deferred<Unit>? = null
    private var currentLongRTPressedButtons = listOf<ApplicationLayer.RTButton>()
    private var longRTPressLoopRunning = true

    // A Channel that is used as a "barrier" of sorts to block button
    // pressing functions from continuing until the Combo sends
    // a confirmation for the key press. Up until that confirmation
    // is received, the client must not send any other button press
    // commands to the Combo. To ensure that, this barrier exists.
    // Its payload is a Boolean to let waiting coroutines know whether
    // to finish or to continue any internal loops. The former happens
    // during disconnect. It is set up as a conflated channel. That
    // way, if a confirmation is received before button press commands
    // call receive(), information about the confirmation is not lost
    // (which would happen with a rendezvous channel). And, in case
    // disconnect() is called, it is important to overwrite any other
    // existing value with "false" to stop button pressing commands
    // (hence a conflated channel instead of DROP_OLDEST buffer
    // overflow behavior).
    private var rtButtonConfirmationBarrier = newRtButtonConfirmationBarrier()

    private val displayFrameAssembler = DisplayFrameAssembler()

    // Whether we are in RT or COMMAND mode, or null at startup
    // before an initial mode was set.
    private val _currentModeFlow = MutableStateFlow<Mode?>(null)

    /************************************
     *** PUBLIC FUNCTIONS AND CLASSES ***
     ************************************/

    /**
     * The mode the pump can operate in.
     */
    enum class Mode(val str: String) {
        REMOTE_TERMINAL("REMOTE_TERMINAL"),
        COMMAND("COMMAND");

        override fun toString() = str
    }

    /**
     * Current connection state.
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,

        /**
         * State after a pump command failed, typically because of an IO error.
         *
         * When this state is reached, nothing more can be done with the pump
         * until [disconnect] is called.
         */
        FAILED
    }

    /**
     * Exception thrown after all attempts to establish a regular connection failed.
     *
     * See [connect] for details about the regular connection attempts.
     */
    class ConnectionRequestIsNotBeingAcceptedException :
        ComboIOException("All attempts to have the Combo accept connection request after establishing Bluetooth socket failed")

    /**
     * The pump's Bluetooth address.
     */
    val address: BluetoothAddress = bluetoothDevice.address

    /**
     * Read-only [StateFlow] property that announces when the current [Mode] changes.
     *
     * This flow's value is null until the connection is fully established (at which point
     * the mode is set to [PumpIO.Mode.REMOTE_TERMINAL] or [PumpIO.Mode.COMMAND]), and
     * set back to null again after disconnecting.
     */
    val currentModeFlow = _currentModeFlow.asStateFlow()

    private var _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)

    /**
     * Read-only [StateFlow] property that notifies about connection state changes.
     */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /**
     * Returns whether this pump has already been paired.
     *
     * "Pairing" refers to the custom Combo pairing here, not the Bluetooth pairing.
     * It is not possible to get a valid [PumpIO] instance without the device having
     * been paired at the Bluetooth level before anyway.
     *
     * This detects whether the Combo pairing has been performed by looking at the
     * persistent state associated with this [PumpIO] instance. If the state
     * is set to valid values, then the pump is assumed to be paired. If the persistent
     * state is in its initial state (ciphers set to null, key response address
     * set to null), then it is assumed to be unpaired.
     *
     * @return true if the pump is paired.
     */
    fun isPaired() = pumpStateStore.hasPumpState(bluetoothDevice.address)

    /**
     * Performs a pairing procedure with a Combo.
     *
     * This performs the Combo-specific pairing. When this is called,
     * the pump must have been paired at the Bluetooth level already.
     * From Bluetooth's point of view, the pump is already paired with
     * the client at this point. But the Combo itself needs an additional
     * custom pairing. As part of this extra pairing, this function sets
     * up a special temporary pairing connection to the Combo, and terminates
     * that connection before finishing. Manually setting up such a connection
     * is not necessary and not supported by the public API.
     *
     * However, the Bluetooth connection setup and teardown _is_ handled
     * by this function. If the Combo-specific pairing fails, this also
     * automatically unpairs the pump at the Bluetooth level.
     *
     * Cancelling the coroutine this function runs in will abort the pairing
     * process in an orderly fashion.
     *
     * Pairing will initialize a new state for this pump [PumpStateStore] that
     * was passed to the constructor of this class. This state will contain
     * new pairing data, a new pump ID string, and a new initial nonce.
     *
     * The [onPairingPIN] block has two arguments. previousAttemptFailed
     * is set to false initially, and true if this is a repeated call due
     * to a previous failure to apply the PIN. Such a failure typically
     * happens because the user mistyped the PIN, but in rare cases can also
     * happen due to corrupted packets.
     *
     * Note that [onPairingPIN] is called by a coroutine that is run on
     * a different thread than the one that called this function . With
     * some UI frameworks like JavaFX, it is invalid to operate UI controls
     * in coroutines that are not associated with a particular UI coroutine
     * context. Consider using [kotlinx.coroutines.withContext] in
     * [onPairingPIN] for this reason.
     *
     * WARNING: Do not run multiple performPairing functions simultaneously
     * on the same pump. Otherwise, undefined behavior occurs.
     *
     * @param bluetoothFriendlyName The Bluetooth friendly name to use in
     *   REQUEST_ID packets. Use [BluetoothInterface.getAdapterFriendlyName]
     *   to get the friendly name.
     * @param progressReporter [ProgressReporter] for tracking pairing progress.
     * @param onPairingPIN Suspending block that asks the user for
     *   the 10-digit pairing PIN during the pairing process.
     * @throws IllegalStateException if this is ran while a connection
     *   is running.
     * @throws PumpStateAlreadyExistsException if the pump was already
     *   fully paired before.
     * @throws TransportLayer.PacketReceiverException if an exception
     *   is thrown while this function is waiting for a packet.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun performPairing(
        bluetoothFriendlyName: String,
        progressReporter: ProgressReporter<Unit>?,
        onPairingPIN: suspend (newPumpAddress: BluetoothAddress, previousAttemptFailed: Boolean) -> PairingPIN
    ) {
        check(!isPaired()) {
            "Attempting to pair with pump with address ${bluetoothDevice.address} even though it is already paired"
        }

        check(!isIORunning()) {
            "Attempted to perform pairing while pump with address ${bluetoothDevice.address} is connected"
        }

        // This flag is used for checking if we need to unpair
        // the Bluetooth device before leaving this function. This
        // makes sure that in case of an error, any established
        // Bluetooth pairing is undone, and the persistent state
        // is reverted to its initial state.
        var doUnpair = true

        // Make sure the frame parser has no leftover data from
        // a previous connection.
        framedComboIO.reset()

        // Set up a custom coroutine scope to run the packet receiver in.
        coroutineScope {
            try {
                _connectionState.value = ConnectionState.CONNECTING

                // Connecting to Bluetooth may block, so run it in
                // a coroutine with an IO dispatcher.
                withContext(bluetoothDevice.ioDispatcher) {
                    bluetoothDevice.connect()
                }

                _connectionState.value = ConnectionState.CONNECTED

                transportLayerIO.start(packetReceiverScope = this) { tpLayerPacket -> processReceivedPacket(tpLayerPacket) }

                progressReporter?.setCurrentProgressStage(BasicProgressStage.PerformingConnectionHandshake)

                // Initiate pairing and wait for the response.
                // (The response contains no meaningful payload.)
                logger(LogLevel.DEBUG) { "Sending pairing connection request" }
                sendPacketWithResponse(
                    TransportLayer.createRequestPairingConnectionPacketInfo(),
                    TransportLayer.Command.PAIRING_CONNECTION_REQUEST_ACCEPTED
                )

                // Initiate pump-client and client-pump keys request.
                // This will cause the pump to generate and show a
                // 10-digit PIN.
                logger(LogLevel.DEBUG) { "Requesting the pump to generate and show the pairing PIN" }
                sendPacketWithoutResponse(TransportLayer.createRequestKeysPacketInfo())

                progressReporter?.setCurrentProgressStage(BasicProgressStage.ComboPairingKeyAndPinRequested)

                logger(LogLevel.DEBUG) { "Requesting the keys from the pump" }
                val keyResponsePacket = sendPacketWithResponse(
                    TransportLayer.createGetAvailableKeysPacketInfo(),
                    TransportLayer.Command.KEY_RESPONSE
                )

                logger(LogLevel.DEBUG) { "Will ask for pairing PIN" }
                var previousPINAttemptFailed = false

                lateinit var keyResponseInfo: KeyResponseInfo
                while (true) {
                    logger(LogLevel.DEBUG) { "Waiting for the PIN to be provided" }

                    // Request the PIN. If canceled, PairingAbortedException is
                    // thrown by the callback.
                    val pin = onPairingPIN(bluetoothDevice.address, previousPINAttemptFailed)

                    logger(LogLevel.DEBUG) { "Provided PIN: $pin" }

                    val weakCipher = Cipher(generateWeakKeyFromPIN(pin))
                    logger(LogLevel.DEBUG) { "Generated weak cipher key ${weakCipher.key.toHexString()} out of pairing PIN" }

                    if (keyResponsePacket.verifyAuthentication(weakCipher)) {
                        logger(LogLevel.DEBUG) { "KEY_RESPONSE packet verified" }
                        keyResponseInfo = processKeyResponsePacket(keyResponsePacket, weakCipher)
                        // Exit the loop since we successfully verified the packet.
                        break
                    } else {
                        logger(LogLevel.DEBUG) { "Could not verify KEY_RESPONSE packet; user may have entered PIN incorrectly; asking again for PIN" }
                        previousPINAttemptFailed = true
                    }
                }

                // Manually set the cached invariant pump data inside transportLayerIO,
                // otherwise the next outgoing packets will not be properly authenticated
                // (and their address bytes won't be valid). We'll update this later on
                // with the final version of the invariant data. That's also the one
                // that will be written into the pump state store.
                transportLayerIO.setManualInvariantPumpData(
                    InvariantPumpData(
                        clientPumpCipher = keyResponseInfo.clientPumpCipher,
                        pumpClientCipher = keyResponseInfo.pumpClientCipher,
                        keyResponseAddress = keyResponseInfo.keyResponseAddress,
                        pumpID = ""
                    )
                )

                logger(LogLevel.DEBUG) { "Requesting the pump ID from the pump" }
                val idResponsePacket = sendPacketWithResponse(
                    TransportLayer.createRequestIDPacketInfo(bluetoothFriendlyName),
                    TransportLayer.Command.ID_RESPONSE
                )
                val pumpID = processIDResponsePacket(idResponsePacket)

                val newPumpData = InvariantPumpData(
                    clientPumpCipher = keyResponseInfo.clientPumpCipher,
                    pumpClientCipher = keyResponseInfo.pumpClientCipher,
                    keyResponseAddress = keyResponseInfo.keyResponseAddress,
                    pumpID = pumpID
                )
                transportLayerIO.setManualInvariantPumpData(newPumpData)

                val currentSystemDateTime = Clock.System.now()
                val currentSystemTimeZone = TimeZone.currentSystemDefault()
                val currentSystemUtcOffset = currentSystemTimeZone.offsetAt(currentSystemDateTime)

                pumpStateStore.createPumpState(
                    bluetoothDevice.address,
                    newPumpData,
                    currentSystemUtcOffset,
                    CurrentTbrState.NoTbrOngoing
                )

                val firstTxNonce = Nonce(
                    byteArrayListOfInts(
                        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                    )
                )
                pumpStateStore.setCurrentTxNonce(bluetoothDevice.address, firstTxNonce)

                progressReporter?.setCurrentProgressStage(BasicProgressStage.ComboPairingFinishing)

                // Initiate a regular (= non-pairing) transport layer connection.
                // Note that we are still pairing - it just continues in the
                // application layer. For this to happen, we need a regular
                // _transport layer_ connection.
                // Wait for the response and verify it.
                logger(LogLevel.DEBUG) { "Sending regular connection request" }
                sendPacketWithResponse(
                    TransportLayer.createRequestRegularConnectionPacketInfo(),
                    TransportLayer.Command.REGULAR_CONNECTION_REQUEST_ACCEPTED
                )

                // Initiate application-layer connection and wait for the response.
                // (The response contains no meaningful payload.)
                logger(LogLevel.DEBUG) { "Initiating application layer connection" }
                sendPacketWithResponse(
                    ApplicationLayer.createCTRLConnectPacket(),
                    ApplicationLayer.Command.CTRL_CONNECT_RESPONSE
                )

                // Next, we have to query the versions of both command mode and
                // RT mode services. It is currently unknown how to interpret
                // the version numbers, but apparently we _have_ to query them,
                // otherwise the pump considers it an error.
                // TODO: Further verify this.
                logger(LogLevel.DEBUG) { "Requesting command mode service version" }
                sendPacketWithResponse(
                    ApplicationLayer.createCTRLGetServiceVersionPacket(ApplicationLayer.ServiceID.COMMAND_MODE),
                    ApplicationLayer.Command.CTRL_GET_SERVICE_VERSION_RESPONSE
                )
                // NOTE: These two steps may not be necessary. See the
                // "Application layer pairing" section in the spec.
                /*
                sendPacketWithResponse(
                    ApplicationLayer.ApplicationLayer.createCTRLGetServiceVersionPacket(ApplicationLayer.ServiceID.RT_MODE),
                    ApplicationLayer.Command.CTRL_GET_SERVICE_VERSION_RESPONSE
                )
                */

                // Next, send a BIND command and wait for the response.
                // (The response contains no meaningful payload.)
                logger(LogLevel.DEBUG) { "Sending BIND command" }
                sendPacketWithResponse(
                    ApplicationLayer.createCTRLBindPacket(),
                    ApplicationLayer.Command.CTRL_BIND_RESPONSE
                )

                // We have to re-connect the regular connection at the
                // transport layer now. (Unclear why, but it seems this
                // is necessary for the pairing process to succeed.)
                // Wait for the response and verify it.
                logger(LogLevel.DEBUG) { "Reconnecting regular connection" }
                sendPacketWithResponse(
                    TransportLayer.createRequestRegularConnectionPacketInfo(),
                    TransportLayer.Command.REGULAR_CONNECTION_REQUEST_ACCEPTED
                )

                // Pairing complete.
                doUnpair = false
                logger(LogLevel.DEBUG) { "Pairing finished successfully - sending CTRL_DISCONNECT to Combo" }
            } catch (e: CancellationException) {
                logger(LogLevel.DEBUG) { "Pairing cancelled - sending CTRL_DISCONNECT to Combo" }
                throw e
            } catch (t: Throwable) {
                logger(LogLevel.ERROR) {
                    "Pairing aborted due to throwable - sending CTRL_DISCONNECT to Combo; " +
                    "throwable details: ${t.stackTraceToString()}"
                }
                throw t
            } finally {
                val disconnectPacketInfo = ApplicationLayer.createCTRLDisconnectPacket()
                transportLayerIO.stop(
                    disconnectPacketInfo.toTransportLayerPacketInfo(),
                    ::disconnectBTDeviceAndCatchExceptions
                )

                _connectionState.value = ConnectionState.DISCONNECTED

                disconnectBTDeviceAndCatchExceptions()

                if (doUnpair) {
                    // Unpair in a separate context, since this
                    // can block for up to a second or so.
                    withContext(bluetoothDevice.ioDispatcher) {
                        bluetoothDevice.unpair()
                    }
                    pumpStateStore.deletePumpState(address)
                }
            }
        }
    }

    /**
     * Establishes a regular connection.
     *
     * A "regular connection" is a connection that is used for typical Combo
     * operations such as sending bolus commands, sending RT button presses etc.
     * The client must have been paired with the Combo before such connections
     * can be established.
     *
     * This function suspends the calling coroutine until the connection is up
     * and running or a connection setup error occurs, which will cause an
     * exception to be thrown. If this happens, users must call [disconnect]
     * before doing anything else again with this [PumpIO] instance.
     *
     * The Bluetooth connection is set up by this function. [connectionState]
     * is updated by it as well, as is the [connectProgressReporter] that is
     * passed to this function as an argument.
     *
     * This must be called before [switchMode] and any RT / command mode
     * function are used.
     *
     * Transport layer packets are received in a background coroutine that is part
     * of an internal coroutine scope. Reception is handled by [TransportLayer.IO].
     *
     * [isIORunning] will return true if the connection was established.
     *
     * [disconnect] is the counterpart to this function. It terminates
     * an existing connection and stops the worker.
     *
     * This also starts the "heartbeat" (unless explicitly requested not to).
     * See the [PumpIO] documentation above for details.
     *
     * The [connectionState] is set to [ConnectionState.FAILED] in case of
     * an exception, unless it is a [CancellationException], in which case
     * this function performs a normal disconnection by calling [disconnect].
     *
     * If the [connectProgressReporter] isn't null, progress is reported through
     * it to the caller. The [BasicProgressStage.Aborted] (and its subclasses),
     * [BasicProgressStage.Finished], and [BasicProgressStage.Cancelled] stages
     * are _not_ set; this is up to the caller. Likewise, the progress reporter
     * is not automatically reset; if it requires a reset, the caller must do
     * that by calling [ProgressReporter.reset]. This is done that way because
     * the caller may implement some mechanism to retry a connection attempt;
     * in such a case, setting these stages and resetting the reported would
     * interfere, so this is not done. The [connectProgressReporter] must contain
     * the [BasicProgressStage.PerformingConnectionHandshake] stage in its
     * planned progress sequence (see [ProgressReporter] for details).
     *
     * This function also handles a special situation if the [Nonce] that is
     * stored in [PumpStateStore] for this pump is incorrect. The Bluetooth
     * socket can then be successfully connected, but right afterwards, when
     * this function tries to send a [TransportLayer.Command.REQUEST_REGULAR_CONNECTION]
     * packet, the Combo does not respond, instead terminating the connection
     * and producing a [BluetoothException]. If this happens, this function
     * increments the nonce and tries again. This is done multiple times
     * until either the connection setup succeeds or the maximum number of
     * attempts is reached. In the latter case, this function throws a
     * [ConnectionRequestIsNotBeingAcceptedException]. The user should then
     * be recommended to re-pair with the Combo, since establishing a connection
     * isn't working.
     *
     * @param initialMode What mode to initially switch to.
     * @param runHeartbeat True if the heartbeat shall be started.
     * @param connectProgressReporter Optional [ProgressReporter] to update
     *   during the connection progress.
     * @throws ConnectionRequestIsNotBeingAcceptedException if connecting the
     *   actual Bluetooth socket succeeds, but the Combo does not accept the
     *   packet that requests a connection, and this failed several times
     *   in a row.
     * @throws IllegalStateException if IO was already started by a previous
     *   [connect] call or if the [PumpStateStore] that was passed to the
     *   class constructor has no pairing data for this pump (most likely
     *   because the pump isn't paired yet).
     */
    suspend fun connect(
        initialMode: Mode = Mode.REMOTE_TERMINAL,
        runHeartbeat: Boolean = true,
        connectProgressReporter: ProgressReporter<Unit>? = null
    ) {
        // Prerequisites.

        check(isPaired()) {
            "Attempted to connect without a valid persistent state; pairing may not have been done"
        }
        check(!isIORunning()) {
            "Attempted to connect even though a connection is already ongoing or established"
        }

        // Reset the display frame assembler in case it contains
        // partial frames from an earlier connection.
        displayFrameAssembler.reset()

        // Tell the callback that there's currently no frame available.
        onNewDisplayFrame(null)

        // Reinitialize the barrier, since it may have been closed
        // earlier due to an exception in the packet receiver.
        // (See the transportLayerIO initialization code.)
        rtButtonConfirmationBarrier = newRtButtonConfirmationBarrier()

        // Start the internal coroutine scope that will run the heartbeat,
        // packet receiver, and other internal coroutines. Enforce the
        // default dispatcher to rule out that something like the UI
        // scope could be picked automatically on some platforms.
        val newScopeJob = SupervisorJob()
        val newScope = CoroutineScope(newScopeJob + Dispatchers.Default)

        this.initialMode = initialMode
        this.internalScopeJob = newScopeJob
        this.internalScope = newScope

        // Make sure the frame parser has no leftover data from
        // a previous connection.
        framedComboIO.reset()

        logger(LogLevel.DEBUG) { "Pump IO connecting asynchronously" }

        try {
            _connectionState.value = ConnectionState.CONNECTING

            // The Combo does not tell us if the nonce is wrong. We have to infer
            // that from its behavior. If the nonce is wrong, then the Bluetooth
            // socket can be connected, but sending the REQUEST_REGULAR_CONNECTION
            // packet fails - the expected response is not received, and instead,
            // a BluetoothException occurs. In this particular case - Bluetooth
            // connect() call succeeds, sendPacketWithResponse() call that shall
            // send REQUEST_REGULAR_CONNECTION fails with BluetoothException - we
            // may have an incorrect nonce. Increment the nonce by NONCE_INCREMENT,
            // then retry. We retry a limited number of times. If sending the
            // REQUEST_REGULAR_CONNECTION still fails, then we give up, and throw
            // an exception that shall show on a UI a message to the user that
            // establishing a connection isn't working and the user should consider
            // re-pairing the pump instead.
            var regularConnectionRequestAccepted = false
            for (regularConnectionAttemptNr in 0 until PumpIOConstants.MAX_NUM_REGULAR_CONNECTION_ATTEMPTS) {
                // Suspend the coroutine until Bluetooth is connected.
                // Do this in a separate coroutine with an IO dispatcher
                // since the connection setup may block.
                withContext(bluetoothDevice.ioDispatcher) {
                    bluetoothDevice.connect()
                }

                connectProgressReporter?.setCurrentProgressStage(BasicProgressStage.PerformingConnectionHandshake)

                try {
                    // Start the actual IO activity.
                    transportLayerIO.start(newScope) { tpLayerPacket -> processReceivedPacket(tpLayerPacket) }

                    logger(LogLevel.DEBUG) { "Sending regular connection request" }

                    // Initiate connection at the transport layer.
                    sendPacketWithResponse(
                        TransportLayer.createRequestRegularConnectionPacketInfo(),
                        TransportLayer.Command.REGULAR_CONNECTION_REQUEST_ACCEPTED
                    )

                    regularConnectionRequestAccepted = true

                    // Exit the connection-attempt for-loop, since we are done.
                    break
                } catch (e: TransportLayer.PacketReceiverException) {
                    logger(LogLevel.INFO) {
                        "Successfully set up Bluetooth socket, but attempting to send " +
                        "the regular connection request packet failed; exception: ${e.cause}"
                    }
                    logger(LogLevel.INFO) {
                        "Nonce might be wrong; incrementing nonce by ${PumpIOConstants.NONCE_INCREMENT} " +
                        "and retrying (attempt $regularConnectionAttemptNr of " +
                        "${PumpIOConstants.MAX_NUM_REGULAR_CONNECTION_ATTEMPTS})"
                    }

                    // Call this to reset the states in the transport layer IO object
                    // and to disconnect Bluetooth. Otherwise we cannot call
                    // transportLayerIO.start() later again.
                    transportLayerIO.stop(disconnectPacketInfo = null, ::disconnectBTDeviceAndCatchExceptions)

                    pumpStateStore.incrementTxNonce(bluetoothDevice.address, PumpIOConstants.NONCE_INCREMENT)

                    // Wait one second before the next attempt. The Combo does not seem to be able
                    // to handle an immediate reconnect attempt, and some Bluetooth stacks don't either.
                    delay(1000)
                }
            }

            if (!regularConnectionRequestAccepted) {
                logger(LogLevel.ERROR) { "All attempts to request regular connection failed" }
                throw ConnectionRequestIsNotBeingAcceptedException()
            }

            // Initiate connection at the application layer.
            logger(LogLevel.DEBUG) { "Initiating application layer connection" }
            sendPacketWithResponse(
                ApplicationLayer.createCTRLConnectPacket(),
                ApplicationLayer.Command.CTRL_CONNECT_RESPONSE
            )

            // Explicitly switch to the initial mode.
            switchMode(initialMode, runHeartbeat)

            logger(LogLevel.INFO) { "Pump IO connected" }

            _connectionState.value = ConnectionState.CONNECTED
        } catch (e: CancellationException) {
            disconnect()
            throw e
        } catch (t: Throwable) {
            newScopeJob.cancelAndJoin()
            _connectionState.value = ConnectionState.FAILED
            throw t
        }
    }

    /**
     * Disconnects from a pump.
     *
     * This terminates the connection that was set up by [connect].
     *
     * If no connection is running, this does nothing.
     *
     * Calling this ensures an orderly IO shutdown and should not be
     * omitted when shutting down an application.
     */
    suspend fun disconnect() {
        // Make sure that any function that is suspended by this
        // barrier is woken up. Pass "false" to these functions
        // to let them know that they need to abort any loop
        // they might be running.
        rtButtonConfirmationBarrier.trySend(false)

        stopCMDPingHeartbeat()
        stopRTKeepAliveHeartbeat()

        val disconnectPacketInfo = ApplicationLayer.createCTRLDisconnectPacket()
        logger(LogLevel.VERBOSE) { "Will send application layer disconnect packet:  $disconnectPacketInfo" }

        transportLayerIO.stop(
            disconnectPacketInfo.toTransportLayerPacketInfo(),
            ::disconnectBTDeviceAndCatchExceptions
        )

        internalScope = null
        internalScopeJob?.cancelAndJoin()
        internalScopeJob = null
        _currentModeFlow.value = null
        onNewDisplayFrame(null)

        logger(LogLevel.DEBUG) { "Pump IO disconnected" }
    }

    /**
     * Reads the current datetime of the pump in COMMAND (CMD) mode.
     *
     * The current datetime is always given in localtime.
     *
     * @return The current datetime.
     * @throws IllegalStateException if the pump is not in the comand
     *   mode or the pump is not connected.
     * @throws TransportLayer.PacketReceiverException if an exception is thrown
     *   in the packet receiver while this call is waiting for a packet or if
     *   an exception was thrown in the packet receiver prior to this call.
     * @throws ApplicationLayer.InvalidPayloadException if the size
     *   of a packet's payload does not match the expected size.
     * @throws ComboIOException if IO with the pump fails.
     */
    suspend fun readCMDDateTime(): LocalDateTime = runPumpIOCall("get current pump datetime", Mode.COMMAND) {
        val packet = sendPacketWithResponse(
            ApplicationLayer.createCMDReadDateTimePacket(),
            ApplicationLayer.Command.CMD_READ_DATE_TIME_RESPONSE
        )
        return@runPumpIOCall ApplicationLayer.parseCMDReadDateTimeResponsePacket(packet)
    }

    /**
     * Reads the current status of the pump in COMMAND (CMD) mode.
     *
     * The pump can be either in the stopped or in the running status.
     *
     * @return The current status.
     * @throws IllegalStateException if the pump is not in the command
     *   mode or the pump is not connected.
     * @throws TransportLayer.PacketReceiverException if an exception is thrown
     *   in the packet receiver while this call is waiting for a packet or if
     *   an exception was thrown in the packet receiver prior to this call.
     * @throws ApplicationLayer.InvalidPayloadException if the size
     *   of a packet's payload does not match the expected size.
     * @throws ComboIOException if IO with the pump fails.
     */
    suspend fun readCMDPumpStatus(): ApplicationLayer.CMDPumpStatus = runPumpIOCall("get pump status", Mode.COMMAND) {
        val packet = sendPacketWithResponse(
            ApplicationLayer.createCMDReadPumpStatusPacket(),
            ApplicationLayer.Command.CMD_READ_PUMP_STATUS_RESPONSE
        )
        return@runPumpIOCall ApplicationLayer.parseCMDReadPumpStatusResponsePacket(packet)
    }

    /**
     * Reads the current error/warning status of the pump in COMMAND (CMD) mode.
     *
     * @return The current status.
     * @throws IllegalStateException if the pump is not in the comand
     *   mode or the pump is not connected.
     * @throws TransportLayer.PacketReceiverException if an exception is thrown
     *   in the packet receiver while this call is waiting for a packet or if
     *   an exception was thrown in the packet receiver prior to this call.
     * @throws ApplicationLayer.InvalidPayloadException if the size
     *   of a packet's payload does not match the expected size.
     * @throws ComboIOException if IO with the pump fails.
     */
    suspend fun readCMDErrorWarningStatus(): ApplicationLayer.CMDErrorWarningStatus =
        runPumpIOCall("get error/warning status", Mode.COMMAND) {
        val packet = sendPacketWithResponse(
            ApplicationLayer.createCMDReadErrorWarningStatusPacket(),
            ApplicationLayer.Command.CMD_READ_ERROR_WARNING_STATUS_RESPONSE
        )
        return@runPumpIOCall ApplicationLayer.parseCMDReadErrorWarningStatusResponsePacket(packet)
    }

    /**
     * Requests a CMD history delta.
     *
     * In the command mode, the Combo can provide a "history delta".
     * This means that the user can get what events occurred since the
     * last time a request was sent. Because this is essentially the
     * difference between the current history state and the history
     * state when the last request was sent, it is called a "delta".
     * This also means that if a request is sent again, and no new
     * event occurred in the meantime, the history delta will be empty
     * (= it will have zero events recorded). It is _not_ possible
     * to get the entire history with this function.
     *
     * The maximum amount of history block request is limited by the
     * maxRequests argument. This is a safeguard in case the data
     * keeps getting corrupted for some reason. Having a maximum
     * guarantees that we can't get stuck in an infinite loop.
     *
     * @param maxRequests How many history block request we can
     *        maximally send. This must be at least 10.
     * @return The history delta.
     * @throws IllegalArgumentException if maxRequests is less than 10.
     * @throws IllegalStateException if the pump is not in the comand
     *   mode or the pump is not connected.
     * @throws TransportLayer.PacketReceiverException if an exception is thrown
     *   in the packet receiver while this call is waiting for a packet or if
     *   an exception was thrown in the packet receiver prior to this call.
     * @throws ApplicationLayer.InvalidPayloadException if the size
     *   of a packet's payload does not match the expected size.
     * @throws ApplicationLayer.PayloadDataCorruptionException if
     *   packet data integrity is compromised.
     * @throws ApplicationLayer.InfiniteHistoryDataException if the
     *   call did not ever get a history block that marked an end
     *   to the history.
     * @throws ComboIOException if IO with the pump fails.
     */
    suspend fun getCMDHistoryDelta(maxRequests: Int = 40): List<ApplicationLayer.CMDHistoryEvent> {
        require(maxRequests >= 10) { "Maximum amount of requests must be at least 10; caller specified $maxRequests" }

        return runPumpIOCall("get history delta", Mode.COMMAND) {
            val historyDelta = mutableListOf<ApplicationLayer.CMDHistoryEvent>()
            var reachedEnd = false

            // Keep requesting history blocks until we reach the end,
            // and fill historyDelta with the events from each block,
            // skipping those events whose IDs are unknown (this is
            // taken care of by parseCMDReadHistoryBlockResponsePacket()).
            for (requestNr in 1 until maxRequests) {
                // Request the current history block from the Combo.
                val packet = sendPacketWithResponse(
                    ApplicationLayer.createCMDReadHistoryBlockPacket(),
                    ApplicationLayer.Command.CMD_READ_HISTORY_BLOCK_RESPONSE
                )

                // Try to parse and validate the packet data.
                val historyBlock = try {
                    ApplicationLayer.parseCMDReadHistoryBlockResponsePacket(packet)
                } catch (t: Throwable) {
                    logger(LogLevel.ERROR) {
                        "Could not parse history block; data may have been corrupted; requesting the block again (throwable: $t)"
                    }
                    continue
                }

                // Confirm this history block to let the Combo consider
                // it processed. The Combo can then move on to the next
                // history block.
                sendPacketWithResponse(
                    ApplicationLayer.createCMDConfirmHistoryBlockPacket(),
                    ApplicationLayer.Command.CMD_CONFIRM_HISTORY_BLOCK_RESPONSE
                )

                historyDelta.addAll(historyBlock.events)

                // Check if there is a next history block to get.
                // If not, we are done, and need to exit this loop.
                if (!historyBlock.moreEventsAvailable ||
                    (historyBlock.numRemainingEvents <= historyBlock.events.size)
                ) {
                    reachedEnd = true
                    break
                }
            }

            if (!reachedEnd)
                throw ApplicationLayer.InfiniteHistoryDataException(
                    "Did not reach an end of the history event list even after $maxRequests request(s)"
                )

            return@runPumpIOCall historyDelta
        }
    }

    /**
     * Requests the current status of an ongoing bolus delivery.
     *
     * This is used for keeping track of the status of an ongoing bolus.
     * If no bolus is ongoing, the return value's bolusType field is
     * set to [ApplicationLayer.CMDBolusDeliveryState.NOT_DELIVERING].
     *
     * @return The current status.
     * @throws IllegalStateException if the pump is not in the comand
     *   mode or the pump is not connected.
     * @throws TransportLayer.PacketReceiverException if an exception is thrown
     *   in the packet receiver while this call is waiting for a packet or if
     *   an exception was thrown in the packet receiver prior to this call.
     * @throws ApplicationLayer.InvalidPayloadException if the size
     *   of a packet's payload does not match the expected size.
     * @throws ApplicationLayer.DataCorruptionException if some of
     *   the fields in the status data received from the pump
     *   contain invalid values.
     * @throws ComboIOException if IO with the pump fails.
     */
    suspend fun getCMDCurrentBolusDeliveryStatus(): ApplicationLayer.CMDBolusDeliveryStatus =
        runPumpIOCall("get current bolus delivery status", Mode.COMMAND) {

        val packet = sendPacketWithResponse(
            ApplicationLayer.createCMDGetBolusStatusPacket(),
            ApplicationLayer.Command.CMD_GET_BOLUS_STATUS_RESPONSE
        )

        return@runPumpIOCall ApplicationLayer.parseCMDGetBolusStatusResponsePacket(packet)
    }

    /**
     * Instructs the pump to deliver the specified standard bolus amount.
     *
     * As the name suggests, this function can only deliver a standard bolus,
     * and no multi-wave or extended ones. In the future, additional functions
     * may be written that can deliver those.
     *
     * The return value indicates whether the delivery was actually done.
     * The delivery may not happen if for example the pump is currently
     * stopped, or if it is already administering another bolus. It is
     * recommended to keep track of the current bolus status by periodically
     * calling [getCMDCurrentBolusDeliveryStatus].
     *
     * @param bolusAmount Bolus amount to deliver. Note that this is given
     *        in 0.1 IU units, so for example, "57" means 5.7 IU.
     * @return true if the bolus could be delivered, false otherwise.
     * @throws IllegalStateException if the pump is not in the comand
     *   mode or the pump is not connected.
     * @throws TransportLayer.PacketReceiverException if an exception is thrown
     *   in the packet receiver while this call is waiting for a packet or if
     *   an exception was thrown in the packet receiver prior to this call.
     * @throws ApplicationLayer.InvalidPayloadException if the size
     *   of a packet's payload does not match the expected size.
     * @throws ComboIOException if IO with the pump fails.
     */
    suspend fun deliverCMDStandardBolus(totalBolusAmount: Int): Boolean =
        deliverCMDStandardBolus(
            totalBolusAmount,
            immediateBolusAmount = 0,
            durationInMinutes = 0,
            bolusType = ApplicationLayer.CMDDeliverBolusType.STANDARD_BOLUS
        )

    suspend fun deliverCMDStandardBolus(
        totalBolusAmount: Int,
        immediateBolusAmount: Int,
        durationInMinutes: Int,
        bolusType: ApplicationLayer.CMDDeliverBolusType
    ): Boolean =
        runPumpIOCall("deliver standard bolus", Mode.COMMAND) {

        val packet = sendPacketWithResponse(
            ApplicationLayer.createCMDDeliverBolusPacket(
                totalBolusAmount,
                immediateBolusAmount,
                durationInMinutes,
                bolusType
            ),
            ApplicationLayer.Command.CMD_DELIVER_BOLUS_RESPONSE
        )

        return@runPumpIOCall ApplicationLayer.parseCMDDeliverBolusResponsePacket(packet)
    }

    /**
     * Cancels an ongoing bolus.
     *
     * @return true if the bolus was cancelled, false otherwise.
     *   If no bolus is ongoing, this returns false as well.
     * @throws IllegalStateException if the pump is not in the command
     *   mode or the pump is not connected.
     * @throws TransportLayer.PacketReceiverException if an exception is thrown
     *   in the packet receiver while this call is waiting for a packet or if
     *   an exception was thrown in the packet receiver prior to this call.
     */
    suspend fun cancelCMDStandardBolus(): Boolean = runPumpIOCall("cancel bolus", Mode.COMMAND) {
        // TODO: Test that this function does the expected thing
        // when no bolus is actually ongoing.
        val packet = sendPacketWithResponse(
            ApplicationLayer.createCMDCancelBolusPacket(ApplicationLayer.CMDImmediateBolusType.STANDARD),
            ApplicationLayer.Command.CMD_CANCEL_BOLUS_RESPONSE
        )

        return@runPumpIOCall ApplicationLayer.parseCMDCancelBolusResponsePacket(packet)
    }

    /**
     * Performs a short button press.
     *
     * This mimics the physical pressing of buttons for a short
     * moment, followed by those buttons being released.
     *
     * This may not be called while a long button press is ongoing.
     * It can only be called in the remote terminal (RT) mode.
     *
     * It is possible to short-press multiple buttons at the same
     * time. This is necessary for moving back in the Combo's menu
     * example. The buttons in the specified list are combined to
     * form a code that is transmitted to the pump.
     *
     * @param buttons What button(s) to short-press.
     * @throws IllegalArgumentException If the buttons list is empty.
     * @throws IllegalStateException if a long button press is
     *   ongoing, the pump is not in the RT mode, or the
     *   pump is not connected.
     * @throws TransportLayer.PacketReceiverException if an exception is thrown
     *   in the packet receiver while this call is waiting for a packet or if
     *   an exception was thrown in the packet receiver prior to this call.
     */
    suspend fun sendShortRTButtonPress(buttons: List<ApplicationLayer.RTButton>) {
        require(buttons.isNotEmpty()) { "Cannot send short RT button press since the specified buttons list is empty" }
        check(currentLongRTPressJob == null) { "Cannot send short RT button press while a long RT button press is ongoing" }

        runPumpIOCall("send short RT button press", Mode.REMOTE_TERMINAL) {
            val buttonCodes = getCombinedButtonCodes(buttons)
            var delayBeforeNoButton = false
            var ignoreNoButtonError = false

            try {
                withContext(sequencedDispatcher) {
                    sendPacketWithoutResponse(ApplicationLayer.createRTButtonStatusPacket(buttonCodes, true))
                    // Wait by "receiving" a value. We aren't actually interested
                    // in that value, just in receive() suspending this coroutine
                    // until the RT button was confirmed by the Combo.
                    rtButtonConfirmationBarrier.receive()
                }
            } catch (e: CancellationException) {
                delayBeforeNoButton = true
                ignoreNoButtonError = true
                throw e
            } catch (t: Throwable) {
                delayBeforeNoButton = true
                ignoreNoButtonError = true
                logger(LogLevel.ERROR) { "Error thrown during short RT button press: ${t.stackTraceToString()}" }
                throw t
            } finally {
                // Wait 200 milliseconds before sending NO_BUTTON if we reached this
                // location due to an exception. That's because in that case we cannot
                // know if the button confirmation barrier' receive() call was
                // cancelled or not, and we shouldn't send button status packets
                // to the Combo too quickly.
                if (delayBeforeNoButton)
                    delay(TransportLayer.PACKET_SEND_INTERVAL_IN_MS)

                // Make sure we always attempt to send the NO_BUTTON
                // code to finish the short button press, even if
                // an exception is thrown.
                try {
                    sendPacketWithoutResponse(
                        ApplicationLayer.createRTButtonStatusPacket(ApplicationLayer.RTButton.NO_BUTTON.id, true)
                    )
                } catch (t: Throwable) {
                    // The various IO operations in this function need to be viewed as being part
                    // of one big IO activity. That is, the RT button sending above _and_ the
                    // NO_BUTTON transmission here need to be seen as one single IO action. This
                    // IO action can fail at multiple stages. It can fail in the try block in the
                    // beginning of the runPumpIOCall when calling sendPacketWithoutResponse(). Or,
                    // it can fail in the second sendPacketWithoutResponse() call right above
                    // which sends NO_BUTTON to the Combo.
                    // If the first call fails, we ignore errors caused by the second call, since
                    // at this point, we'll forward the first call's exception anyway. In other
                    // words, in that situation, we already know something went wrong, so extra
                    // exceptions from the NO_BUTTON transmission are redundant.
                    // But if the first call succeeds, and instead, the _second_ call (the one which
                    // transmits NO_BUTTON to the Combo) fails, we do _not_ ignore that second call's
                    // exception, since that is the only one we've got at that point. If it were
                    // ignored, the user would never learn that something wrong happened with the IO.
                    // And this is important, since IO errors may require reconnecting to the Combo.
                    // To fix this, only ignore exceptions from the NO_BUTTON transmission if the
                    // ignoreNoButtonError variable is set to true.
                    if (ignoreNoButtonError) {
                        logger(LogLevel.DEBUG) {
                            "Ignoring error that was thrown while sending NO_BUTTON to end short button press; exception: $t"
                        }
                    } else {
                        logger(LogLevel.ERROR) {
                            "Error thrown while sending NO_BUTTON to end short button press; exception ${t.stackTraceToString()}"
                        }
                        throw t
                    }
                }
            }
        }
    }

    /**
     * Performs a short button press.
     *
     * This overload is for convenience in case exactly one button
     * is to be pressed.
     */
    suspend fun sendShortRTButtonPress(button: ApplicationLayer.RTButton) =
        sendShortRTButtonPress(listOf(button))

    /**
     * Starts a long RT button press, imitating buttons being held down.
     *
     * This can only be called in the remote terminal (RT) mode.
     *
     * If a long button press is already ongoing, this function
     * does nothing.
     *
     * It is possible to long-press multiple buttons at the same
     * time. This is necessary for moving back in the Combo's menu
     * example. The buttons in the specified list are combined to
     * form a code that is transmitted to the pump.
     *
     * Internally, a coroutine is launched that repeatedly transmits
     * a confirmation command to the Combo that the buttons are still
     * being held down. This loop continues until either the keepGoing
     * predicate returns true (if that predicate isn't null) or until
     * [stopLongRTButtonPress] is called. In both cases, a command is
     * sent to the Combo to signal that the user "released" the buttons.
     *
     * If the [keepGoing] predicate is set, it is called before sending
     * each confirmation command. This is particularly useful for
     * aborting the loop at just the right time. In the Combo, this
     * command triggers updates associated with the button(s) and the
     * current screen. For example, in the bolus screen, if the UP
     * button is pressed, such a command will cause the bolus amount
     * to be incremented. Therefore, if the code in keepGoing waits
     * for received [DisplayFrame] instances to check their contents
     * before deciding whether to return true or false, it becomes
     * possible to stop the bolus increment at precisely the correct
     * moment (= when the target amount is reached). If however the
     * confirmation commands were sent _too_ quickly, the user would
     * see that the bolus amount is incremented even after "releasing"
     * the button.
     *
     * @param buttons What button(s) to long-press.
     * @param keepGoing Predicate for deciding whether to continue
     *        the internal loop. If this is set to null, the loop
     *        behaves as if this returned true all the time.
     * @throws IllegalArgumentException If the buttons list is empty.
     * @throws IllegalStateException if the pump is not in the RT mode
     *   or the pump is not connected.
     * @throws TransportLayer.PacketReceiverException if an exception is thrown
     *   in the packet receiver while this call is waiting for a packet or if
     *   an exception was thrown in the packet receiver prior to this call.
     */
    suspend fun startLongRTButtonPress(buttons: List<ApplicationLayer.RTButton>, keepGoing: (suspend () -> Boolean)? = null) {
        require(buttons.isNotEmpty()) { "Cannot start long RT button press since the specified buttons list is empty" }

        if (currentLongRTPressJob != null) {
            logger(LogLevel.DEBUG) { "Long RT button press job already running; ignoring redundant call" }
            return
        }

        runPumpIOCall("start long RT button press", Mode.REMOTE_TERMINAL) {
            try {
                issueLongRTButtonPressUpdate(buttons, keepGoing, pressing = true)
            } catch (t: Throwable) {
                stopLongRTButtonPress()
                throw t
            }
        }
    }

    /**
     * Performs a long button press.
     *
     * This overload is for convenience in case exactly one button
     * is to be pressed.
     */
    suspend fun startLongRTButtonPress(button: ApplicationLayer.RTButton, keepGoing: (suspend () -> Boolean)? = null) =
        startLongRTButtonPress(listOf(button), keepGoing)

    suspend fun stopLongRTButtonPress() {
        if (currentLongRTPressJob == null) {
            logger(LogLevel.DEBUG) {
                "No long RT button press job running, and button press state is RELEASED; ignoring redundant call"
            }
            return
        }

        runPumpIOCall("stop long RT button press", Mode.REMOTE_TERMINAL) {
            issueLongRTButtonPressUpdate(listOf(ApplicationLayer.RTButton.NO_BUTTON), keepGoing = null, pressing = false)
        }
    }

    /**
     * Waits for the coroutine that drives the long RT button press loop to finish.
     *
     * This finishes when either the keepAlive predicate in [startLongRTButtonPress]
     * returns false or [stopLongRTButtonPress] is called. The former is the more
     * common use case for this function.
     *
     * If no long RT button press is ongoing, this function does nothing,
     * and just exits immediately.
     *
     * @throws Exception Exceptions that were thrown in the keepGoing callback
     *   that was passed to [startLongRTButtonPress].
     */
    suspend fun waitForLongRTButtonPressToFinish() {
        // currentLongRTPressJob is set to null automatically when the job finishes
        currentLongRTPressJob?.await()
    }

    /**
     * Switches the Combo to a different mode.
     *
     * The two valid modes are the remote terminal (RT) mode and the command mode.
     *
     * If an exception occurs, either disconnect, or try to repeat the mode switch.
     * This is important to make sure the pump is in a known mode.
     *
     * The runHeartbeat argument functions just like the one in [connect].
     * It is necessary because mode switching stops any currently ongoing heartbeat.
     *
     * If the mode specified by newMode is the same as the current mode,
     * this function does nothing.
     *
     * @param newMode Mode to switch to.
     * @param runHeartbeat Whether or not to run the "heartbeat".
     * @throws IllegalStateException if the pump is not connected.
     * @throws TransportLayer.PacketReceiverException if an exception is thrown
     *   inside the packet receiver while this call is waiting for a packet
     *   or if an exception was thrown inside the receiver prior to this call.
     * @throws ComboIOException if IO with the pump fails.
     */
    suspend fun switchMode(newMode: Mode, runHeartbeat: Boolean = true) = withContext(NonCancellable) {
        // This function is in a NonCancellable context to avoid undefined behavior
        // if cancellation occurs during mode change.

        check(isIORunning()) { "Cannot switch mode because the pump is not connected" }

        if (_currentModeFlow.value == newMode)
            return@withContext

        try {
            logger(LogLevel.DEBUG) { "Switching mode from ${_currentModeFlow.value} to $newMode" }

            stopCMDPingHeartbeat()
            stopRTKeepAliveHeartbeat()

            // Inform the callback that there's no frame available after the
            // switch. This is particularly important when switching from the
            // RT to the command mode.
            onNewDisplayFrame(null)

            // Do the entire mode switch with the lock held and do it inside a NonCancellable
            // context. We must make sure that _nothing_ else is communicated with the
            // Combo during the mode switch. Using these blocks guarantees that. That's why
            // we don't use sendPacketWithResponse() here and instead handle this manually.
            sendPacketMutex.withLock {
                withContext(NonCancellable) {
                    _currentModeFlow.value?.let { modeToDeactivate ->
                        logger(LogLevel.DEBUG) { "Deactivating current service" }
                        sendAppLayerPacket(
                            ApplicationLayer.createCTRLDeactivateServicePacket(
                                when (modeToDeactivate) {
                                    Mode.REMOTE_TERMINAL -> ApplicationLayer.ServiceID.RT_MODE
                                    Mode.COMMAND -> ApplicationLayer.ServiceID.COMMAND_MODE
                                }
                            )
                        )
                        logger(LogLevel.DEBUG) { "Sent CTRL_DEACTIVATE packet; waiting for CTRL_DEACTIVATE_SERVICE_RESPONSE packet" }
                        val receivedAppLayerPacket = transportLayerIO.receive(TransportLayer.Command.DATA).toAppLayerPacket()
                        if (receivedAppLayerPacket.command != ApplicationLayer.Command.CTRL_DEACTIVATE_SERVICE_RESPONSE) {
                            throw ApplicationLayer.IncorrectPacketException(
                                receivedAppLayerPacket,
                                ApplicationLayer.Command.CTRL_DEACTIVATE_SERVICE_RESPONSE
                            )
                        }
                    }

                    logger(LogLevel.DEBUG) { "Activating new service" }
                    sendAppLayerPacket(
                        ApplicationLayer.createCTRLActivateServicePacket(
                            when (newMode) {
                                Mode.REMOTE_TERMINAL -> ApplicationLayer.ServiceID.RT_MODE
                                Mode.COMMAND -> ApplicationLayer.ServiceID.COMMAND_MODE
                            }
                        )
                    )
                    logger(LogLevel.DEBUG) { "Sent CTRL_ACTIVATE packet; waiting for CTRL_ACTIVATE_SERVICE_RESPONSE packet" }
                    var receivedAppLayerPacket = transportLayerIO.receive(TransportLayer.Command.DATA).toAppLayerPacket()

                    // XXX: In a few cases, we get this response instead. This seems to be a Combo bug -
                    // an extra CTRL_DEACTIVATE_SERVICE_RESPONSE packet is inserted before the actual
                    // response. The workaround appears to be to read and drop that extra response packet
                    // and then proceed as usual (since correct response packets follow that one).
                    if (receivedAppLayerPacket.command == ApplicationLayer.Command.CTRL_DEACTIVATE_SERVICE_RESPONSE) {
                        logger(LogLevel.INFO) {
                            "Got CTRL_DEACTIVATE_SERVICE_RESPONSE packet even though CTRL_ACTIVATE_SERVICE_RESPONSE was expected; " +
                            "suspected to be a Combo bug; trying to receive packet again as a workaround"
                        }
                        // Retry receiving.
                        receivedAppLayerPacket = transportLayerIO.receive(TransportLayer.Command.DATA).toAppLayerPacket()
                    }

                    if (receivedAppLayerPacket.command != ApplicationLayer.Command.CTRL_ACTIVATE_SERVICE_RESPONSE) {
                        throw ApplicationLayer.IncorrectPacketException(
                            receivedAppLayerPacket,
                            ApplicationLayer.Command.CTRL_ACTIVATE_SERVICE_RESPONSE
                        )
                    }
                }
            }

            _currentModeFlow.value = newMode

            if (runHeartbeat) {
                logger(LogLevel.DEBUG) { "Resetting heartbeat" }
                when (newMode) {
                    Mode.COMMAND -> startCMDPingHeartbeat()
                    Mode.REMOTE_TERMINAL -> startRTKeepAliveHeartbeat()
                }
            }
        } catch (t: Throwable) {
            _connectionState.value = ConnectionState.FAILED
            throw t
        }
    }

    /**
     * Run a block with the heartbeat disabled, and enable the heartbeat again once the block is finished.
     *
     * If no heartbeat was running prior to the block invocation, this does not enable the heartbeat.
     *
     * @param block Block to run without a heartbeat.
     */
    suspend fun runWithoutHeartbeat(block: (suspend () -> Unit)) {
        val mode = _currentModeFlow.value
        when (mode) {
            Mode.COMMAND -> stopCMDPingHeartbeat()
            Mode.REMOTE_TERMINAL -> stopRTKeepAliveHeartbeat()
            else -> Unit
        }

        block()

        when (mode) {
            Mode.COMMAND -> startCMDPingHeartbeat()
            Mode.REMOTE_TERMINAL -> startRTKeepAliveHeartbeat()
            else -> Unit
        }
    }

    /*************************************
     *** PRIVATE FUNCTIONS AND CLASSES ***
     *************************************/

    private fun isIORunning() = transportLayerIO.isIORunning()

    private fun newRtButtonConfirmationBarrier() =
        Channel<Boolean>(capacity = Channel.CONFLATED)

    private fun getCombinedButtonCodes(buttons: List<ApplicationLayer.RTButton>) =
        buttons.fold(0) { codes, button -> codes or button.id }

    private fun toString(buttons: List<ApplicationLayer.RTButton>) = buttons.joinToString(" ") { it.str }

    // The sendPacketWithResponse and sendPacketWithoutResponse calls
    // are surrounded by a sendPacketMutex lock to prevent these functions
    // from being called concurrently. This is essential, since the Combo
    // cannot handle such concurrent calls. In particular, if a command
    // that is sent to the Combo will cause the pump to respond with
    // another command, we must make sure that we receive the response
    // _before_ sending another command to the pump. (The main potential
    // cause of concurrent send calls are the heartbeat coroutines.)
    //
    // Note that these functions use a coroutine mutex, not a "classical",
    // thread level mutex. See kotlinx.coroutines.sync.Mutex for details.
    //
    // Furthermore, these use the NonCancellable context to prevent the
    // prompt cancellation guarantee from cancelling any send attempts.

    private suspend fun sendPacketWithResponse(
        tpLayerPacketInfo: TransportLayer.OutgoingPacketInfo,
        expectedResponseCommand: TransportLayer.Command? = null
    ): TransportLayer.Packet = sendPacketMutex.withLock {
        return withContext(NonCancellable) {
            transportLayerIO.send(tpLayerPacketInfo)
            transportLayerIO.receive(expectedResponseCommand)
        }
    }

    private suspend fun sendPacketWithResponse(
        appLayerPacketToSend: ApplicationLayer.Packet,
        expectedResponseCommand: ApplicationLayer.Command? = null,
        doRestartHeartbeat: Boolean = true
    ): ApplicationLayer.Packet = sendPacketMutex.withLock {
        return withContext(NonCancellable) {
            if (doRestartHeartbeat)
                restartHeartbeat()

            sendAppLayerPacket(appLayerPacketToSend)

            logger(LogLevel.VERBOSE) {
                if (expectedResponseCommand == null)
                    "Waiting for application layer packet (will arrive in a transport layer DATA packet)"
                else
                    "Waiting for application layer ${expectedResponseCommand.name} " +
                    "packet (will arrive in a transport layer DATA packet)"
            }

            val receivedAppLayerPacket = transportLayerIO.receive(TransportLayer.Command.DATA).toAppLayerPacket()

            if ((expectedResponseCommand != null) && (receivedAppLayerPacket.command != expectedResponseCommand))
                throw ApplicationLayer.IncorrectPacketException(receivedAppLayerPacket, expectedResponseCommand)

            receivedAppLayerPacket
        }
    }

    private suspend fun sendPacketWithoutResponse(
        tpLayerPacketInfo: TransportLayer.OutgoingPacketInfo
    ) = sendPacketMutex.withLock {
        withContext(NonCancellable) {
            transportLayerIO.send(tpLayerPacketInfo)
        }
    }

    private suspend fun sendPacketWithoutResponse(
        appLayerPacketToSend: ApplicationLayer.Packet,
        doRestartHeartbeat: Boolean = true
    ) = sendPacketMutex.withLock {
        withContext(NonCancellable) {
            if (doRestartHeartbeat)
                restartHeartbeat()
            sendAppLayerPacket(appLayerPacketToSend)
        }
    }

    private suspend fun sendAppLayerPacket(appLayerPacket: ApplicationLayer.Packet) {
        // NOTE: This function does NOT lock a mutex and does NOT use
        // NonCancellable. Make sure to set these up before calling this.
        check(sendPacketMutex.isLocked)

        logger(LogLevel.VERBOSE) {
            "Sending application layer packet via transport layer:  $appLayerPacket"
        }

        val outgoingPacketInfo = appLayerPacket.toTransportLayerPacketInfo()

        if (appLayerPacket.command.serviceID == ApplicationLayer.ServiceID.RT_MODE) {
            if (outgoingPacketInfo.payload.size < (ApplicationLayer.PAYLOAD_BYTES_OFFSET + 2)) {
                throw ApplicationLayer.InvalidPayloadException(
                    appLayerPacket,
                    "Cannot send application layer RT packet since there's no room in the payload for the RT sequence number"
                )
            }

            logger(LogLevel.VERBOSE) { "Writing current RT sequence number $currentRTSequence into packet" }

            // The RT sequence is always stored in the
            // first 2 bytes  of an RT packet's payload.
            //
            // Also, we set the RT sequence in the outgoingPacketInfo,
            // and not in appLayerPacket's payload, since the latter
            // is a function argument, and modifying the payload of
            // an outside value may lead to confusing behavior.
            // By writing the RT sequence into outgoingPacketInfo
            // instead, that change stays contained in here.
            outgoingPacketInfo.payload[ApplicationLayer.PAYLOAD_BYTES_OFFSET + 0] =
                ((currentRTSequence shr 0) and 0xFF).toByte()
            outgoingPacketInfo.payload[ApplicationLayer.PAYLOAD_BYTES_OFFSET + 1] =
                ((currentRTSequence shr 8) and 0xFF).toByte()

            // After using the RT sequence, increment it to
            // make sure the next RT packet doesn't use the
            // same RT sequence.
            currentRTSequence++
            if (currentRTSequence > 65535)
                currentRTSequence = 0
        }

        transportLayerIO.send(outgoingPacketInfo)
    }

    private fun processReceivedPacket(tpLayerPacket: TransportLayer.Packet) =
        if (tpLayerPacket.command == TransportLayer.Command.DATA) {
            when (ApplicationLayer.extractAppLayerPacketCommand(tpLayerPacket)) {
                ApplicationLayer.Command.CTRL_ACTIVATE_SERVICE_RESPONSE -> {
                    logger(LogLevel.DEBUG) { "New service was activated; resetting RT sequence number" }
                    currentRTSequence = 0
                    TransportLayer.IO.ReceiverBehavior.FORWARD_PACKET
                }

                ApplicationLayer.Command.RT_DISPLAY -> {
                    processRTDisplayPayload(
                        ApplicationLayer.parseRTDisplayPacket(tpLayerPacket.toAppLayerPacket())
                    )
                    // Signal the arrival of the button confirmation.
                    // (Either RT_BUTTON_CONFIRMATION or RT_DISPLAY
                    // function as confirmations.) Transmit "true"
                    // to let the receivers know that everything
                    // is OK and that they don't need to abort.
                    rtButtonConfirmationBarrier.trySend(true)
                    TransportLayer.IO.ReceiverBehavior.DROP_PACKET
                }

                ApplicationLayer.Command.RT_BUTTON_CONFIRMATION -> {
                    logger(LogLevel.VERBOSE) { "Got RT_BUTTON_CONFIRMATION packet from the Combo" }
                    // Signal the arrival of the button confirmation.
                    // (Either RT_BUTTON_CONFIRMATION or RT_DISPLAY
                    // function as confirmations.) Transmit "true"
                    // to let the receivers know that everything
                    // is OK and that they don't need to abort.
                    rtButtonConfirmationBarrier.trySend(true)
                    TransportLayer.IO.ReceiverBehavior.DROP_PACKET
                }

                // We do not care about keep-alive packets from the Combo.
                ApplicationLayer.Command.RT_KEEP_ALIVE -> {
                    logger(LogLevel.VERBOSE) { "Got RT_KEEP_ALIVE packet from the Combo; ignoring" }
                    TransportLayer.IO.ReceiverBehavior.DROP_PACKET
                }

                // RT_AUDIO, RT_PAUSE, RT_RELEASE, RT_VIBRATION packets
                // are purely for information. We just log them and
                // otherwise ignore them.

                ApplicationLayer.Command.RT_AUDIO -> {
                    logger(LogLevel.VERBOSE) {
                        val audioType = ApplicationLayer.parseRTAudioPacket(tpLayerPacket.toAppLayerPacket())
                        "Got RT_AUDIO packet with audio type ${audioType.toHexString(8)}; ignoring"
                    }
                    TransportLayer.IO.ReceiverBehavior.DROP_PACKET
                }

                ApplicationLayer.Command.RT_PAUSE,
                ApplicationLayer.Command.RT_RELEASE -> {
                    logger(LogLevel.VERBOSE) {
                        "Got ${ApplicationLayer.Command} packet with payload " +
                                "${tpLayerPacket.toAppLayerPacket().payload.toHexString()}; ignoring"
                    }
                    TransportLayer.IO.ReceiverBehavior.DROP_PACKET
                }

                ApplicationLayer.Command.RT_VIBRATION -> {
                    logger(LogLevel.VERBOSE) {
                        val vibrationType = ApplicationLayer.parseRTVibrationPacket(
                            tpLayerPacket.toAppLayerPacket()
                        )
                        "Got RT_VIBRATION packet with vibration type ${vibrationType.toHexString(8)}; ignoring"
                    }
                    TransportLayer.IO.ReceiverBehavior.DROP_PACKET
                }

                // This is an information by the pump that something is wrong
                // with the connection / with the service. This error is
                // not recoverable. Throw an exception here to let the
                // packet receiver fail. It will forward the exception to
                // any ongoing send and receive calls.
                ApplicationLayer.Command.CTRL_SERVICE_ERROR -> {
                    val appLayerPacket = tpLayerPacket.toAppLayerPacket()
                    val ctrlServiceError = ApplicationLayer.parseCTRLServiceErrorPacket(appLayerPacket)
                    logger(LogLevel.ERROR) { "Got CTRL_SERVICE_ERROR packet from the Combo; throwing exception" }
                    throw ApplicationLayer.ServiceErrorException(appLayerPacket, ctrlServiceError)
                }

                else -> TransportLayer.IO.ReceiverBehavior.FORWARD_PACKET
            }
        } else
            TransportLayer.IO.ReceiverBehavior.FORWARD_PACKET

    private fun processRTDisplayPayload(rtDisplayPayload: ApplicationLayer.RTDisplayPayload) {
        // Feed the payload to the display frame assembler to let it piece together
        // frames and output them via the callback.

        try {
            val displayFrame = displayFrameAssembler.processRTDisplayPayload(
                rtDisplayPayload.index,
                rtDisplayPayload.row,
                rtDisplayPayload.rowBytes
            )
            if (displayFrame != null)
                onNewDisplayFrame(displayFrame)
        } catch (t: Throwable) {
            logger(LogLevel.ERROR) { "Could not process RT_DISPLAY payload: $t" }
            throw t
        }
    }

    private fun isCMDPingHeartbeatRunning() = (cmdPingHeartbeatJob != null)

    private fun startCMDPingHeartbeat() {
        if (isCMDPingHeartbeatRunning())
            return

        logger(LogLevel.VERBOSE) { "Starting background CMD ping heartbeat" }

        require(internalScope != null)

        cmdPingHeartbeatJob = internalScope!!.launch {
            // In command mode, if no command has been sent to the Combo
            // within about 1-1.5 seconds, the Combo terminates the
            // connection, assuming that the client is gone. As a
            // consequence, we have to send an CMD_PING packet
            // to the Combo after a second.
            //
            // It is possible to prevent these packets from being
            // sent when other commands were sent. To that end, if
            // a command is to be sent, restartHeartbeat() can be
            // called to effectively reset this timeout back to
            // one second. If command-mode commands are sent frequently,
            // this causes the timeout to be constantly reset, and the
            // CMD_PING packet isn't sent until no more command-mode
            // commands are sent.
            //
            // Also note that in here, we use sendPacketWithResponse()
            // with doRestartHeartbeat set to false. The reason for this
            // is that otherwise, the sendPacketWithResponse() function
            // would internally call restartHeartbeat(), and doing that
            // here would cause an infinite loop (and make no sense).
            while (true) {
                // *First* wait, and only *afterwards* send the
                // CMD_PING packet. This order is important, since
                // otherwise, an CMD_PING packet would be sent out
                // immediately, and thus we would not have the timeout
                // behavior described above.
                delay(1000)
                logger(LogLevel.VERBOSE) { "Transmitting CMD ping packet" }
                try {
                    sendPacketWithResponse(
                        ApplicationLayer.createCMDPingPacket(),
                        ApplicationLayer.Command.CMD_PING_RESPONSE,
                        doRestartHeartbeat = false
                    )
                } catch (e: CancellationException) {
                    cmdPingHeartbeatJob = null
                    throw e
                } catch (e: TransportLayer.PacketReceiverException) {
                    logger(LogLevel.ERROR) {
                        "Could not send CMD ping packet because packet receiver failed - stopping CMD ping heartbeat"
                    }
                    cmdPingHeartbeatJob = null
                    break
                } catch (t: Throwable) {
                    logger(LogLevel.ERROR) {
                        "Error caught when attempting to transmit CMD ping packet - stopping CMD ping heartbeat"
                    }
                    logger(LogLevel.ERROR) {
                        "Error: ${t.stackTraceToString()}"
                    }
                    cmdPingHeartbeatJob = null
                    break
                }
            }
        }
    }

    private suspend fun stopCMDPingHeartbeat() {
        if (!isCMDPingHeartbeatRunning())
            return

        logger(LogLevel.VERBOSE) { "Stopping background CMD ping heartbeat" }

        cmdPingHeartbeatJob?.cancelAndJoin()
        cmdPingHeartbeatJob = null

        logger(LogLevel.VERBOSE) { "Background CMD ping heartbeat stopped" }
    }

    private fun isRTKeepAliveHeartbeatRunning() = (rtKeepAliveHeartbeatJob != null)

    private fun startRTKeepAliveHeartbeat() {
        if (isRTKeepAliveHeartbeatRunning())
            return

        logger(LogLevel.VERBOSE) { "Starting background RT keep-alive heartbeat" }

        require(internalScope != null)

        rtKeepAliveHeartbeatJob = internalScope!!.launch {
            // In RT mode, if no RT command has been sent to the Combo
            // within about 1-1.5 seconds, the Combo terminates the
            // connection, assuming that the client is gone. As a
            // consequence, we have to send an RT_KEEP_ALIVE packet
            // to the Combo after a second.
            //
            // It is possible to prevent these packets from being
            // sent when other RT commands were sent. To that end, if
            // an RT command is to be sent, restartHeartbeat() can be
            // called to effectively reset this timeout back to
            // one second. If RT commands are sent frequently, this
            // causes the timeout to be constantly reset, and the
            // RT_KEEP_ALIVE packet isn't sent until no more RT
            // commands are sent.
            //
            // Also note that in here, we use sendPacketWithoutResponse()
            // with doRestartHeartbeat set to false. The reason for this
            // is that otherwise, the sendPacketWithoutResponse() function
            // would internally call restartHeartbeat(), and doing that
            // here would cause an infinite loop (and make no sense).
            while (true) {
                // *First* wait, and only *afterwards* send the
                // RT_KEEP_ALIVE packet. This order is important, since
                // otherwise, an RT_KEEP_ALIVE packet would be sent out
                // immediately, and thus we would not have the timeout
                // behavior described above.
                delay(1000)
                logger(LogLevel.VERBOSE) { "Transmitting RT keep-alive packet" }
                try {
                    sendPacketWithoutResponse(
                        ApplicationLayer.createRTKeepAlivePacket(),
                        doRestartHeartbeat = false
                    )
                } catch (e: CancellationException) {
                    rtKeepAliveHeartbeatJob = null
                    throw e
                } catch (e: TransportLayer.PacketReceiverException) {
                    logger(LogLevel.ERROR) {
                        "Could not send RT keep-alive packet because packet receiver failed - stopping RT keep-alive heartbeat"
                    }
                    rtKeepAliveHeartbeatJob = null
                    break
                } catch (t: Throwable) {
                    logger(LogLevel.ERROR) {
                        "Error caught when attempting to transmit RT keep-alive packet - stopping RT keep-alive heartbeat"
                    }
                    logger(LogLevel.ERROR) {
                        "Error: ${t.stackTraceToString()}"
                    }
                    rtKeepAliveHeartbeatJob = null
                    break
                }
            }
        }
    }

    private suspend fun stopRTKeepAliveHeartbeat() {
        if (!isRTKeepAliveHeartbeatRunning())
            return

        logger(LogLevel.VERBOSE) { "Stopping background RT keep-alive heartbeat" }

        rtKeepAliveHeartbeatJob!!.cancelAndJoin()
        rtKeepAliveHeartbeatJob = null

        logger(LogLevel.VERBOSE) { "Background RT keep-alive heartbeat stopped" }
    }

    private suspend fun restartHeartbeat() {
        when (currentModeFlow.value) {
            Mode.REMOTE_TERMINAL -> {
                if (isRTKeepAliveHeartbeatRunning()) {
                    stopRTKeepAliveHeartbeat()
                    startRTKeepAliveHeartbeat()
                }
            }

            Mode.COMMAND -> {
                if (isCMDPingHeartbeatRunning()) {
                    stopCMDPingHeartbeat()
                    startCMDPingHeartbeat()
                }
            }

            // This happens during pairing and connecting, when CTRL packets
            // are sent via sendPacketWithResponse() calls. These calls in
            // turn call this function, but there is no defined heartbeat
            // in the control mode (which is only used to set up pairing
            // and a connection). Just don't do anything in that case.
            null -> Unit
        }
    }

    private suspend fun issueLongRTButtonPressUpdate(
        buttons: List<ApplicationLayer.RTButton>,
        keepGoing: (suspend () -> Boolean)?,
        pressing: Boolean
    ) {
        if (!pressing) {
            logger(LogLevel.DEBUG) {
                "Releasing RTs button(s) ${toString(currentLongRTPressedButtons)}"
            }

            // Set this to false to stop the long RT button press.
            longRTPressLoopRunning = false

            // Wait for job completion by using await(). This will
            // also re-throw any exceptions caught in that coroutine.
            // In cases where connection to the pump fails, and no
            // confirmation can be received anymore, this is still
            // woken up, because in tha case, this channel is closed.
            // See the transportLayerIO initialization above.
            currentLongRTPressJob?.await()

            return
        }

        currentLongRTPressedButtons = buttons
        val buttonCodes = getCombinedButtonCodes(buttons)
        longRTPressLoopRunning = true

        var delayBeforeNoButton = false
        var ignoreNoButtonError = false

        currentLongRTPressJob = internalScope!!.async {
            try {
                // First time, we send the button status with
                // the CHANGED status and with the codes for
                // the pressed buttons.
                var buttonStatusChanged = true

                while (longRTPressLoopRunning) {
                    // If there is a keepGoing predicate, call it _before_ sending
                    // a button status packet in case keepGoing() wishes to abort
                    // this loop already in its first iteration (for example, because
                    // a quantity that is shown on-screen is already correct).
                    if (keepGoing != null) {
                        try {
                            if (!keepGoing()) {
                                logger(LogLevel.DEBUG) { "Aborting long RT button press flow" }
                                break
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            logger(LogLevel.DEBUG) { "keepGoing callback threw error: $t" }
                            throw t
                        }
                    }

                    // Dummy tryReceive() call to clear out the barrier in case it isn't empty.
                    rtButtonConfirmationBarrier.tryReceive()

                    logger(LogLevel.DEBUG) {
                        "Sending long RT button press; button(s) = ${toString(buttons)} status changed = $buttonStatusChanged"
                    }

                    // Send the button status. This triggers an update on the Combo's
                    // remote terminal screen. For example, when pressing UP to
                    // increment a quantity, said quantity is incremented only
                    // after the Combo receives this status.
                    sendPacketWithoutResponse(
                        ApplicationLayer.createRTButtonStatusPacket(buttonCodes, buttonStatusChanged)
                    )

                    // Wait for the Combo to send us a button
                    // confirmation. We cannot send more button
                    // status commands until then.
                    logger(LogLevel.DEBUG) { "Waiting for button confirmation" }
                    val canContinue = rtButtonConfirmationBarrier.receive()
                    logger(LogLevel.DEBUG) { "Got button confirmation; canContinue = $canContinue" }

                    if (!canContinue)
                        break

                    // The next time we send the button status, we must
                    // send NOT_CHANGED to the Combo.
                    buttonStatusChanged = false
                }
            } catch (e: CancellationException) {
                delayBeforeNoButton = true
                ignoreNoButtonError = true
                throw e
            } catch (t: Throwable) {
                delayBeforeNoButton = true
                ignoreNoButtonError = true
                logger(LogLevel.ERROR) { "Error thrown during long RT button press: ${t.stackTraceToString()}" }
                throw t
            } finally {
                logger(LogLevel.DEBUG) { "Ending long RT button press by sending NO_BUTTON" }
                try {
                    // Call sendPacketWithoutResponse() and delay() in a NonCancellable
                    // context to circumvent the prompt cancellation guarantee (it is
                    // undesirable here because we need to let the Combo know that we
                    // want to stop the long RT button press).
                    withContext(NonCancellable) {
                        // Wait 200 milliseconds before sending NO_BUTTON if we reached this
                        // location due to an exception. That's because in that case we cannot
                        // know if the button confirmation barrier' receive() call was
                        // cancelled or not, and we shouldn't send button status packets
                        // to the Combo too quickly.
                        if (delayBeforeNoButton)
                            delay(200L)

                        sendPacketWithoutResponse(
                            ApplicationLayer.createRTButtonStatusPacket(
                                ApplicationLayer.RTButton.NO_BUTTON.id,
                                buttonStatusChanged = true
                            )
                        )
                    }
                } catch (t: Throwable) {
                    // See the explanation inside sendShortRTButtonPress()
                    // for details why this logic is needed.
                    if (ignoreNoButtonError) {
                        logger(LogLevel.DEBUG) {
                            "Ignoring error that was thrown while sending NO_BUTTON to end long button press; exception: $t"
                        }
                    } else {
                        logger(LogLevel.ERROR) {
                            "Error thrown while sending NO_BUTTON to end long button press; exception ${t.stackTraceToString()}"
                        }
                        throw t
                    }
                }

                currentLongRTPressJob = null
            }
        }
    }

    private data class KeyResponseInfo(val pumpClientCipher: Cipher, val clientPumpCipher: Cipher, val keyResponseAddress: Byte)

    private fun processKeyResponsePacket(packet: TransportLayer.Packet, weakCipher: Cipher): KeyResponseInfo {
        if (packet.payload.size != (CIPHER_KEY_SIZE * 2))
            throw TransportLayer.InvalidPayloadException(packet, "Expected ${CIPHER_KEY_SIZE * 2} bytes, got ${packet.payload.size}")

        val encryptedPCKey = ByteArray(CIPHER_KEY_SIZE)
        val encryptedCPKey = ByteArray(CIPHER_KEY_SIZE)

        for (i in 0 until CIPHER_KEY_SIZE) {
            encryptedPCKey[i] = packet.payload[i + 0]
            encryptedCPKey[i] = packet.payload[i + CIPHER_KEY_SIZE]
        }

        val pumpClientCipher = Cipher(weakCipher.decrypt(encryptedPCKey))
        val clientPumpCipher = Cipher(weakCipher.decrypt(encryptedCPKey))

        // Note: Source and destination addresses are reversed,
        // since they are set from the perspective of the pump.
        val addressInt = packet.address.toPosInt()
        val sourceAddress = addressInt and 0xF
        val destinationAddress = (addressInt shr 4) and 0xF
        val keyResponseAddress = ((sourceAddress shl 4) or destinationAddress).toByte()

        // We begin setting up the invariant pump data here. However,
        // the pump state store cannot be initialized yet, because
        // we do not yet know the pump ID. This initialization continues
        // in processIDResponsePacket(). We fill cachedInvariantPumpData
        // with the data we currently know. Later, it is filled again,
        // and the remaining unknown data is also added.

        return KeyResponseInfo(
            pumpClientCipher = pumpClientCipher,
            clientPumpCipher = clientPumpCipher,
            keyResponseAddress = keyResponseAddress
        )
    }

    private fun processIDResponsePacket(packet: TransportLayer.Packet): String {
        if (packet.payload.size != 17)
            throw TransportLayer.InvalidPayloadException(packet, "Expected 17 bytes, got ${packet.payload.size}")

        val serverID = ((packet.payload[0].toPosLong() shl 0) or
                (packet.payload[1].toPosLong() shl 8) or
                (packet.payload[2].toPosLong() shl 16) or
                (packet.payload[3].toPosLong() shl 24))

        // The pump ID string can be up to 13 bytes long. If it
        // is shorter, the unused bytes are filled with nullbytes.
        val pumpIDStrBuilder = StringBuilder()
        for (i in 0 until 13) {
            val pumpIDByte = packet.payload[4 + i]
            if (pumpIDByte == 0.toByte()) break
            else pumpIDStrBuilder.append(pumpIDByte.toInt().toChar())
        }
        val pumpID = pumpIDStrBuilder.toString()

        logger(LogLevel.DEBUG) {
            "Received IDs: server ID: $serverID pump ID: $pumpID"
        }

        return pumpID
    }

    private suspend fun <T> runPumpIOCall(
        commandDesc: String,
        expectedMode: Mode,
        block: suspend () -> T
    ): T {
        check(isIORunning()) {
            "Cannot $commandDesc because the pump is not connected"
        }
        check(_currentModeFlow.value == expectedMode) {
            "Cannot $commandDesc while being in ${_currentModeFlow.value} mode"
        }

        try {
            return block()
        } catch (t: Throwable) {
            _connectionState.value = ConnectionState.FAILED
            throw t
        }
    }

    private suspend fun disconnectBTDeviceAndCatchExceptions() {
        // Disconnect the Bluetooth device and catch exceptions.
        // disconnectBTDeviceAndCatchExceptions() is a function that gets called
        // in catch and finally blocks, so propagating exceptions
        // here would only complicate matters, because disconnect()
        // gets called in catch blocks.
        try {
            // Use a NonCancellable context in case we are here because
            // the performPairing or connectAsync coroutine got cancelled.
            withContext(bluetoothDevice.ioDispatcher + NonCancellable) {
                bluetoothDevice.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logger(LogLevel.ERROR) {
                "Error occurred during Bluetooth device disconnect; not propagating; error: $t"
            }
        }
    }
}
