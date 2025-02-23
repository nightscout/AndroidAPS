package app.aaps.pump.common.hw.rileylink.ble.command

import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType

abstract class RileyLinkCommand {

    abstract fun getCommandType(): RileyLinkCommandType
    abstract fun getRaw(): ByteArray
    fun getByteArray(vararg input: Byte): ByteArray = input
}
