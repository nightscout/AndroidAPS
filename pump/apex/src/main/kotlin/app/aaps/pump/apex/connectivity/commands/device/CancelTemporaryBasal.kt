package app.aaps.pump.apex.connectivity.commands.device

import app.aaps.pump.apex.interfaces.ApexDeviceInfo

/** Cancel temporary basal if set before */
class CancelTemporaryBasal(info: ApexDeviceInfo): BaseValueCommand(info) {
    override val valueId = 0x05
    override val isWrite = true

    override fun toString(): String = "CancelTemporaryBasal()"
}
