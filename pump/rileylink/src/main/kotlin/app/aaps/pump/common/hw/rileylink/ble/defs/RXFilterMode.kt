package app.aaps.pump.common.hw.rileylink.ble.defs

/**
 * Created by andy on 21/05/2018.
 */
enum class RXFilterMode(val value: Byte) {

    Wide(0x50),
    Narrow(0x90.toByte());
}