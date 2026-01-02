package app.aaps.pump.insight.connection_service

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.AppLayerMessage.Companion.unwrap
import app.aaps.pump.insight.app_layer.AppLayerMessage.Companion.wrap
import app.aaps.pump.insight.app_layer.ReadParameterBlockMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.app_layer.configuration.CloseConfigurationWriteSessionMessage
import app.aaps.pump.insight.app_layer.configuration.OpenConfigurationWriteSessionMessage
import app.aaps.pump.insight.app_layer.configuration.WriteConfigurationBlockMessage
import app.aaps.pump.insight.app_layer.connection.ActivateServiceMessage
import app.aaps.pump.insight.app_layer.connection.BindMessage
import app.aaps.pump.insight.app_layer.connection.ConnectMessage
import app.aaps.pump.insight.app_layer.connection.DisconnectMessage
import app.aaps.pump.insight.app_layer.connection.ServiceChallengeMessage
import app.aaps.pump.insight.app_layer.parameter_blocks.SystemIdentificationBlock
import app.aaps.pump.insight.app_layer.status.GetFirmwareVersionsMessage
import app.aaps.pump.insight.descriptors.FirmwareVersions
import app.aaps.pump.insight.descriptors.InsightState
import app.aaps.pump.insight.descriptors.SystemIdentification
import app.aaps.pump.insight.exceptions.ConnectionFailedException
import app.aaps.pump.insight.exceptions.ConnectionLostException
import app.aaps.pump.insight.exceptions.DisconnectedException
import app.aaps.pump.insight.exceptions.InsightException
import app.aaps.pump.insight.exceptions.InvalidNonceException
import app.aaps.pump.insight.exceptions.InvalidSatlCommandException
import app.aaps.pump.insight.exceptions.ReceivedPacketInInvalidStateException
import app.aaps.pump.insight.exceptions.TimeoutException
import app.aaps.pump.insight.exceptions.TooChattyPumpException
import app.aaps.pump.insight.exceptions.satl_errors.SatlCompatibleStateErrorException
import app.aaps.pump.insight.exceptions.satl_errors.SatlDecryptVerifyFailedErrorException
import app.aaps.pump.insight.exceptions.satl_errors.SatlIncompatibleVersionErrorException
import app.aaps.pump.insight.exceptions.satl_errors.SatlInvalidCRCErrorException
import app.aaps.pump.insight.exceptions.satl_errors.SatlInvalidCommIdErrorException
import app.aaps.pump.insight.exceptions.satl_errors.SatlInvalidMacErrorException
import app.aaps.pump.insight.exceptions.satl_errors.SatlInvalidMessageTypeErrorException
import app.aaps.pump.insight.exceptions.satl_errors.SatlInvalidNonceErrorException
import app.aaps.pump.insight.exceptions.satl_errors.SatlInvalidPacketErrorException
import app.aaps.pump.insight.exceptions.satl_errors.SatlInvalidPayloadLengthErrorException
import app.aaps.pump.insight.exceptions.satl_errors.SatlNoneErrorException
import app.aaps.pump.insight.exceptions.satl_errors.SatlPairingRejectedException
import app.aaps.pump.insight.exceptions.satl_errors.SatlUndefinedErrorException
import app.aaps.pump.insight.exceptions.satl_errors.SatlWrongStateException
import app.aaps.pump.insight.keys.InsightIntKey
import app.aaps.pump.insight.satl.ConnectionRequest
import app.aaps.pump.insight.satl.ConnectionResponse
import app.aaps.pump.insight.satl.DataMessage
import app.aaps.pump.insight.satl.ErrorMessage
import app.aaps.pump.insight.satl.KeyRequest
import app.aaps.pump.insight.satl.KeyResponse
import app.aaps.pump.insight.satl.PairingStatus
import app.aaps.pump.insight.satl.SatlError
import app.aaps.pump.insight.satl.SatlMessage
import app.aaps.pump.insight.satl.SatlMessage.Companion.deserialize
import app.aaps.pump.insight.satl.SatlMessage.Companion.hasCompletePacket
import app.aaps.pump.insight.satl.SynAckResponse
import app.aaps.pump.insight.satl.SynRequest
import app.aaps.pump.insight.satl.VerifyConfirmRequest
import app.aaps.pump.insight.satl.VerifyConfirmResponse
import app.aaps.pump.insight.satl.VerifyDisplayRequest
import app.aaps.pump.insight.satl.VerifyDisplayResponse
import app.aaps.pump.insight.utils.ByteBuf
import app.aaps.pump.insight.utils.ConnectionEstablisher
import app.aaps.pump.insight.utils.DelayedActionThread
import app.aaps.pump.insight.utils.DelayedActionThread.Companion.runDelayed
import app.aaps.pump.insight.utils.InputStreamReader
import app.aaps.pump.insight.utils.Nonce
import app.aaps.pump.insight.utils.OutputStreamWriter
import app.aaps.pump.insight.utils.PairingDataStorage
import app.aaps.pump.insight.utils.crypto.Cryptograph.combine
import app.aaps.pump.insight.utils.crypto.Cryptograph.decryptRSA
import app.aaps.pump.insight.utils.crypto.Cryptograph.deriveKeys
import app.aaps.pump.insight.utils.crypto.Cryptograph.generateRSAKey
import app.aaps.pump.insight.utils.crypto.Cryptograph.getServicePasswordHash
import app.aaps.pump.insight.utils.crypto.KeyPair
import dagger.android.DaggerService
import org.spongycastle.crypto.InvalidCipherTextException
import java.io.IOException
import java.security.SecureRandom
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class InsightConnectionService : DaggerService(), ConnectionEstablisher.Callback, InputStreamReader.Callback, OutputStreamWriter.Callback {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var preferences: Preferences
    private val stateCallbacks: MutableList<StateCallback> = ArrayList()
    private val connectionRequests: MutableList<Any> = ArrayList()
    private val exceptionCallbacks: MutableList<ExceptionCallback> = ArrayList()
    private val localBinder: LocalBinder = LocalBinder()
    internal lateinit var pairingDataStorage: PairingDataStorage
    @get:Synchronized lateinit var state: InsightState
        private set
    private lateinit var wakeLock: PowerManager.WakeLock
    private var disconnectTimer: DelayedActionThread? = null
    private var recoveryTimer: DelayedActionThread? = null
    private var timeoutTimer: DelayedActionThread? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var connectionEstablisher: ConnectionEstablisher? = null
    private var inputStreamReader: InputStreamReader? = null
    private var outputStreamWriter: OutputStreamWriter? = null
    private var keyRequest: KeyRequest? = null
    private val buffer = ByteBuf(BUFFER_SIZE)
    @get:Synchronized var verificationString: String? = null
        private set
    private val messageQueue = MessageQueue()
    private val activatedServices: MutableList<Service?> = ArrayList()
    var lastDataTime: Long = 0
        private set
    var lastConnected: Long = 0
        private set
    @get:Synchronized var recoveryDuration: Long = 0
        private set
    private var timeoutDuringHandshakeCounter = 0
    private var intKeyPair: KeyPair? = null
    val keyPair: KeyPair = intKeyPair ?: generateRSAKey().also { intKeyPair = it }
    private var intRandomBytes: ByteArray? = null
    val randomBytes: ByteArray = intRandomBytes ?: ByteArray(28).also { intRandomBytes = it; SecureRandom().nextBytes(intRandomBytes) }

    private fun increaseRecoveryDuration() {
        var maxRecoveryDuration = preferences.get(InsightIntKey.MaxRecoveryDuration).toLong()
        maxRecoveryDuration = min(maxRecoveryDuration, 20)
        maxRecoveryDuration = max(maxRecoveryDuration, 0)
        var minRecoveryDuration = preferences.get(InsightIntKey.MinRecoveryDuration).toLong()
        minRecoveryDuration = min(minRecoveryDuration, 20)
        minRecoveryDuration = max(minRecoveryDuration, 0)
        recoveryDuration += 1000
        recoveryDuration = max(recoveryDuration, minRecoveryDuration * 1000)
        recoveryDuration = min(recoveryDuration, maxRecoveryDuration * 1000)
    }

    val pumpFirmwareVersions: FirmwareVersions?             // pairingDataStorage is a lateinit var
        get() = pairingDataStorage.firmwareVersions
    val pumpSystemIdentification: SystemIdentification?     // pairingDataStorage is a lateinit var
        get() = pairingDataStorage.systemIdentification
    val bluetoothAddress: String?                           // pairingDataStorage is a lateinit var
        get() = pairingDataStorage.macAddress

    @Synchronized fun registerStateCallback(stateCallback: StateCallback) {
        stateCallbacks.add(stateCallback)
    }

    @Synchronized fun unregisterStateCallback(stateCallback: StateCallback) {
        stateCallbacks.remove(stateCallback)
    }

    @Synchronized fun registerExceptionCallback(exceptionCallback: ExceptionCallback) {
        exceptionCallbacks.add(exceptionCallback)
    }

    @Synchronized fun unregisterExceptionCallback(exceptionCallback: ExceptionCallback) {
        exceptionCallbacks.remove(exceptionCallback)
    }

    @Synchronized fun confirmVerificationString() {
        setState(InsightState.SATL_VERIFY_CONFIRM_REQUEST)
        sendSatlMessage(VerifyConfirmRequest())
    }

    @Synchronized fun rejectVerificationString() {
        handleException(SatlPairingRejectedException())
    }

    @get:Synchronized val isPaired: Boolean             // pairingDataStorage is a lateinit var
        get() = pairingDataStorage.paired

    @Synchronized fun <T : AppLayerMessage> requestMessage(message: T): MessageRequest<T> {
        val messageRequest: MessageRequest<T>
        if (state !== InsightState.CONNECTED) {
            messageRequest = MessageRequest(message)
            messageRequest.exception = DisconnectedException()
            return messageRequest
        }
        if (message is WriteConfigurationBlockMessage) {
            val openRequest = MessageRequest(OpenConfigurationWriteSessionMessage())
            val closeRequest = MessageRequest(CloseConfigurationWriteSessionMessage())
            messageRequest = ConfigurationMessageRequest(message, openRequest, closeRequest)
            messageQueue.enqueueRequest(openRequest)
            messageQueue.enqueueRequest(messageRequest)
            messageQueue.enqueueRequest(closeRequest)
        } else {
            messageRequest = MessageRequest(message)
            messageQueue.enqueueRequest(messageRequest)
        }
        requestNextMessage()
        return messageRequest
    }

    private fun requestNextMessage() {
        while (messageQueue.activeRequest == null && messageQueue.hasPendingMessages()) {
            messageQueue.nextRequest()
            val service = messageQueue.activeRequest?.request?.service
            if (service !== Service.CONNECTION && !activatedServices.contains(service)) {
                if (service!!.servicePassword == null) {
                    val activateServiceMessage = ActivateServiceMessage()
                    activateServiceMessage.serviceID = service.id
                    activateServiceMessage.version = service.version
                    activateServiceMessage.servicePassword = ByteArray(16)
                    sendAppLayerMessage(activateServiceMessage)
                } else {
                    val serviceChallengeMessage = ServiceChallengeMessage()
                    serviceChallengeMessage.serviceID = service.id
                    serviceChallengeMessage.version = service.version
                    sendAppLayerMessage(serviceChallengeMessage)
                }
            } else sendAppLayerMessage(messageQueue.activeRequest?.request)
        }
    }

    @Synchronized override fun onCreate() {
        super.onCreate()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter = (applicationContext.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        }
        pairingDataStorage = PairingDataStorage(this)
        state = if (pairingDataStorage.paired) InsightState.DISCONNECTED else InsightState.NOT_PAIRED
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AAPS:InsightConnectionService")
    }

    private fun setState(state: InsightState) {
        if (this.state === state) return
        if (this.state === InsightState.CONNECTED) lastConnected = System.currentTimeMillis()
        if ((state === InsightState.DISCONNECTED || state === InsightState.NOT_PAIRED) && wakeLock.isHeld) wakeLock.release() else if (!wakeLock.isHeld) wakeLock.acquire()
        this.state = state
        for (stateCallback in stateCallbacks) stateCallback.onStateChanged(state)
        aapsLogger.info(LTag.PUMP, "Insight state changed: " + state.name)
    }

    @Synchronized fun requestConnection(lock: Any) {
        if (connectionRequests.contains(lock)) return
        connectionRequests.add(lock)
        disconnectTimer?.interrupt()
        disconnectTimer = null
        if (state === InsightState.DISCONNECTED && pairingDataStorage.paired) {
            recoveryDuration = 0
            timeoutDuringHandshakeCounter = 0
            connect()
        }
    }

    @Synchronized fun withdrawConnectionRequest(lock: Any) {
        if (!connectionRequests.contains(lock)) return
        connectionRequests.remove(lock)
        if (connectionRequests.isEmpty()) {
            if (state === InsightState.RECOVERING) {
                recoveryTimer?.interrupt()
                recoveryTimer = null
                setState(InsightState.DISCONNECTED)
                cleanup(true)
            } else if (state !== InsightState.DISCONNECTED) {
                var disconnectTimeout = preferences.get(InsightIntKey.DisconnectDelay).toLong()
                disconnectTimeout = min(disconnectTimeout, 15)
                disconnectTimeout = max(disconnectTimeout, 0)
                aapsLogger.info(LTag.PUMP, "Last connection lock released, will disconnect in $disconnectTimeout seconds")
                disconnectTimer = runDelayed("Disconnect Timer", disconnectTimeout * 1000) { disconnect() }
            }
        }
    }

    @Synchronized fun hasRequestedConnection(lock: Any): Boolean {
        return connectionRequests.contains(lock)
    }

    private fun cleanup(closeSocket: Boolean) {
        messageQueue.completeActiveRequest(ConnectionLostException())
        messageQueue.completePendingRequests(ConnectionLostException())
        recoveryTimer?.interrupt()
        recoveryTimer = null

        disconnectTimer?.interrupt()
        disconnectTimer = null

        inputStreamReader?.close()
        inputStreamReader = null

        outputStreamWriter?.close()
        outputStreamWriter = null

        connectionEstablisher?.let {
            if (closeSocket) {
                it.close(closeSocket)
                bluetoothSocket = null
            }
        }
        connectionEstablisher = null
        timeoutTimer?.interrupt()
        timeoutTimer = null

        buffer.clear()
        verificationString = null
        intKeyPair = null
        intRandomBytes = null
        activatedServices.clear()
        if (!pairingDataStorage.paired) {
            bluetoothSocket = null
            bluetoothDevice = null
            pairingDataStorage.reset()
        }
    }

    @Synchronized private fun handleException(e: Exception) {
        when (state) {
            InsightState.NOT_PAIRED,
            InsightState.DISCONNECTED,
            InsightState.RECOVERING -> return

            else                    -> Unit
        }
        aapsLogger.info(LTag.PUMP, "Exception occurred: " + e.javaClass.simpleName)
        if (pairingDataStorage.paired) {
            if (e is TimeoutException && (state === InsightState.SATL_SYN_REQUEST || state === InsightState.APP_CONNECT_MESSAGE)) {
                if (++timeoutDuringHandshakeCounter == TIMEOUT_DURING_HANDSHAKE_NOTIFICATION_THRESHOLD) {
                    for (stateCallback in stateCallbacks) {
                        stateCallback.onTimeoutDuringHandshake()
                    }
                }
            }
            setState(if (connectionRequests.isNotEmpty()) InsightState.RECOVERING else InsightState.DISCONNECTED)
            if (e is ConnectionFailedException) {
                cleanup(e.durationOfConnectionAttempt <= 1000)
            } else cleanup(true)
            messageQueue.completeActiveRequest(e)
            messageQueue.completePendingRequests(e)
            if (connectionRequests.isNotEmpty()) {
                if (e !is ConnectionFailedException) {
                    connect()
                } else {
                    increaseRecoveryDuration()
                    if (recoveryDuration == 0L) connect() else {
                        recoveryTimer = runDelayed("RecoveryTimer", recoveryDuration) {
                            recoveryTimer = null
                            synchronized(this@InsightConnectionService) { if (!Thread.currentThread().isInterrupted) connect() }
                        }
                    }
                }
            }
        } else {
            setState(InsightState.NOT_PAIRED)
            cleanup(true)
        }
        for (exceptionCallback in exceptionCallbacks) exceptionCallback.onExceptionOccur(e)
    }

    @Synchronized private fun disconnect() {
        if (state === InsightState.CONNECTED) {
            sendAppLayerMessage(DisconnectMessage())
            sendSatlMessageAndWait(app.aaps.pump.insight.satl.DisconnectMessage())
        }
        cleanup(true)
        setState(if (pairingDataStorage.paired) InsightState.DISCONNECTED else InsightState.NOT_PAIRED)
    }

    @Synchronized fun reset() {
        pairingDataStorage.reset()
        disconnect()
    }

    @Synchronized fun pair(macAddress: String?) {
        check(!pairingDataStorage.paired) { "Pump must be unbonded first." }
        check(connectionRequests.isNotEmpty()) { "A connection lock must be hold for pairing" }
        aapsLogger.info(LTag.PUMP, "Pairing initiated")
        cleanup(true)
        pairingDataStorage.macAddress = macAddress
        connect()
    }

    @Synchronized private fun connect() {
        bluetoothAdapter?.let { bluetoothAdapter ->
            if (bluetoothDevice == null) bluetoothDevice = bluetoothAdapter.getRemoteDevice(pairingDataStorage.macAddress)
            setState(InsightState.CONNECTING)
            bluetoothDevice?.let { bluetoothDevice ->
                connectionEstablisher = ConnectionEstablisher(this, !pairingDataStorage.paired, bluetoothAdapter, bluetoothDevice, bluetoothSocket).also {
                    it.start()
                }
            }
        }
    }

    @Synchronized override fun onSocketCreated(bluetoothSocket: BluetoothSocket?) {
        this.bluetoothSocket = bluetoothSocket
    }

    @Synchronized override fun onConnectionSucceed() {
        try {
            recoveryDuration = 0
            inputStreamReader = InputStreamReader(bluetoothSocket!!.inputStream, this).also { it.start() }
            outputStreamWriter = OutputStreamWriter(bluetoothSocket!!.outputStream, this).also { it.start() }
            if (pairingDataStorage.paired) {
                setState(InsightState.SATL_SYN_REQUEST)
                sendSatlMessage(SynRequest())
            } else {
                setState(InsightState.SATL_CONNECTION_REQUEST)
                sendSatlMessage(ConnectionRequest())
            }
        } catch (e: IOException) {
            handleException(e)
        }
    }

    @Synchronized override fun onReceiveBytes(buffer: ByteArray, bytesRead: Int) {
        this.buffer.putBytes(buffer, bytesRead)
        try {
            while (hasCompletePacket(this.buffer)) {
                val satlMessage = deserialize(this.buffer, pairingDataStorage.lastNonceReceived, pairingDataStorage.incomingKey)
                satlMessage?.let {
                    if (pairingDataStorage.incomingKey != null && pairingDataStorage.lastNonceReceived != null && !pairingDataStorage.lastNonceReceived!!.isSmallerThan(it.nonce!!)) {
                        throw InvalidNonceException()
                    } else processSatlMessage(it)
                }
            }
        } catch (e: InsightException) {
            handleException(e)
        }
    }

    private fun prepareSatlMessage(satlMessage: SatlMessage): ByteArray {
        satlMessage.commID = pairingDataStorage.commId
        val nonce = pairingDataStorage.lastNonceSent
        nonce?.let {
            it.increment()
            pairingDataStorage.lastNonceSent = it
            satlMessage.nonce = it
        }
        val serialized = satlMessage.serialize(pairingDataStorage.outgoingKey)
        timeoutTimer?.interrupt()
        timeoutTimer = runDelayed("TimeoutTimer", RESPONSE_TIMEOUT) {
            timeoutTimer = null
            handleException(TimeoutException())
        }
        return serialized.bytes
    }

    private fun sendSatlMessage(satlMessage: SatlMessage) = outputStreamWriter?.write(prepareSatlMessage(satlMessage))

    private fun sendSatlMessageAndWait(satlMessage: SatlMessage) = outputStreamWriter?.writeAndWait(prepareSatlMessage(satlMessage))

    private fun processSatlMessage(satlMessage: SatlMessage?) {
        timeoutTimer?.interrupt()
        timeoutTimer = null
        satlMessage?.let { pairingDataStorage.lastNonceReceived = it.nonce }
        if (satlMessage is ConnectionResponse) processConnectionResponse()      // Pairing seems to be better with if ... else if than with when (satlMessage) is ... ->
        else if (satlMessage is KeyResponse) processKeyResponse(satlMessage)
        else if (satlMessage is VerifyDisplayResponse) processVerifyDisplayResponse()
        else if (satlMessage is VerifyConfirmResponse) processVerifyConfirmResponse(satlMessage)
        else if (satlMessage is DataMessage) processDataMessage(satlMessage)
        else if (satlMessage is SynAckResponse) processSynAckResponse()
        else if (satlMessage is ErrorMessage) processErrorMessage(satlMessage)
        else handleException(InvalidSatlCommandException())
    }

    private fun processConnectionResponse() {
        if (state !== InsightState.SATL_CONNECTION_REQUEST) {
            handleException(ReceivedPacketInInvalidStateException())
            return
        }
        keyRequest = KeyRequest().also {
            it.setPreMasterKey(keyPair.publicKeyBytes)
            it.setRandomBytes(randomBytes)
            setState(InsightState.SATL_KEY_REQUEST)
            sendSatlMessage(it)
        }
    }

    private fun processKeyResponse(keyResponse: KeyResponse) {
        if (state !== InsightState.SATL_KEY_REQUEST) {
            handleException(ReceivedPacketInInvalidStateException())
            return
        }
        try {
            val derivedKeys = deriveKeys(
                verificationSeed = combine(keyRequest!!.satlContent, keyResponse.satlContent),
                secret = decryptRSA(keyPair.privateKey, keyResponse.preMasterSecret),
                random = randomBytes,
                peerRandom = keyResponse.randomData
            )
            pairingDataStorage.commId = keyResponse.commID
            keyRequest = null
            intRandomBytes = null
            intKeyPair = null
            verificationString = derivedKeys.verificationString
            pairingDataStorage.outgoingKey = derivedKeys.outgoingKey
            pairingDataStorage.incomingKey = derivedKeys.incomingKey
            pairingDataStorage.lastNonceSent = Nonce()
            setState(InsightState.SATL_VERIFY_DISPLAY_REQUEST)
            sendSatlMessage(VerifyDisplayRequest())
        } catch (e: InvalidCipherTextException) {
            handleException(e)
        }
    }

    private fun processVerifyDisplayResponse() {
        if (state !== InsightState.SATL_VERIFY_DISPLAY_REQUEST) {
            handleException(ReceivedPacketInInvalidStateException())
            return
        }
        setState(InsightState.AWAITING_CODE_CONFIRMATION)
    }

    private fun processVerifyConfirmResponse(verifyConfirmResponse: VerifyConfirmResponse) {
        if (state !== InsightState.SATL_VERIFY_CONFIRM_REQUEST) {
            handleException(ReceivedPacketInInvalidStateException())
            return
        }
        when (verifyConfirmResponse.pairingStatus) {
            PairingStatus.CONFIRMED -> {
                verificationString = null
                setState(InsightState.APP_BIND_MESSAGE)
                sendAppLayerMessage(BindMessage())
            }

            PairingStatus.PENDING   -> try {
                Thread.sleep(200)
                sendSatlMessage(VerifyConfirmRequest())
            } catch (_: InterruptedException) {
                //Redirect interrupt flag
                Thread.currentThread().interrupt()
            }

            PairingStatus.REJECTED  -> handleException(SatlPairingRejectedException())
        }
    }

    private fun processSynAckResponse() {
        if (state !== InsightState.SATL_SYN_REQUEST) {
            handleException(ReceivedPacketInInvalidStateException())
            return
        }
        setState(InsightState.APP_CONNECT_MESSAGE)
        sendAppLayerMessage(ConnectMessage())
    }

    private fun processErrorMessage(errorMessage: ErrorMessage) {
        when (errorMessage.error) {
            SatlError.INVALID_NONCE          -> handleException(SatlInvalidNonceErrorException())
            SatlError.INVALID_CRC            -> handleException(SatlInvalidCRCErrorException())
            SatlError.INVALID_MAC_TRAILER    -> handleException(SatlInvalidMacErrorException())
            SatlError.DECRYPT_VERIFY_FAILED  -> handleException(SatlDecryptVerifyFailedErrorException())
            SatlError.INVALID_PAYLOAD_LENGTH -> handleException(SatlInvalidPayloadLengthErrorException())
            SatlError.INVALID_MESSAGE_TYPE   -> handleException(SatlInvalidMessageTypeErrorException())
            SatlError.INCOMPATIBLE_VERSION   -> handleException(SatlIncompatibleVersionErrorException())
            SatlError.COMPATIBLE_STATE       -> handleException(SatlCompatibleStateErrorException())
            SatlError.INVALID_COMM_ID        -> handleException(SatlInvalidCommIdErrorException())
            SatlError.INVALID_PACKET         -> handleException(SatlInvalidPacketErrorException())
            SatlError.WRONG_STATE            -> handleException(SatlWrongStateException())
            SatlError.UNDEFINED              -> handleException(SatlUndefinedErrorException())
            SatlError.NONE                   -> handleException(SatlNoneErrorException())
        }
    }

    private fun processDataMessage(dataMessage: DataMessage) {
        when (state) {
            InsightState.CONNECTED,
            InsightState.APP_BIND_MESSAGE,
            InsightState.APP_CONNECT_MESSAGE,
            InsightState.APP_ACTIVATE_PARAMETER_SERVICE,
            InsightState.APP_ACTIVATE_STATUS_SERVICE,
            InsightState.APP_FIRMWARE_VERSIONS,
            InsightState.APP_SYSTEM_IDENTIFICATION -> Unit

            else                                   -> handleException(ReceivedPacketInInvalidStateException())
        }
        try {
            val appLayerMessage = unwrap(dataMessage)
            if (appLayerMessage is BindMessage) processBindMessage()
            else if (appLayerMessage is ConnectMessage) processConnectMessage()
            else if (appLayerMessage is ActivateServiceMessage) processActivateServiceMessage()
            else if (appLayerMessage is DisconnectMessage)
            else if (appLayerMessage is ServiceChallengeMessage) processServiceChallengeMessage(appLayerMessage)
            else if (appLayerMessage is GetFirmwareVersionsMessage) processFirmwareVersionsMessage(appLayerMessage)
            else if (appLayerMessage is ReadParameterBlockMessage) processReadParameterBlockMessage(appLayerMessage)
            else processGenericAppLayerMessage(appLayerMessage)
        } catch (e: Exception) {
            if (state !== InsightState.CONNECTED) {
                handleException(e)
            } else {
                if (messageQueue.activeRequest == null) {
                    handleException(TooChattyPumpException())
                } else {
                    messageQueue.completeActiveRequest(e)
                    requestNextMessage()
                }
            }
        }
    }

    private fun processBindMessage() {
        if (state !== InsightState.APP_BIND_MESSAGE) {
            handleException(ReceivedPacketInInvalidStateException())
            return
        }
        setState(InsightState.APP_ACTIVATE_STATUS_SERVICE)
        val activateServiceMessage = ActivateServiceMessage()
        activateServiceMessage.serviceID = Service.STATUS.id
        activateServiceMessage.servicePassword = ByteArray(16)
        activateServiceMessage.version = Service.STATUS.version
        sendAppLayerMessage(activateServiceMessage)
    }

    private fun processFirmwareVersionsMessage(message: GetFirmwareVersionsMessage) {
        if (state !== InsightState.APP_FIRMWARE_VERSIONS) {
            handleException(ReceivedPacketInInvalidStateException())
            return
        }
        pairingDataStorage.firmwareVersions = message.firmwareVersions
        setState(InsightState.APP_ACTIVATE_PARAMETER_SERVICE)
        val activateServiceMessage = ActivateServiceMessage()
        activateServiceMessage.serviceID = Service.PARAMETER.id
        activateServiceMessage.servicePassword = ByteArray(16)
        activateServiceMessage.version = Service.PARAMETER.version
        sendAppLayerMessage(activateServiceMessage)
    }

    private fun processConnectMessage() {
        if (state !== InsightState.APP_CONNECT_MESSAGE) {
            handleException(ReceivedPacketInInvalidStateException())
            return
        }
        setState(InsightState.CONNECTED)
    }

    private fun processActivateServiceMessage() {
        if (state === InsightState.APP_ACTIVATE_PARAMETER_SERVICE) {
            activatedServices.add(Service.PARAMETER)
            setState(InsightState.APP_SYSTEM_IDENTIFICATION)
            val message = ReadParameterBlockMessage()
            message.parameterBlockId = SystemIdentificationBlock::class.java
            message.service = Service.PARAMETER
            sendAppLayerMessage(message)
        } else if (state === InsightState.APP_ACTIVATE_STATUS_SERVICE) {
            activatedServices.add(Service.STATUS)
            setState(InsightState.APP_FIRMWARE_VERSIONS)
            sendAppLayerMessage(GetFirmwareVersionsMessage())
        } else {
            messageQueue.activeRequest?.let {
                activatedServices.add(it.request.service)
                sendAppLayerMessage(it.request)
            }
                ?: handleException(TooChattyPumpException())
        }
    }

    private fun processReadParameterBlockMessage(message: ReadParameterBlockMessage) {
        if (state === InsightState.APP_SYSTEM_IDENTIFICATION) {
            if (message.parameterBlock !is SystemIdentificationBlock) handleException(TooChattyPumpException()) else {
                val systemIdentification = (message.parameterBlock as SystemIdentificationBlock?)!!.systemIdentification
                pairingDataStorage.systemIdentification = systemIdentification
                pairingDataStorage.paired = true
                aapsLogger.info(LTag.PUMP, "Pairing completed YEE-HAW ♪ ┏(・o･)┛ ♪ ┗( ･o･)┓ ♪")
                setState(InsightState.CONNECTED)
                for (stateCallback in stateCallbacks) stateCallback.onPumpPaired()
            }
        } else processGenericAppLayerMessage(message)
    }

    private fun processServiceChallengeMessage(serviceChallengeMessage: ServiceChallengeMessage) {
        messageQueue.activeRequest?.let { messageRequest ->
            val service = messageRequest.request.service
            val activateServiceMessage = ActivateServiceMessage()
            if (service != null) {
                activateServiceMessage.serviceID = service.id
                activateServiceMessage.version = service.version
            }
            service?.run { servicePassword?.let { activateServiceMessage.servicePassword = getServicePasswordHash(it, serviceChallengeMessage.randomData) } }

            sendAppLayerMessage(activateServiceMessage)
        }
            ?: handleException(TooChattyPumpException())
    }

    private fun processGenericAppLayerMessage(appLayerMessage: AppLayerMessage) {
        if (messageQueue.activeRequest == null) handleException(TooChattyPumpException()) else {
            try {
                messageQueue.completeActiveRequest(appLayerMessage)
                lastDataTime = System.currentTimeMillis()
            } catch (e: Exception) {
                messageQueue.completeActiveRequest(e)
            }
            requestNextMessage()
        }
    }

    fun sendAppLayerMessage(appLayerMessage: AppLayerMessage?) {
        appLayerMessage?.let { sendSatlMessage(wrap(it)) }
    }

    @Synchronized override fun onConnectionFail(e: Exception?, duration: Long) {
        handleException(ConnectionFailedException(duration))
    }

    @Synchronized override fun onErrorWhileReading(e: Exception) {
        handleException(ConnectionLostException())
    }

    @Synchronized override fun onErrorWhileWriting(e: Exception) {
        handleException(ConnectionLostException())
    }

    override fun onDestroy() {
        disconnect()
    }

    override fun onBind(intent: Intent): IBinder {
        return localBinder
    }

    inner class LocalBinder : Binder() {

        val service: InsightConnectionService
            get() = this@InsightConnectionService
    }

    interface StateCallback {

        fun onStateChanged(state: InsightState?)
        fun onPumpPaired() {}
        fun onTimeoutDuringHandshake() {}
    }

    interface ExceptionCallback {

        fun onExceptionOccur(e: Exception?)
    }

    companion object {

        private const val BUFFER_SIZE = 1024
        private const val TIMEOUT_DURING_HANDSHAKE_NOTIFICATION_THRESHOLD = 3
        private const val RESPONSE_TIMEOUT: Long = 6000
    }
}