package app.aaps.pump.common.hw.rileylink.ble.defs

/**
 * Created by andy on 5/6/18.
 */
enum class RLMessageType {

    PowerOn,  // for powering on the pump (wakeup)
    ReadSimpleData // for checking if pump is readable (for Medtronic we can use GetModel)
}