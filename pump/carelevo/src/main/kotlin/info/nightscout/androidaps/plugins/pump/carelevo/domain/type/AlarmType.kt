package info.nightscout.androidaps.plugins.pump.carelevo.domain.type

enum class AlarmType(val code: Int) {
    WARNING(0),
    ALERT(1),
    NOTICE(2),
    UNKNOWN_TYPE(3);

    companion object {

        fun fromCode(code: Int?): AlarmType {
            return entries.find { it.code == code } ?: UNKNOWN_TYPE
        }

        fun fromAlarmType(type: AlarmType): Int {
            return type.code
        }

        fun AlarmType.isCritical(): Boolean =
            this == AlarmType.WARNING
    }
}

enum class AlarmCause(val alarmType: AlarmType, val code: Int?, val value: Int? = null) {
    ALARM_WARNING_LOW_INSULIN(AlarmType.WARNING, 0x01),
    ALARM_WARNING_PATCH_EXPIRED_PHASE_1(AlarmType.WARNING, 0x02),
    ALARM_WARNING_LOW_BATTERY(AlarmType.WARNING, 0x03),
    ALARM_WARNING_INVALID_TEMPERATURE(AlarmType.WARNING, 0x04),
    ALARM_WARNING_NOT_USED_APP_AUTO_OFF(AlarmType.WARNING, 0x05),
    ALARM_WARNING_BLE_NOT_CONNECTED(AlarmType.WARNING, 0x06),
    ALARM_WARNING_INCOMPLETE_PATCH_SETTING(AlarmType.WARNING, 0x07),
    ALARM_WARNING_SELF_DIAGNOSIS_FAILED(AlarmType.WARNING, 0x09),
    ALARM_WARNING_PATCH_EXPIRED(AlarmType.WARNING, 0x0a),
    ALARM_WARNING_PATCH_ERROR(AlarmType.WARNING, 0x0b),
    ALARM_WARNING_PUMP_CLOGGED(AlarmType.WARNING, 0x0c),
    ALARM_WARNING_NEEDLE_INSERTION_ERROR(AlarmType.WARNING, 99),

    ALARM_ALERT_OUT_OF_INSULIN(AlarmType.ALERT, 0x01),
    ALARM_ALERT_PATCH_EXPIRED_PHASE_2(AlarmType.ALERT, 0x02),
    ALARM_ALERT_LOW_BATTERY(AlarmType.ALERT, 0x03),
    ALARM_ALERT_INVALID_TEMPERATURE(AlarmType.ALERT, 0x04),
    ALARM_ALERT_APP_NO_USE(AlarmType.ALERT, 0x05),
    ALARM_ALERT_BLE_NOT_CONNECTED(AlarmType.ALERT, 0x06),
    ALARM_ALERT_PATCH_APPLICATION_INCOMPLETE(AlarmType.ALERT, 0x07),
    ALARM_ALERT_RESUME_INSULIN_DELIVERY_TIMEOUT(AlarmType.ALERT, 0x08),
    ALARM_ALERT_PATCH_EXPIRED_PHASE_1(AlarmType.ALERT, 0x0a),
    ALARM_ALERT_BLUETOOTH_OFF(AlarmType.ALERT, 97),

    ALARM_NOTICE_LOW_INSULIN(AlarmType.NOTICE, 0x01),
    ALARM_NOTICE_PATCH_EXPIRED(AlarmType.NOTICE, 0x02),
    ALARM_NOTICE_ATTACH_PATCH_CHECK(AlarmType.NOTICE, 0x09),
    ALARM_NOTICE_TIME_ZONE_CHANGED(AlarmType.NOTICE, 96),
    ALARM_NOTICE_BG_CHECK(AlarmType.NOTICE, 98),
    ALARM_NOTICE_LGS_START(AlarmType.NOTICE, 99),
    ALARM_NOTICE_LGS_FINISHED_DISCONNECTED_PATCH_OR_CGM(AlarmType.NOTICE, 100, 1),
    ALARM_NOTICE_LGS_FINISHED_PAUSE_LGS(AlarmType.NOTICE, 100, 2),
    ALARM_NOTICE_LGS_FINISHED_TIME_OVER(AlarmType.NOTICE, 100, 3),
    ALARM_NOTICE_LGS_FINISHED_OFF_LGS(AlarmType.NOTICE, 100, 4),
    ALARM_NOTICE_LGS_FINISHED_HIGH_BG(AlarmType.NOTICE, 100, 5),
    ALARM_NOTICE_LGS_FINISHED_UNKNOWN(AlarmType.NOTICE, 100),
    ALARM_NOTICE_LGS_NOT_WORKING(AlarmType.NOTICE, 101),

    ALARM_UNKNOWN(AlarmType.UNKNOWN_TYPE, null);

    companion object {

        fun fromTypeAndCode(alarmType: AlarmType, code: Int?, value: Int? = null): AlarmCause {
            return entries.find {
                it.alarmType == alarmType && it.code == code && it.value == value
            } ?: ALARM_UNKNOWN
        }
    }
}
