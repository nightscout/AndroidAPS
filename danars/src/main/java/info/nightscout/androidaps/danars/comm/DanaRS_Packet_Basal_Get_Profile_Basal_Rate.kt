package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import java.util.*
import javax.inject.Inject

open class DanaRS_Packet_Basal_Get_Profile_Basal_Rate(
    injector: HasAndroidInjector,
    private val profileNumber: Int = 0
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_BASAL_RATE
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
        danaPump.pumpProfiles = Array(4) { Array(48) { 0.0 } }
        var i = 0
        val size = 24
        while (i < size) {
            danaPump.pumpProfiles!![profileNumber][i] = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
            dataIndex += dataSize
            dataSize = 2
            i++
        }
        for (index in 0..23)
            aapsLogger.debug(LTag.PUMPCOMM, "Basal " + String.format(Locale.ENGLISH, "%02d", index) + "h: " + danaPump.pumpProfiles!![profileNumber][index])
    }

    override fun getFriendlyName(): String {
        return "BASAL__GET_PROFILE_BASAL_RATE"
    }
}