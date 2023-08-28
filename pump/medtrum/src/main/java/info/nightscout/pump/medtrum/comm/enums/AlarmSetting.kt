package info.nightscout.pump.medtrum.comm.enums

enum class AlarmSetting(val code: Byte) {
    LIGHT_VIBRATE_AND_BEEP(0),
    LIGHT_AND_VIBRATE(1),
    LIGHT_AND_BEEP(2),
    LIGHT_ONLY(3),
    VIBRATE_AND_BEEP(4),
    VIBRATE_ONLY(5),
    BEEP_ONLY(6),
    NONE(7)
}
