package info.nightscout.androidaps.plugins.pump.danaRS.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaRS.encryption.BleEncryption
import javax.inject.Inject

class DanaRS_Packet_General_Get_Today_Delivery_Total(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaRPump: DanaRPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__GET_TODAY_DELIVERY_TOTAL
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        if (data.size < 8) {
            failed = true
            return
        } else failed = false
        var dataIndex = DATA_START
        var dataSize = 2
        danaRPump.dailyTotalUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        danaRPump.dailyTotalBasalUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        danaRPump.dailyTotalBolusUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total: " + danaRPump.dailyTotalUnits + " U")
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total bolus: " + danaRPump.dailyTotalBolusUnits + " U")
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total basal: " + danaRPump.dailyTotalBasalUnits + " U")
    }

    override fun getFriendlyName(): String {
        return "REVIEW__GET_TODAY_DELIVERY_TOTAL"
    }
}