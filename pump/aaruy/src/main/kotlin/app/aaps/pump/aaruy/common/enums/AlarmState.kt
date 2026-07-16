package app.aaps.pump.aaruy.common.enums

enum class AlarmState(val state: Int) {
    NONE(0),
    PUMP_ZERO_RESERVOIR(1),                     // 药量为0
    PUMP_RUN_BASAL(0x800C),                     // 运行基础率失败
    PUMP_RUN_TMP_BASAL(0x800D),                 // 运行临时基础率失败
    PUMP_RUN_BOLUS(0x800E),                     // 运行大剂量失败
    PUMP_DISCHARGE_LIQUID(0x8010),              // 进行了推杆定位的排液操作失败
    PUMP_PUSH_BACK(0x8011),                     // 推杆回退失败
    PUMP_PLEASE_RUN_BASAL(0x8012),              // 请先运行基础率
    PUMP_STOP_15M_LATER(0x8013),                // 提醒用户15分钟后无操作将关闭胰岛素泵注射
    PUMP_STOP_TOO_LONG(0x8015),                 // 泵长时间未运行
    PUMP_LOW_BATTERY_RESET(0x8017),             // 电量过低不允许运行回退操作
    PUMP_LOW_BATTERY_DISCHARGE(0x8018),         // 电量过低不允许运行排液操作

    PUMP_LOW_POWER(0x8101),                     // 低电量
    PUMP_NO_POWER(0x8102),                      // 电量即将耗尽
    PUMP_LOW_RESERVOIR(0x8103),                 // 低药量
    PUMP_NO_RESERVOIR(0x8104),                  // 药量耗尽
    PUMP_MOTOR_BLOCK(0x8105),                   // 电机阻塞
    PUMP_MOTOR_FAILED(0x8106),                  // 电机运行失败
    PUMP_MOTOR_BUSY(0x8107),                    // 电机busy
    PUMP_MOTOR_REPEAT(0x8108),                  // 电机repeat
    PUMP_MOTOR_MCU_BLOCK(0x8109),               // 电机阻塞2
    PUMP_MOTOR_MCU_ILLEGAL(0x810A),             // 泵故障1
    PUMP_MOTOR_MCU_PLUSES_EXTRA(0x810B),        // 泵故障2
    PUMP_MOTOR_DESTRUCTIVE_ANOMALY(0x810C),     // 泵故障4
    PUMP_MOTOR_FAILED_PLUS(0x810D),             // 电机运行失败 -> 泵故障3+
    PUMP_MOTOR_AUTO_POWEROFF(0x8114);           // 自动关机

    companion object {

        fun fromInt(state: Int) = AlarmState.entries.find { it.state == state }
            ?: NONE

        fun isHighAlarm(state: Int): Boolean {
            val alarmState = fromInt(state)
            return alarmState == PUMP_NO_POWER ||
                (alarmState in PUMP_NO_RESERVOIR..PUMP_MOTOR_AUTO_POWEROFF)
        }

        fun isLowAlarm(state: Int): Boolean {
            val alarmState = fromInt(state)
            return (alarmState == PUMP_LOW_POWER ||
                alarmState == PUMP_LOW_RESERVOIR)
        }

        fun isRemindAlarm(state: Int): Boolean {
            val alarmState = fromInt(state)
            return (alarmState < PUMP_LOW_POWER &&
                alarmState != NONE)
        }

        fun isHighAlarm(state: AlarmState): Boolean {
            return state == PUMP_NO_POWER || state == PUMP_ZERO_RESERVOIR ||
                (state in PUMP_NO_RESERVOIR..PUMP_MOTOR_AUTO_POWEROFF)
        }

        fun isLowAlarm(state: AlarmState): Boolean {
            return (state == PUMP_LOW_POWER ||
                state == PUMP_LOW_RESERVOIR)
        }

        fun isRemindAlarm(state: AlarmState): Boolean {
            return (state < PUMP_LOW_POWER &&
                state != NONE)
        }
    }
}
