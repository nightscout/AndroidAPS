package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRS_Packet_General_Get_Today_Delivery_Total(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaPump: DanaPump

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
        danaPump.dailyTotalUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        danaPump.dailyTotalBasalUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        danaPump.dailyTotalBolusUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total: " + danaPump.dailyTotalUnits + " U")
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total bolus: " + danaPump.dailyTotalBolusUnits + " U")
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total basal: " + danaPump.dailyTotalBasalUnits + " U")
    }

    override fun getFriendlyName(): String {
        return "REVIEW__GET_TODAY_DELIVERY_TOTAL"
    }
}