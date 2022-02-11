package info.nightscout.androidaps.plugins.pump.eopatch

import java.util.concurrent.TimeUnit

interface AppConstant {
    companion object {

        val BASAL_MIN_AMOUNT = 0.05f
        val CLICK_THROTTLE = 600L

        /**
         * Bluetooth Connection State
         */
        val BT_STATE_NOT_CONNECT = 1
        val BT_STATE_CONNECTED = 2


        val INSULIN_DECIMAL_PLACE_VAR = 100f // 10.f; 소수점자리수 계산상수 (100.f 는 두자리)

        // 패치 1P = 1 cycle = 0.1U
        const val INSULIN_UNIT_P = 0.05f // 최소 주입 단위

        val INSULIN_UNIT_MIN_U = 0f
        val INSULIN_UNIT_STEP_U = INSULIN_UNIT_P

        /**
         * On/Off
         */
        val OFF = 0
        val ON = 1

        /**
         * Pump Duration, Interval
         */
        val PUMP_DURATION_MILLI = TimeUnit.SECONDS.toMillis(4) // 15;
        val PUMP_RESOLUTION = INSULIN_UNIT_P

        /**
         * Basal
         */
        val BASAL_RATE_PER_HOUR_MIN = BASAL_MIN_AMOUNT
        val BASAL_RATE_PER_HOUR_STEP = INSULIN_UNIT_STEP_U
        val BASAL_RATE_PER_HOUR_MAX = 15.0f // 30.0f; 30.00U/hr

        val SEGMENT_MAX_SIZE_48 = 48
        val SEGMENT_MAX_SIZE = SEGMENT_MAX_SIZE_48

        val SEGMENT_COUNT_MAX = SEGMENT_MAX_SIZE_48

        /**
         * Bolus
         */
        val BOLUS_NORMAL_ID = 0x1
        val BOLUS_EXTENDED_ID = 0x2

        val BOLUS_ACTIVE_OFF = OFF
        val BOLUS_ACTIVE_NORMAL = 0x01
        val BOLUS_ACTIVE_EXTENDED_WAIT = 0x2
        val BOLUS_ACTIVE_EXTENDED = 0x04
        val BOLUS_ACTIVE_DISCONNECTED = 0x08

        val BOLUS_UNIT_MIN = BASAL_MIN_AMOUNT
        val BOLUS_UNIT_STEP = INSULIN_UNIT_STEP_U
        val BOLUS_UNIT_MAX = 25.0f // 30.0f;
        val BOLUS_DELIVER_MIN = 0.0f



        /* Wizard */
        val WIZARD_STEP_MAX = 24

        val INFO_REMINDER_DEFAULT_VALUE = 1


        val DAY_START_MINUTE = 0 * 60
        val DAY_END_MINUTE = 24 * 60


        val SNOOZE_INTERVAL_STEP = 5

        /* Insulin Duration */
        val INSULIN_DURATION_MIN = 2.0f
        val INSULIN_DURATION_MAX = 8.0f
        val INSULIN_DURATION_STEP = 0.5f

    }
}