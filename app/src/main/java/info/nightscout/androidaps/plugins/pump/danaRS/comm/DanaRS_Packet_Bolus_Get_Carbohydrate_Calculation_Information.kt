package info.nightscout.androidaps.plugins.pump.danaRS.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaRS.encryption.BleEncryption
import javax.inject.Inject

class DanaRS_Packet_Bolus_Get_Carbohydrate_Calculation_Information(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaRPump: DanaRPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__GET_CARBOHYDRATE_CALCULATION_INFORMATION
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        var dataIndex = DATA_START
        var dataSize = 1
        val error = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        val carbs = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaRPump.currentCIR = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        if (error != 0) failed = true
        aapsLogger.debug(LTag.PUMPCOMM, "Result: $error")
        aapsLogger.debug(LTag.PUMPCOMM, "Carbs: $carbs")
        aapsLogger.debug(LTag.PUMPCOMM, "Current CIR: " + danaRPump.currentCIR)
    }

    override fun getFriendlyName(): String {
        return "BOLUS__GET_CARBOHYDRATE_CALCULATION_INFORMATION"
    }
}