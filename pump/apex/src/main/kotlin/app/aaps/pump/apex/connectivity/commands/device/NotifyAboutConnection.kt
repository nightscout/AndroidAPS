package app.aaps.pump.apex.connectivity.commands.device

import app.aaps.pump.apex.interfaces.ApexDeviceInfo

/** Notify pump about connection, should be sent right after connection established. */
class NotifyAboutConnection(
    info: ApexDeviceInfo,
) : BaseValueCommand(info) {
    override val valueId = 0x33
    override val isWrite = true

    override val additionalData: ByteArray
        get() = byteArrayOf(0x01, 0x00) // TODO: find out what do these values mean

    override fun toString(): String = "NotifyAboutConnection()"
}