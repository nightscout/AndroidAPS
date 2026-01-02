package info.nightscout.comboctl.base

import info.nightscout.comboctl.base.ApplicationLayer.createCMDDeliverBolusPacket
import info.nightscout.comboctl.base.ApplicationLayer.createCMDReadHistoryBlockPacket
import info.nightscout.comboctl.base.ApplicationLayer.parseCMDReadHistoryBlockResponsePacket
import kotlinx.datetime.LocalDateTime

private val logger = Logger.get("ApplicationLayer")

/**
 * This object contains types and constants related to the Combo application layer.
 * The types include classes (exceptions, packet ...) and enums (available commands ...)
 * This also contains functions for creating and parsing application layer packets.
 * These packets are wrapped in transport layer DATA packets; that is, the application
 * layer packet data is stored as the payload of the DATA packet.
 *
 * Unlike [TransportLayer], this has no IO class.
 */
object ApplicationLayer {
    // Application layer packet structure (excluding the additional transport layer packet metadata):
    //
    // 1. 4 bits  : Application layer major version (always set to 0x01)
    // 2. 4 bits  : Application layer minor version (always set to 0x00)
    // 3. 8 bits  : Service ID; can be one of these values:
    //              0x00 : control service ID
    //              0x48 : RT mode service ID
    //              0xB7 : command mode service ID
    // 4. 16 bits : Command ID, stored as a 16-bit little endian integer
    // 5. n bytes : Payload

    // 1 byte with major & minor version
    // 1 byte with service ID
    // 2 bytes with command ID
    const val PACKET_HEADER_SIZE = 1 + 1 + 2

    const val VERSION_BYTE_OFFSET = 0
    const val SERVICE_ID_BYTE_OFFSET = 1
    const val COMMAND_ID_BYTE_OFFSET = 2
    const val PAYLOAD_BYTES_OFFSET = 4

    /**
     * Maximum allowed size for application layer packet payloads, in bytes.
     */
    const val MAX_VALID_PAYLOAD_SIZE = 65535 - PACKET_HEADER_SIZE

    /**
     * Base class for application layer exceptions.
     *
     * @param message The detail message.
     */
    open class ExceptionBase(message: String) : ComboException(message)

    /**
     * Exception thrown when an application layer packet arrives with an invalid service ID.
     *
     * @property tpLayerPacket Underlying transport layer DATA packet containing the application layer packet data.
     * @property serviceID The invalid service ID.
     * @property payload The application packet's payload.
     */
    class InvalidServiceIDException(
        val tpLayerPacket: TransportLayer.Packet,
        val serviceID: Int,
        val payload: List<Byte>
    ) : ExceptionBase("Invalid/unknown application layer packet service ID 0x${serviceID.toString(16)}")

    /**
     * Exception thrown when an application layer packet arrives with an invalid application layer command ID.
     *
     * @property tpLayerPacket Underlying transport layer DATA packet containing the application layer packet data.
     * @property serviceID Service ID from the application layer packet.
     * @property commandID The invalid application layer command ID.
     * @property payload The application packet's payload.
     */
    class InvalidCommandIDException(
        val tpLayerPacket: TransportLayer.Packet,
        val serviceID: ServiceID,
        val commandID: Int,
        val payload: List<Byte>
    ) : ExceptionBase(
        "Invalid/unknown application layer packet command ID " +
            "0x${commandID.toString(16)} (service ID: ${serviceID.name})"
    )

    /**
     * Exception thrown when a different application layer packet was expected than the one that arrived.
     *
     * More precisely, the arrived packet's command is not the one that was expected.
     *
     * @property appLayerPacket Application layer packet that arrived.
     * @property expectedCommand The command that was expected in the packet.
     */
    class IncorrectPacketException(
        val appLayerPacket: Packet,
        val expectedCommand: Command
    ) : ExceptionBase(
        "Incorrect packet: expected ${expectedCommand.name} " +
            "packet, got ${appLayerPacket.command.name} one"
    )

    /**
     * Exception thrown when the combo sends a CTRL_SERVICE_ERROR packet.
     *
     * These packets notify about errors in the communication between client and Combo
     * at the application layer.
     *
     * @property appLayerPacket Application layer packet that arrived.
     * @property serviceError The service error information from the packet.
     */
    class ServiceErrorException(
        val appLayerPacket: Packet,
        val serviceError: CTRLServiceError
    ) : ExceptionBase(
        "Service error reported by Combo: $serviceError"
    )

    /**
     * Exception thrown when something is wrong with an application layer packet's payload.
     *
     * @property appLayerPacket Application layer packet with the invalid payload.
     * @property message Detail message.
     */
    class InvalidPayloadException(
        val appLayerPacket: Packet,
        message: String
    ) : ExceptionBase(message)

    /**
     * Exception thrown when something a packet's payload data is considered corrupted.
     *
     * This is distinct from [InvalidPayloadException] in that the former is more concerned
     * about parameters like the payload size (example: "expected 15 bytes payload, got 7 bytes"),
     * while this exception is thrown when for example a CRC integrity check indicates that
     * the payload bytes themselves are incorrect.
     *
     * @property appLayerPacket Application layer packet with the corrupted payload.
     * @property message Detail message.
     */
    class PayloadDataCorruptionException(
        val appLayerPacket: Packet,
        message: String
    ) : ExceptionBase(message)

    /**
     * Exception thrown when during an attempt to retrieve history data said data never seems to end.
     *
     * Normally, there will eventually be a packet that indicates that the history
     * has been fully received. If no such packet arrives, then something is wrong.
     *
     * @property message Detail message.
     */
    class InfiniteHistoryDataException(
        message: String
    ) : ExceptionBase(message)

    /**
     * Exception thrown when an application layer packet is received with an error code that indicates an error.
     *
     * All application layer packets that are transmitted to the client via reliable
     * transport layer packet have a 16-bit error code in the first 2 bytes of their
     * payloads. If this error code's value is 0, there's no error. Otherwise, an
     * error occurred. These are not recoverable, so this exception is thrown which
     * causes the packet receiver to fail.
     *
     * @property appLayerPacket Application layer packet with the nonzero error code.
     * @property errorCode Parsed error code.
     */
    class ErrorCodeException(
        val appLayerPacket: Packet,
        val errorCode: ErrorCode
    ) : ExceptionBase("received error code $errorCode in packet $appLayerPacket")

    /**
     * Valid application layer commands.
     *
     * An application layer command is a combination of a service ID, a command ID,
     * and a flag whether or not the command is to be sent with the underlying
     * DATA transport layer packet's reliability flag set or unset. The former
     * two already uniquely identify the command; the "reliable" flag is additional
     * information.
     */
    enum class Command(val serviceID: ServiceID, val commandID: Int, val reliable: Boolean) {

        CTRL_CONNECT(ServiceID.CONTROL, 0x9055, true),
        CTRL_CONNECT_RESPONSE(ServiceID.CONTROL, 0xA055, true),
        CTRL_GET_SERVICE_VERSION(ServiceID.CONTROL, 0x9065, true),
        CTRL_GET_SERVICE_VERSION_RESPONSE(ServiceID.CONTROL, 0xA065, true),
        CTRL_BIND(ServiceID.CONTROL, 0x9095, true),
        CTRL_BIND_RESPONSE(ServiceID.CONTROL, 0xA095, true),
        CTRL_DISCONNECT(ServiceID.CONTROL, 0x005A, true),
        CTRL_ACTIVATE_SERVICE(ServiceID.CONTROL, 0x9066, true),
        CTRL_ACTIVATE_SERVICE_RESPONSE(ServiceID.CONTROL, 0xA066, true),
        CTRL_DEACTIVATE_SERVICE(ServiceID.CONTROL, 0x9069, true),
        CTRL_DEACTIVATE_SERVICE_RESPONSE(ServiceID.CONTROL, 0xA069, true),
        CTRL_DEACTIVATE_ALL_SERVICES(ServiceID.CONTROL, 0x906A, true),
        CTRL_DEACTIVATE_ALL_SERVICES_RESPONSE(ServiceID.CONTROL, 0xA06A, true),
        CTRL_SERVICE_ERROR(ServiceID.CONTROL, 0x00AA, true),

        CMD_PING(ServiceID.COMMAND_MODE, 0x9AAA, true),
        CMD_PING_RESPONSE(ServiceID.COMMAND_MODE, 0xAAAA, true),
        CMD_READ_DATE_TIME(ServiceID.COMMAND_MODE, 0x9AA6, true),
        CMD_READ_DATE_TIME_RESPONSE(ServiceID.COMMAND_MODE, 0xAAA6, true),
        CMD_READ_PUMP_STATUS(ServiceID.COMMAND_MODE, 0x9A9A, true),
        CMD_READ_PUMP_STATUS_RESPONSE(ServiceID.COMMAND_MODE, 0xAA9A, true),
        CMD_READ_ERROR_WARNING_STATUS(ServiceID.COMMAND_MODE, 0x9AA5, true),
        CMD_READ_ERROR_WARNING_STATUS_RESPONSE(ServiceID.COMMAND_MODE, 0xAAA5, true),
        CMD_READ_HISTORY_BLOCK(ServiceID.COMMAND_MODE, 0x9996, true),
        CMD_READ_HISTORY_BLOCK_RESPONSE(ServiceID.COMMAND_MODE, 0xA996, true),
        CMD_CONFIRM_HISTORY_BLOCK(ServiceID.COMMAND_MODE, 0x9999, true),
        CMD_CONFIRM_HISTORY_BLOCK_RESPONSE(ServiceID.COMMAND_MODE, 0xA999, true),
        CMD_GET_BOLUS_STATUS(ServiceID.COMMAND_MODE, 0x966A, true),
        CMD_GET_BOLUS_STATUS_RESPONSE(ServiceID.COMMAND_MODE, 0xA66A, true),
        CMD_DELIVER_BOLUS(ServiceID.COMMAND_MODE, 0x9669, true),
        CMD_DELIVER_BOLUS_RESPONSE(ServiceID.COMMAND_MODE, 0xA669, true),
        CMD_CANCEL_BOLUS(ServiceID.COMMAND_MODE, 0x9695, true),
        CMD_CANCEL_BOLUS_RESPONSE(ServiceID.COMMAND_MODE, 0xA695, true),

        RT_BUTTON_STATUS(ServiceID.RT_MODE, 0x0565, false),
        RT_KEEP_ALIVE(ServiceID.RT_MODE, 0x0566, false),
        RT_BUTTON_CONFIRMATION(ServiceID.RT_MODE, 0x0556, false),
        RT_DISPLAY(ServiceID.RT_MODE, 0x0555, false),
        RT_AUDIO(ServiceID.RT_MODE, 0x0559, false),
        RT_VIBRATION(ServiceID.RT_MODE, 0x055A, false),
        RT_PAUSE(ServiceID.RT_MODE, 0x0569, false),
        RT_RELEASE(ServiceID.RT_MODE, 0x056A, false);

        companion object {

            private val values = Command.entries.toTypedArray()

            /**
             * Returns the command that has a matching service ID and command ID.
             *
             * @return Command, or null if no matching command exists.
             */
            fun fromIDs(serviceID: ServiceID, commandID: Int) = values.firstOrNull {
                (it.serviceID == serviceID) && (it.commandID == commandID)
            }
        }
    }

    /**
     * Valid application layer command service IDs.
     */
    enum class ServiceID(val id: Int) {

        CONTROL(0x00),
        RT_MODE(0x48),
        COMMAND_MODE(0xB7);

        companion object {

            private val values = ServiceID.entries.toTypedArray()

            /**
             * Converts an int to a service ID.
             *
             * @return ServiceID, or null if the int is not a valid ID.
             */
            fun fromInt(value: Int) = values.firstOrNull { it.id == value }
        }
    }

    /**
     * Class for error codes contained in reliable application layer packets coming from the pump.
     *
     * All application layer packets that are transmitted to the client via reliable
     * transport layer packet have a 16-bit error code in the first 2 bytes of their
     * payloads. This class contains that error code. The [ErrorCode.Known.Code] enum
     * contains all currently known error codes. [ErrorCode.Unknown] is used in case
     * the error code value is not one of the known ones. The toString functions of
     * both [ErrorCode.Known] and [ErrorCode.Unknown] are overridden to provide better
     * descriptions of their contents.
     *
     * The [ErrorCode.fromInt] function is used for converting an integer value to
     * an ErrorCode instance. Said integer comes from the reliable packets.
     */
    sealed class ErrorCode {

        data class Known(val code: Code) : ErrorCode() {

            override fun toString(): String = "error code \"${code.description}\""

            enum class Category {
                GENERAL,
                REMOTE_TERMINAL_MODE,
                COMMAND_MODE
            }

            enum class Code(val value: Int, val category: Category, val description: String) {
                NO_ERROR(0x0000, Category.GENERAL, "No error"),

                UNKNOWN_SERVICE_ID(0xF003, Category.GENERAL, "Unknown service ID"),
                INCOMPATIBLE_AL_PACKET_VERSION(0xF005, Category.GENERAL, "Incompatible application layer packet version"),
                INVALID_PAYLOAD_LENGTH(0xF006, Category.GENERAL, "Invalid payload length"),
                NOT_CONNECTED(0xF056, Category.GENERAL, "Application layer not connected"),
                INCOMPATIBLE_SERVICE_VERSION(0xF059, Category.GENERAL, "Incompatible service version"),
                REQUEST_WITH_UNKNOWN_SERVICE_ID(
                    0xF05A, Category.GENERAL,
                    "Version, activate, deactivate request with unknown service ID"
                ),
                SERVICE_ACTIVATION_NOT_ALLOWED(0xF05C, Category.GENERAL, "Service activation not allowed"),
                COMMAND_NOT_ALLOWED(0xF05F, Category.GENERAL, "Command not allowed (wrong mode)"),

                RT_PAYLOAD_WRONG_LENGTH(0xF503, Category.REMOTE_TERMINAL_MODE, "RT payload wrong length"),
                RT_DISPLAY_INCORRECT_INDEX(
                    0xF505, Category.REMOTE_TERMINAL_MODE,
                    "RT display with incorrect row index, update, or display index"
                ),
                RT_DISPLAY_TIMEOUT(0xF506, Category.REMOTE_TERMINAL_MODE, "RT display timeout"),
                RT_UNKNOWN_AUDIO_SEQUENCE(0xF509, Category.REMOTE_TERMINAL_MODE, "RT unknown audio sequence"),
                RT_UNKNOWN_VIBRATION_SEQUENCE(0xF50A, Category.REMOTE_TERMINAL_MODE, "RT unknown vibration sequence"),
                RT_INCORRECT_SEQUENCE_NUMBER(0xF50C, Category.REMOTE_TERMINAL_MODE, "RT command has incorrect sequence number"),
                RT_ALIVE_TIMEOUT_EXPIRED(0xF533, Category.REMOTE_TERMINAL_MODE, "RT alive timeout expired"),

                CMD_VALUES_NOT_WITHIN_THRESHOLD(0xF605, Category.COMMAND_MODE, "CMD values not within threshold"),
                CMD_WRONG_BOLUS_TYPE(0xF606, Category.COMMAND_MODE, "CMD wrong bolus type"),
                CMD_BOLUS_NOT_DELIVERING(0xF60A, Category.COMMAND_MODE, "CMD bolus not delivering"),
                CMD_HISTORY_READ_EEPROM_ERROR(0xF60C, Category.COMMAND_MODE, "CMD history read EEPROM error"),
                CMD_HISTORY_FRAM_NOT_ACCESSIBLE(0xF633, Category.COMMAND_MODE, "CMD history confirm FRAM not readable or writeable"),
                CMD_UNKNOWN_BOLUS_TYPE(0xF635, Category.COMMAND_MODE, "CMD unknown bolus type"),
                CMD_BOLUS_CURRENTLY_UNAVAILABLE(0xF636, Category.COMMAND_MODE, "CMD bolus is not available at the moment"),
                CMD_INCORRECT_CRC_VALUE(0xF639, Category.COMMAND_MODE, "CMD incorrect CRC value"),
                CMD_CH1_CH2_VALUES_INCONSISTENT(0xF63A, Category.COMMAND_MODE, "CMD ch1 and ch2 values inconsistent"),
                CMD_INTERNAL_PUMP_ERROR(0xF63C, Category.COMMAND_MODE, "CMD pump has internal error (RAM values changed)");
            }
        }

        data class Unknown(val code: Int) : ErrorCode() {

            override fun toString(): String = "unknown error code ${code.toHexString(4, true)}"
        }

        companion object {

            private val knownCodes = Known.Code.entries.toTypedArray()

            fun fromInt(value: Int): ErrorCode {
                val foundCode = knownCodes.firstOrNull { (it.value == value) }
                return if (foundCode != null)
                    Known(foundCode)
                else
                    Unknown(value)
            }
        }
    }

    /**
     * Error information from CTRL_SERVICE_ERROR packets.
     *
     * The service and command ID are kept as integer on purpose, since
     * it is not known if all possible values are known, so directly
     * having enum types here would not allow for representing unknown
     * values properly.
     *
     * @property errorCode Error code specifying the error.
     * @property serviceIDValue Integer with the value of the
     *           service ID of the command that caused the error.
     * @property commandIDValue Integer with the value of the
     *           command ID of the command that caused the error.
     */
    data class CTRLServiceError(
        val errorCode: ErrorCode,
        val serviceIDValue: Int,
        val commandIDValue: Int
    ) {

        override fun toString(): String {
            var command: Command? = null

            val serviceID = ServiceID.fromInt(serviceIDValue)
            if (serviceID != null)
                command = Command.fromIDs(serviceID, commandIDValue)

            val commandStr =
                if (command != null)
                    "command \"${command.name}\""
                else
                    "service ID 0x${serviceIDValue.toString(16)} command ID 0x${commandIDValue.toString(16)}"

            return "$errorCode $commandStr"
        }
    }

    /**
     * Possible status the pump can be in.
     */
    enum class CMDPumpStatus(val str: String) {

        STOPPED("STOPPED"),
        RUNNING("RUNNING");

        override fun toString() = str
    }

    data class CMDErrorWarningStatus(val errorOccurred: Boolean, val warningOccurred: Boolean)

    /**
     * Command mode history event details.
     *
     * IMPORTANT: Bolus amounts are given in 0.1 IU units,
     * so for example, "57" means 5.7 IU.
     */
    sealed class CMDHistoryEventDetail(val isBolusDetail: Boolean) {

        data class QuickBolusRequested(val bolusAmount: Int) : CMDHistoryEventDetail(isBolusDetail = true)
        data class QuickBolusInfused(val bolusAmount: Int) : CMDHistoryEventDetail(isBolusDetail = true)
        data class StandardBolusRequested(
            val bolusAmount: Int,
            val manual: Boolean
        ) : CMDHistoryEventDetail(isBolusDetail = true)

        data class StandardBolusInfused(
            val bolusAmount: Int,
            val manual: Boolean
        ) : CMDHistoryEventDetail(isBolusDetail = true)

        data class ExtendedBolusStarted(
            val totalBolusAmount: Int,
            val totalDurationMinutes: Int,
            val manual: Boolean
        ) : CMDHistoryEventDetail(isBolusDetail = true)

        data class ExtendedBolusEnded(
            val totalBolusAmount: Int,
            val totalDurationMinutes: Int,
            val manual: Boolean
        ) : CMDHistoryEventDetail(isBolusDetail = true)

        data class MultiwaveBolusStarted(
            val totalBolusAmount: Int,
            val immediateBolusAmount: Int,
            val totalDurationMinutes: Int,
            val manual: Boolean
        ) : CMDHistoryEventDetail(isBolusDetail = true)

        data class MultiwaveBolusEnded(
            val totalBolusAmount: Int,
            val immediateBolusAmount: Int,
            val totalDurationMinutes: Int,
            val manual: Boolean
        ) : CMDHistoryEventDetail(isBolusDetail = true)

        data class NewDateTimeSet(val dateTime: LocalDateTime) : CMDHistoryEventDetail(isBolusDetail = false)
    }

    /**
     * Information about an event in a command mode history block.
     *
     * "Quick bolus of 3.7 IU infused at 2020-03-11 11:55:23" is one example
     * of the information events provide. Each event contains a timestamp
     * and event specific details.
     *
     * Each event has an associated counter value. The way it is currently
     * understood is that these are the values of a unique internal event
     * counter at the time the event occurred, making this a de-facto ID.
     *
     * @property timestamp Timestamp of when the event occurred.
     * @property eventCounter Counter value for this event.
     * @property detail Event specific details (see [CMDHistoryEventDetail]).
     */
    data class CMDHistoryEvent(
        val timestamp: LocalDateTime,
        val eventCounter: Long,
        val detail: CMDHistoryEventDetail
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null) return false
            if (this::class != other::class) return false

            other as CMDHistoryEvent

            if (timestamp != other.timestamp)
                return false

            if (eventCounter != other.eventCounter)
                return false

            if (detail != other.detail)
                return false

            return true
        }

        override fun hashCode(): Int {
            var result = timestamp.hashCode()
            result = 31 * result + eventCounter.hashCode()
            result = 31 * result + detail.hashCode()
            return result
        }
    }

    /**
     * A block of command mode history events.
     *
     * In command mode, history events are communicated in blocks. Each block
     * consists of a list of "events", for example "quick bolus of 0.5 infused".
     * Each event has a timestamp and event specific details. In addition, the
     * block contains extra information about the other available events.
     *
     * To get all available events, the user has to send multiple history block
     * requests according to that extra information. If moreEventsAvailable is
     * true, then there are more history blocks that can be retrieved. Otherwise,
     * this is the last block.
     *
     * A block is retrieved with the CMD_READ_HISTORY_BLOCK command, and arrives
     * as the CMD_READ_HISTORY_BLOCK_RESPONSE command. The former is generated
     * using [createCMDReadHistoryBlockPacket], the latter is parsed using
     * [parseCMDReadHistoryBlockResponsePacket]. The parse function throws an
     * exception if its integrity checks discover that the block seems corrupted.
     * In such a case, the block can be requested again simply by sending the
     * CMD_READ_HISTORY_BLOCK again. If the block is OK, it is confirmed by
     * sending CMD_CONFIRM_HISTORY_BLOCK. This will inform the Combo that the
     * user is done with that block. Afterwards, a CMD_READ_HISTORY_BLOCK
     * command sent to the Combo will result in the next block being returned.
     *
     * In pseudo code:
     *
     * ```
     * while (true) {
     *     sendPacketToCombo(createCMDReadHistoryBlockPacket())
     *     packet = waitForPacketFromCombo(CMD_READ_HISTORY_BLOCK_RESPONSE)
     *
     *     try {
     *         historyBlock = parseCMDReadHistoryBlockResponsePacket(packet)
     *     } catch (exception) {
     *         continue
     *     }
     *
     *     processHistoryBlock(historyBlock)
     *
     *     sendPacketToCombo(createCMDConfirmHistoryBlockPacket())
     *     waitForPacketFromCombo(CMD_CONFIRM_HISTORY_BLOCK_RESPONSE) // actual packet data is not needed here
     *
     *     if (!historyBlock.moreEventsAvailable)
     *         break
     * }
     * ```
     *
     * @property numRemainingEvents How many events remain available. This
     *           includes the number of events in this block. This means that
     *           numRemainingEvents is <= events.size in the last block.
     * @property moreEventsAvailable true if there are more events available
     *           in other blocks, false if this is the last block.
     * @property historyGap If the history FIFO buffer's capacity was exceeded
     *           and the oldest history events were overwritten as a result.
     * @events List of history events in this block.
     */
    data class CMDHistoryBlock(
        val numRemainingEvents: Int,
        val moreEventsAvailable: Boolean,
        val historyGap: Boolean,
        val events: List<CMDHistoryEvent>
    )

    /**
     * Possible immediate bolus types used in COMMAND mode commands.
     *
     * "Immediate" means that the bolus gets delivered immediately once the command is sent.
     * A standard bolus only has an immediate delivery, an extended bolus has none, and
     * a multiwave bolus is partially made up of an immediate and an extended delivery.
     */
    enum class CMDImmediateBolusType(val id: Int) {

        STANDARD(0x47),
        MULTI_WAVE(0xB7);

        companion object {

            private val values = CMDImmediateBolusType.entries.toTypedArray()
            fun fromInt(value: Int) = values.firstOrNull { it.id == value }
        }
    }

    /**
     * Possible states of an ongoing bolus (or NOT_DELIVERING if there's no bolus ongoing).
     */
    enum class CMDBolusDeliveryState(val id: Int) {

        NOT_DELIVERING(0x55),
        DELIVERING(0x66),
        DELIVERED(0x99),
        CANCELLED_BY_USER(0xA9),
        ABORTED_DUE_TO_ERROR(0xAA);

        companion object {

            private val values = CMDBolusDeliveryState.entries.toTypedArray()
            fun fromInt(value: Int) = values.firstOrNull { it.id == value }
        }
    }

    /**
     * Information about an ongoing bolus.
     *
     * If bolusType is set to [CMDBolusDeliveryState.NOT_DELIVERING],
     * then the other fields are meaningless.
     *
     * @property bolusType Type of the bolus (standard / multi-wave).
     * @property deliveryState Type of the current bolus delivery.
     * @proeperty remainingAmount Remaining bolus amount to administer.
     *            Note that this is given in 0.1 IU units, so for example,
     *            "57" means 5.7 IU.
     */
    data class CMDBolusDeliveryStatus(
        val bolusType: CMDImmediateBolusType,
        val deliveryState: CMDBolusDeliveryState,
        val remainingAmount: Int
    )

    /**
     * Valid button codes that an RT_BUTTON_STATUS packet can contain in its payload.
     * These can be bitwise OR combined to implement combined button presses.
     */
    enum class RTButton(val id: Int, val str: String) {

        UP(0x30, "UP"),
        DOWN(0xC0, "DOWN"),
        MENU(0x03, "MENU"),
        CHECK(0x0C, "CHECK"),
        NO_BUTTON(0x00, "NO_BUTTON")
    }

    /**
     * Valid display update reasons that an RT_DISPLAY packet can contain in its payload.
     */
    enum class RTDisplayUpdateReason(val id: Int) {

        UPDATED_BY_COMBO(0x48),
        UPDATED_BY_CLIENT(0xB7);

        companion object {

            private val values = RTDisplayUpdateReason.entries.toTypedArray()

            /**
             * Converts an int to an RTDisplayUpdateReason.
             *
             * @return RTDisplayUpdateReason, or null if the int is not a valid reason ID.
             */
            fun fromInt(value: Int) = values.firstOrNull { it.id == value }
        }
    }

    /**
     * Data class containing the fields of an RT_DISPLAY packet's payload.
     */
    data class RTDisplayPayload(
        val currentRTSequence: Int,
        val reason: RTDisplayUpdateReason,
        val index: Int,
        val row: Int,
        val rowBytes: List<Byte>
    )

    /**
     * Extracts the [Command] out of a transport layer DATA packet.
     *
     * Transport layer DATA packets contain application layer packets as
     * their payload. In some cases, it is necessary to "peek" into that
     * payload to see what application layer command this contains. This
     * function serves that purpose.
     *
     * @param tpLayerPacket Transport layer packet to extract the [Command] from.
     * @return The extracted [Command].
     * @throws TransportLayer.IncorrectPacketException if [tpLayerPacket]
     *         is not a DATA packet.
     */
    fun extractAppLayerPacketCommand(
        tpLayerPacket: TransportLayer.Packet
    ): Command {
        if (tpLayerPacket.command != TransportLayer.Command.DATA) {
            throw TransportLayer.IncorrectPacketException(
                tpLayerPacket,
                TransportLayer.Command.DATA
            )
        }

        val serviceIDInt = tpLayerPacket.payload[SERVICE_ID_BYTE_OFFSET].toPosInt()
        val serviceID = ServiceID.fromInt(serviceIDInt)
            ?: throw InvalidServiceIDException(
                tpLayerPacket,
                serviceIDInt,
                ArrayList(tpLayerPacket.payload.subList(PAYLOAD_BYTES_OFFSET, tpLayerPacket.payload.size))
            )

        val commandID = (tpLayerPacket.payload[COMMAND_ID_BYTE_OFFSET + 0].toPosInt() shl 0) or
            (tpLayerPacket.payload[COMMAND_ID_BYTE_OFFSET + 1].toPosInt() shl 8)

        return Command.fromIDs(serviceID, commandID)
            ?: throw InvalidCommandIDException(
                tpLayerPacket,
                serviceID,
                commandID,
                ArrayList(tpLayerPacket.payload.subList(PAYLOAD_BYTES_OFFSET, tpLayerPacket.payload.size))
            )
    }

    /**
     * Class containing data of a Combo application layer packet.
     *
     * Just like the transport layer, the application layer also uses packets as the
     * basic unit. Each application layer packet is contained in a transport layer
     * DATA packet and contains a small header and a payload. It is easy to confuse
     * its payload with the payload of the transport layer DATA packet, since this
     * packet's data _is_ the payload of the DATA transport layer packet. In other
     * words, within the payload of the DATA transport layer packet is _another_
     * header (the application layer packet header), and after that, the actual
     * application layer packet comes.
     *
     * Unlike transport layer packet data, application layer packet data does not
     * include any CRC or MAC authentication metadata (since the underlying DATA
     * packet already provides that).
     *
     * Also note that the application layer packet header contains a version byte.
     * It is identical in structure to the version byte in the transport layer packet
     * header, but is something entirely separate.
     *
     * See "Application layer packet structure" in combo-comm-spec.adoc for details.
     *
     * Since these packets are stored in the payload of transport layer DATA packets,
     * the transport layer DATA packet's reliability and sequence bits do need to
     * be addressed. This is done by looking up the "reliable" boolean in the command
     * enum value. For each valid command, there is one such boolean. It determines
     * whether the reliability bit of the DATA TL packet will be set or cleared.
     *
     * NOTE: Prefer using [toAppLayerPacket] instead of directly constructing
     * an instance of this class on packets that were received from the Combo.
     * That extension function runs additional checks on the packet.
     *
     * @property command The command of this packet. This is a combination of a
     *           service ID and a command ID, which together uniquely identify
     *           the command.
     * @property version Byte containing version numbers. The upper 4 bit contain the
     *           major, the lower 4 bit the minor version number.
     *           In all observed packets, this was set to 0x10.
     * @property payload The application layer packet payload.
     * @throws IllegalArgumentException if the payload size exceeds
     *         [MAX_VALID_PAYLOAD_SIZE].
     */
    data class Packet(
        val command: Command,
        val version: Byte = 0x10,
        var payload: ArrayList<Byte> = ArrayList(0)
    ) {

        init {
            if (payload.size > MAX_VALID_PAYLOAD_SIZE) {
                throw IllegalArgumentException(
                    "Payload size ${payload.size} exceeds allowed maximum of $MAX_VALID_PAYLOAD_SIZE bytes"
                )
            }
        }

        /**
         * Creates an application layer packet out of a transport layer DATA packet.
         *
         * @param tpLayerPacket The transport layer DATA packet.
         * @throws IncorrectPacketException if the given packet is not a DATA packet.
         */
        constructor(tpLayerPacket: TransportLayer.Packet) : this(
            command = extractAppLayerPacketCommand(tpLayerPacket),
            version = tpLayerPacket.payload[VERSION_BYTE_OFFSET],
            payload = ArrayList<Byte>(tpLayerPacket.payload.subList(PAYLOAD_BYTES_OFFSET, tpLayerPacket.payload.size))
        )

        /**
         * Produces transport layer DATA packet info containing this application layer
         * packet's data as its payload.
         *
         * @return Transport layer DATA packet.
         */
        fun toTransportLayerPacketInfo(): TransportLayer.OutgoingPacketInfo {
            val appLayerPacketPayload = ArrayList<Byte>(PACKET_HEADER_SIZE + payload.size)
            appLayerPacketPayload.add(version)
            appLayerPacketPayload.add(command.serviceID.id.toByte())
            appLayerPacketPayload.add(((command.commandID shr 0) and 0xFF).toByte())
            appLayerPacketPayload.add(((command.commandID shr 8) and 0xFF).toByte())
            appLayerPacketPayload.addAll(payload)

            return TransportLayer.createDataPacketInfo(
                command.reliable,
                appLayerPacketPayload
            )
        }

        override fun toString(): String {
            return "version: ${version.toHexString(2)}" +
                "  service ID: ${command.serviceID}" +
                "  command: $command" +
                "  payload: ${payload.size} byte(s): ${payload.toHexString()}"
        }
    }

    /**
     * Parses the first 2 bytes of a packet's payload.
     *
     * This only works if the packet was sent by the Combo as a reliable
     * transport layer packet that contains an application layer packet.
     * Only then will that encapsulated application layer's payload have
     * a 16-bit error code in its first 2 bytes.
     *
     * @param payload Payload to parse.
     */
    fun parseErrorCode(payload: List<Byte>) =
        ErrorCode.fromInt((payload[0].toPosInt() shl 0) or (payload[1].toPosInt() shl 8))

    // NOTE: Some of the CTRL and CMD packet parse functions below do
    // not touch the first 2 bytes of the payload. This is because these
    // first 2 bytes contain a 16-bit error code, and that error code is
    // already dealt with in the checkAndParseTransportLayerDataPacket()
    // function.

    /**
     * Creates a CTRL_CONNECT packet.
     *
     * This initiates a connection at the application layer. The transport
     * layer must have been connected first with the transport layer's
     * REQUEST_REGULAR_CONNECTION command.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet.
     */
    fun createCTRLConnectPacket(): Packet {
        val serialNumber = Constants.APPLICATION_LAYER_CONNECT_SERIAL_NUMBER
        val payload = byteArrayListOfInts(
            (serialNumber shr 0) and 0xFF,
            (serialNumber shr 8) and 0xFF,
            (serialNumber shr 16) and 0xFF,
            (serialNumber shr 24) and 0xFF
        )
        return Packet(
            command = Command.CTRL_CONNECT,
            payload = payload
        )
    }

    /**
     * Creates a CTRL_GET_SERVICE_VERSION packet.
     *
     * This is used during the pairing process. It is not needed in
     * regular connections.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet.
     */
    fun createCTRLGetServiceVersionPacket(serviceID: ServiceID) = Packet(
        command = Command.CTRL_GET_SERVICE_VERSION,
        payload = byteArrayListOfInts(serviceID.id)
    )

    /**
     * Creates a CTRL_BIND packet.
     *
     * This is used during the pairing process. It is not needed in
     * regular connections.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet.
     */
    fun createCTRLBindPacket() = Packet(
        // TODO: See the spec for this command. It is currently
        // unclear why the payload has to be 0x48.
        command = Command.CTRL_BIND,
        payload = byteArrayListOfInts(0x48)
    )

    /**
     * Creates a CTRL_DISCONNECT packet.
     *
     * This terminates the connection at the application layer.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet.
     */
    fun createCTRLDisconnectPacket() = Packet(
        // TODO: See the spec for this command. It is currently
        // unclear why the payload should be 0x6003, and why
        // Ruffy sets this to 0x0003 instead. But since we know
        // that Ruffy works, we currently pick 0x0003.
        // Also, this payload is actually an error code.
        // Should it just be 0x0000 instead?
        command = Command.CTRL_DISCONNECT,
        payload = byteArrayListOfInts(0x03, 0x00)
    )

    /**
     * Creates a CTRL_ACTIVATE_SERVICE packet.
     *
     * This activates the RT or command mode (depending on the argument).
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet.
     */
    fun createCTRLActivateServicePacket(serviceID: ServiceID) = Packet(
        command = Command.CTRL_ACTIVATE_SERVICE,
        payload = byteArrayListOfInts(serviceID.id, 1, 0)
    )

    /**
     * Creates a CTRL_DEACTIVATE_SERVICE packet.
     *
     * This deactivates the active service with the given ID.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @param serviceID ID of the service to deactivate.
     * @return The produced packet.
     */
    fun createCTRLDeactivateServicePacket(serviceID: ServiceID) = Packet(
        command = Command.CTRL_DEACTIVATE_SERVICE,
        payload = byteArrayListOfInts(serviceID.id)
    )

    /**
     * Creates a CTRL_DEACTIVATE_ALL_SERVICES packet.
     *
     * This deactivates any currently active service.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet.
     */
    fun createCTRLDeactivateAllServicesPacket() = Packet(
        command = Command.CTRL_DEACTIVATE_ALL_SERVICES
    )

    /**
     * Parses an CTRL_SERVICE_ERROR packet and extracts its payload.
     *
     * @param packet Application layer CTRL_SERVICE_ERROR packet to parse.
     * @return The packet's parsed payload.
     * @throws InvalidPayloadException if the payload size is not the expected size.
     */
    fun parseCTRLServiceErrorPacket(packet: Packet): CTRLServiceError {
        val payload = packet.payload
        checkPayloadSize(packet, 5)

        return CTRLServiceError(
            errorCode = parseErrorCode(payload),
            serviceIDValue = payload[2].toPosInt(),
            commandIDValue = (payload[3].toPosInt() shl 0) or (payload[4].toPosInt() shl 8)
        )
    }

    /**
     * Creates a CMD_PING packet.
     *
     * The command mode must have been activated before this can be sent to the Combo.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet.
     */
    fun createCMDPingPacket() = Packet(
        command = Command.CMD_PING
    )

    /**
     * Creates a CMD_READ_DATE_TIME packet.
     *
     * The command mode must have been activated before this can be sent to the Combo.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet.
     */
    fun createCMDReadDateTimePacket() = Packet(
        command = Command.CMD_READ_DATE_TIME
    )

    /**
     * Creates a CMD_READ_PUMP_STATUS packet.
     *
     * The command mode must have been activated before this can be sent to the Combo.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet.
     */
    fun createCMDReadPumpStatusPacket() = Packet(
        command = Command.CMD_READ_PUMP_STATUS
    )

    /**
     * Creates a CMD_READ_ERROR_WARNING_STATUS packet.
     *
     * The command mode must have been activated before this can be sent to the Combo.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet.
     */
    fun createCMDReadErrorWarningStatusPacket() = Packet(
        command = Command.CMD_READ_ERROR_WARNING_STATUS
    )

    /**
     * Creates a CMD_READ_HISTORY_BLOCK packet.
     *
     * The command mode must have been activated before this can be sent to the Combo.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet.
     */
    fun createCMDReadHistoryBlockPacket() = Packet(
        command = Command.CMD_READ_HISTORY_BLOCK
    )

    /**
     * Creates a CMD_CONFIRM_HISTORY_BLOCK packet.
     *
     * The command mode must have been activated before this can be sent to the Combo.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet.
     */
    fun createCMDConfirmHistoryBlockPacket() = Packet(
        command = Command.CMD_CONFIRM_HISTORY_BLOCK
    )

    /**
     * Creates a CMD_GET_BOLUS_STATUS packet.
     *
     * The command mode must have been activated before this can be sent to the Combo.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet.
     */
    fun createCMDGetBolusStatusPacket() = Packet(
        command = Command.CMD_GET_BOLUS_STATUS
    )

    enum class CMDDeliverBolusType {
        STANDARD_BOLUS,
        EXTENDED_BOLUS,
        MULTIWAVE_BOLUS
    }

    /**
     * Creates a CMD_DELIVER_BOLUS packet for a standard bolus.
     *
     * This is equivalent to calling the full [createCMDDeliverBolusPacket] function
     * with bolusType set to [CMDDeliverBolusType.STANDARD_BOLUS]. The [bolusAmount]
     * argument here is passed as the full function's totalBolusAmount argument.
     *
     * The command mode must have been activated before this can be sent to the Combo.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @param bolusAmount Amount of insulin to use for the bolus.
     *        Note that this is given in 0.1 IU units, so for example,
     *        "57" means 5.7 IU.
     * @return The produced packet.
     */
    fun createCMDDeliverBolusPacket(bolusAmount: Int) =
        createCMDDeliverBolusPacket(
            totalBolusAmount = bolusAmount,
            immediateBolusAmount = 0,
            durationInMinutes = 0,
            bolusType = CMDDeliverBolusType.STANDARD_BOLUS
        )

    /**
     * Creates a CMD_DELIVER_BOLUS packet.
     *
     * The command mode must have been activated before this can be sent to the Combo.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @param totalBolusAmount Total amount of insulin to use for the bolus.
     *   Note that this is given in 0.1 IU units, so for example, "57" means 5.7 IU.
     * @param immediateBolusAmount The amount of insulin units to use for the
     *   immediate portion of a multiwave bolus. This value is only used if
     *   [bolusType] is [CMDDeliverBolusType.MULTIWAVE_BOLUS]. This
     *   value must be <= [totalBolusAmount].
     * @param durationInMinutes The duration of the extended bolus or the
     *   extended portion of the multiwave bolus. If [bolusType] is set to
     *   [CMDDeliverBolusType.STANDARD_BOLUS], this value is ignored.
     *   Otherwise, it must be at least 15.
     * @param bolusType Type of the bolus.
     * @return The produced packet.
     * @throws IllegalArgumentException if [immediateBolusAmount] exceeds
     *   [totalBolusAmount], or if [durationInMinutes] is <15 when [bolusType]
     *   is set to anything other than [CMDDeliverBolusType.STANDARD_BOLUS].
     */
    fun createCMDDeliverBolusPacket(
        totalBolusAmount: Int,
        immediateBolusAmount: Int,
        durationInMinutes: Int,
        bolusType: CMDDeliverBolusType
    ): Packet {
        // Values that aren't used for the particular bolus type are set to 0
        // since we don't know what happens if we transmit a nonzero value to
        // the Combo with these bolus types.
        val (effectiveImmediateBolusAmount, effectiveDurationInMinutes) = when (bolusType) {
            CMDDeliverBolusType.STANDARD_BOLUS  -> Pair(0, 0)
            CMDDeliverBolusType.EXTENDED_BOLUS  -> Pair(0, durationInMinutes)
            CMDDeliverBolusType.MULTIWAVE_BOLUS -> Pair(immediateBolusAmount, durationInMinutes)
        }

        // Apply argument requirement checks, depending on the bolus type.
        when (bolusType) {
            CMDDeliverBolusType.STANDARD_BOLUS  -> Unit

            CMDDeliverBolusType.EXTENDED_BOLUS  ->
                require(effectiveDurationInMinutes >= 15) {
                    "extended bolus duration must be at least 15; actual duration: $effectiveDurationInMinutes"
                }

            CMDDeliverBolusType.MULTIWAVE_BOLUS -> {
                require(effectiveDurationInMinutes >= 15) {
                    "multiwave bolus duration must be at least 15; actual duration: $effectiveDurationInMinutes"
                }
                require(immediateBolusAmount <= totalBolusAmount) {
                    "immediate bolus duration must be <= total bolus amount; actual immediate/total amount: " +
                        "$effectiveImmediateBolusAmount / $totalBolusAmount"
                }
            }
        }

        // Need to convert the bolus amount to a 32-bit floating point, and
        // then convert that into a form that can be stored below as 4 bytes
        // in little-endian order.
        val totalBolusAmountAsFloatBits = totalBolusAmount.toFloat().toBits().toPosLong()
        val effectiveImmediateBolusAmountAsFloatBits = effectiveImmediateBolusAmount.toFloat().toBits().toPosLong()
        val effectiveDurationInMinutesAsFloatBits = effectiveDurationInMinutes.toFloat().toBits().toPosLong()

        // TODO: It is currently unknown why the same bolus parameters have to
        // be added twice (once as 16-bit integers and once as 32-bit floats).

        // NOTE: The 0x55, 0x59, 0x65 etc. values have been found empirically.
        val bolusTypeIDBytes = when (bolusType) {
            CMDDeliverBolusType.STANDARD_BOLUS  -> intArrayOf(0x55, 0x59)
            CMDDeliverBolusType.EXTENDED_BOLUS  -> intArrayOf(0x65, 0x69)
            CMDDeliverBolusType.MULTIWAVE_BOLUS -> intArrayOf(0xA5, 0xA9)
        }

        val payload = byteArrayListOfInts(
            bolusTypeIDBytes[0], bolusTypeIDBytes[1],

            // Total bolus amount, encoded as a 16-bit little endian integer.
            (totalBolusAmount and 0x00FF) ushr 0,
            (totalBolusAmount and 0xFF00) ushr 8,
            // Duration in minutes, encoded as a 16-bit little endian integer.
            // (Only relevant for multi-wave and extended bolus.)
            (effectiveDurationInMinutes and 0x00FF) ushr 0,
            (effectiveDurationInMinutes and 0xFF00) ushr 8,
            // Immediate bolus amount encoded as a 16-bit little endian integer.
            // (Only relevant for multi-wave bolus.)
            (effectiveImmediateBolusAmount and 0x00FF) ushr 0,
            (effectiveImmediateBolusAmount and 0xFF00) ushr 8,

            // Total bolus amount, encoded as a 32-bit little endian float point.
            ((totalBolusAmountAsFloatBits and 0x000000FFL) ushr 0).toInt(),
            ((totalBolusAmountAsFloatBits and 0x0000FF00L) ushr 8).toInt(),
            ((totalBolusAmountAsFloatBits and 0x00FF0000L) ushr 16).toInt(),
            ((totalBolusAmountAsFloatBits and 0xFF000000L) ushr 24).toInt(),
            // Duration in minutes, encoded as a 32-bit little endian float point.
            // (Only relevant for multi-wave and extended bolus.)
            ((effectiveDurationInMinutesAsFloatBits and 0x000000FFL) ushr 0).toInt(),
            ((effectiveDurationInMinutesAsFloatBits and 0x0000FF00L) ushr 8).toInt(),
            ((effectiveDurationInMinutesAsFloatBits and 0x00FF0000L) ushr 16).toInt(),
            ((effectiveDurationInMinutesAsFloatBits and 0xFF000000L) ushr 24).toInt(),
            // Immediate bolus amount encoded as a 32-bit little endian float point.
            // (Only relevant for multi-wave bolus.)
            ((effectiveImmediateBolusAmountAsFloatBits and 0x000000FFL) ushr 0).toInt(),
            ((effectiveImmediateBolusAmountAsFloatBits and 0x0000FF00L) ushr 8).toInt(),
            ((effectiveImmediateBolusAmountAsFloatBits and 0x00FF0000L) ushr 16).toInt(),
            ((effectiveImmediateBolusAmountAsFloatBits and 0xFF000000L) ushr 24).toInt(),
        )

        // Add a CRC16 checksum for all the parameters
        // stored in the payload above.
        val crcChecksum = calculateCRC16MCRF4XX(payload)
        payload.add(((crcChecksum and 0x00FF) ushr 0).toByte())
        payload.add(((crcChecksum and 0xFF00) ushr 8).toByte())

        return Packet(
            command = Command.CMD_DELIVER_BOLUS,
            payload = payload
        )
    }

    /**
     * Creates a CMD_CANCEL_BOLUS packet.
     *
     * The command mode must have been activated before this can be sent to the Combo.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @param bolusType The type of the bolus to cancel.
     * @return The produced packet.
     */
    fun createCMDCancelBolusPacket(bolusType: CMDImmediateBolusType) = Packet(
        command = Command.CMD_CANCEL_BOLUS,
        payload = byteArrayListOfInts(bolusType.id)
    )

    /**
     * Parses a CMD_READ_DATE_TIME_RESPONSE packet and extracts its payload.
     *
     * @param packet Application layer CMD_READ_DATE_TIME_RESPONSE packet to parse.
     * @return The packet's parsed payload (the pump's current datetime).
     * @throws InvalidPayloadException if the payload size is not the expected size.
     */
    fun parseCMDReadDateTimeResponsePacket(packet: Packet): LocalDateTime {
        logger(LogLevel.DEBUG) { "Parsing CMD_READ_DATE_TIME_RESPONSE packet" }

        // Payload size sanity check.
        if (packet.payload.size != 12) {
            throw InvalidPayloadException(
                packet,
                "Incorrect payload size in ${packet.command} packet; expected exactly 12 bytes, got ${packet.payload.size}"
            )
        }

        val payload = packet.payload

        val dateTime = LocalDateTime(
            second = payload[8].toPosInt(),
            minute = payload[7].toPosInt(),
            hour = payload[6].toPosInt(),
            day = payload[5].toPosInt(),
            month = payload[4].toPosInt(),
            year = (payload[2].toPosInt() shl 0) or (payload[3].toPosInt() shl 8)
        )

        logger(LogLevel.DEBUG) { "Current pump datetime: $dateTime" }

        return dateTime
    }

    /**
     * Parses a CMD_READ_PUMP_STATUS_RESPONSE packet and extracts its payload.
     *
     * @param packet Application layer CMD_READ_PUMP_STATUS_RESPONSE packet to parse.
     * @return The packet's parsed payload (the pump status).
     * @throws InvalidPayloadException if the payload size is not the expected size.
     */
    fun parseCMDReadPumpStatusResponsePacket(packet: Packet): CMDPumpStatus {
        logger(LogLevel.DEBUG) { "Parsing CMD_READ_PUMP_STATUS_RESPONSE packet" }

        // Payload size sanity check.
        if (packet.payload.size != 3) {
            throw InvalidPayloadException(
                packet,
                "Incorrect payload size in ${packet.command} packet; expected exactly 3 bytes, got ${packet.payload.size}"
            )
        }

        val payload = packet.payload

        val status = if (payload[2].toPosInt() == 0xB7)
            CMDPumpStatus.RUNNING
        else
            CMDPumpStatus.STOPPED

        logger(LogLevel.DEBUG) { "Pump status information: $status" }

        return status
    }

    /**
     * Parses a CMD_READ_ERROR_WARNING_STATUS_RESPONSE packet and extracts its payload.
     *
     * @param packet Application layer CMD_READ_ERROR_WARNING_STATUS_RESPONSE packet to parse.
     * @return The packet's parsed payload (the error/warning status).
     * @throws InvalidPayloadException if the payload size is not the expected size.
     */
    fun parseCMDReadErrorWarningStatusResponsePacket(packet: Packet): CMDErrorWarningStatus {
        logger(LogLevel.DEBUG) { "Parsing CMD_READ_ERROR_WARNING_STATUS_RESPONSE packet" }

        // Payload size sanity check.
        if (packet.payload.size != 4) {
            throw InvalidPayloadException(
                packet,
                "Incorrect payload size in ${packet.command} packet; expected exactly 4 bytes, got ${packet.payload.size}"
            )
        }

        val payload = packet.payload

        val errorWarningStatus = CMDErrorWarningStatus(
            errorOccurred = (payload[2].toPosInt() == 0xB7),
            warningOccurred = (payload[3].toPosInt() == 0xB7)
        )

        logger(LogLevel.DEBUG) { "Error/warning status: $errorWarningStatus" }

        return errorWarningStatus
    }

    /**
     * Parses a CMD_READ_HISTORY_BLOCK_RESPONSE packet and extracts its payload.
     *
     * @param packet Application layer CMD_READ_HISTORY_BLOCK_RESPONSE packet to parse.
     * @return The packet's parsed payload (the history block).
     * @throws InvalidPayloadException if the payload size is not the expected size.
     * @throws PayloadDataCorruptionException if the payload contains corrupted data.
     */
    fun parseCMDReadHistoryBlockResponsePacket(packet: Packet): CMDHistoryBlock {
        logger(LogLevel.DEBUG) { "Parsing CMD_READ_HISTORY_BLOCK_RESPONSE packet" }

        // Payload size sanity check.
        if (packet.payload.size < 7) {
            throw InvalidPayloadException(
                packet,
                "Incorrect payload size in ${packet.command} packet; expected at least 7 bytes, got ${packet.payload.size}"
            )
        }

        val payload = packet.payload

        val numEvents = payload[6].toPosInt()

        // Payload size sanity check. We expect the packet to contain
        // an amount of bytes that matches the expected size of the
        // events exactly. Anything else indicates that something is
        // wrong with the packet.
        val expectedPayloadSize = (7 + numEvents * 18)
        if (packet.payload.size != expectedPayloadSize) {
            throw PayloadDataCorruptionException(
                packet,
                "Incorrect payload size in ${packet.command} packet; expected $expectedPayloadSize bytes " +
                    "for a history block with $numEvents events, got ${packet.payload.size} bytes instead; " +
                    "event amount may have been corrupted"
            )
        }

        logger(LogLevel.DEBUG) { "Packet contains $numEvents history event(s)" }

        val numRemainingEvents = (payload[2].toPosInt() shl 0) or (payload[3].toPosInt() shl 8)
        val moreEventsAvailable = (payload[4].toPosInt() == 0x48)
        val historyGap = (payload[5].toPosInt() == 0x48)

        logger(LogLevel.DEBUG) {
            "History block information:  " +
                "num remaining events: $numRemainingEvents  " +
                "more events available: $moreEventsAvailable  " +
                "historyGap: $historyGap  " +
                "number of events: $numEvents"
        }

        val events = mutableListOf<CMDHistoryEvent>()
        for (eventIndex in 0 until numEvents) {
            val payloadOffset = 7 + eventIndex * 18

            // The first four bytes contain the timestamp:
            // byte 0: bits 0..5 : seconds                         bits 6..7 : lower 2 bits of the minutes
            // byte 1: bits 0..3 : upper 4 bits of the minutes     bits 4..7 : lower 4 bits of the hours
            // byte 2: bit 0 : highest bit of the hours            bits 1..5 : days                            bits 6..7 : lower 2 bits of the months
            // byte 3: bits 0..1 : upper 2 bits of the months      bits 2..7 : years
            val timestamp = LocalDateTime(
                second = payload[payloadOffset + 0].toPosInt() and 0b00111111,
                minute = ((payload[payloadOffset + 0].toPosInt() and 0b11000000) ushr 6) or
                    ((payload[payloadOffset + 1].toPosInt() and 0b00001111) shl 2),
                hour = ((payload[payloadOffset + 1].toPosInt() and 0b11110000) ushr 4) or
                    ((payload[payloadOffset + 2].toPosInt() and 0b00000001) shl 4),
                day = (payload[payloadOffset + 2].toPosInt() and 0b00111110) ushr 1,
                month = ((payload[payloadOffset + 2].toPosInt() and 0b11000000) ushr 6) or
                    ((payload[payloadOffset + 3].toPosInt() and 0b00000011) shl 2),
                year = ((payload[payloadOffset + 3].toPosInt() and 0b11111100) ushr 2) + 2000
            )

            val eventTypeId = (payload[payloadOffset + 8].toPosInt() shl 0) or
                (payload[payloadOffset + 9].toPosInt() shl 8)
            val detailBytesCrcChecksum = (payload[payloadOffset + 10].toPosInt() shl 0) or
                (payload[payloadOffset + 11].toPosInt() shl 8)
            val eventCounter = (payload[payloadOffset + 12].toPosLong() shl 0) or
                (payload[payloadOffset + 13].toPosLong() shl 8) or
                (payload[payloadOffset + 14].toPosLong() shl 16) or
                (payload[payloadOffset + 15].toPosLong() shl 24)
            val eventCounterCrcChecksum = (payload[payloadOffset + 16].toPosInt() shl 0) or
                (payload[payloadOffset + 17].toPosInt() shl 8)
            val detailBytes = payload.subList(payloadOffset + 4, payloadOffset + 8)

            logger(LogLevel.DEBUG) {
                "Event #$eventIndex:  timestamp $timestamp  event type ID $eventTypeId  " +
                    "detail bytes CRC16 checksum ${detailBytesCrcChecksum.toHexString(width = 4, prependPrefix = true)}  " +
                    "event counter $eventCounter  " +
                    "counter CRC16 checksum ${eventCounterCrcChecksum.toHexString(width = 4, prependPrefix = true)}  " +
                    "raw detail data bytes ${detailBytes.toHexString()}"
            }

            // The eventCounterCrcChecksum is the CRC-16-MCRF4XX checksum
            // of the 4 bytes that make up the event counter value.
            val computedEventCounterCrcChecksum = calculateCRC16MCRF4XX(payload.subList(payloadOffset + 12, payloadOffset + 16))
            val counterIntegrityOk = computedEventCounterCrcChecksum == eventCounterCrcChecksum
            if (!counterIntegrityOk) {
                throw PayloadDataCorruptionException(
                    packet,
                    "Integrity check failed for counter value of event #$eventIndex; computed CRC16 is " +
                        "checksum ${computedEventCounterCrcChecksum.toHexString(width = 4, prependPrefix = true)}, " +
                        "expected checksum is ${eventCounterCrcChecksum.toHexString(width = 4, prependPrefix = true)}"
                )
            }

            // The detailBytesCrcChecksum is the CRC-16-MCRF4XX checksum
            // of the first 10 bytes in the event's data (the "detail bytes").
            // This includes: timestamp, detail bytes, and the event type ID.
            val computedDetailCrcChecksum = calculateCRC16MCRF4XX(payload.subList(payloadOffset + 0, payloadOffset + 10))
            val detailIntegrityOk = computedDetailCrcChecksum == detailBytesCrcChecksum
            if (!detailIntegrityOk) {
                throw PayloadDataCorruptionException(
                    packet,
                    "Integrity check failed for detail bytes of event #$eventIndex; computed CRC16 is " +
                        "checksum ${computedDetailCrcChecksum.toHexString(width = 4, prependPrefix = true)}, " +
                        "expected checksum is ${detailBytesCrcChecksum.toHexString(width = 4, prependPrefix = true)}"
                )
            }

            // All bolus amounts are recorded as an integer that got multiplied by 10.
            // For example, an amount of 3.7 IU is recorded as the 16-bit integer 37.

            // NOTE: "Manual" means that the user manually administered the bolus
            // on the pump itself, with the pump's buttons. So, manual == false
            // specifies that the bolus was given off programmatically (that is,
            // through the CMD_DELIVER_BOLUS command).

            val eventDetail = when (eventTypeId) {
                // Quick bolus.
                4, 5           -> {
                    // Bolus amount is recorded in the first 2 detail bytes as a 16-bit little endian integer.
                    val bolusAmount = (detailBytes[1].toPosInt() shl 8) or detailBytes[0].toPosInt()
                    // Event type ID 4 = bolus requested. ID 5 = bolus infused (= it is done).
                    val requested = (eventTypeId == 4)

                    logger(LogLevel.DEBUG) {
                        "Detail info: got history event \"quick bolus ${if (requested) "requested" else "infused"}\" " +
                            "with amount of ${bolusAmount.toFloat() / 10} IU"
                    }

                    if (requested)
                        CMDHistoryEventDetail.QuickBolusRequested(
                            bolusAmount = bolusAmount
                        )
                    else
                        CMDHistoryEventDetail.QuickBolusInfused(
                            bolusAmount = bolusAmount
                        )
                }

                // Extended bolus.
                8, 9, 16, 17   -> {
                    // Total bolus amount is recorded in the first 2 detail bytes as a 16-bit little endian integer.
                    val totalBolusAmount = (detailBytes[1].toPosInt() shl 8) or detailBytes[0].toPosInt()
                    // Total duration in minutes is recorded in the next 2 detail bytes as a 16-bit little endian integer.
                    val totalDurationMinutes = (detailBytes[3].toPosInt() shl 8) or detailBytes[2].toPosInt()
                    // Event type IDs 8 or 16 = bolus started. IDs 9 or 17 = bolus ended.
                    val started = (eventTypeId == 8) || (eventTypeId == 16)
                    val manual = (eventTypeId == 8) || (eventTypeId == 9)

                    logger(LogLevel.DEBUG) {
                        "Detail info: got history event \"${if (manual) "manual" else "automatic"} " +
                            "extended bolus ${if (started) "started" else "ended"}\" " +
                            "with total amount of ${totalBolusAmount.toFloat() / 10} IU and " +
                            "total duration of $totalDurationMinutes minutes"
                    }

                    if (started)
                        CMDHistoryEventDetail.ExtendedBolusStarted(
                            totalBolusAmount = totalBolusAmount,
                            totalDurationMinutes = totalDurationMinutes,
                            manual = manual
                        )
                    else
                        CMDHistoryEventDetail.ExtendedBolusEnded(
                            totalBolusAmount = totalBolusAmount,
                            totalDurationMinutes = totalDurationMinutes,
                            manual = manual
                        )
                }

                // Multiwave bolus.
                10, 11, 18, 19 -> {
                    // All 8 bits of first byte + 2 LSB of second byte: bolus amount.
                    // 6 MSB of second byte + 4 LSB of third byte: immediate bolus amount.
                    // 4 MSB of third byte + all 8 bits of fourth byte: duration in minutes.
                    val totalBolusAmount = ((detailBytes[1].toPosInt() and 0b00000011) shl 8) or detailBytes[0].toPosInt()
                    val immediateBolusAmount = ((detailBytes[2].toPosInt() and 0b00001111) shl 6) or
                        ((detailBytes[1].toPosInt() and 0b11111100) ushr 2)
                    val totalDurationMinutes = (detailBytes[3].toPosInt() shl 4) or
                        ((detailBytes[2].toPosInt() and 0b11110000) ushr 4)
                    // Event type IDs 10 or 18 = bolus started. IDs 11 or 19 = bolus ended.
                    val started = (eventTypeId == 10) || (eventTypeId == 18)
                    val manual = (eventTypeId == 10) || (eventTypeId == 11)

                    logger(LogLevel.DEBUG) {
                        "Detail info: got history event \"${if (manual) "manual" else "automatic"} " +
                            "multiwave bolus ${if (started) "started" else "ended"}\" " +
                            "with total amount of ${totalBolusAmount.toFloat() / 10} IU, " +
                            "immediate amount of ${immediateBolusAmount.toFloat() / 10} IU, " +
                            "and total duration of $totalDurationMinutes minutes"
                    }

                    if (started)
                        CMDHistoryEventDetail.MultiwaveBolusStarted(
                            totalBolusAmount = totalBolusAmount,
                            immediateBolusAmount = immediateBolusAmount,
                            totalDurationMinutes = totalDurationMinutes,
                            manual = manual
                        )
                    else
                        CMDHistoryEventDetail.MultiwaveBolusEnded(
                            totalBolusAmount = totalBolusAmount,
                            immediateBolusAmount = immediateBolusAmount,
                            totalDurationMinutes = totalDurationMinutes,
                            manual = manual
                        )
                }

                // Standard bolus.
                6, 14, 7, 15   -> {
                    // Bolus amount is recorded in the first 2 detail bytes as a 16-bit little endian integer.
                    val bolusAmount = (detailBytes[1].toPosInt() shl 8) or detailBytes[0].toPosInt()
                    // Events with type IDs 6 and 7 indicate manual infusion.
                    val manual = (eventTypeId == 6) || (eventTypeId == 7)
                    // Events with type IDs 6 and 14 indicate that a bolus was requested, while
                    // events with type IDs 7 and 15 indicate that a bolus was infused (= finished).
                    val requested = (eventTypeId == 6) || (eventTypeId == 14)

                    logger(LogLevel.DEBUG) {
                        "Detail info: got history event \"${if (manual) "manual" else "automatic"} " +
                            "standard bolus ${if (requested) "requested" else "infused"}\" " +
                            "with amount of ${bolusAmount.toFloat() / 10} IU"
                    }

                    if (requested)
                        CMDHistoryEventDetail.StandardBolusRequested(
                            bolusAmount = bolusAmount,
                            manual = manual
                        )
                    else
                        CMDHistoryEventDetail.StandardBolusInfused(
                            bolusAmount = bolusAmount,
                            manual = manual
                        )
                }

                // New datetime set.
                24             -> {
                    // byte 0: bits 0..5 : seconds                         bits 6..7 : lower 2 bits of the minutes
                    // byte 1: bits 0..3 : upper 4 bits of the minutes     bits 4..7 : lower 4 bits of the hours
                    // byte 2: bit 0 : highest bit of the hours            bits 1..5 : days                            bits 6..7 : lower 2 bits of the months
                    // byte 3: bits 0..1 : upper 2 bits of the months      bits 2..7 : years

                    val newDateTime = LocalDateTime(
                        second = detailBytes[0].toPosInt() and 0b00111111,
                        minute = ((detailBytes[0].toPosInt() and 0b11000000) ushr 6) or
                            ((detailBytes[1].toPosInt() and 0b00001111) shl 2),
                        hour = ((detailBytes[1].toPosInt() and 0b11110000) ushr 4) or
                            ((detailBytes[2].toPosInt() and 0b00000001) shl 4),
                        day = (detailBytes[2].toPosInt() and 0b00111110) ushr 1,
                        month = ((detailBytes[2].toPosInt() and 0b11000000) ushr 6) or
                            ((detailBytes[3].toPosInt() and 0b00000011) shl 2),
                        year = ((detailBytes[3].toPosInt() and 0b11111100) ushr 2) + 2000
                    )

                    logger(LogLevel.DEBUG) {
                        "Detail info: got history event \"new datetime set\" with new datetime $newDateTime"
                    }

                    CMDHistoryEventDetail.NewDateTimeSet(newDateTime)
                }

                else           -> {
                    logger(LogLevel.DEBUG) {
                        "No detail info available: event type ID unrecognized; skipping this event"
                    }
                    continue
                }
            }

            events.add(
                CMDHistoryEvent(
                    timestamp = timestamp,
                    eventCounter = eventCounter,
                    detail = eventDetail
                )
            )
        }

        return CMDHistoryBlock(
            numRemainingEvents = numRemainingEvents,
            moreEventsAvailable = moreEventsAvailable,
            historyGap = historyGap,
            events = events
        )
    }

    /**
     * Parses a CMD_GET_BOLUS_STATUS_RESPONSE packet and extracts its payload.
     *
     * @param packet Application layer CMD_GET_BOLUS_STATUS_RESPONSE packet to parse.
     * @return The packet's parsed payload (the current bolus delivery status).
     * @throws InvalidPayloadException if the payload size is not the expected size,
     * @throws PayloadDataCorruptionException if the payload contains corrupted data.
     */
    fun parseCMDGetBolusStatusResponsePacket(packet: Packet): CMDBolusDeliveryStatus {
        logger(LogLevel.DEBUG) { "Parsing CMD_GET_BOLUS_STATUS_RESPONSE packet" }

        // Payload size sanity check.
        if (packet.payload.size != 8) {
            throw InvalidPayloadException(
                packet,
                "Incorrect payload size in ${packet.command} packet; expected exactly 8 bytes, got ${packet.payload.size}"
            )
        }

        val payload = packet.payload

        val bolusTypeInt = payload[2].toPosInt()
        val bolusType = CMDImmediateBolusType.fromInt(bolusTypeInt)
            ?: throw PayloadDataCorruptionException(
                packet,
                "Invalid bolus type ${bolusTypeInt.toHexString(2, true)}"
            )

        val deliveryStateInt = payload[3].toPosInt()
        val deliveryState = CMDBolusDeliveryState.fromInt(deliveryStateInt)
            ?: throw PayloadDataCorruptionException(
                packet,
                "Invalid delivery state ${deliveryStateInt.toHexString(2, true)}"
            )

        val bolusStatus = CMDBolusDeliveryStatus(
            bolusType = bolusType,
            deliveryState = deliveryState,
            remainingAmount = (payload[4].toPosInt() shl 0) or (payload[5].toPosInt() shl 8)
        )

        logger(LogLevel.DEBUG) { "Bolus status: $bolusStatus" }

        return bolusStatus
    }

    /**
     * Parses a CMD_DELIVER_BOLUS_RESPONSE packet and extracts its payload.
     *
     * @param packet Application layer CMD_DELIVER_BOLUS_RESPONSE packet to parse.
     * @return true if the bolus was delivered correctly, false otherwise.
     * @throws InvalidPayloadException if the payload size is not the expected size,
     */
    fun parseCMDDeliverBolusResponsePacket(packet: Packet): Boolean {
        logger(LogLevel.DEBUG) { "Parsing CMD_DELIVER_BOLUS_RESPONSE packet" }

        // Payload size sanity check.
        if (packet.payload.size != 3) {
            throw InvalidPayloadException(
                packet,
                "Incorrect payload size in ${packet.command} packet; expected exactly 3 bytes, got ${packet.payload.size}"
            )
        }

        val payload = packet.payload

        val bolusStarted = (payload[2].toPosInt() == 0x48)

        logger(LogLevel.DEBUG) { "Bolus started: $bolusStarted" }

        return bolusStarted
    }

    /**
     * Parses a CMD_CANCEL_BOLUS_RESPONSE packet and extracts its payload.
     *
     * @param packet Application layer CMD_CANCEL_BOLUS_RESPONSE packet to parse.
     * @return true if the bolus was cancelled, false otherwise.
     * @throws InvalidPayloadException if the payload size is not the expected size,
     */
    fun parseCMDCancelBolusResponsePacket(packet: Packet): Boolean {
        logger(LogLevel.DEBUG) { "Parsing CMD_CANCEL_BOLUS_RESPONSE packet" }

        // Payload size sanity check.
        if (packet.payload.size != 3) {
            throw InvalidPayloadException(
                packet,
                "Incorrect payload size in ${packet.command} packet; expected exactly 3 bytes, got ${packet.payload.size}"
            )
        }

        val payload = packet.payload

        val bolusCancelled = (payload[2].toPosInt() == 0x48)

        logger(LogLevel.DEBUG) { "Bolus cancelled: $bolusCancelled" }

        return bolusCancelled
    }

    /**
     * Creates an RT_BUTTON_STATUS packet.
     *
     * The RT mode must have been activated before this can be sent to the Combo.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * To implement multiple pressed buttons, use a bitwise OR combination
     * of IDs from the RTButtonCode enum.
     *
     * @param rtButtonCodes Button ID / combined button IDs.
     * @param buttonStatusChanged Whether or not the button status
     *        actually changed since the last time the status was
     *        sent to the Combo.
     * @return The produced packet.
     */
    fun createRTButtonStatusPacket(rtButtonCodes: Int, buttonStatusChanged: Boolean) = Packet(
        command = Command.RT_BUTTON_STATUS,
        payload = byteArrayListOfInts(
            0x00, 0x00, // RT sequence - will be filled in later by sendPacket()
            rtButtonCodes,
            if (buttonStatusChanged) 0xB7 else 0x48
        )
    )

    /**
     * Creates an RT_KEEP_ALIVE packet.
     *
     * The RT mode must have been activated before this can be sent to the Combo.
     *
     * See the combo-comm-spec.adoc file for details about this packet.
     *
     * @return The produced packet.
     */
    fun createRTKeepAlivePacket() = Packet(
        command = Command.RT_KEEP_ALIVE,
        payload = byteArrayListOfInts(
            0x00, 0x00 // RT sequence - will be filled in later by sendPacket()
        )
    )

    /**
     * Parses an RT_DISPLAY packet and extracts its payload.
     *
     * @param packet Application layer RT_DISPLAY packet to parse.
     * @return The packet's parsed payload.
     * @throws InvalidPayloadException if the payload size is not the expected size,
     *         or if the payload contains an invalid display row ID or reason.
     */
    fun parseRTDisplayPacket(packet: Packet): RTDisplayPayload {
        val payload = packet.payload
        checkPayloadSize(packet, 5 + 96)

        val reasonInt = payload[2].toPosInt()
        val reason = RTDisplayUpdateReason.fromInt(reasonInt) ?: throw InvalidPayloadException(
            packet, "Invalid RT display update reason $reasonInt"
        )

        val row = when (val rowInt = payload[4].toPosInt()) {
            0x47 -> 0
            0x48 -> 1
            0xB7 -> 2
            0xB8 -> 3
            else -> throw InvalidPayloadException(packet, "Invalid RT display update row $rowInt")
        }

        return RTDisplayPayload(
            currentRTSequence = (payload[0].toPosInt() shl 0) or (payload[1].toPosInt() shl 8),
            reason = reason,
            index = payload[3].toPosInt(),
            row = row,
            rowBytes = payload.subList(5, 101)
        )
    }

    /**
     * Parses an RT_AUDIO packet and extracts its payload.
     *
     * @param packet Application layer RT_AUDIO packet to parse.
     * @return The packet's parsed payload (the 32-bit little
     *         endian integer specifying the audio type).
     * @throws InvalidPayloadException if the payload size is not the expected size.
     */
    fun parseRTAudioPacket(packet: Packet): Int {
        val payload = packet.payload
        checkPayloadSize(packet, 6)

        // The first 2 bytes in the payload contain the RT
        // sequence number, which we are not interested in,
        // so we ignore these 2 bytes.

        return (payload[2].toPosInt() shl 0) or
            (payload[3].toPosInt() shl 8) or
            (payload[4].toPosInt() shl 16) or
            (payload[5].toPosInt() shl 24)
    }

    /**
     * Parses an RT_VIBRATION packet and extracts its payload.
     *
     * @param packet Application layer RT_VIBRATION packet to parse.
     * @return The packet's parsed payload (the 32-bit little
     *         endian integer specifying the vibration type).
     * @throws InvalidPayloadException if the payload size is not the expected size.
     */
    fun parseRTVibrationPacket(packet: Packet): Int {
        val payload = packet.payload
        checkPayloadSize(packet, 6)

        // The first 2 bytes in the payload contain the RT
        // sequence number, which we are not interested in,
        // so we ignore these 2 bytes.

        return (payload[2].toPosInt() shl 0) or
            (payload[3].toPosInt() shl 8) or
            (payload[4].toPosInt() shl 16) or
            (payload[5].toPosInt() shl 24)
    }

    /**
     * Utility function to produce an application layer packet out of a transport layer packet, with extra checks.
     *
     * This is preferred over directly using the [Packet] constructor.
     */
    fun checkAndParseTransportLayerDataPacket(tpLayerPacket: TransportLayer.Packet):
        Packet {
        try {
            logger(LogLevel.VERBOSE) { "Parsing DATA packet as application layer packet" }
            val appLayerPacket = Packet(tpLayerPacket)
            logger(LogLevel.VERBOSE) {
                "This is an application layer packet with command ${appLayerPacket.command} and payload ${appLayerPacket.payload.toHexString()}"
            }

            // Application layer packets which were transmitted inside a transport
            // layer packet with the reliability bit set always have a 16-bit error
            // code in their first 2 bytes. Parse that error code, and if it is not
            // set to NO_ERROR, throw an exception, since it is not known how to
            // recover from those errors.
            // An exception is made for CTRL_SERVICE_ERROR packets. These are passed
            // through, since they are supposed to inform the caller about an error.
            if ((tpLayerPacket.reliabilityBit) && (appLayerPacket.command != Command.CTRL_SERVICE_ERROR)) {
                logger(LogLevel.VERBOSE) {
                    "This packet was delivered inside a reliable transport layer packet; checking the error code"
                }

                val errorCode = parseErrorCode(appLayerPacket.payload)

                if (errorCode != ErrorCode.Known(ErrorCode.Known.Code.NO_ERROR)) {
                    logger(LogLevel.ERROR) { "Application layer packet has error code $errorCode" }
                    throw ErrorCodeException(appLayerPacket, errorCode)
                }
            }

            return appLayerPacket
        } catch (e: InvalidCommandIDException) {
            logger(LogLevel.WARN) {
                "Got an application layer packet with invalid/unknown command ID 0x${e.commandID.toString(16)} " +
                    "service ID ${e.serviceID.name} and payload (with ${e.payload.size} byte(s)) ${e.payload.toHexString()}" +
                    ""
            }
            throw e
        } catch (e: ErrorCodeException) {
            // We already logged the error code, so just pass through the exception
            throw e
        } catch (e: ExceptionBase) {
            logger(LogLevel.ERROR) { "Could not parse DATA packet as application layer packet: $e" }
            throw e
        }
    }

    private fun checkPayloadSize(packet: Packet, expectedPayloadSize: Int) {
        if (packet.payload.size != expectedPayloadSize) {
            throw InvalidPayloadException(
                packet,
                "Incorrect payload size in ${packet.command} packet; expected $expectedPayloadSize byte(s), got ${packet.payload.size}"
            )
        }
    }
}

internal fun TransportLayer.Packet.toAppLayerPacket() =
    ApplicationLayer.checkAndParseTransportLayerDataPacket(this)
