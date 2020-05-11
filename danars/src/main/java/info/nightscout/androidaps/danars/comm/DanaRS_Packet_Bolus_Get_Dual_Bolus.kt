package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRS_Packet_Bolus_Get_Dual_Bolus(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__GET_DUAL_BOLUS
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        val error = byteArrayToInt(getBytes(data, DATA_START, 1))
        danaPump.bolusStep = byteArrayToInt(getBytes(data, DATA_START + 1, 2)) / 100.0
        danaPump.extendedBolusAbsoluteRate = byteArrayToInt(getBytes(data, DATA_START + 3, 2)) / 100.0
        danaPump.maxBolus = byteArrayToInt(getBytes(data, DATA_START + 5, 2)) / 100.0
        val bolusIncrement = byteArrayToInt(getBytes(data, DATA_START + 7, 1)) / 100.0
        failed = error != 0
        aapsLogger.debug(LTag.PUMPCOMM, "Result: $error")
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus step: ${danaPump.bolusStep} U")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus running: ${danaPump.extendedBolusAbsoluteRate} U/h")
        aapsLogger.debug(LTag.PUMPCOMM, "Max bolus: " + danaPump.maxBolus + " U")
        aapsLogger.debug(LTag.PUMPCOMM, "bolusIncrement: $bolusIncrement U")
    }

    override fun getFriendlyName(): String {
        return "BOLUS__GET_DUAL_BOLUS"
    }
}