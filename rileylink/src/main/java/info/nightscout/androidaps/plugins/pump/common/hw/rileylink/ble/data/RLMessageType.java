package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data;

/**
 * Created by andy on 5/6/18.
 */

public enum RLMessageType {
    PowerOn, // for powering on the pump (wakeup)
    ReadSimpleData, // for checking if pump is readable (for Medtronic we can use GetModel)
    ;
}
