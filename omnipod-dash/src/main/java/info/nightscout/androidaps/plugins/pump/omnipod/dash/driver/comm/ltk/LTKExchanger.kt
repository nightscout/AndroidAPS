package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.ltk

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.Address
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessagePacket
import info.nightscout.androidaps.utils.extensions.hexStringToByteArray

internal class LTKExchanger(private val aapsLogger: AAPSLogger,private val msgIO: MessageIO) {

    fun negociateLTKAndNonce(): LTK? {
        val msg = MessagePacket(
            destination = Address(byteArrayOf(1,2,3,4)),
            source = Address(byteArrayOf(5,6,7,8)),
            payload = "545710030100038002420000fffffffe5350313d0004024200032c5350323d000bffc32dbd20030e01000016".hexStringToByteArray(),
            sequenceNumber = 1,
        )
        msgIO.sendMesssage(msg)

        return null
    }

}
