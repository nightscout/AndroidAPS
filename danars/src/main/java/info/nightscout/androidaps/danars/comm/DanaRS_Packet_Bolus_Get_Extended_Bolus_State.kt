package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.androidaps.utils.T
import javax.inject.Inject

class DanaRS_Packet_Bolus_Get_Extended_Bolus_State(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__GET_EXTENDED_BOLUS_STATE
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        var dataIndex = DATA_START
        var dataSize = 1
        val error = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val isExtendedInProgress = byteArrayToInt(getBytes(data, dataIndex, dataSize)) == 0x01
        dataIndex += dataSize
        dataSize = 1
        val extendedBolusDuration = T.mins(byteArrayToInt(getBytes(data, dataIndex, dataSize)) * 30L).msecs()
        dataIndex += dataSize
        dataSize = 2
        val extendedBolusAbsoluteRate = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0 // duration must be set first for recalculation to amount
        dataIndex += dataSize
        dataSize = 2
        val extendedBolusSoFarInMinutes = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        val extendedBolusDeliveredSoFar = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        failed = error != 0
        aapsLogger.debug(LTag.PUMPCOMM, "Result: $error")
        aapsLogger.debug(LTag.PUMPCOMM, "Is extended bolus running: $isExtendedInProgress")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus running: $extendedBolusAbsoluteRate U/h")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus duration: " + T.msecs(extendedBolusDuration).mins() + " min")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus so far: $extendedBolusSoFarInMinutes min")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus delivered so far: $extendedBolusDeliveredSoFar U")
    }

    override fun getFriendlyName(): String {
        return "BOLUS__GET_EXTENDED_BOLUS_STATE"
    }
}