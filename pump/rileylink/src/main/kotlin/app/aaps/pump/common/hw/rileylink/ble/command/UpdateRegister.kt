package app.aaps.pump.common.hw.rileylink.ble.command

import app.aaps.pump.common.hw.rileylink.ble.defs.CC111XRegister
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType

class UpdateRegister(val register: CC111XRegister, val registerValue: Byte) : RileyLinkCommand() {

    override fun getCommandType(): RileyLinkCommandType = RileyLinkCommandType.UpdateRegister
    override fun getRaw(): ByteArray = getByteArray(getCommandType().code, register.value, registerValue)
}
