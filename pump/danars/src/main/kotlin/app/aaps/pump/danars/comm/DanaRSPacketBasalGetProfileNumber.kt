package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.dana.DanaPump
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRSPacketBasalGetProfileNumber(
    injector: HasAndroidInjector
) : DanaRSPacket(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_NUMBER
        aapsLogger.debug(LTag.PUMPCOMM, "Requesting active profile")
    }

    override fun handleMessage(data: ByteArray) {
        danaPump.activeProfile = byteArrayToInt(getBytes(data, DATA_START, 1))
        aapsLogger.debug(LTag.PUMPCOMM, "Active profile: " + danaPump.activeProfile)
    }

    override val friendlyName: String = "BASAL__GET_PROFILE_NUMBER"
}