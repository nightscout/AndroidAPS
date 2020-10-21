package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRS_Packet_Basal_Get_Profile_Number(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_NUMBER
        aapsLogger.debug(LTag.PUMPCOMM, "Requesting active profile")
    }

    override fun handleMessage(data: ByteArray) {
        danaPump.activeProfile = byteArrayToInt(getBytes(data, DATA_START, 1))
        aapsLogger.debug(LTag.PUMPCOMM, "Active profile: " + danaPump.activeProfile)
    }

    override fun getFriendlyName(): String {
        return "BASAL__GET_PROFILE_NUMBER"
    }

}