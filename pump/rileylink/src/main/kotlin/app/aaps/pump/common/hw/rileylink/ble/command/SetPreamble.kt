package app.aaps.pump.common.hw.rileylink.ble.command

import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import org.apache.commons.lang3.NotImplementedException
import java.nio.ByteBuffer

class SetPreamble(rileyLinkServiceData: RileyLinkServiceData, private val preamble: Int) : RileyLinkCommand() {

    init {
        // this command was not supported before 2.0
        if (rileyLinkServiceData.firmwareVersion?.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher) == false) {
            throw NotImplementedException("Old firmware does not support SetPreamble command")
        }

        require(!(preamble < 0 || preamble > 0xFFFF)) { "preamble value is out of range" }
    }

    override fun getCommandType(): RileyLinkCommandType = RileyLinkCommandType.SetPreamble
    override fun getRaw(): ByteArray {
        val bytes = ByteBuffer.allocate(4).putInt(preamble).array()
        return getByteArray(this.getCommandType().code, bytes[2], bytes[3])
    }
}
