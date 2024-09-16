package app.aaps.pump.equil

object EquilConst {

    const val EQUIL_CMD_TIME_OUT: Long = 300000
    const val EQUIL_BLE_WRITE_TIME_OUT: Long = 20
    const val EQUIL_BLE_NEXT_CMD: Long = 150
    const val EQUIL_SUPPORT_LEVEL = 5.3f
    const val EQUIL_BLOUS_THRESHOLD_STEP = 1600
    const val EQUIL_STEP_MAX = 32000
    const val EQUIL_STEP_FILL = 160
    const val EQUIL_STEP_AIR = 120
    object Prefs {

        val EQUIL_DEVICES = R.string.key_equil_devices
        val EQUIL_PASSWORD = R.string.key_equil_password
        val Equil_ALARM_BATTERY_10 = R.string.key_equil_alarm_battery_10
        val EQUIL_ALARM_INSULIN_10 = R.string.key_equil_alarm_insulin_10
        val EQUIL_ALARM_INSULIN_5 = R.string.key_equil_alarm_insulin_5
        val EQUIL_BASAL_SET = R.string.key_equil_basal_set
        val EQUIL_STATE = R.string.key_equil_state
    }
}
