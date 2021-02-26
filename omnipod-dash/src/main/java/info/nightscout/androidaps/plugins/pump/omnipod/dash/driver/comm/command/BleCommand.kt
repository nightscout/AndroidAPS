package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command

import info.nightscout.androidaps.utils.extensions.toHex

open class BleCommand(val data: ByteArray) {

    constructor(type: BleCommandType) : this(byteArrayOf(type.value))

    constructor(type: BleCommandType, payload: ByteArray) : this(
        byteArrayOf(type.value) + payload
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BleCommand) return false

        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun toString(): String {
        return "Raw command: [${data.toHex()}]";
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }
}

class BleCommandRTS : BleCommand(BleCommandType.RTS)

class BleCommandCTS : BleCommand(BleCommandType.CTS)

class BleCommandAbort : BleCommand(BleCommandType.ABORT)

class BleCommandSuccess : BleCommand(BleCommandType.SUCCESS)

class BleCommandFail : BleCommand(BleCommandType.FAIL)
