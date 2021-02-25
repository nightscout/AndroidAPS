package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command

abstract class BleCommand {

    val data: ByteArray

    constructor(type: BleCommandType) {
        data = byteArrayOf(type.value)
    }

    constructor(type: BleCommandType, payload: ByteArray) {
        val n = payload.size + 1
        data = ByteArray(n)
        data[0] = type.value
        System.arraycopy(payload, 0, data, 1, payload.size)
    }
}