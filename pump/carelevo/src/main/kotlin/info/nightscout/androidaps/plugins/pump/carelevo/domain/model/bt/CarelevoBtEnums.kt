package info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt

enum class Result {
    SUCCESS,
    FAILED;

    companion object {

        fun Result.commandToCode() = when (this) {
            SUCCESS -> 0
            FAILED -> 1
        }

        fun Int.codeToResultCommand() = when (this) {
            0 -> SUCCESS
            1 -> FAILED
            else -> FAILED
        }
    }
}

enum class SafetyCheckResult {
    SUCCESS,
    INSULIN_DEFICIENCY,
    EXPIRED,
    LOW_VOLTAGE,
    PATCH_ERROR,
    PUMP_ERROR,
    REP_REQUEST,
    REP_REQUEST1,
    FAILED;

    companion object {

        fun SafetyCheckResult.commandToCode() = when (this) {
            SUCCESS -> 0
            INSULIN_DEFICIENCY -> 1
            EXPIRED -> 2
            LOW_VOLTAGE -> 3
            PATCH_ERROR -> 11
            PUMP_ERROR -> 12
            REP_REQUEST -> 4
            REP_REQUEST1 -> 18
            else -> -1
        }

        fun Int.codeToSafetyCheckCommand() = when (this) {
            0 -> SUCCESS
            1 -> INSULIN_DEFICIENCY
            2 -> EXPIRED
            3 -> LOW_VOLTAGE
            11 -> PATCH_ERROR
            12 -> PUMP_ERROR
            4 -> REP_REQUEST
            18 -> REP_REQUEST1
            else -> FAILED
        }
    }
}

enum class SetBasalProgramResult {
    SUCCESS,
    INSULIN_DEFICIENCY,
    EXPIRED,
    LOW_VOLTAGE,
    ABNORMAL_TEMP,
    PUMP_ERROR,
    ABNORMAL_PROGRAM,
    EXCEED_LIMIT,
    FAILED;

    companion object {

        fun SetBasalProgramResult.commandToCode() = when (this) {
            SUCCESS -> 0
            INSULIN_DEFICIENCY -> 1
            EXPIRED -> 2
            LOW_VOLTAGE -> 3
            ABNORMAL_TEMP -> 4
            PUMP_ERROR -> 12
            ABNORMAL_PROGRAM -> 19
            EXCEED_LIMIT -> 20
            else -> -1
        }

        fun Int.codeToSetBasalProgramCommand() = when (this) {
            0 -> SUCCESS
            1 -> INSULIN_DEFICIENCY
            2 -> EXPIRED
            3 -> LOW_VOLTAGE
            4 -> ABNORMAL_TEMP
            12 -> PUMP_ERROR
            19 -> ABNORMAL_PROGRAM
            20 -> EXCEED_LIMIT
            else -> FAILED
        }
    }
}

enum class SetBolusProgramResult {
    SUCCESS,
    INSULIN_DEFICIENCY,
    EXPIRED,
    LOW_VOLTAGE,
    ABNORMAL_TEMP,
    PUMP_ERROR,
    EXCEED_LIMIT,
    FAILED;

    companion object {

        fun SetBolusProgramResult.commandToCode() = when (this) {
            SUCCESS -> 0
            INSULIN_DEFICIENCY -> 1
            EXPIRED -> 2
            LOW_VOLTAGE -> 3
            ABNORMAL_TEMP -> 4
            PUMP_ERROR -> 12
            EXCEED_LIMIT -> 20
            else -> -1
        }

        fun Int.codeToSetBolusProgramCommand() = when (this) {
            0 -> SUCCESS
            1 -> INSULIN_DEFICIENCY
            2 -> EXPIRED
            3 -> LOW_VOLTAGE
            4 -> ABNORMAL_TEMP
            12 -> PUMP_ERROR
            20 -> EXCEED_LIMIT
            else -> FAILED
        }
    }
}

enum class StopPumpResult {
    BY_REQ,
    INSULIN_DEFICIENCY,
    ABNORMAL_PUMP,
    LOW_VOLTAGE,
    ABNORMAL_TEMP,
    NOT_USED,
    PUMP_ERROR,
    BY_LGS,
    ERROR;

    companion object {

        fun StopPumpResult.commandToCode() = when (this) {
            BY_REQ -> 0
            INSULIN_DEFICIENCY -> 1
            ABNORMAL_PUMP -> 2
            LOW_VOLTAGE -> 3
            ABNORMAL_TEMP -> 4
            NOT_USED -> 5
            PUMP_ERROR -> 12
            BY_LGS -> 29
            ERROR -> -1
        }

        fun Int.codeToStopPumpCommand() = when (this) {
            0 -> BY_REQ
            1 -> INSULIN_DEFICIENCY
            2 -> ABNORMAL_PUMP
            3 -> LOW_VOLTAGE
            4 -> ABNORMAL_TEMP
            5 -> NOT_USED
            12 -> PUMP_ERROR
            29 -> BY_LGS
            else -> ERROR
        }
    }
}

enum class InfusionModeResult {
    BASAL,
    TEMP_BASAL,
    IMME_BOLUS,
    EXTEND_IMME_BOLUS,
    EXTEND_BOLUS,
    ERROR;

    companion object {

        fun InfusionModeResult.commandToCode() = when (this) {
            BASAL -> 1
            TEMP_BASAL -> 2
            IMME_BOLUS -> 3
            EXTEND_IMME_BOLUS -> 4
            EXTEND_BOLUS -> 5
            ERROR -> -1
        }

        fun Int.codeToInfusionModeCommand() = when (this) {
            1 -> BASAL
            2 -> TEMP_BASAL
            3 -> IMME_BOLUS
            4 -> EXTEND_IMME_BOLUS
            5 -> EXTEND_BOLUS
            else -> ERROR
        }
    }
}

enum class InfusionInfoResult {
    BY_REQ,
    BY_REMAIN_REQ,
    BY_30MIN_RPT,
    BY_RECONNECT,
    ERROR;

    companion object {

        fun InfusionInfoResult.commandToCode() = when (this) {
            BY_REQ -> 0
            BY_REMAIN_REQ -> 1
            BY_30MIN_RPT -> 2
            BY_RECONNECT -> 3
            ERROR -> -1
        }

        fun Int.codeToInfusionInfoCommand() = when (this) {
            0 -> BY_REQ
            1 -> BY_REMAIN_REQ
            2 -> BY_30MIN_RPT
            3 -> BY_RECONNECT
            else -> ERROR
        }
    }
}

enum class PumpStateResult {
    READY,
    PRIMING,
    RUNNING,
    ERROR;

    companion object {

        fun PumpStateResult.commandToCode() = when (this) {
            READY -> 0
            PRIMING -> 1
            RUNNING -> 2
            ERROR -> 3
        }

        fun Int?.codeToPumpStateCommand() = when (this) {
            0 -> READY
            1 -> PRIMING
            2 -> RUNNING
            else -> ERROR
        }
    }
}

enum class WarningMessageResult {
    INSULIN_DEFICIENCY,
    EXPIRED,
    LOW_VOLTAGE,
    ABNORMAL_TEMP,
    NOT_USED,
    BLE_CONNECT,
    NOT_STARTED_BASAL,
    EXTENDED_EXPIRED,
    PUMP_ERROR,
    CANNULA_ERROR,
    ERROR;

    companion object {

        fun WarningMessageResult.commandToCode() = when (this) {
            INSULIN_DEFICIENCY -> 1
            EXPIRED -> 2
            LOW_VOLTAGE -> 3
            ABNORMAL_TEMP -> 4
            NOT_USED -> 5
            BLE_CONNECT -> 6
            NOT_STARTED_BASAL -> 7
            EXTENDED_EXPIRED -> 10
            PUMP_ERROR -> 12
            CANNULA_ERROR -> 99
            else -> -1
        }

        fun Int.codeToWarningMessageCommand() = when (this) {
            1 -> INSULIN_DEFICIENCY
            2 -> EXPIRED
            3 -> LOW_VOLTAGE
            4 -> ABNORMAL_TEMP
            5 -> NOT_USED
            6 -> BLE_CONNECT
            7 -> NOT_STARTED_BASAL
            10 -> EXTENDED_EXPIRED
            12 -> PUMP_ERROR
            99 -> CANNULA_ERROR
            else -> ERROR
        }
    }
}

enum class AlertMessageResult {
    INSULIN_LOW,
    EXPIRED_ALERT,
    BATTERY_EXCEED,
    ABNORMAL_TEMP,
    NOT_USED,
    BLE_CONNECT,
    NOT_START_BASAL,
    PUMP_STOP_FINISH,
    EXTEND_EXPIRED,
    ERROR;

    companion object {

        fun AlertMessageResult.commandToCode() = when (this) {
            INSULIN_LOW -> 1
            EXPIRED_ALERT -> 2
            BATTERY_EXCEED -> 3
            ABNORMAL_TEMP -> 4
            NOT_USED -> 5
            BLE_CONNECT -> 6
            NOT_START_BASAL -> 7
            PUMP_STOP_FINISH -> 8
            EXTEND_EXPIRED -> 10
            else -> -1
        }

        fun Int.codeToAlertMessageCommand() = when (this) {
            1 -> INSULIN_LOW
            2 -> EXPIRED_ALERT
            3 -> BATTERY_EXCEED
            4 -> ABNORMAL_TEMP
            5 -> NOT_USED
            6 -> BLE_CONNECT
            7 -> NOT_START_BASAL
            8 -> PUMP_STOP_FINISH
            10 -> EXTEND_EXPIRED
            else -> ERROR
        }
    }
}

enum class NoticeMessageResult {
    REMAIN_EXCEED,
    EXPIRED_NOTICE,
    INSPECTING,
    SYNC_TIME,
    GLUCOSE,
    ERROR;

    companion object {

        fun NoticeMessageResult.commandToCode() = when (this) {
            REMAIN_EXCEED -> 1
            EXPIRED_NOTICE -> 2
            INSPECTING -> 3
            SYNC_TIME -> 26
            GLUCOSE -> 27
            else -> -1
        }

        fun Int.codeToNoticeMessageCommand() = when (this) {
            1 -> REMAIN_EXCEED
            2 -> EXPIRED_NOTICE
            3 -> INSPECTING
            26 -> SYNC_TIME
            27 -> GLUCOSE
            else -> ERROR
        }
    }
}