package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaRPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRS_Packet_General_Get_Password(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaRPump: DanaRPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__GET_PASSWORD
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        if (data.size < 2) { // returned data size is too small
            failed = true
            return
        } else {
            failed = false
        }
        var pass: Int = (data[DATA_START + 1].toInt() and 0x000000FF shl 8) + (data[DATA_START + 0].toInt() and 0x000000FF)
        pass = pass xor 3463
        danaRPump.rsPassword = Integer.toHexString(pass)
        aapsLogger.debug(LTag.PUMPCOMM, "Pump password: " + danaRPump.rsPassword)
    }

    override fun getFriendlyName(): String {
        return "REVIEW__GET_PASSWORD"
    }
}