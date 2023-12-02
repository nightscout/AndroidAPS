package info.nightscout.comboctl.base

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

private val logger = Logger.get("TransportLayer")

/**
 * This object contains types and constants related to the Combo transport layer.
 * Included are functions for creating and parsing transport layer packets as well
 * as an [IO] class that handles the transport layer IO and contains associated
 * states. The [IO] class uses an internal coroutine that runs the "packet receiver",
 * which is a loop where blocking [ComboIO.receive] calls are done to get packets
 * from the Combo in the background.
 */
object TransportLayer {
    /* Internal offset and sizes for packet IO. */

    const val PACKET_HEADER_SIZE = 1 + 1 + 2 + 1 + NUM_NONCE_BYTES

    const val VERSION_BYTE_OFFSET = 0
    const val SEQ_REL_CMD_BYTE_OFFSET = 1
    const val PAYLOAD_LENGTH_BYTES_OFFSET = 2
    const val ADDRESS_BYTE_OFFSET = 4
    const val NONCE_BYTES_OFFSET = 5
    const val PAYLOAD_BYTES_OFFSET = NONCE_BYTES_OFFSET + NUM_NONCE_BYTES

    /**
     * Maximum allowed size for transport layer packet payloads, in bytes.
     */
    const val MAX_VALID_PAYLOAD_SIZE = 65535

    /**
     * Minimum interval between sent packets, in ms.
     * See PacketSender.send() for more information.
     */
    const val PACKET_SEND_INTERVAL_IN_MS = 200L

    /**
     * Base class for transport layer exceptions.
     *
     * @param message The detail message.
     * @param cause Throwable that further describes the cause of the error.
     */
    open class ExceptionBase(message: String?, cause: Throwable? = null) :
        ComboException(message, cause)

    /**
     * Exception thrown when a transport layer packet arrives with an
     * invalid application layer command ID.
     *
     * The packet is provided as bytes list since the [Packet] parser
     * will refuse to parse a packet with an unknown ID. That's because
     * an unknown ID may indicate that this is actually not packet data.
     *
     * @property commandID The invalid application layer command ID.
     * @property packetBytes The bytes forming the invalid packet.
     */
    class InvalidCommandIDException(
        val commandID: Int,
        val packetBytes: List<Byte>
    ) : ExceptionBase("Invalid/unknown transport layer packet command ID $commandID")

    /**
     * Exception thrown when the arrived packet's command is not the one that was expected.
     *
     * @property packet Transport layer packet that arrived.
     * @property expectedCommand The command that was expected in the packet.
     */
    class IncorrectPacketException(
        val packet: Packet,
        val expectedCommand: Command
    ) : ExceptionBase("Incorrect packet: expected ${expectedCommand.name} packet, got ${packet.command.name} one")

    /**
     * Exception thrown when a packet fails verification.
     *
     * @property packet Transport layer packet that failed verification.
     */
    class PacketVerificationException(
        val packet: Packet
    ) : ExceptionBase("Packet verification failed; packet details:  $packet")

    /**
     * Exception thrown when something is wrong with a transport layer packet's payload.
     *
     * @property packet Transport layer packet with the invalid payload.
     * @property message Detail message.
     */
    class InvalidPayloadException(
        val packet: Packet,
        message: String
    ) : ExceptionBase(message)

    /**
     * Exception thrown when the packet receiver fails.
     *
     * @param cause The throwable that was thrown in the packet
     *        receiver's loop, specifying want went wrong there.
     */
    class PacketReceiverException(cause: Throwable) : ExceptionBase(cause.message, cause)

    /**
     * Exception thrown when the Combo sends an ERROR_RESPONSE packet.
     *
     * These packets notify about errors in the communication between client
     * and Combo at the transport layer.
     *
     * @property packet Transport layer packet with the error information.
     * @property errorID ID of the error.
     */
    class ErrorResponseException(
        val packet: Packet,
        val errorID: Int
    ) : ExceptionBase("Error response by the Combo; error ID = 0x${errorID.toString(16)}")

    /**
     * Valid commands for Combo transport layer packets.
     */
    enum class Command(
        val id: Int
    ) {
        // Pairing commands
        REQUEST_PAIRING_CONNECTION(0x09),
        PAIRING_CONNECTION_REQUEST_ACCEPTED(0x0A),
        REQUEST_KEYS(0x0C),
        GET_AVAILABLE_KEYS(0x0F),
        KEY_RESPONSE(0x11),
        REQUEST_ID(0x12),
        ID_RESPONSE(0x14),

        // Regular commands - these require that pairing was performed
        REQUEST_REGULAR_CONNECTION(0x17),
        REGULAR_CONNECTION_REQUEST_ACCEPTED(0x18),
        DISCONNECT(0x1B),
        ACK_RESPONSE(0x05),
        DATA(0x03),
        ERROR_RESPONSE(0x06);

        companion object {
            private val values = Command.values()
            /**
             * Converts an int to a command with the matching ID.
             *
             * @return Command, or null if the int is not a valid command IUD.
             */
            fun fromInt(value: Int) = values.firstOrNull { it.id == value }
        }
    }

    // Utility function to be able to throw an exception in case of
    // an invalid command ID in the Packet constructor below.
    private fun checkedGetCommand(value: Int, bytes: List<Byte>): Command =
        Command.fromInt(value) ?: throw InvalidCommandIDException(value, bytes)

    /**
     * Class containing Combo transport layer packet data.
     *
     * Communication with the Combo uses packets as the basic unit. Each packet
     * has a header, payload, and a machine authentication code (MAC). (Some initial
     * pairing packets have a MAC made of nullbytes.) This class provides all
     * properties of a packet as well as functions for converting from/to byte lists
     * and for verifying / authenticating via MAC (and CRC for certain pairing packets).
     *
     * See "Transport layer packet structure" in combo-comm-spec.adoc for details.
     *
     * NOTE: Currently, it is not clear what "address" means. However, these values
     * are checked by the Combo, so they must be set to valid values.
     *
     * Packets that are to be transmitted to the Combo are generated inside the
     * [send] call out of [OutgoingPacketInfo] instances.
     *
     * @property command The command of this packet.
     * @property version Byte containing version numbers. The upper 4 bit contain the
     *           major, the lower 4 bit the minor version number.
     *           In all observed packets, this was set to 0x10.
     * @property sequenceBit The packet's sequence bit.
     * @property reliabilityBit The packet's reliability bit.
     * @property address Address byte. The upper 4 bit contain the source, the lower
     *           4 bit the destination address.
     * @property payload The packet's actual payload. Max valid size is 65535 bytes.
     * @property machineAuthenticationCode Machine authentication code. Must be
     *           (re)calculated using [authenticate] if the packet uses MACs and
     *           it is being set up or its payload was modified.
     * @throws IllegalArgumentException if the payload size exceeds
     *         [MAX_VALID_PAYLOAD_SIZE].
     */
    data class Packet(
        val command: Command,
        val version: Byte = 0x10,
        val sequenceBit: Boolean = false,
        val reliabilityBit: Boolean = false,
        val address: Byte = 0,
        val nonce: Nonce = Nonce.nullNonce(),
        var payload: ArrayList<Byte> = ArrayList(0),
        var machineAuthenticationCode: MachineAuthCode = NullMachineAuthCode
    ) {
        init {
            if (payload.size > MAX_VALID_PAYLOAD_SIZE) {
                throw IllegalArgumentException(
                    "Payload size ${payload.size} exceeds allowed maximum of $MAX_VALID_PAYLOAD_SIZE bytes"
                )
            }
        }

        // This is a trick to avoid having to retrieve the payload size from
        // the bytes more than once. The public variant of this constructor
        // extracts the size, and then calls this one, passing the size as
        // the second argument.
        private constructor(bytes: List<Byte>, payloadSize: Int) : this(
            command = checkedGetCommand(bytes[SEQ_REL_CMD_BYTE_OFFSET].toPosInt() and 0x1F, bytes),
            version = bytes[VERSION_BYTE_OFFSET],
            sequenceBit = (bytes[SEQ_REL_CMD_BYTE_OFFSET].toPosInt() and 0x80) != 0,
            reliabilityBit = (bytes[SEQ_REL_CMD_BYTE_OFFSET].toPosInt() and 0x20) != 0,
            address = bytes[ADDRESS_BYTE_OFFSET],
            nonce = Nonce(bytes.subList(NONCE_BYTES_OFFSET, NONCE_BYTES_OFFSET + NUM_NONCE_BYTES)),
            payload = ArrayList<Byte>(bytes.subList(PAYLOAD_BYTES_OFFSET, PAYLOAD_BYTES_OFFSET + payloadSize)),
            machineAuthenticationCode = MachineAuthCode(
                bytes.subList(PAYLOAD_BYTES_OFFSET + payloadSize, PAYLOAD_BYTES_OFFSET + payloadSize + NUM_MAC_BYTES)
            )
        )

        /**
         * Deserializes a packet from a binary representation.
         *
         * This is needed for parsing packets coming from the Combo. However,
         * packets coming from the Combo are framed, so it is important to
         * make sure that the packet data was parsed using ComboFrameParser
         * first. In other words, don't pass data coming through the Combo
         * RFCOMM channel to this constructor directly.
         *
         * @param bytes Packet data to parse.
         * @throws InvalidCommandIDException if the packet data
         *         contains a command ID that is unknown/unsupported.
         */
        constructor(bytes: List<Byte>) :
                this(bytes, (bytes[PAYLOAD_LENGTH_BYTES_OFFSET + 1].toPosInt() shl 8) or bytes[PAYLOAD_LENGTH_BYTES_OFFSET + 0].toPosInt())

        /**
         * Serializes a packet to a binary representation suitable for framing and sending.
         *
         * This is needed for sending packets to the Combo. This function produces
         * data that can be framed using [toComboFrame]. The resulting framed
         * data can then be transmitted to the Combo through the RFCOMM channel.
         * (Alternatively, the [FramedComboIO] class can be used to automatically
         * frame outgoing packets).
         *
         * The withMAC and withPayload arguments exist mainly to be able to
         * produce packet data that is suitable for generating CRCs and MACs.
         *
         * @param withMAC Include the MAC bytes into the packet data.
         * @param withPayload Include the payload bytes into the packet data.
         * @return The serialized packet data.
         */
        fun toByteList(withMAC: Boolean = true, withPayload: Boolean = true): ArrayList<Byte> {
            val bytes = ArrayList<Byte>(PACKET_HEADER_SIZE)

            bytes.add(version)
            bytes.add(((if (sequenceBit) 0x80 else 0)
                    or (if (reliabilityBit) 0x20 else 0)
                    or command.id).toByte())
            bytes.add((payload.size and 0xFF).toByte())
            bytes.add(((payload.size shr 8) and 0xFF).toByte())
            bytes.add(address)

            bytes.addAll(nonce.asSequence())

            if (withPayload)
                bytes.addAll(payload)

            if (withMAC)
                bytes.addAll(machineAuthenticationCode.asSequence())

            return bytes
        }

        /**
         * Computes a 2-byte payload that is the CRC-16-MCRF4XX checksum of the packet header.
         *
         * This erases any previously existing payload
         * and resets the payload size to 2 bytes.
         */
        fun computeCRC16Payload() {
            payload = byteArrayListOfInts(0, 0)
            val headerData = toByteList(withMAC = false, withPayload = false)
            val calculatedCRC16 = calculateCRC16MCRF4XX(headerData)
            payload[0] = (calculatedCRC16 and 0xFF).toByte()
            payload[1] = ((calculatedCRC16 shr 8) and 0xFF).toByte()
        }

        /**
         * Verifies the packet header data by computing its CRC-16-MCRF4XX checksum and
         * comparing it against the one present as the packet's 2-byte payload.
         *
         * @return true if the CRC check succeeds, false if it fails (indicating data corruption).
         * @throws InvalidPayloadException if the payload is not made of 2 bytes.
         */
        fun verifyCRC16Payload(): Boolean {
            if (payload.size != 2) {
                throw InvalidPayloadException(
                    this,
                    "Invalid CRC16 payload: CRC16 payload has 2 bytes, this packet has ${payload.size}"
                )
            }
            val headerData = toByteList(withMAC = false, withPayload = false)
            val calculatedCRC16 = calculateCRC16MCRF4XX(headerData)
            return (payload[0] == (calculatedCRC16 and 0xFF).toByte()) &&
                    (payload[1] == ((calculatedCRC16 shr 8) and 0xFF).toByte())
        }

        /**
         * Authenticates the packet using the given cipher.
         *
         * Authentication means that a MAC is generated for this packet and stored
         * in the packet's last 8 bytes. The MAC is generated using the given cipher.
         *
         * @param cipher Cipher to use for generating the MAC.
         */
        fun authenticate(cipher: Cipher) {
            machineAuthenticationCode = calculateMAC(cipher)
        }

        /**
         * Verify the authenticity of the packet using the MAC.
         *
         * @param cipher Cipher to use for the verification.
         * @return true if the packet is found to be valid, false otherwise
         *         (indicating data corruption).
         */
        fun verifyAuthentication(cipher: Cipher): Boolean =
            calculateMAC(cipher) == machineAuthenticationCode

        // This computes the MAC using Two-Fish and a modified RFC3610 CCM authentication
        // process. See "Packet authentication" in combo-comm-spec.adoc for details.
        private fun calculateMAC(cipher: Cipher): MachineAuthCode {
            val macBytes = ArrayList<Byte>(NUM_MAC_BYTES)
            var block = ByteArray(CIPHER_BLOCK_SIZE)

            // Set up B_0.
            block[0] = 0x79
            for (i in 0 until NUM_NONCE_BYTES) block[i + 1] = nonce[i]
            block[14] = 0x00
            block[15] = 0x00

            // Produce X_1 out of B_0.
            block = cipher.encrypt(block)

            val packetData = toByteList(withMAC = false, withPayload = true)
            val numDataBlocks = packetData.size / CIPHER_BLOCK_SIZE

            // Repeatedly produce X_i+1 out of X_i and B_i.
            // X_i is the current block value, B_i is the
            // data from packetData that is being accessed
            // inside the loop.
            for (dataBlockNr in 0 until numDataBlocks) {
                for (i in 0 until CIPHER_BLOCK_SIZE) {
                    val a: Int = block[i].toPosInt()
                    val b: Int = packetData[dataBlockNr * CIPHER_BLOCK_SIZE + i].toPosInt()
                    block[i] = (a xor b).toByte()
                }

                block = cipher.encrypt(block)
            }

            // Handle the last block, and apply padding if needed.
            val remainingDataBytes = packetData.size - numDataBlocks * CIPHER_BLOCK_SIZE
            if (remainingDataBytes > 0) {
                for (i in 0 until remainingDataBytes) {
                    val a: Int = block[i].toPosInt()
                    val b: Int = packetData[packetData.size - remainingDataBytes + i].toPosInt()
                    block[i] = (a xor b).toByte()
                }

                val paddingValue = 16 - remainingDataBytes

                for (i in remainingDataBytes until CIPHER_BLOCK_SIZE)
                    block[i] = ((block[i].toPosInt()) xor paddingValue).toByte()

                block = cipher.encrypt(block)
            }

            // Here, the non-standard portion of the authentication starts.

            // Produce the "U" value.
            for (i in 0 until NUM_MAC_BYTES)
                macBytes.add(block[i])

            // Produce the new B_0.
            block[0] = 0x41
            for (i in 0 until NUM_NONCE_BYTES) block[i + 1] = nonce[i]
            block[14] = 0x00
            block[15] = 0x00

            // Produce X_1 out of the new B_0.
            block = cipher.encrypt(block)

            // Compute the final MAC out of U and the
            // first 8 bytes of X_1 XORed together.
            for (i in 0 until NUM_MAC_BYTES)
                macBytes[i] = ((macBytes[i].toPosInt()) xor (block[i].toPosInt())).toByte()

            return MachineAuthCode(macBytes)
        }

        override fun toString() =
            "version: ${version.toHexString(2)}" +
                    "  command: ${command.name}" +
                    "  sequence bit: $sequenceBit" +
                    "  reliability bit: $reliabilityBit" +
                    "  address: ${address.toHexString(2)}" +
                    "  nonce: $nonce" +
                    "  MAC: $machineAuthenticationCode" +
                    "  payload: ${payload.size} byte(s): [${payload.toHexString()}]"
    }

    /**
     * Data class with information about a packet that will go out to the Combo.
     *
     * This is essentially a template for a [Packet] instance that will then
     * be sent to the Combo. Compared to [Packet], this is missing several fields
     * of the header in [Packet], most notably the Tx nonce and MAC authentication.
     * These fields would require access to internal [IO] states in order to be
     * computed, and that state is changed after every packet send operation,
     * so we instead pass instances of this class to [IO] to send. [IO] then
     * internally produces a [Packet] out of this along with these extra fields.
     *
     * @property command Command of the outgoing packet.
     * @property payload The outgoing packet's payload. Empty payloads are valid
     *           (depending on the particular command).
     * @property reliable This is set to true if the packet's reliability bit
     *           shall be set to 1.
     * @property sequenceBitOverride If null, the [IO] class will use its
     *           normal sequence bit logic, otherwise it will set the outgoing
     *           packet's bit to this value.
     */
    data class OutgoingPacketInfo(
        val command: Command,
        val payload: ArrayList<Byte> = ArrayList(),
        val reliable: Boolean = false,
        val sequenceBitOverride: Boolean? = null
    ) {
        override fun toString() =
            "command: ${command.name}" +
                    "  reliable: $reliable" +
                    "  sequenceBitOverride: ${sequenceBitOverride ?: "<not set>"}" +
                    "  payload: ${payload.size} byte(s): [${payload.toHexString()}]"
    }

    /**
     * Creates a REQUEST_PAIRING_CONNECTION OutgoingPacketInfo instance.
     *
     * This is exclusively used during the pairing process.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet info.
     */
    fun createRequestPairingConnectionPacketInfo() =
        OutgoingPacketInfo(command = Command.REQUEST_PAIRING_CONNECTION)

    /**
     * Creates a REQUEST_KEYS OutgoingPacketInfo instance.
     *
     * This is exclusively used during the pairing process.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet info.
     */
    fun createRequestKeysPacketInfo() =
        OutgoingPacketInfo(command = Command.REQUEST_KEYS)

    /**
     * Creates a GET_AVAILABLE_KEYS OutgoingPacketInfo instance.
     *
     * This is exclusively used during the pairing process.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet info.
     */
    fun createGetAvailableKeysPacketInfo() =
        OutgoingPacketInfo(command = Command.GET_AVAILABLE_KEYS)

    /**
     * Creates a REQUEST_ID OutgoingPacketInfo instance.
     *
     * This is exclusively used during the pairing process.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @param bluetoothFriendlyName Bluetooth friendly name to use in the request.
     *        Maximum length is 13 characters.
     *        See the Bluetooth specification, Vol. 3 part C section 3.2.2
     *        for details about Bluetooth friendly names.
     * @return The produced packet info.
     */
    fun createRequestIDPacketInfo(bluetoothFriendlyName: String): OutgoingPacketInfo {
        val btFriendlyNameBytes = bluetoothFriendlyName.encodeToByteArray()
        val numBTFriendlyNameBytes = kotlin.math.min(btFriendlyNameBytes.size, 13)

        val payload = ArrayList<Byte>(17)

        payload.add(((Constants.CLIENT_SOFTWARE_VERSION shr 0) and 0xFF).toByte())
        payload.add(((Constants.CLIENT_SOFTWARE_VERSION shr 8) and 0xFF).toByte())
        payload.add(((Constants.CLIENT_SOFTWARE_VERSION shr 16) and 0xFF).toByte())
        payload.add(((Constants.CLIENT_SOFTWARE_VERSION shr 24) and 0xFF).toByte())

        // If the BT friendly name is shorter than 13 bytes,
        // the rest must be set to zero.
        for (i in 0 until numBTFriendlyNameBytes) payload.add(btFriendlyNameBytes[i])
        for (i in numBTFriendlyNameBytes until 13) payload.add(0.toByte())

        return OutgoingPacketInfo(
            command = Command.REQUEST_ID,
            payload = payload
        )
    }

    /**
     * Creates a REQUEST_REGULAR_CONNECTION OutgoingPacketInfo instance.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet info.
     */
    fun createRequestRegularConnectionPacketInfo() =
        OutgoingPacketInfo(command = Command.REQUEST_REGULAR_CONNECTION)

    /**
     * Creates an ACK_RESPONSE OutgoingPacketInfo instance.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @param sequenceBit Sequence bit to set in the ACK_RESPONSE packet.
     * @return The produced packet info.
     */
    fun createAckResponsePacketInfo(sequenceBit: Boolean) =
        OutgoingPacketInfo(
            command = Command.ACK_RESPONSE,
            sequenceBitOverride = sequenceBit
        )

    /**
     * Creates a DATA OutgoingPacketInfo instance.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @param reliabilityBit Reliability bit to set in the DATA packet.
     * @param payload Payload to assign to the DATA packet.
     * @return The produced packet info.
     */
    fun createDataPacketInfo(reliabilityBit: Boolean, payload: ArrayList<Byte>) =
        OutgoingPacketInfo(
            command = Command.DATA,
            payload = payload,
            reliable = reliabilityBit
        )

    /**
     * Class for performing IO operations at the transport layer level.
     *
     * This takes care of transport layer details such as alternating
     * sequence flags and packet authentications & verifications.
     * Packet reception is handled in an internal coroutine, which
     * run with a "sequenced dispatcher". That's a coroutine dispatcher
     * which never runs more than one task at the same time, even if
     * it uses an underlying thread pool, thus disallowing parallelism.
     * Send operations are also run in that dispatcher. This is done
     * for thread safety reasons.
     *
     * This class is used for both pairing with a pump and for regular
     * communication with a pump. However, this class assumes that the
     * pump data from the [pumpStateStore] is invariant. Since the
     * pairing process sets this data up, that assumption would be
     * violated if the same [IO] instance were used for both pairing
     * and for regular connection. For this reason, when pairing with
     * a pump, create a dedicatd [IO] instance for the pairing, and
     * afterwards, discard it.
     *
     * This is not typically used by external callers. Instead, this is
     * meant for other ComboCtl classes.
     *
     * Functions from this class must not be called simultaneously
     * across threads. The Combo does not support concurrent read and
     * write operations.
     *
     * @param pumpStateStore Pump state store to use.
     * @param pumpAddress Bluetooth address of the pump. Used for
     *        accessing the pump state store.
     * @param comboIO Combo IO object to use for sending/receiving data.
     * @param onPacketReceiverException Callback meant for custom cleanup in case
     *   a [PacketReceiverException] is thrown inside the packet receiver.
     */
    class IO(
        private val pumpStateStore: PumpStateStore,
        private val pumpAddress: BluetoothAddress,
        private val comboIO: ComboIO,
        private val onPacketReceiverException: (e: PacketReceiverException) -> Unit
    ) {
        // Invariant pump data from the state store. Retrieved
        // and cached into this instace when start() is called.
        private var cachedInvariantPumpData = InvariantPumpData.nullData()

        // The current transport layer sequence flag, toggled
        // for each reliable packet.
        private var currentSequenceFlag = false

        // Timestamp (in ms) of the last time a packet was sent.
        // Used for throttling the output.
        private var lastSentPacketTimestamp: Long? = null

        // The last PacketReceiverException encountered in the
        // packet receiver coroutine.
        private var lastPacketReceiverException: PacketReceiverException? = null
        // Job instance representing the packet receiver coroutine.
        private var packetReceiverJob: Job? = null
        // Channel used for transporting the received packets from
        // the packet receiver to the receive() function.
        private var packetReceiverChannel = Channel<Packet>(
            capacity = Channel.UNLIMITED,
            onBufferOverflow = BufferOverflow.SUSPEND
        )

        /**
         * Return value used for [start] onPacketReceived callbacks.
         *
         * This tells the packet receiver what to do with that packet -
         * drop it, or forward it through the packet receiver channel
         * so that [receive] can get them.
         */
        enum class ReceiverBehavior {
            FORWARD_PACKET,
            DROP_PACKET
        }

        /**
         * Manually set the internal cached invariant pump data.
         *
         * This is only useful during pairing, when the contents of the
         * KEY_RESPONSE packet is needed for correctly producing outgoing
         * REQUEST_ID packet (and others). During regular connections,
         * this must not be used - the data is instead retrieved from
         * [pumpStateStore].
         *
         * @param newInvariantPumpData New data to use as the cached pump data.
         */
        fun setManualInvariantPumpData(newInvariantPumpData: InvariantPumpData) {
            cachedInvariantPumpData = newInvariantPumpData
        }

        /**
         * Starts IO activities.
         *
         * This must be called before [send] and [receive] can be used.
         *
         * To receive packets in the background from the Combo, this starts
         * an internal coroutine that runs in the [packetReceiverScope].
         * That scope's associated dispatcher is overwritten; a different
         * dispatcher is used instead (one that never executes tasks
         * simultaneously, on several threads). The "packet receiver"
         * is that coroutine. It runs a loop that keeps receiving packets.
         * The [onPacketReceived] callback defines if the packet receiver
         * should drop the packet or forward it through an internal channel
         * to receive() calls.
         *
         * @param packetReceiverScope [CoroutineScope] to run the packet
         *     receiver coroutine in.
         * @param onPacketReceived Callback that defines if the packet
         *     needs to be dropped or forwarded.
         */
        fun start(
            packetReceiverScope: CoroutineScope,
            onPacketReceived: (packet: Packet) -> ReceiverBehavior
        ) {
            check(packetReceiverJob == null) { "IO already started" }

            // Override the scope's existing dispatcher with the
            // sequencedDispatcher to ensure our IO operations never
            // run in parallel and to prevent internal states to be
            // accessed in parallel by multiple threads.
            startInternal(packetReceiverScope + sequencedDispatcher, onPacketReceived)
        }

        /**
         * Stops ongoing IO.
         *
         * If no IO is ongoing, this does nothing. If there is IO ongoing,
         * this suspends the calling coroutine until all IO activity ceases.
         *
         * The packet receiver that was spawned in [start] is stopped
         * and destroyed by this function.
         *
         * [disconnectDeviceCallback] is necessary to unblock the packet
         * receiver. The IO underlying comboIO may not be cancellable by
         * anything other than by closing a socket for example. For this
         * reason, this callback exists. That callback can then close such
         * a socket, thus unblocking IO calls.
         *
         * @param disconnectPacketInfo Information about the final packet
         *        to generate and send to the Combo as part of an orderly
         *        shutdown. If set to null, no packet will be sent.
         * @param disconnectDeviceCallback Callback to be invoked during
         *        the shutdown procedure.
         */
        suspend fun stop(disconnectPacketInfo: OutgoingPacketInfo? = null, disconnectDeviceCallback: suspend () -> Unit = { }) {
            if (!isIORunning()) {
                // Invoke the disconnectDeviceCallback even if IO isn't actually running.
                // That's because the callback may be needed to for example close a socket
                // and abort an ongoing connect attempt.
                try {
                    disconnectDeviceCallback()
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    logger(LogLevel.WARN) { "Error thrown in disconnectDeviceCallback: $t ; swallowing this throwable" }
                    // We are tearing down IO already, so we swallow throwables here.
                }
                return
            }

            try {
                if (disconnectPacketInfo != null) {
                    val packet = produceOutgoingPacket(disconnectPacketInfo)

                    // We use comboIO.send() directly instead of send()
                    // here, since we need to send the disconnect packet
                    // even if the packet receiver failed.
                    logger(LogLevel.VERBOSE) { "Sending transport layer packet: $packet" }
                    comboIO.send(packet.toByteList())
                    logger(LogLevel.VERBOSE) { "Packet sent" }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // Swallow throwable since we are anyway already disconnecting.
                logger(LogLevel.ERROR) { "Caught error while sending disconnect packet: $t" }
            } finally {
                logger(LogLevel.DEBUG) { "Disconnecting device" }

                // Do device specific disconnect here to unblock any ongoing
                // blocking receive / send calls. Normally, this is not
                // necessary, since the Combo terminates the connection once
                // the disconnect packet gets transmitted. But in case the
                // Combo doesn't terminate the connection (for example, because
                // the packet never arrived, or because Bluetooth failed), we
                // still have to make sure that the blocking calls are
                // unblocked right away.
                try {
                    disconnectDeviceCallback()
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    logger(LogLevel.WARN) { "Error thrown in disconnectDeviceCallback: $t ; swallowing this throwable" }
                    // We are tearing down IO already, so we swallow throwables here.
                }

                logger(LogLevel.DEBUG) { "Stopping packet receiver" }

                try {
                    packetReceiverJob?.cancelAndJoin()
                } catch (e: ComboException) {
                    logger(LogLevel.WARN) { "Exception while cancelling IO: $e ; swallowing this exception" }
                    // We are tearing down IO already, so we swallow exceptions here.
                }
                packetReceiverJob = null

                logger(LogLevel.DEBUG) { "Transport layer IO stopped" }
            }
        }

        /** Returns true if IO is ongoing (due to a [startIO] call), false otherwise. */
        fun isIORunning() = (packetReceiverJob != null)

        /**
         * Generates a packet out of the given [packetInfo] and sends it to the Combo.
         *
         * [start] must have been called prior to calling this function.
         *
         * This function suspends the calling coroutine until the send operation
         * is complete, or an exception is thrown.
         *
         * @param packetInfo Information about the packet to generate and send.
         * @throws IllegalStateException if IO is not running or if it has failed.
         * @throws PacketReceiverException if an exception was thrown inside the
         *         packet receiver prior to this call.
         * @throws ComboIOException if sending fails due to an underlying IO error.
         * @throws PumpStateStoreAccessException if accessing the current Tx
         *         nonce in the pump state store failed while preparing the packet
         *         for sending.
         */
        suspend fun send(packetInfo: OutgoingPacketInfo) {
            check(isIORunning()) {
                "Attempted to send packet even though IO is not running"
            }

            if (!receiverIsOK()) {
                lastPacketReceiverException?.let {
                    throw it
                } ?: throw Error("Packet receiver channel failed for unknown reason")
            }

            sendInternal(packetInfo)
        }

        /**
         * Receives transport layer packets from the Combo.
         *
         * The actual receiving is done by the internal packet receiver.
         * See [start] for details about this.
         *
         * [start] must have been called prior to calling this function.
         *
         * This function suspends the calling coroutine until the receive
         * operation is complete, or an exception is thrown.
         *
         * Optionally, this function can check if a received packet has a
         * correct command. This is useful if during a sequence a specific
         * command is expected. This is done if expectedCommand is non-null.
         *
         * @param expectedCommand Optional TransportLayerIO Packet command to check for.
         * @throws IllegalStateException if IO is not running or if it has failed.
         * @throws PacketReceiverException if an exception was thrown inside the
         *         packet receiver prior to this call, or if an exception is thrown
         *         inside the packet receiver while this call is waiting for a packet.
         * @throws IncorrectPacketException if expectedCommand is non-null and
         *         the received packet's command does not match expectedCommand.
         */
        suspend fun receive(expectedCommand: Command? = null): Packet {
            // In here, we mainly listen to the packetReceiverChannel
            // for incoming packets from the packet receiver coroutine.
            // The actual reception takes place there. startInternal()
            // contains that receiver's code.

            check(isIORunning()) {
                "Attempted to receive packet even though IO is not running"
            }

            if (!receiverIsOK()) {
                lastPacketReceiverException?.let {
                    throw it
                } ?: throw Error("Packet receiver channel failed for unknown reason")
            }

            logger(LogLevel.VERBOSE) {
                if (expectedCommand == null)
                    "Waiting for transport layer packet"
                else
                    "Waiting for transport layer ${expectedCommand.name} packet"
            }

            val packet = packetReceiverChannel.receive()

            // Check if the packet's command is correct (if required).
            if ((expectedCommand != null) && (packet.command != expectedCommand))
                throw IncorrectPacketException(packet, expectedCommand)

            logger(LogLevel.VERBOSE) { "Received packet: $packet" }

            return packet
        }

        /*************************************
         *** PRIVATE FUNCTIONS AND CLASSES ***
         *************************************/

        private fun receiverIsOK() = packetReceiverJob?.isActive ?: false

        private fun startInternal(
            packetReceiverScope: CoroutineScope,
            onPacketReceived: (packet: Packet) -> ReceiverBehavior
        ) {
            cachedInvariantPumpData = if (pumpStateStore.hasPumpState(pumpAddress))
                pumpStateStore.getInvariantPumpData(pumpAddress)
            else
                InvariantPumpData.nullData()

            currentSequenceFlag = false
            lastSentPacketTimestamp = null
            lastPacketReceiverException = null

            reopenPacketReceiverChannel()

            packetReceiverJob = packetReceiverScope.launch {
                while (true) {
                    try {
                        receiveAndPreprocessPacket()?.let { packet ->
                            if (onPacketReceived(packet) == ReceiverBehavior.FORWARD_PACKET)
                                packetReceiverChannel.send(packet)
                        }
                    } catch (t: Throwable) {
                        val packetReceiverException = PacketReceiverException(t)
                        lastPacketReceiverException = packetReceiverException
                        packetReceiverChannel.close(packetReceiverException)
                        onPacketReceiverException(packetReceiverException)

                        when (t) {
                            // Pass through CancellationException to make sure coroutine
                            // cancellation is not broken by this try-catch block.
                            is CancellationException -> throw t
                            is ComboException -> {
                                logger(LogLevel.DEBUG) { "Caught Combo exception in receive loop: $t" }
                                logger(LogLevel.DEBUG) { "Combo exception stacktrace: ${t.stackTraceToString()}" }
                                break
                            }
                            else -> {
                                logger(LogLevel.ERROR) {
                                    "FATAL: Unhandled throwable observed in receiver loop: ${t.stackTraceToString()}"
                                }
                                break
                            }
                        }
                    }
                }
            }
        }

        private suspend fun sendInternal(packetInfo: OutgoingPacketInfo) = withContext(sequencedDispatcher) {
            // It is important to throttle the output to not overload
            // the Combo's packet ring buffer. Otherwise, old packets
            // get overwritten by new ones, and the Combo begins to
            // report errors. Empirically, a waiting period of around
            // 150-200 ms seems to work well to avoid this. Here, we
            // check how much time has passed since the last packet
            // transmission. If less than 200 ms have passed, we wait
            // with delay() until a total of 200 ms elapsed.

            val elapsedTime = getElapsedTimeInMs()

            if (lastSentPacketTimestamp != null) {
                val timePassed = elapsedTime - lastSentPacketTimestamp!!
                if (timePassed < PACKET_SEND_INTERVAL_IN_MS) {
                    val waitPeriod = PACKET_SEND_INTERVAL_IN_MS - timePassed
                    logger(LogLevel.VERBOSE) { "Waiting for $waitPeriod ms until a packet can be sent" }
                    delay(waitPeriod)
                }
            }

            lastSentPacketTimestamp = elapsedTime

            // Proceed with sending the packet.
            // Do this in a NonCancellable context to prevent cancellations
            // from happening between produceOutgoingPacket() and send().
            // This is because produceOutgoingPacket() updates internal
            // states in a way that the pump expects (specifically the
            // reliability bit and sequence flag updates). If we allow
            // cancellations in between the functions here, we may not
            // be aware post-cancellation what the state at the pump is,
            // causing undefined behavior. The risk of getting stuck here
            // due to cancellation being disabled is mitigated, since the
            // only function that can suspend here is send(). That function
            // cannot be cancelled in the usual manner, since it uses
            // blocking system IO. But, such IO layers typically have
            // some sort of close() function to close the socket or
            // tunnel etc., and that function immediately aborts any
            // blocking send/receive operations.
            withContext(NonCancellable) {
                val packet = produceOutgoingPacket(packetInfo)

                logger(LogLevel.VERBOSE) { "Sending transport layer packet: $packet" }
                comboIO.send(packet.toByteList())
                logger(LogLevel.VERBOSE) { "Packet sent" }
            }
        }

        private suspend fun receiveAndPreprocessPacket(): Packet? {
            lateinit var packet: Packet

            try {
                packet = Packet(comboIO.receive())
            } catch (e: InvalidCommandIDException) {
                logger(LogLevel.WARN) {
                    "Skipping packet with invalid/unknown ID ${e.commandID}; ${e.packetBytes.size} packet byte(s): ${e.packetBytes.toHexString()}"
                }
                return null
            }

            logger(LogLevel.VERBOSE) { "Incoming transport layer packet: $packet" }

            // Authenticate the packet. A special exemption applies to the
            // KEY_RESPONSE and ID_RESPONSE packets. These need to be manually
            // verified by callers during the pairing process (which isn't
            // handled by the IO class), since they are part of the
            // authentication key setup.
            // TODO: Also verify packets with no MAC but with a CRC checksum.

            val packetIsValid = when (packet.command) {
                Command.REGULAR_CONNECTION_REQUEST_ACCEPTED,
                Command.ACK_RESPONSE,
                Command.DATA,
                Command.ERROR_RESPONSE -> {
                    logger(LogLevel.VERBOSE) { "Verifying incoming packet with pump-client cipher" }
                    check(pumpStateStore.hasPumpState(pumpAddress)) {
                        "Cannot verify incoming ${packet.command} packet without a pump-client cipher"
                    }
                    packet.verifyAuthentication(cachedInvariantPumpData.pumpClientCipher)
                }

                else -> true
            }
            if (!packetIsValid)
                throw PacketVerificationException(packet)

            // Packets with the reliability flag set must be immediately
            // responded to with an ACK_RESPONSE packet whose sequence bit
            // must match that of the received packet.
            if (packet.reliabilityBit) {
                logger(LogLevel.VERBOSE) {
                    "Got a transport layer ${packet.command.name} packet with its reliability bit set; " +
                            "responding with ACK_RESPONSE packet; sequence bit: ${packet.sequenceBit}"
                }
                val ackResponsePacketInfo = createAckResponsePacketInfo(packet.sequenceBit)

                try {
                    sendInternal(ackResponsePacketInfo)
                } catch (t: Throwable) {
                    logger(LogLevel.ERROR) { "Error while sending ACK_RESPONSE transport layer packet: $t" }
                    throw t
                }
            }

            // Check that this is a packet that we expect to be one that
            // comes from the Combo. Some packets are only ever _sent_ to
            // the Combo, so if we _receive_ them, something is wrong,
            // and we must skip those packets.
            // Also, the Combo periodically sends ACK_RESPONSE packets
            // to us. These packets must be skipped, but they are not
            // an error. Note that these ACK_RESPONSE are not the same
            // as the ACK_RESPONSE packets above - those are sent _by_
            // us _to_ the Combo as a response to an incoming reliable
            // packet, while here, we are talking about an ACK_RESPONSE
            // packet coming _from_ the Combo.
            val skipPacket = when (packet.command) {
                Command.ACK_RESPONSE -> {
                    logger(LogLevel.VERBOSE) { "Got ACK_RESPONSE packet; skipping" }
                    true
                }
                Command.ERROR_RESPONSE,
                Command.DATA,
                Command.PAIRING_CONNECTION_REQUEST_ACCEPTED,
                Command.KEY_RESPONSE,
                Command.ID_RESPONSE,
                Command.REGULAR_CONNECTION_REQUEST_ACCEPTED -> false
                else -> {
                    logger(LogLevel.WARN) { "Cannot process ${packet.command.name} packet coming from the Combo; skipping packet"
                    }
                    true
                }
            }

            if (skipPacket)
                return null

            // Perform some command specific processing.
            when (packet.command) {
                // When we get this command, we must reset the current
                // sequence flag to make sure we start the regular
                // connection with the correct flag.
                // (Not doing this for pairing connections since this
                // flag is never used during pairing.)
                Command.REGULAR_CONNECTION_REQUEST_ACCEPTED -> currentSequenceFlag = false
                Command.ERROR_RESPONSE -> processErrorResponsePacket(packet)
                else -> Unit
            }

            return packet
        }

        // Produces a Packet that is to be sent to the Combo,
        // and updates the state object's nonce (since every
        // outgoing packet must have a unique nonce). It
        // also flips the state's currentSequenceFlag if this
        // is a reliable packet, and authenticates the packet
        // with the appropriate cipher if necessary.
        private fun produceOutgoingPacket(outgoingPacketInfo: OutgoingPacketInfo): Packet {
            logger(LogLevel.VERBOSE) { "About to produce outgoing packet from info: $outgoingPacketInfo" }

            val nonce = when (outgoingPacketInfo.command) {
                // These commands don't use a nonce, so we have
                // to stick with the null nonce.
                Command.REQUEST_PAIRING_CONNECTION,
                Command.REQUEST_KEYS,
                Command.GET_AVAILABLE_KEYS -> Nonce.nullNonce()

                // This is the first command that uses a non-null
                // nonce. All packets after this one increment
                // the nonce. See combo-comm-spec.adoc for details.
                // That first nonce always has value 1. We return
                // a hard-coded nonce here, since at this point,
                // we cannot call getCurrentTxNonce() yet - the
                // pump state is not yet set up. It will be once
                // the ID_RESPONSE packet (which is the response
                // to REQUEST_ID) arrives.
                Command.REQUEST_ID -> Nonce(byteArrayListOfInts(
                    0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                ))

                // These are the commands that are used in regular
                // (= non-pairing) connections. They all increment
                // the nonce.
                Command.REQUEST_REGULAR_CONNECTION,
                Command.ACK_RESPONSE,
                Command.DATA -> pumpStateStore.incrementTxNonce(pumpAddress)

                else -> throw Error("This is not a valid outgoing packet")
            }

            val address = when (outgoingPacketInfo.command) {
                // Initial pairing commands use a hardcoded address.
                Command.REQUEST_PAIRING_CONNECTION,
                Command.REQUEST_KEYS,
                Command.GET_AVAILABLE_KEYS -> 0xF0.toByte()

                Command.REQUEST_ID,
                Command.REQUEST_REGULAR_CONNECTION,
                Command.ACK_RESPONSE,
                Command.DATA -> cachedInvariantPumpData.keyResponseAddress

                else -> throw Error("This is not a valid outgoing packet")
            }

            val isCRCPacket = when (outgoingPacketInfo.command) {
                Command.REQUEST_PAIRING_CONNECTION,
                Command.REQUEST_KEYS,
                Command.GET_AVAILABLE_KEYS -> true

                else -> false
            }

            val reliabilityBit = outgoingPacketInfo.reliable

            // For reliable packets, use the current currentSequenceFlag
            // as the sequence bit, then flip the currentSequenceFlag.
            // For unreliable packets, don't touch the currentSequenceFlag,
            // and clear the sequence bit.
            // This behavior is overridden if sequenceBitOverride is
            // non-null. In that case, the value of sequenceBitOverride
            // is used for the sequence bit, and currentSequenceFlag
            // is not touched. sequenceBitOverride is used for when
            // ACK_RESPONSE packets have to be sent to the Combo.
            val sequenceBit =
                when {
                    outgoingPacketInfo.sequenceBitOverride != null -> outgoingPacketInfo.sequenceBitOverride
                    reliabilityBit -> {
                        val currentSequenceFlag = this.currentSequenceFlag
                        this.currentSequenceFlag = !this.currentSequenceFlag
                        currentSequenceFlag
                    }
                    else -> false
                }

            val packet = Packet(
                command = outgoingPacketInfo.command,
                sequenceBit = sequenceBit,
                reliabilityBit = reliabilityBit,
                address = address,
                nonce = nonce,
                payload = outgoingPacketInfo.payload
            )

            if (isCRCPacket) {
                packet.computeCRC16Payload()
                logger(LogLevel.DEBUG) {
                    val crc16 = (packet.payload[1].toPosInt() shl 8) or packet.payload[0].toPosInt()
                    "Computed CRC16 payload ${crc16.toHexString(4)}"
                }
            }

            // Outgoing packets either use no cipher (limited to some
            // of the initial pairing commands) or the client-pump cipher.
            // The pump-client cipher is used for verifying incoming packets,
            val cipher = when (outgoingPacketInfo.command) {
                Command.REQUEST_PAIRING_CONNECTION,
                Command.REQUEST_KEYS,
                Command.GET_AVAILABLE_KEYS -> null

                Command.REQUEST_ID,
                Command.REQUEST_REGULAR_CONNECTION,
                Command.ACK_RESPONSE,
                Command.DATA -> cachedInvariantPumpData.clientPumpCipher

                else -> throw Error("This is not a valid outgoing packet")
            }

            // Authenticate the packet if necessary.
            if (cipher != null) {
                logger(LogLevel.VERBOSE) { "Authenticating outgoing packet" }
                packet.authenticate(cipher)
            }

            return packet
        }

        // Reads the error ID out of the packet and throws an exception.
        // This is appropriate, since an error message coming from the
        // Combo is non-recoverable.
        private fun processErrorResponsePacket(packet: Packet) {
            if (packet.command != Command.ERROR_RESPONSE)
                throw IncorrectPacketException(packet, Command.ERROR_RESPONSE)
            if (packet.payload.size != 1)
                throw InvalidPayloadException(packet, "Expected 1 byte, got ${packet.payload.size}")

            val errorID = packet.payload[0].toInt()

            throw ErrorResponseException(packet, errorID)
        }

        private fun reopenPacketReceiverChannel() {
            // Once a channel is closed, we can't use it anymore,
            // so we must recreate it to effectively "reset" it.
            packetReceiverChannel.close()
            packetReceiverChannel = Channel<Packet>(
                capacity = Channel.UNLIMITED,
                onBufferOverflow = BufferOverflow.SUSPEND
            )
        }
    }
}

/**
 * Produces a transport layer packet out of given data.
 *
 * This is just a convenience extension function that internally
 * creates a TransportLayer.Packet instance and passes the data
 * to its constructor.
 *
 * See the TransportLayer.Packet constructor for details.
 */
fun List<Byte>.toTransportLayerPacket(): TransportLayer.Packet {
    return TransportLayer.Packet(this)
}
