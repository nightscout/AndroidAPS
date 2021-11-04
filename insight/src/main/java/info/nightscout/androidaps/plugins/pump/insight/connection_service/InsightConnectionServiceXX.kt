package info.nightscout.androidaps.plugins.pump.insight.connection_service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import dagger.android.DaggerService
import info.nightscout.androidaps.insight.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage.Companion.unwrap
import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage.Companion.wrap
import info.nightscout.androidaps.plugins.pump.insight.app_layer.ReadParameterBlockMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration.CloseConfigurationWriteSessionMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration.OpenConfigurationWriteSessionMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration.WriteConfigurationBlockMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.connection.ActivateServiceMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.connection.BindMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.connection.ConnectMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.connection.DisconnectMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.connection.ServiceChallengeMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.SystemIdentificationBlock
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetFirmwareVersionsMessage
import info.nightscout.androidaps.plugins.pump.insight.descriptors.FirmwareVersions
import info.nightscout.androidaps.plugins.pump.insight.descriptors.InsightState
import info.nightscout.androidaps.plugins.pump.insight.descriptors.SystemIdentification
import info.nightscout.androidaps.plugins.pump.insight.exceptions.*
import info.nightscout.androidaps.plugins.pump.insight.exceptions.satl_errors.*
import info.nightscout.androidaps.plugins.pump.insight.satl.*
import info.nightscout.androidaps.plugins.pump.insight.utils.*
import info.nightscout.androidaps.plugins.pump.insight.utils.crypto.Cryptograph
import info.nightscout.androidaps.plugins.pump.insight.utils.crypto.KeyPair
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.spongycastle.crypto.InvalidCipherTextException
import java.io.IOException
import java.security.SecureRandom
import java.util.*
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

// Todo I cannot pair with this file (cannot establish connection with pump during pairing process)
class InsightConnectionServiceXX : DaggerService(), ConnectionEstablisher.Callback, InputStreamReader.Callback, OutputStreamWriter.Callback {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP

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
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
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

    private var _keyPair: KeyPair? = null
    private val keyPair: KeyPair = _keyPair ?: Cryptograph.generateRSAKey().also { _keyPair = it }

    private var _randomBytes: ByteArray? = null
    val randomBytes: ByteArray = _randomBytes ?: ByteArray(28).also {  _randomBytes = it; SecureRandom().nextBytes(_randomBytes) }

    private fun increaseRecoveryDuration() {
        var maxRecoveryDuration = sp.getInt(R.string.key_insight_max_recovery_duration, 20).toLong()
        maxRecoveryDuration = min(maxRecoveryDuration, 20)
        maxRecoveryDuration = max(maxRecoveryDuration, 0)
        var minRecoveryDuration = sp.getInt(R.string.key_insight_min_recovery_duration, 5).toLong()
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
        pairingDataStorage = PairingDataStorage(this)
        state = if (pairingDataStorage.paired) InsightState.DISCONNECTED else InsightState.NOT_PAIRED
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:InsightConnectionService")
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
        if (connectionRequests.size == 0) {
            if (state === InsightState.RECOVERING) {
                recoveryTimer?.interrupt()
                recoveryTimer = null
                setState(InsightState.DISCONNECTED)
                cleanup(true)
            } else if (state !== InsightState.DISCONNECTED) {
                var disconnectTimeout = sp.getInt(R.string.key_insight_disconnect_delay, 5).toLong()
                disconnectTimeout = min(disconnectTimeout, 15)
                disconnectTimeout = max(disconnectTimeout, 0)
                aapsLogger.info(LTag.PUMP, "Last connection lock released, will disconnect in $disconnectTimeout seconds")
                disconnectTimer = DelayedActionThread.runDelayed("Disconnect Timer", disconnectTimeout * 1000) { disconnect() }
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
        _keyPair = null
        _randomBytes = null
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
            setState(if (connectionRequests.size != 0) InsightState.RECOVERING else InsightState.DISCONNECTED)
            if (e is ConnectionFailedException) {
                cleanup(e.durationOfConnectionAttempt <= 1000)
            } else cleanup(true)
            messageQueue.completeActiveRequest(e)
            messageQueue.completePendingRequests(e)
            if (connectionRequests.size != 0) {
                if (e !is ConnectionFailedException) {
                    connect()
                } else {
                    increaseRecoveryDuration()
                    if (recoveryDuration == 0L) connect() else {
                        recoveryTimer = DelayedActionThread.runDelayed("RecoveryTimer", recoveryDuration) {
                            recoveryTimer = null
                            synchronized(this@InsightConnectionServiceXX) { if (!Thread.currentThread().isInterrupted) connect() }
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
            sendSatlMessageAndWait(info.nightscout.androidaps.plugins.pump.insight.satl.DisconnectMessage())
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
        check(connectionRequests.size != 0) { "A connection lock must be hold for pairing" }
        aapsLogger.info(LTag.PUMP, "Pairing initiated")
        cleanup(true)
        pairingDataStorage.macAddress = macAddress
        connect()
    }

    @Synchronized private fun connect() {
        if (bluetoothDevice == null) bluetoothDevice = bluetoothAdapter.getRemoteDevice(pairingDataStorage.macAddress)
        setState(InsightState.CONNECTING)
        connectionEstablisher = ConnectionEstablisher(this, !pairingDataStorage.paired, bluetoothAdapter, bluetoothDevice!!, bluetoothSocket)
        connectionEstablisher?.start()
    }

    @Synchronized override fun onSocketCreated(bluetoothSocket: BluetoothSocket) {
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
            while (SatlMessage.hasCompletePacket(this.buffer)) {
                val satlMessage = SatlMessage.deserialize(this.buffer, pairingDataStorage.lastNonceReceived, pairingDataStorage.incomingKey)
                satlMessage?.let {
                    if (pairingDataStorage.incomingKey != null && pairingDataStorage.lastNonceReceived != null && !pairingDataStorage.lastNonceReceived!!.isSmallerThan(it.nonce!!)) {
                        throw InvalidNonceException()
                    } else processSatlMessage(satlMessage)
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
        timeoutTimer = DelayedActionThread.runDelayed("TimeoutTimer", RESPONSE_TIMEOUT) {
            timeoutTimer = null
            handleException(TimeoutException())
        }
        return serialized.bytes
    }

    private fun sendSatlMessage(satlMessage: SatlMessage?) {
        outputStreamWriter?.write(satlMessage?.let { prepareSatlMessage(it) })
    }

    private fun sendSatlMessageAndWait(satlMessage: SatlMessage?) {
        outputStreamWriter?.writeAndWait(satlMessage?.let { prepareSatlMessage(it) })
    }

    private fun processSatlMessage(satlMessage: SatlMessage?) {
        timeoutTimer?.interrupt()
        timeoutTimer = null
        satlMessage?.let { pairingDataStorage.lastNonceReceived = it.nonce }
        when (satlMessage) {
            is KeyResponse              -> processKeyResponse(satlMessage)
            is VerifyDisplayResponse    -> processVerifyDisplayResponse()
            is VerifyConfirmResponse    -> processVerifyConfirmResponse(satlMessage)
            is DataMessage              -> processDataMessage(satlMessage)
            is SynAckResponse           -> processSynAckResponse()
            is ErrorMessage             -> processErrorMessage(satlMessage)
            else                        -> handleException(InvalidSatlCommandException())
        }
    }

    private fun processConnectionResponse() {
        if (state !== InsightState.SATL_CONNECTION_REQUEST) {
            handleException(ReceivedPacketInInvalidStateException())
            return
        }
        keyRequest = KeyRequest().also {
            it.setPreMasterKey(keyPair.publicKeyBytes)
            it.setRandomBytes(randomBytes)
        }
        setState(InsightState.SATL_KEY_REQUEST)
        sendSatlMessage(keyRequest)
    }

    private fun processKeyResponse(keyResponse: KeyResponse) {
        if (state !== InsightState.SATL_KEY_REQUEST) {
            handleException(ReceivedPacketInInvalidStateException())
            return
        }
        try {
            val derivedKeys = Cryptograph.deriveKeys(Cryptograph.combine(keyRequest!!.satlContent, keyResponse.satlContent),
                Cryptograph.decryptRSA(keyPair.privateKey, keyResponse.preMasterSecret),
                randomBytes,
                keyResponse.randomData)
            keyRequest = null
            _randomBytes = null
            _keyPair = null
            verificationString = derivedKeys.verificationString
            pairingDataStorage.commId = keyResponse.commID
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
                } catch (e: InterruptedException) {
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
            InsightState.APP_SYSTEM_IDENTIFICATION      -> Unit
            else                                        -> handleException(ReceivedPacketInInvalidStateException())
        }
        try {
            val appLayerMessage = unwrap(dataMessage)
            when(appLayerMessage) {
                is BindMessage                  -> processBindMessage()
                is ConnectMessage               -> processConnectMessage()
                is ActivateServiceMessage       -> processActivateServiceMessage()
                is ServiceChallengeMessage      -> processServiceChallengeMessage(appLayerMessage)
                is GetFirmwareVersionsMessage   -> processFirmwareVersionsMessage(appLayerMessage)
                is ReadParameterBlockMessage    -> processReadParameterBlockMessage(appLayerMessage)
                is DisconnectMessage            -> Unit
                else                            -> processGenericAppLayerMessage(appLayerMessage)
            }

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
            val activeRequest = messageQueue.activeRequest
            if (activeRequest == null) {
                handleException(TooChattyPumpException())
            } else {
                activatedServices.add(activeRequest.request!!.service)
                sendAppLayerMessage(activeRequest.request)
            }
        }
    }

    private fun processReadParameterBlockMessage(message: ReadParameterBlockMessage) {
        if (state === InsightState.APP_SYSTEM_IDENTIFICATION) {
            if (message.parameterBlock !is SystemIdentificationBlock)
                handleException(TooChattyPumpException())
            else {
                var systemIdentification = (message.parameterBlock as SystemIdentificationBlock).systemIdentification
                pairingDataStorage.systemIdentification = systemIdentification
                pairingDataStorage.paired = true
                aapsLogger.info(LTag.PUMP, "Pairing completed YEE-HAW ♪ ┏(・o･)┛ ♪ ┗( ･o･)┓ ♪")
                setState(InsightState.CONNECTED)
                for (stateCallback in stateCallbacks) stateCallback.onPumpPaired()
            }
        } else processGenericAppLayerMessage(message)
    }

    private fun processServiceChallengeMessage(serviceChallengeMessage: ServiceChallengeMessage) {
        val activeRequest = messageQueue.activeRequest
        if (activeRequest == null) {
            handleException(TooChattyPumpException())
        } else {
            val service = activeRequest.request!!.service
            val activateServiceMessage = ActivateServiceMessage()
            if (service != null) {
                activateServiceMessage.serviceID = service.id
                activateServiceMessage.version = service.version
            }
            service?.run { servicePassword?.let { activateServiceMessage.servicePassword = Cryptograph.getServicePasswordHash(it, serviceChallengeMessage.randomData) } }

            sendAppLayerMessage(activateServiceMessage)
        }
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

    @Synchronized override fun onConnectionFail(e: Exception, duration: Long) {
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

        val service: InsightConnectionServiceXX
            get() = this@InsightConnectionServiceXX
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