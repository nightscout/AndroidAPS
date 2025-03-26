package app.aaps.pump.common.hw.rileylink.ble.command

import app.aaps.core.utils.pump.ByteUtil
import app.aaps.pump.common.hw.rileylink.ble.data.RadioPacket
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import java.nio.ByteBuffer
import java.util.ArrayList

class SendAndListen(
    val rileyLinkServiceData: RileyLinkServiceData,
    val sendChannel: Byte,
    val repeatCount: Byte,
    val delayBetweenPacketsMs: Int,
    val listenChannel: Byte,
    val timeoutMs: Int,
    val retryCount: Byte,
    var preambleExtensionMs: Int = 0,
    val packetToSend: RadioPacket
) : RileyLinkCommand() {

    override fun getCommandType(): RileyLinkCommandType {
        return RileyLinkCommandType.SendAndListen
    }

    override fun getRaw(): ByteArray {
        // If firmware version is not set (error reading version from device, shouldn't happen),
        // we will default to version 2

        val isPacketV2 = rileyLinkServiceData.firmwareVersion == null || rileyLinkServiceData.firmwareVersion?.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher) == true

        val bytes = ArrayList<Byte>()
        bytes.add(this.getCommandType().code)
        bytes.add(this.sendChannel)
        bytes.add(this.repeatCount)

        if (isPacketV2) { // delay is unsigned 16-bit integer
            val delayBuff = ByteBuffer.allocate(4).putInt(delayBetweenPacketsMs).array()
            bytes.add(delayBuff[2])
            bytes.add(delayBuff[3])
        } else {
            bytes.add(delayBetweenPacketsMs.toByte())
        }

        bytes.add(this.listenChannel)

        val timeoutBuff = ByteBuffer.allocate(4).putInt(timeoutMs).array()

        bytes.add(timeoutBuff[0])
        bytes.add(timeoutBuff[1])
        bytes.add(timeoutBuff[2])
        bytes.add(timeoutBuff[3])

        bytes.add(retryCount)

        if (isPacketV2) { // 2.x (and probably higher versions) support preamble extension
            val preambleBuf = ByteBuffer.allocate(4).putInt(preambleExtensionMs).array()
            bytes.add(preambleBuf[2])
            bytes.add(preambleBuf[3])
        }

        return ByteUtil.concat(ByteUtil.getByteArrayFromList(bytes), packetToSend.getEncoded())
    }
}
