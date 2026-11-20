package app.aaps.pump.aaruy.comm

import app.aaps.core.interfaces.pump.ble.BleTransport

/**
 * The shared [BleTransport], plus what the Aaruy pump needs and it does not have.
 * [AaruyBleTransportImpl] is the radio. The emulator module has the other implementation.
 */
interface AaruyBleTransport : BleTransport {

    fun setExtrasListener(listener: AaruyTransportExtras?)

    /** The answer comes in [AaruyTransportExtras.onMtuChanged]. */
    fun requestMtu(mtu: Int)

    /** The answer comes in [AaruyTransportExtras.onFirmwareVersionRead]. */
    fun readFirmwareVersion()
}

interface AaruyTransportExtras {

    fun onMtuChanged(mtu: Int)
    fun onFirmwareVersionRead(data: ByteArray)

    /** The write did not happen. [app.aaps.core.interfaces.pump.ble.BleTransportListener.onCharacteristicWritten] does not come for it. */
    fun onWriteFailed()
}
