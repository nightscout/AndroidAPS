package app.aaps.pump.common.hw.rileylink.defs

import app.aaps.pump.common.hw.rileylink.R

/**
 * Created by andy on 14/05/2018.
 */
enum class RileyLinkError(val resourceId: Int, val resourceIdPod: Int? = null) {

    // Configuration
    // BT
    NoBluetoothAdapter(R.string.rileylink_error_no_bt_adapter),
    BluetoothDisabled(R.string.rileylink_error_bt_disabled),

    // RileyLink
    RileyLinkUnreachable(R.string.rileylink_error_unreachable),
    DeviceIsNotRileyLink(R.string.rileylink_error_not_rl),

    // Device
    TuneUpOfDeviceFailed(R.string.rileylink_error_tuneup_failed),
    NoContactWithDevice(R.string.rileylink_error_pump_unreachable, R.string.rileylink_error_pod_unreachable),
    ;

    fun getResourceId(targetDevice: RileyLinkTargetDevice?): Int =
        if (resourceIdPod != null) {
            if (targetDevice == RileyLinkTargetDevice.MedtronicPump) resourceId
            else resourceIdPod
        } else resourceId
}
