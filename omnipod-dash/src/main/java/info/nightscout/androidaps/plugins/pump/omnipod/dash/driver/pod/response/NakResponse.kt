package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlarmType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.NakErrorType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus
import java.util.*

class NakResponse(encoded: ByteArray) : ResponseBase(ResponseType.NAK_RESPONSE, encoded) {

    private val messageType: Byte // TODO directly assign here
    private val messageLength: Short
    private val nakErrorType: NakErrorType
    private var alarmType: AlarmType? = null
    private var podStatus: PodStatus? = null
    private var securityNakSyncCount: Short = 0
    fun getMessageType(): Byte {
        return messageType
    }

    fun getMessageLength(): Short {
        return messageLength
    }

    fun getNakErrorType(): NakErrorType { // TODO make public, a val cannot be reassigned, same for other Responses
        return nakErrorType
    }

    fun getAlarmType(): AlarmType? {
        return alarmType
    }

    fun getPodStatus(): PodStatus? {
        return podStatus
    }

    fun getSecurityNakSyncCount(): Short {
        return securityNakSyncCount
    }

    override fun toString(): String {
        return "NakResponse{" +
            "messageType=" + messageType +
            ", messageLength=" + messageLength +
            ", nakErrorType=" + nakErrorType +
            ", alarmType=" + alarmType +
            ", podStatus=" + podStatus +
            ", securityNakSyncCount=" + securityNakSyncCount +
            ", responseType=" + responseType +
            ", encoded=" + encoded.contentToString() +
            '}'
    }

    init {
        messageType = encoded[0]
        messageLength = encoded[1].toShort()
        nakErrorType = NakErrorType.byValue(encoded[2])
        val byte3 = encoded[3]
        val byte4 = encoded[4]
        if (nakErrorType == NakErrorType.ILLEGAL_SECURITY_CODE) {
            securityNakSyncCount = ((byte3.toInt() shl 8 or byte4.toInt()).toShort()) // TODO: toInt()
            alarmType = null
            podStatus = null
        } else {
            securityNakSyncCount = 0
            alarmType = AlarmType.byValue(byte3)
            podStatus = PodStatus.byValue(byte4)
        }
    }
}