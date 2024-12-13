package app.aaps.pump.omnipod.dash.driver.pod.response

import app.aaps.pump.omnipod.dash.driver.pod.definition.AlarmType
import app.aaps.pump.omnipod.dash.driver.pod.definition.NakErrorType
import app.aaps.pump.omnipod.dash.driver.pod.definition.PodStatus
import app.aaps.pump.omnipod.dash.driver.pod.util.byValue

class NakResponse(
    encoded: ByteArray
) : ResponseBase(ResponseType.NAK_RESPONSE, encoded) {

    val messageType: Byte = encoded[0]
    val messageLength: Short = encoded[1].toShort()
    val nakErrorType: NakErrorType = byValue(encoded[2], NakErrorType.UNKNOWN)
    var alarmType: AlarmType? = null
        private set
    var podStatus: PodStatus? = null
        private set

    var securityNakSyncCount: Short = 0
        private set

    init {
        val byte3 = encoded[3]
        val byte4 = encoded[4]
        if (nakErrorType == NakErrorType.ILLEGAL_SECURITY_CODE) {
            securityNakSyncCount = ((byte3.toInt() shl 8 or byte4.toInt()).toShort())
            alarmType = null
            podStatus = null
        } else {
            securityNakSyncCount = 0
            alarmType = byValue(byte3, AlarmType.UNKNOWN)
            podStatus = byValue(byte4, PodStatus.UNKNOWN)
        }
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
}
