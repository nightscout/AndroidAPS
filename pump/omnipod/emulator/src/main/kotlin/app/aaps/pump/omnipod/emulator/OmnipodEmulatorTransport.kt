package app.aaps.pump.omnipod.emulator

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.emulator.EmulatedAlarm
import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import app.aaps.core.interfaces.pump.emulator.EmulatedPumpState
import app.aaps.core.interfaces.pump.emulator.EmulatorSpeed
import app.aaps.core.interfaces.pump.emulator.PumpEmulatorControl
import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandHello
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.EnDecrypt
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.Nonce
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.FailedToConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ScanException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.BleDiscoveredDevice
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.PodScanner
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnectionFactory
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessagePacket
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageType
import app.aaps.pump.omnipod.common.bledriver.comm.message.StringLengthPrefixEncoding
import app.aaps.pump.omnipod.common.bledriver.comm.session.Connected
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionState
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition
import app.aaps.pump.omnipod.common.bledriver.comm.session.EapAkaAttributeAutn
import app.aaps.pump.omnipod.common.bledriver.comm.session.EapAkaAttributeCustomIV
import app.aaps.pump.omnipod.common.bledriver.comm.session.EapAkaAttributeRand
import app.aaps.pump.omnipod.common.bledriver.comm.session.EapAkaAttributeRes
import app.aaps.pump.omnipod.common.bledriver.comm.session.EapCode
import app.aaps.pump.omnipod.common.bledriver.comm.session.EapMessage
import app.aaps.pump.omnipod.common.bledriver.comm.session.EapSqn
import app.aaps.pump.omnipod.common.bledriver.comm.session.Milenage
import app.aaps.pump.omnipod.common.bledriver.comm.session.NotConnected
import app.aaps.pump.omnipod.common.bledriver.comm.session.Session
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionEstablisher
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionKeys
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionNegotiationResynchronization
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodType
import app.aaps.pump.omnipod.common.bledriver.pod.util.RandomByteGenerator
import kotlin.concurrent.thread
import kotlin.experimental.xor

/**
 * The emulated Dash pod behind the driver's own Bluetooth interfaces. The driver gets it in place of the real radio
 * ([BleConnectionFactory] and [BleDeviceManager]), and a test reaches the pod's hooks through [PumpEmulatorControl].
 *
 * Only the radio is replaced. Above it the driver runs as with a real pod: its `MessageIO` splits each message into
 * packets with the RTS/CTS flow control, the pairing makes a long term key, each connection makes session keys with
 * EAP-AKA, and every command is encrypted. Here the pod side of all that runs on its own thread, with the driver's own
 * wire classes: a second `MessageIO` on the other end of a [PodLink], the pod half of the key exchange, and
 * [PodEnDecrypt]. [PodEmulator] then gets each command as the driver built it.
 *
 * What a lost command and a lost answer mean here: in both cases the message reached the pod over the radio and the
 * driver sees it as sent, and no answer comes. After a lost command the pod did nothing; after a lost answer it ran
 * the command. The driver can tell them apart only by the pod's "last programming command" number in a later status.
 */
class OmnipodEmulatorTransport(
    val emulator: PodEmulator,
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val podType: PodType = PodType.DASH,
    /** Told each change of the link, as the real connection writes it into the pod state of the driver. */
    private val onLinkState: (PodLinkState) -> Unit = {},
    /** How the driver builds its session on a link. Omnipod 5 gives one that signs commands, as its real connection does. */
    private val sessionFactory: (MessageIO, Ids, SessionKeys, EnDecrypt) -> Session = { messageIO, ids, keys, enDecrypt ->
        Session(aapsLogger, messageIO, ids, sessionKeys = keys, enDecrypt = enDecrypt)
    }
) : BleConnectionFactory, BleDeviceManager, PumpEmulatorControl {

    /** While true, the pod cannot be reached: a scan does not find it and a connect never comes up. */
    @Volatile private var outOfRange = false
    @Volatile private var link: PodLink? = null
    @Volatile private var connection: EmulatorBleConnection? = null

    // ---- PumpEmulatorControl -------------------------------------------------------------------------------------

    /**
     * Only the scan is shorter at FAST. The pod gives each pulse in its real time at both speeds: the driver waits for
     * the end of a bolus and of the priming by its own clock, with the pulse times the pod told it.
     */
    @Volatile override var speed: EmulatorSpeed = EmulatorSpeed.REAL

    override fun breakLink(): Boolean {
        link?.up = false
        // As the Bluetooth callback of the real stack does.
        connection?.onConnectionLost(LINK_LOST_STATUS)
        return true
    }

    override fun setOutOfRange(outOfRange: Boolean): Boolean {
        this.outOfRange = outOfRange
        if (outOfRange) breakLink()
        return true
    }

    override fun loseCommand(command: EmulatedCommand): Boolean = emulator.loseCommand(command)
    override fun loseAnswer(command: EmulatedCommand): Boolean = emulator.loseAnswer(command)
    override fun raise(alarm: EmulatedAlarm): Boolean = emulator.raise(alarm)
    override fun stopNextBolusAt(units: Double): Boolean = emulator.stopNextBolusAt(units)

    /** A new pod, in range. The pod before it is gone. */
    override fun replaceWithNewPump(): Boolean {
        breakLink()
        emulator.replaceWithNewPod()
        outOfRange = false
        return true
    }

    override fun state(): EmulatedPumpState = emulator.snapshot(linkUp = link?.up == true)
    override fun saveState(): ByteArray = emulator.saveState()

    // ---- BleDeviceManager ----------------------------------------------------------------------------------------

    override fun ensureBondedIfRequired(podAddress: String): Boolean = true
    override fun removeBond(podAddress: String) = Unit
    override fun isBluetoothAvailable(): Boolean = true

    override fun createPodScanner(): PodScanner = object : PodScanner {
        /** The real scan listens for 5 s and then takes what it found. It finds a pod that is not paired yet. */
        override fun scanForPod(serviceUUID: String?, podID: Long): BleDiscoveredDevice {
            Thread.sleep(if (speed == EmulatorSpeed.FAST) FAST_SCAN_MS else SCAN_MS)
            val pod = emulator.state
            if (outOfRange || pod.paired || podID != PodScanner.POD_ID_NOT_ACTIVATED) throw ScanException("Not found")
            return object : BleDiscoveredDevice {
                override val address: String = pod.address
            }
        }
    }

    // ---- BleConnectionFactory ------------------------------------------------------------------------------------

    override fun createConnection(podAddress: String): BleConnection = createConnection(podAddress, null)

    /** As [createConnection], with the controller id for the hello packet of an Omnipod 5 pairing. */
    fun createConnection(podAddress: String, controllerId: Long?): BleConnection = EmulatorBleConnection(podAddress, controllerId)

    /** The scan of the Omnipod 5 driver for a pod that is not paired yet. Returns its address. */
    fun scanForNewPod(): String = createPodScanner().scanForPod(null, PodScanner.POD_ID_NOT_ACTIVATED).address

    private fun reachable(address: String): Boolean {
        val pod = emulator.state
        return !outOfRange && pod.address == address && pod.podStatus != EmulatedPodState.STATUS_DEACTIVATED
    }

    /** A new link for [owner], with the pod listening on its end. The link before it is closed. */
    private fun openLink(owner: EmulatorBleConnection): PodLink {
        link?.up = false
        val newLink = PodLink()
        link = newLink
        connection = owner
        thread(isDaemon = true, name = "omnipod-emulator-pod") { PodSide(newLink).serve() }
        return newLink
    }

    /** The driver's end: what `SharedBleConnection` does, without the radio. */
    private inner class EmulatorBleConnection(private val address: String, private val controllerId: Long?) : BleConnection {

        @Volatile override var session: Session? = null
            private set

        @Volatile override var msgIO: MessageIO? = null
            private set

        @Volatile private var ownLink: PodLink? = null

        @Synchronized
        override fun connect(connectionWaitCond: ConnectionWaitCondition) {
            onLinkState(PodLinkState.CONNECTING)
            try {
                awaitReachable(connectionWaitCond)
            } catch (e: Exception) {
                onLinkState(PodLinkState.DISCONNECTED)
                throw e
            }
            val newLink = openLink(this)
            ownLink = newLink
            val cmd = PipeCmdIO(newLink, newLink.cmdToPhone, newLink.cmdToPod)
            val data = PipeDataIO(newLink, newLink.dataToPhone, newLink.dataToPod)
            msgIO = MessageIO(aapsLogger, cmd, data, podType)
            if (controllerId != null) cmd.sendAndConfirmPacket(BleCommandHello(controllerId.toInt()).data) else cmd.hello()
            cmd.readyToRead()
            data.readyToRead()
            onLinkState(PodLinkState.CONNECTED)
        }

        /** As on a real phone: the connect waits until the pod answers, the time is up, or the queue gives up. */
        private fun awaitReachable(condition: ConnectionWaitCondition) {
            val start = System.currentTimeMillis()
            while (!reachable(address)) {
                val waited = System.currentTimeMillis() - start
                condition.timeoutMs?.let { if (waited >= it) throw FailedToConnectException(address) }
                condition.stopConnection?.let {
                    if (it.count == 0L) throw ConnectException("stopConnecting called")
                    if (waited > MAX_WAIT_FOR_CONNECTION_MS) throw ConnectException("connection timeout")
                }
                Thread.sleep(RANGE_CHECK_MS)
            }
        }

        override fun disconnect(closeGatt: Boolean) {
            ownLink?.up = false
            onLinkState(PodLinkState.DISCONNECTED)
            if (closeGatt) {
                ownLink = null
                session = null
                msgIO = null
            }
        }

        override fun connectionState(): ConnectionState = if (ownLink?.up == true) Connected else NotConnected

        override fun establishSession(ltk: ByteArray, msgSeq: Byte, ids: Ids, eapSqn: ByteArray): EapSqn? {
            val messageIO = msgIO ?: throw ConnectException("Connection lost")
            return when (val keys = SessionEstablisher(aapsLogger, config, messageIO, ltk, eapSqn, ids, msgSeq).negotiateSessionKeys()) {
                is SessionNegotiationResynchronization -> keys.synchronizedEapSqn
                is SessionKeys                         -> {
                    session = sessionFactory(messageIO, ids, keys, EnDecrypt(aapsLogger, keys.nonce, keys.ck))
                    null
                }
            }
        }

        override fun onConnectionLost(status: Int) = disconnect(true)
    }

    /** The pod's end of one link: it reads each message and answers it, until the link goes down. */
    private inner class PodSide(private val podLink: PodLink) {

        private val messageIO = MessageIO(
            aapsLogger,
            PipeCmdIO(podLink, podLink.cmdToPod, podLink.cmdToPhone),
            PipeDataIO(podLink, podLink.dataToPod, podLink.dataToPhone),
            podType
        )
        private var pairingId: Id? = null
        private var keyExchange: PodKeyExchange? = null
        private var o5KeyExchange: O5PodKeyExchange? = null
        private var pendingSession: Pair<ByteArray, ByteArray>? = null
        private var crypt: PodEnDecrypt? = null

        fun serve() {
            while (podLink.up) {
                try {
                    // Null after 5 s with no message, and for the hello packet, which is no message.
                    val message = messageIO.receiveMessage() ?: continue
                    val answer = handle(message) ?: continue
                    messageIO.sendMessage(answer)
                } catch (e: Exception) {
                    aapsLogger.error(LTag.PUMPEMULATOR, "Pod: a message failed", e)
                }
            }
        }

        private fun handle(message: MessagePacket): MessagePacket? = when (message.type) {
            MessageType.PAIRING               -> if (podType.isO5) o5Pairing(message) else pairing(message)
            MessageType.SESSION_ESTABLISHMENT -> sessionEstablishment(message)
            MessageType.ENCRYPTED,
            MessageType.ENCRYPTED_SIGNED      -> command(message)
            else                              -> null
        }

        /**
         * The four steps of `LTKExchanger`: the pod gets its id, the two sides swap public keys and nonces, then their
         * confirmation values, and the pod keeps the key once the controller's value is right.
         */
        private fun pairing(message: MessagePacket): MessagePacket? {
            if (emulator.state.paired && keyExchange == null) return null
            val payload = message.payload
            return when {
                payload.startsWith(SPS1) -> {
                    val data = StringLengthPrefixEncoding.parseKeys(arrayOf(SPS1), payload)[0]
                    if (data.size != PodKeyExchange.PUBLIC_KEY_SIZE + PodKeyExchange.NONCE_SIZE) return null
                    val keys = PodKeyExchange(
                        data.copyOfRange(0, PodKeyExchange.PUBLIC_KEY_SIZE),
                        data.copyOfRange(PodKeyExchange.PUBLIC_KEY_SIZE, data.size)
                    )
                    keyExchange = keys
                    pairingAnswer(message, SPS1, keys.podPublic + keys.podNonce)
                }

                payload.startsWith(SPS2) -> {
                    val keys = keyExchange ?: return null
                    if (!StringLengthPrefixEncoding.parseKeys(arrayOf(SPS2), payload)[0].contentEquals(keys.pdmConf)) return null
                    emulator.paired(keys.ltk)
                    pairingAnswer(message, SPS2, keys.podConf)
                }

                payload.startsWith(SP1)  -> {
                    pairingId = Id(StringLengthPrefixEncoding.parseKeys(arrayOf(SP1, SP2), payload)[0])
                    null
                }

                payload.startsWith(SP0)  -> pairingAnswer(message, P0, P0_PAYLOAD)
                else                     -> null
            }
        }

        /**
         * The Omnipod 5 pairing, as `O5LTKExchanger` runs it: the pod gets its id, the two sides agree on the algorithm
         * (SPS0), swap P-256 public keys and nonces (SPS1), and then each sends two encrypted messages with its
         * certificates (SPS2.1 and SPS2). The pod keeps the key once the public keys are swapped.
         */
        private fun o5Pairing(message: MessagePacket): MessagePacket? {
            if (emulator.state.paired && o5KeyExchange == null) return null
            val payload = message.payload
            return when {
                payload.startsWith(SPS0)   -> {
                    val header = byteArrayOf(0x00, 0x00, 0x09)
                    val crc = crc16Xmodem(header)
                    pairingAnswer(message, SPS0, header + byteArrayOf((crc ushr 8).toByte(), crc.toByte()))
                }

                payload.startsWith(SPS1)   -> {
                    val data = StringLengthPrefixEncoding.parseKeys(arrayOf(SPS1), payload)[0]
                    if (data.size != O5PodKeyExchange.PUBLIC_KEY_SIZE + O5PodKeyExchange.NONCE_SIZE) return null
                    val keys = O5PodKeyExchange(
                        data.copyOfRange(0, O5PodKeyExchange.PUBLIC_KEY_SIZE),
                        data.copyOfRange(O5PodKeyExchange.PUBLIC_KEY_SIZE, data.size)
                    )
                    o5KeyExchange = keys
                    emulator.paired(keys.ltk)
                    pairingAnswer(message, SPS1, keys.podPublic + keys.sentPodNonce)
                }

                payload.startsWith(SPS2_1) -> {
                    val keys = o5KeyExchange ?: return null
                    keys.controllerSent()
                    pairingAnswer(message, SPS2_1, keys.encrypt(ByteArray(O5_PLACEHOLDER_CERTIFICATE_SIZE)))
                }

                payload.startsWith(SPS2)   -> {
                    val keys = o5KeyExchange ?: return null
                    keys.controllerSent()
                    pairingAnswer(message, SPS2, keys.encrypt(ByteArray(O5_PLACEHOLDER_CERTIFICATE_SIZE + O5_SIGNATURE_SIZE)))
                }

                payload.startsWith(SP1)    -> {
                    pairingId = Id(StringLengthPrefixEncoding.parseKeys(arrayOf(SP1, SP2), payload)[0])
                    null
                }

                payload.startsWith(SP0)    -> pairingAnswer(message, P0, P0_PAYLOAD)
                else                       -> null
            }
        }

        /** CRC-16/XMODEM, as `O5LTKExchanger.crc16XMODEM`. */
        private fun crc16Xmodem(data: ByteArray): Int {
            var crc = 0
            for (byte in data) {
                crc = crc xor ((byte.toInt() and 0xff) shl 8)
                repeat(8) { crc = (if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1) and 0xffff }
            }
            return crc
        }

        /**
         * One of the "AID setup" commands of an Omnipod 5 activation: plain text keys with no command envelope. The
         * driver checks only the prefix of each answer. UNVERIFIED: what a real pod puts after the prefix.
         */
        private fun aidSetup(session: PodEnDecrypt, message: MessagePacket, payload: ByteArray): MessagePacket? {
            val answerPrefix = AID_ANSWERS.firstOrNull { payload.startsWith(it.first) }?.second ?: return null
            return session.encrypt(answerTo(message, answerPrefix.toByteArray(Charsets.US_ASCII)))
        }

        private fun answerTo(message: MessagePacket, payload: ByteArray) = MessagePacket(
            type = MessageType.ENCRYPTED,
            source = message.destination,
            destination = message.source,
            payload = payload,
            sequenceNumber = message.sequenceNumber.inc(),
            ack = true,
            ackNumber = message.sequenceNumber.inc(),
            eqos = 1
        )

        private fun pairingAnswer(message: MessagePacket, key: String, payload: ByteArray) = MessagePacket(
            type = MessageType.PAIRING,
            source = pairingId ?: Ids.notActivated(),
            destination = message.source,
            payload = StringLengthPrefixEncoding.formatKeys(arrayOf(key), arrayOf(payload)),
            sequenceNumber = message.sequenceNumber.inc()
        )

        /**
         * EAP-AKA, the pod side. The challenge has RAND and AUTN; with the long term key they give the sequence number
         * the controller used, the answer RES and the session key. UNVERIFIED: a real pod also refuses an old sequence
         * number and asks for a new one. Here every number is taken.
         */
        private fun sessionEstablishment(message: MessagePacket): MessagePacket? {
            val ltk = emulator.state.ltk ?: return null
            val eap = EapMessage.parse(aapsLogger, message.payload)
            if (eap.code == EapCode.SUCCESS) {
                pendingSession?.let { (ck, noncePrefix) -> crypt = PodEnDecrypt(Nonce(noncePrefix, 0), ck) }
                pendingSession = null
                return null
            }
            if (eap.code != EapCode.REQUEST) return null
            val autn = eap.attributes.filterIsInstance<EapAkaAttributeAutn>().firstOrNull()?.payload ?: return null
            val rand = eap.attributes.filterIsInstance<EapAkaAttributeRand>().firstOrNull()?.payload ?: return null
            val controllerIv = eap.attributes.filterIsInstance<EapAkaAttributeCustomIV>().firstOrNull()?.payload ?: return null
            // AUTN starts with the sequence number xor a key. With a sequence number of zero that key shows by itself.
            val key = Milenage(aapsLogger, config, ltk, ByteArray(EAP_SQN_SIZE), rand).autn
            val sqn = ByteArray(EAP_SQN_SIZE) { autn[it] xor key[it] }
            val milenage = Milenage(aapsLogger, config, ltk, sqn, rand)
            if (!milenage.autn.contentEquals(autn)) {
                aapsLogger.debug(LTag.PUMPEMULATOR, "Pod: the session challenge is not made with this pod's key")
                return null
            }
            val podIv = RandomByteGenerator().nextBytes(IV_SIZE)
            pendingSession = milenage.ck to (controllerIv + podIv)
            return MessagePacket(
                type = MessageType.SESSION_ESTABLISHMENT,
                source = message.destination,
                destination = message.source,
                payload = EapMessage(
                    code = EapCode.RESPONSE,
                    identifier = eap.identifier,
                    attributes = arrayOf(EapAkaAttributeRes(milenage.res), EapAkaAttributeCustomIV(podIv))
                ).toByteArray(),
                sequenceNumber = message.sequenceNumber.inc()
            )
        }

        private fun command(message: MessagePacket): MessagePacket? {
            val session = crypt ?: return null
            val plain = session.decrypt(message)
            // The ACK of the controller for the answer before.
            if (plain.payload.isEmpty()) return null
            if (!plain.payload.startsWith(COMMAND_PREFIX)) return aidSetup(session, message, plain.payload)
            val command = StringLengthPrefixEncoding.parseKeys(arrayOf(COMMAND_PREFIX, COMMAND_SUFFIX), plain.payload)[0]
            val answer = emulator.handleCommand(command) ?: return null
            return session.encrypt(answerTo(message, StringLengthPrefixEncoding.formatKeys(arrayOf(ANSWER_PREFIX), arrayOf(answer))))
        }

        private fun ByteArray.startsWith(key: String): Boolean =
            size >= key.length && copyOfRange(0, key.length).decodeToString() == key
    }

    private companion object {

        const val SP1 = "SP1="
        const val SP2 = ",SP2="
        const val SPS1 = "SPS1="
        const val SPS2 = "SPS2="
        const val SPS0 = "SPS0="
        const val SPS2_1 = "SPS2.1="
        const val O5_PLACEHOLDER_CERTIFICATE_SIZE = 70
        const val O5_SIGNATURE_SIZE = 64

        /** The start of each AID setup command and of its answer, see `O5AidSetupCommands` in the driver. */
        val AID_ANSWERS = listOf(
            "SE255.2=" to "ES255.2=",
            "S3.2=" to "3.2=",
            "S3.1=" to "3.1=",
            "S3.9=" to "3.9=",
            "S3.7=" to "3.7=",
            "SE2.1=" to "ES2.1="
        )
        const val SP0 = "SP0,GP0"
        const val P0 = "P0="
        val P0_PAYLOAD = byteArrayOf(0xa5.toByte())
        const val COMMAND_PREFIX = "S0.0="
        const val COMMAND_SUFFIX = ",G0.0"
        const val ANSWER_PREFIX = "0.0="

        const val EAP_SQN_SIZE = 6
        const val IV_SIZE = 4
        const val LINK_LOST_STATUS = 8
        const val SCAN_MS = 5000L
        const val FAST_SCAN_MS = 200L
        const val RANGE_CHECK_MS = 100L

        /** As `SharedBleConnection.MAX_WAIT_FOR_CONNECTION_SECONDS`: the queue's own limit plus 10 s. */
        const val MAX_WAIT_FOR_CONNECTION_MS = 130_000L
    }
}
