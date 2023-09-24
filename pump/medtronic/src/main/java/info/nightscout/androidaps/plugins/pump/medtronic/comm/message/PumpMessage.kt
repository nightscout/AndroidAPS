package info.nightscout.androidaps.plugins.pump.medtronic.comm.message

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RLMessage
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType
import info.nightscout.pump.common.utils.ByteUtil
import kotlin.math.min

/**
 * Created by geoff on 5/29/16.
 */
class PumpMessage : RLMessage {

    private val aapsLogger: AAPSLogger
    private var packetType: PacketType? = PacketType.Carelink
    var address: ByteArray? = byteArrayOf(0, 0, 0)
    var commandType: MedtronicCommandType? = null
    private var invalidCommandType: Byte? = null
    var messageBody: MessageBody? = MessageBody()
    var error: String? = null

    constructor(aapsLogger: AAPSLogger, error: String?) {
        this.error = error
        this.aapsLogger = aapsLogger
    }

    constructor(aapsLogger: AAPSLogger, rxData: ByteArray?) {
        this.aapsLogger = aapsLogger
        init(rxData)
    }

    constructor(aapsLogger: AAPSLogger) {
        this.aapsLogger = aapsLogger
    }

    @Suppress("unused") val isErrorResponse: Boolean
        get() = error != null

    fun init(packetType: PacketType?, address: ByteArray?, commandType: MedtronicCommandType?, messageBody: MessageBody?) {
        this.packetType = packetType
        this.address = address
        this.commandType = commandType
        this.messageBody = messageBody
    }

    fun init(rxData: ByteArray?) {
        if (rxData == null) {
            return
        }
        if (rxData.isNotEmpty()) {
            packetType = PacketType.getByValue(rxData[0].toShort())
        }
        if (rxData.size > 3) {
            address = ByteUtil.substring(rxData, 1, 3)
        }
        if (rxData.size > 4) {
            commandType = MedtronicCommandType.getByCode(rxData[4])
            if (commandType == MedtronicCommandType.InvalidCommand) {
                aapsLogger.error(LTag.PUMPCOMM, "PumpMessage - Unknown commandType " + rxData[4])
            }
        }
        if (rxData.size > 5) {
            messageBody = MedtronicCommandType.constructMessageBody(
                commandType,
                ByteUtil.substring(rxData, 5, rxData.size - 5)
            )
        }
    }

    override fun getTxData(): ByteArray {
        var rval = ByteUtil.concat(byteArrayOf(packetType!!.value), address)
        rval = ByteUtil.concat(rval, commandType!!.commandCode)
        rval = ByteUtil.concat(rval, messageBody!!.txData)
        return rval
    }

    val contents: ByteArray
        get() = ByteUtil.concat(byteArrayOf(commandType!!.commandCode), messageBody!!.txData)// length is not always correct so, we check whole array if we have
    // data, after length

    // check if displayed length is invalid

    // check Old Way

//        if (isLogEnabled())
//            LOG.debug("PumpMessage - Length: " + length + ", Original Length: " + originalLength + ", CommandType: "
//            + commandType);

    // rawContent = just response without code (contents-2, messageBody.txData-1);
    val rawContent: ByteArray
        get() {
            if (messageBody == null || messageBody!!.txData == null || messageBody?.txData?.size == 0) return byteArrayOf()
            val data = messageBody!!.txData
            var length = ByteUtil.asUINT8(data!![0]) // length is not always correct so, we check whole array if we have
            // data, after length
            //val originalLength = length

            // check if displayed length is invalid
            if (length > data.size - 1) {
                return data
            }

            // check Old Way
            var oldWay = false
            for (i in length + 1 until data.size) {
                if (data[i] != 0x00.toByte()) {
                    oldWay = true
                }
            }
            if (oldWay) {
                length = data.size - 1
            }
            val arrayOut = ByteArray(length)
            messageBody?.txData?.let {
                System.arraycopy(it, 1, arrayOut, 0, length)
            }

//        if (isLogEnabled())
//            LOG.debug("PumpMessage - Length: " + length + ", Original Length: " + originalLength + ", CommandType: "
//            + commandType);
            return arrayOut
        }

    val rawContentOfFrame: ByteArray
        get() {
            val raw = messageBody!!.txData
            return if (raw == null || raw.isEmpty()) {
                byteArrayOf()
            } else {
                ByteUtil.substring(raw, 1, min(FRAME_DATA_LENGTH, raw.size - 1))
            }
        }

    override fun isValid(): Boolean {
        if (packetType == null) return false
        if (address == null) return false
        return if (commandType == null) false else messageBody != null
    }

    val responseContent: String
        get() {
            val sb = StringBuilder("PumpMessage [response=")
            var showData = true
            if (commandType != null) {
                if (commandType == MedtronicCommandType.CommandACK) {
                    sb.append("Acknowledged")
                    showData = false
                } else if (commandType == MedtronicCommandType.CommandNAK) {
                    sb.append("NOT Acknowledged")
                    showData = false
                } else {
                    sb.append(commandType!!.name)
                }
            } else {
                sb.append("Unknown_Type")
                sb.append(" ($invalidCommandType)")
            }
            if (showData) {
                sb.append(", rawResponse=")
                sb.append(ByteUtil.shortHexString(rawContent))
            }
            sb.append("]")
            return sb.toString()
        }

    override fun toString(): String {
        val sb = StringBuilder("PumpMessage [")
        sb.append("packetType=")
        sb.append(if (packetType == null) "null" else packetType!!.name)
        sb.append(", address=(")
        sb.append(ByteUtil.shortHexString(address))
        sb.append("), commandType=")
        sb.append(if (commandType == null) "null" else commandType!!.name)
        if (invalidCommandType != null) {
            sb.append(", invalidCommandType=")
            sb.append(invalidCommandType)
        }
        sb.append(", messageBody=(")
        sb.append(if (messageBody == null) "null" else messageBody)
        sb.append(")]")
        return sb.toString()
    }

    companion object {

        const val FRAME_DATA_LENGTH = 64
    }
}