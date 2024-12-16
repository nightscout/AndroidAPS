package app.aaps.pump.common.hw.rileylink.ble.command

import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType

class SetHardwareEncoding(private val encoding: RileyLinkEncodingType) : RileyLinkCommand() {

    override fun getCommandType(): RileyLinkCommandType = RileyLinkCommandType.SetHardwareEncoding
    override fun getRaw(): ByteArray = getByteArray(getCommandType().code, encoding.value)
}
