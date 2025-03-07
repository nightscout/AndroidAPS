package app.aaps.core.interfaces.notifications

import android.content.Context
import androidx.annotation.RawRes
import java.util.concurrent.TimeUnit

open class Notification {

    var id = 0
    var date: Long = 0
    var text: String = ""
    var level = 0
    var validTo: Long = 0
    @RawRes var soundId: Int? = null
    var action: Runnable? = null
    var buttonText = 0

    var contextForAction: Context? = null

    constructor()
    constructor(id: Int, date: Long, text: String, level: Int, validTo: Long) {
        this.id = id
        this.date = date
        this.text = text
        this.level = level
        this.validTo = validTo
    }

    constructor(id: Int, text: String, level: Int, validMinutes: Int) {
        this.id = id
        date = System.currentTimeMillis()
        this.text = text
        this.level = level
        validTo = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(validMinutes.toLong())
    }

    constructor(id: Int, text: String, level: Int) {
        this.id = id
        date = System.currentTimeMillis()
        this.text = text
        this.level = level
    }

    constructor(id: Int) {
        this.id = id
        date = System.currentTimeMillis()
    }

    fun text(text: String): Notification = this.also { it.text = text }
    fun level(level: Int): Notification = this.also { it.level = level }
    fun sound(soundId: Int): Notification = this.also { it.soundId = soundId }

    companion object {

        // Hard to convert to enums because for NS notifications we create dynamic ID
        const val URGENT = 0
        const val NORMAL = 1
        const val LOW = 2
        const val INFO = 3
        const val ANNOUNCEMENT = 4

        const val PROFILE_SET_FAILED = 0
        const val PROFILE_SET_OK = 1
        const val EASY_MODE_ENABLED = 2
        const val EXTENDED_BOLUS_DISABLED = 3
        const val UD_MODE_ENABLED = 4
        const val PROFILE_NOT_SET_NOT_INITIALIZED = 5
        const val FAILED_UPDATE_PROFILE = 6
        const val BASAL_VALUE_BELOW_MINIMUM = 7
        const val OLD_NS = 9
        const val INVALID_PHONE_NUMBER = 10
        const val INVALID_MESSAGE_BODY = 11
        const val APPROACHING_DAILY_LIMIT = 12
        const val NSCLIENT_NO_WRITE_PERMISSION = 13
        const val MISSING_SMS_PERMISSION = 14
        const val PUMP_ERROR = 15
        const val WRONG_SERIAL_NUMBER = 16
        const val NS_ANNOUNCEMENT = 18
        const val NS_ALARM = 19
        const val NS_URGENT_ALARM = 20
        const val SHORT_DIA = 21
        const val TOAST_ALARM = 22
        const val WRONG_BASAL_STEP = 23
        const val WRONG_DRIVER = 24
        const val COMBO_PUMP_ALARM = 25
        const val PUMP_UNREACHABLE = 26
        const val BG_READINGS_MISSED = 27
        const val UNSUPPORTED_FIRMWARE = 28
        const val MINIMAL_BASAL_VALUE_REPLACED = 29
        const val BASAL_PROFILE_NOT_ALIGNED_TO_HOURS = 30
        const val WRONG_PUMP_PASSWORD = 34
        const val PERMISSION_STORAGE = 35
        const val PERMISSION_LOCATION = 36
        const val PERMISSION_BATTERY = 37
        const val PERMISSION_SMS = 38
        const val MAXIMUM_BASAL_VALUE_REPLACED = 39
        const val NS_MALFUNCTION = 40
        const val NEW_VERSION_DETECTED = 41
        const val DEVICE_NOT_PAIRED = 43
        const val MEDTRONIC_PUMP_ALARM = 44
        const val RILEYLINK_CONNECTION = 45
        const val INSIGHT_DATE_TIME_UPDATED = 47
        const val INSIGHT_TIMEOUT_DURING_HANDSHAKE = 48
        const val DST_LOOP_DISABLED = 49
        const val DST_IN_24H = 50
        const val DISK_FULL = 51
        const val OVER_24H_TIME_CHANGE_REQUESTED = 54
        const val INVALID_VERSION = 55
        const val PERMISSION_SYSTEM_WINDOW = 56
        const val TIME_OR_TIMEZONE_CHANGE = 58
        const val OMNIPOD_POD_NOT_ATTACHED = 59
        const val CARBS_REQUIRED = 60
        const val OMNIPOD_POD_SUSPENDED = 61
        const val OMNIPOD_POD_ALERTS_UPDATED = 62
        const val OMNIPOD_POD_ALERTS = 63
        const val OMNIPOD_TBR_ALERTS = 64
        const val OMNIPOD_POD_FAULT = 66
        const val OMNIPOD_UNCERTAIN_SMB = 67
        const val OMNIPOD_UNKNOWN_TBR = 68
        const val OMNIPOD_STARTUP_STATUS_REFRESH_FAILED = 69
        const val OMNIPOD_TIME_OUT_OF_SYNC = 70
        const val UNSUPPORTED_ACTION_IN_PUMP = 71
        const val WRONG_PUMP_DATA = 72
        const val NSCLIENT_VERSION_DOES_NOT_MATCH = 73
        const val VERSION_EXPIRE = 74
        const val INVALID_PROFILE_NOT_ACCEPTED = 75
        const val MDT_INVALID_HISTORY_DATA = 76
        const val IDENTIFICATION_NOT_SET = 77
        const val PERMISSION_BT = 78
        const val EOFLOW_PATCH_ALERTS = 79
        const val PUMP_SUSPENDED = 80
        const val COMBO_UNKNOWN_TBR = 81
        const val BLUETOOTH_NOT_ENABLED = 82
        const val PATCH_NOT_ACTIVE = 83
        const val PUMP_SETTINGS_FAILED = 84
        const val PUMP_TIMEZONE_UPDATE_FAILED = 85
        const val BLUETOOTH_NOT_SUPPORTED = 86
        const val PUMP_WARNING = 87
        const val PUMP_SYNC_ERROR = 88
        const val SMB_FALLBACK = 89
        const val MASTER_PASSWORD_NOT_SET = 90
        const val DYN_ISF_FALLBACK = 91
        const val AAPS_DIR_NOT_SELECTED = 92
        const val EQUIL_ALARM = 93
        const val EQUIL_ALARM_INSULIN = 94

        const val USER_MESSAGE = 1000

        const val IMPORTANCE_HIGH = 2
        const val CATEGORY_ALARM = "alarm"
    }
}