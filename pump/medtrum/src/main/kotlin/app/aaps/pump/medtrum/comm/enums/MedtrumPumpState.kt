package app.aaps.pump.medtrum.comm.enums

import androidx.annotation.StringRes
import app.aaps.pump.medtrum.R

enum class MedtrumPumpState(val state: Byte, @StringRes val label: Int) {
    NONE(0, R.string.alarm_none),
    IDLE(1, R.string.status_idle),
    FILLED(2, R.string.status_filled),
    PRIMING(3, R.string.status_priming),
    PRIMED(4, R.string.status_primed),
    EJECTING(5, R.string.status_ejecting),
    EJECTED(6, R.string.status_ejected),
    ACTIVE(32, R.string.status_active),
    ACTIVE_ALT(33, R.string.status_active),
    LOW_BG_SUSPENDED(64, R.string.alarm_low_bg_suspended),
    LOW_BG_SUSPENDED2(65, R.string.alarm_low_bg_suspended2),
    AUTO_SUSPENDED(66, R.string.alarm_auto_suspended),
    HOURLY_MAX_SUSPENDED(67, R.string.alarm_hourly_max_suspended),
    DAILY_MAX_SUSPENDED(68, R.string.alarm_daily_max_suspended),
    SUSPENDED(69, R.string.alarm_suspended),
    PAUSED(70, R.string.alarm_paused),
    OCCLUSION(96, R.string.alarm_occlusion),
    EXPIRED(97, R.string.alarm_expired),
    RESERVOIR_EMPTY(98, R.string.alarm_reservoir_empty),
    PATCH_FAULT(99, R.string.alarm_patch_fault),
    PATCH_FAULT2(100, R.string.alarm_patch_fault),
    BASE_FAULT(101, R.string.alarm_base_fault),
    BATTERY_OUT(102, R.string.alarm_battery_out),
    NO_CALIBRATION(103, R.string.alarm_no_calibration),
    STOPPED(128.toByte(), R.string.status_stopped);

    fun isSuspendedByPump(): Boolean {
        return this in LOW_BG_SUSPENDED..SUSPENDED
    }

    companion object {

        fun fromByte(state: Byte) = MedtrumPumpState.entries.find { it.state == state }
            ?: throw IllegalAccessException("")
    }
}
