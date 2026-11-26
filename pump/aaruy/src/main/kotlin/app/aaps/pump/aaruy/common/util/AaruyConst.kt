package app.aaps.pump.aaruy.common.util

import app.aaps.pump.aaruy.R

object AaruyConst {
    object Prefs {
        val MaxBasal = R.string.key_pref_aaruy_max_basal
        val MaxBolus = R.string.key_pref_aaruy_max_bolus
        val LowAlert = R.string.key_pref_aaruy_low_alert
        val BolusStep = R.string.key_pref_aaruy_bolus_step
        val SoundRemind = R.string.key_pref_aaruy_sound_remind
        val SoundAlarm = R.string.key_pref_aaruy_sound_alarm
    }

    object errCodeType {
        /**
         *  设置胰岛素泵的基本参数
         */
        const val FLAG_SET_PARAMS: Byte = 1

        /**
         * 设置胰岛素泵的基础率1
         */
        const val FLAG_SET_BASAL1: Byte = 2

        /**
         * 设置胰岛素泵的基础率2
         */
        const val FLAG_SET_BASAL2: Byte = 3

        /**
         * 设置胰岛素泵的基础率3
         */
        const val FLAG_SET_BASAL3: Byte = 4

        /**
         *设置胰岛素泵的大剂量
         */
        const val FLAG_SET_BOLUS: Byte = 5

        /**
         * 设置胰岛素泵的出厂基本参数
         */
        const val FLAG_SET_PARAMS_BACKUP: Byte = 6

        /**
         * 设置胰岛素泵的出厂基础率1
         */
        const val FLAG_SET_BASAL1_BACKUP: Byte = 7

        /**
         * 设置胰岛素泵的出厂基础率2
         */
        const val FLAG_SET_BASAL2_BACKUP: Byte = 8

        /**
         * 设置胰岛素泵的出厂基础率3
         */
        const val FLAG_SET_BASAL3_BACKUP: Byte = 9

        /**
         * 设置胰岛素泵的出厂大剂量
         */
        const val FLAG_SET_BOLUS_BACKUP: Byte = 10

        /**
         * 设置蓝牙名称
         */
        const val FLAG_SET_BLE_NAME: Byte = 11


        /**
         * 运行基础率失败
         */
        const val ERROR_RUN_BASAL: Byte = 12

        /**
         * 运行临时基础率失败
         */
        const val ERROR_RUN_TEMP_BASAL: Byte = 13

        /**
         * 运行大剂量失败
         */
        const val ERROR_RUN_BOLUS: Byte = 14

        /**
         * 运行整机状态
         */
        const val ERROR_RUN_TOTAL_STATUS: Byte = 15

        /**
         * 排液操作
         */
        const val DISCHARGE_LIQUID: Byte = 16

        /**
         * 推杆回退
         */
        const val FLAG_PUSH_BACK_STATUS: Byte = 17

        /**
         * 请先运行基础率
         */
        const val FLAG_PLEASE_RUN_BASAL: Byte = 18


        /**
         * 提醒用户15分钟后无操作将关闭胰岛素泵注射
         */
        const val WARNING_STOP_15M_LATER: Byte = 19


        /**
         *泵长时间未运行
         */
        const val WARNING_STOP_TOO_LONG: Byte = 21

        /**
         * 推杆定位
         */
        const val FLAG_LOCATE_POSITION: Byte = 22

        /**
         * 电量不低不允许运行回退操作
         */
        const val FLAG_LOW_BATTERY_RESET: Byte = 23

        /**
         * 电量过低不允许运行排液操作
         */
        const val FLAG_LOW_BATTERY_DISCHARGE: Byte = 24

        /**
         * 推杆定位，是否有液体排出提醒
         */
        const val FLAG_LOCATE_POS_LIQUID_DISCHARGE: Byte = 25

        /**
         * 本次容量测试完成
         */
        const val FLAG_CAPACITY_COMPLETE: Byte = 26

        /**
         * 若电机出现FLAG_MOTOR_DESTRUCTIVE_ANOMALY报警之后，再运行注射就会回馈该命令表示胰岛素泵不能工作了
         */
        const val FLAG_MOTOR_ANOMALY: Byte = 27

        //-------------------------以下报错报警会出现在历史记录里 0x8100开始-----------------------------
        /**
         * 低电量
         */
        const val ERROR_LOW_BATTERY: Byte = 1

        /**
         * 最小电量
         */
        const val ERROR_MIN_BATTERY: Byte = 2

        /**
         * 低药量
         */
        const val ERROR_LOW_REMAINAMOUNT: Byte = 3

        /**
         * 药量耗尽
         */
        const val ERROR_MIN_REMAINAMOUNT: Byte = 4

        /**
         * 电机阻塞
         */
        const val ERROR_MOTOR_BLOCK: Byte = 5

        /**
         * 电机运行失败
         */
        const val ERROR_MOTOR_FAILED: Byte = 6

        /**
         * 电机忙碌
         */
        const val ERROR_MOTOR_BUSY: Byte = 7

        // 辅助MCU检测到堵塞
        const val ERROR_FLAG_MOTOR_MCU_BLOCK: Byte = 9

        // 辅助MCU检测到非法注射
        const val ERROR_FLAG_MOTOR_MCU_ILLEGAL: Byte = 10

        // 辅助MCU检测到超过预期的剂量
        const val ERROR_FLAG_MOTOR_MCU_PLUSES_EXTRA: Byte = 11

        /**
         * 电机与推杆螺纹打滑导致的破坏性异常 -> 泵故障3
         */
        const val ERROR_FLAG_MOTOR_DESTRUCTIVE_ANOMALY: Byte = 12

        /**
         * 电机运行失败 -> 泵故障3+
         */
        const val ERROR_FLAG_MOTOR_FAILED_PLUS: Byte = 13

        /**
         * 泵关机
         */
        const val ERROR_FLAG_AUTO_POWEROFF: Byte = 20
    }
}