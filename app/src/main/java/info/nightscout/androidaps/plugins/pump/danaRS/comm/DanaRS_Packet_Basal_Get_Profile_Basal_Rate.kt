package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import java.util.*

open class DanaRS_Packet_Basal_Get_Profile_Basal_Rate(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump,
    private val profileNumber: Int = 0
) : DanaRS_Packet() {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_BASAL_RATE
        aapsLogger.debug(LTag.PUMPCOMM, "Requesting basal rates for profile $profileNumber")
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(1)
        request[0] = (profileNumber and 0xff).toByte()
        return request
    }

    override fun handleMessage(data: ByteArray) {
        var dataIndex = DATA_START
        var dataSize = 2
        danaRPump.pumpProfiles = Array(4) {Array(48) {0.0} }
        var i = 0
        val size = 24
        while (i < size) {
            danaRPump.pumpProfiles!![profileNumber][i] = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
            dataIndex += dataSize
            dataSize = 2
            i++
        }
        for (index in 0..23)
            aapsLogger.debug(LTag.PUMPCOMM, "Basal " + String.format(Locale.ENGLISH, "%02d", index) + "h: " + danaRPump.pumpProfiles!![profileNumber][index])
    }

    override fun getFriendlyName(): String {
        return "BASAL__GET_PROFILE_BASAL_RATE"
    }
}