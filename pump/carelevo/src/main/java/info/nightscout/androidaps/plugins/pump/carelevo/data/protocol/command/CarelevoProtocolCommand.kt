package info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.command

enum class CarelevoProtocolCommand {
    CMD_SET_TIME_REQ,
    CMD_SET_TIME_RES,

    CMD_SAFETY_CHECK_REQ,
    CMD_SAFETY_CHECK_RES,

    CMD_BASAL_PROGRAM_REQ1,
    CMD_BASAL_PROGRAM_RES1,

    CMD_BASAL_PROGRAM_REQ2,
    CMD_BASAL_PROGRAM_RES2,

    CMD_NOTICE_THRESHOLD_REQ,
    CMD_NOTICE_THRESHOLD_RES,

    CMD_INFUSION_THRESHOLD_REQ,
    CMD_INFUSION_THRESHOLD_RES,

    CMD_BUZZ_CHANGE_REQ,
    CMD_BUZZ_CHANGE_RES,

    CMD_NEEDLE_STATUS_REQ,
    CMD_NEEDLE_INSERT_RPT,

    CMD_NEEDLE_INSERT_ACK,
    CMD_CANNULA_INSERTION_RPT_ACK_RES,

    CMD_THRESHOLD_SETUP_REQ,
    CMD_THRESHOLD_SETUP_RES,

    CMD_USAGE_TIME_EXTEND_REQ,
    CMD_USAGE_TIME_EXTEND_RES,

    CMD_BASAL_CHANGE_REQ1,
    CMD_BASAL_CHANGE_RES1,

    CMD_BASAL_CHANGE_REQ2,
    CMD_BASAL_CHANGE_RES2,

    CMD_TEMP_BASAL_REQ,
    CMD_TEMP_BASAL_RES,

    CMD_IMMED_BOLUS_REQ,
    CMD_IMMED_BOLUS_RES,

    CMD_EXTENDED_BOLUS_REQ,
    CMD_EXTENDED_BOLUS_RES,

    CMD_PUMP_STOP_REQ,
    CMD_PUMP_STOP_RES,

    CMD_PUMP_RESTART_REQ,
    CMD_PUMP_RESTART_RES,

    CMD_BASAL_RESTART_RPT,

    CMD_EXTEND_BOLUS_CANCEL_REQ,
    CMD_EXTEND_BOLUS_CANCEL_RES,

    CMD_PUMP_STOP_RPT,
    CMD_PUMP_STOP_ACK,

    CMD_BOLUS_CANCEL_REQ,
    CMD_BOLUS_CANCEL_RES,

    CMD_TEMP_BASAL_CANCEL_REQ,
    CMD_TEMP_BASAL_CANCEL_RES,

    CMD_INFUSION_INFO_REQ,
    CMD_INFUSION_INFO_RPT,

    CMD_PATCH_INFO_REQ,
    CMD_PATCH_INFO_RPT1,
    CMD_PATCH_INFO_RPT2,

    CMD_THRESHOLD_VALUE_REQ,
    CMD_THRESHOLD_VALUE_RES,

    CMD_PATCH_DISCARD_REQ,
    CMD_PATCH_DISCARD_RES,

    CMD_BUZZER_CHECK_REQ,
    CMD_BUZZER_CHECK_RES,

    CMD_PATCH_OPERATIONAL_DATA_REQ,
    CMD_PULSE_FINISH_RPT,

    CMD_APP_STATUS_IND,
    CMD_APP_STATUS_ACK,

    CMD_GLUCOSE_MEASUREMENT_ALARM_TIMER_REQ,
    CMD_GLUCOSE_MEASUREMENT_ALARM_TIMER_RES,

    CMD_GLUCOSE_TIMER_FOR_CGM_REQ,
    CMD_GLUCOSE_TIMER_FOR_CGM_RES,

    CMD_GLUCOSE_TIMER_RPT,

    CMD_MAC_ADDR_REQ,
    CMD_MAC_ADDR_RES,

    CMD_WARNING_MSG_RPT,
    CMD_ALERT_MSG_RPT,
    CMD_NOTICE_MSG_RPT,

    CMD_ALARM_CLEAR_REQ,
    CMD_ALARM_CLEAR_RES,

    CMD_PATCH_INIT_REQ,
    CMD_PATCH_INIT_RES,

    CMD_PATCH_RESTORE_REQ,

    CMD_INFUSION_DELAY_RPT,
    CMD_PATCH_RECOVERY_RPT,

    CMD_APP_AUTH_KEY_IND,
    CMD_APP_AUTH_KEY_ACK,
    CMD_APP_AUTH_RPT,
    CMD_APP_AUTH_IND,
    CMD_APP_AUTH_ACK,

    CMD_ADD_PRIMING_REQ,
    CMD_ADD_PRIMING_RES,

    CMD_ALERT_ALARM_SET_REQ,
    CMD_ALERT_ALARM_SET_RES,

    CMD_ELSE;

    companion object {

        fun CarelevoProtocolCommand.commandToCode() = when(this) {
            CMD_SET_TIME_REQ                              -> 0x11.toByte()
            CMD_SET_TIME_RES                              -> 0x71.toByte()
            CMD_SAFETY_CHECK_REQ                          -> 0x12.toByte()
            CMD_SAFETY_CHECK_RES                          -> 0x72.toByte()
            CMD_BASAL_PROGRAM_REQ1                        -> 0x13.toByte()
            CMD_BASAL_PROGRAM_RES1                        -> 0x73.toByte()
            CMD_BASAL_PROGRAM_REQ2                        -> 0x14.toByte()
            CMD_BASAL_PROGRAM_RES2                        -> 0x74.toByte()
            CMD_NOTICE_THRESHOLD_REQ                      -> 0x15.toByte()
            CMD_NOTICE_THRESHOLD_RES                      -> 0x75.toByte()
            CMD_INFUSION_THRESHOLD_REQ                    -> 0x17.toByte()
            CMD_INFUSION_THRESHOLD_RES                    -> 0x77.toByte()
            CMD_BUZZ_CHANGE_REQ                           -> 0x18.toByte()
            CMD_BUZZ_CHANGE_RES                           -> 0x78.toByte()
            CMD_NEEDLE_STATUS_REQ                         -> 0x1A.toByte()
            CMD_NEEDLE_INSERT_RPT                         -> 0x79.toByte()
            CMD_NEEDLE_INSERT_ACK                         -> 0x19.toByte()
            CMD_CANNULA_INSERTION_RPT_ACK_RES             -> 0x7A.toByte()
            CMD_THRESHOLD_SETUP_REQ                       -> 0x1B.toByte()
            CMD_THRESHOLD_SETUP_RES                       -> 0x7B.toByte()
            CMD_USAGE_TIME_EXTEND_REQ                     -> 0x1C.toByte()
            CMD_USAGE_TIME_EXTEND_RES                     -> 0x7C.toByte()
            CMD_BASAL_CHANGE_REQ1                         -> 0x21.toByte()
            CMD_BASAL_CHANGE_RES1                         -> 0x81.toByte()
            CMD_BASAL_CHANGE_REQ2                         -> 0x22.toByte()
            CMD_BASAL_CHANGE_RES2                         -> 0x82.toByte()
            CMD_TEMP_BASAL_REQ                            -> 0x23.toByte()
            CMD_TEMP_BASAL_RES                            -> 0x83.toByte()
            CMD_IMMED_BOLUS_REQ                           -> 0x24.toByte()
            CMD_IMMED_BOLUS_RES                           -> 0x84.toByte()
            CMD_EXTENDED_BOLUS_REQ                        -> 0x25.toByte()
            CMD_EXTENDED_BOLUS_RES                        -> 0x85.toByte()
            CMD_PUMP_STOP_REQ                             -> 0x26.toByte()
            CMD_PUMP_STOP_RES                             -> 0x86.toByte()
            CMD_PUMP_RESTART_REQ                          -> 0x27.toByte()
            CMD_PUMP_RESTART_RES                          -> 0x87.toByte()
            CMD_BASAL_RESTART_RPT                         -> 0x88.toByte()
            CMD_EXTEND_BOLUS_CANCEL_REQ                   -> 0x29.toByte()
            CMD_EXTEND_BOLUS_CANCEL_RES                   -> 0x89.toByte()
            CMD_PUMP_STOP_RPT                             -> 0x8A.toByte()
            CMD_PUMP_STOP_ACK                             -> 0x2A.toByte()
            CMD_BOLUS_CANCEL_REQ                          -> 0x2C.toByte()
            CMD_BOLUS_CANCEL_RES                          -> 0x8C.toByte()
            CMD_TEMP_BASAL_CANCEL_REQ                     -> 0x2D.toByte()
            CMD_TEMP_BASAL_CANCEL_RES                     -> 0x8D.toByte()
            CMD_INFUSION_INFO_REQ                         -> 0x31.toByte()
            CMD_INFUSION_INFO_RPT                         -> 0x91.toByte()
            CMD_PATCH_INFO_REQ                            -> 0x33.toByte()
            CMD_PATCH_INFO_RPT1                           -> 0x93.toByte()
            CMD_PATCH_INFO_RPT2                           -> 0x94.toByte()
            CMD_THRESHOLD_VALUE_REQ                       -> 0x35.toByte()
            CMD_THRESHOLD_VALUE_RES                       -> 0x95.toByte()
            CMD_PATCH_DISCARD_REQ                         -> 0x36.toByte()
            CMD_PATCH_DISCARD_RES                         -> 0x96.toByte()
            CMD_BUZZER_CHECK_REQ                          -> 0x37.toByte()
            CMD_BUZZER_CHECK_RES                          -> 0x97.toByte()
            CMD_PATCH_OPERATIONAL_DATA_REQ                -> 0x38.toByte()
            CMD_PULSE_FINISH_RPT                          -> 0x98.toByte()
            CMD_APP_STATUS_IND                            -> 0x39.toByte()
            CMD_APP_STATUS_ACK                            -> 0x99.toByte()
            CMD_GLUCOSE_MEASUREMENT_ALARM_TIMER_REQ       -> 0x3A.toByte()
            CMD_GLUCOSE_MEASUREMENT_ALARM_TIMER_RES       -> 0x9A.toByte()
            CMD_GLUCOSE_TIMER_FOR_CGM_REQ                 -> 0x3D.toByte()
            CMD_GLUCOSE_TIMER_FOR_CGM_RES                 -> 0x9D.toByte()
            CMD_GLUCOSE_TIMER_RPT                         -> 0x9E.toByte()
            CMD_MAC_ADDR_REQ                              -> 0x3B.toByte()
            CMD_MAC_ADDR_RES                              -> 0x9B.toByte()
            CMD_PATCH_INIT_REQ                            -> 0x3F.toByte()
            CMD_PATCH_INIT_RES                            -> 0x9F.toByte()
            CMD_PATCH_RESTORE_REQ                         -> 0x4D.toByte()
            CMD_WARNING_MSG_RPT                           -> 0xA1.toByte()
            CMD_ALERT_MSG_RPT   -> 0xA2.toByte()
            CMD_NOTICE_MSG_RPT  -> 0xA3.toByte()
            CMD_ALARM_CLEAR_REQ -> 0x47.toByte()
            CMD_ALARM_CLEAR_RES                           -> 0xA7.toByte()
            CMD_INFUSION_DELAY_RPT                        -> 0x9C.toByte()
            CMD_PATCH_RECOVERY_RPT                        -> 0xAD.toByte()
            CMD_APP_AUTH_KEY_ACK                          -> 0xBA.toByte()
            CMD_APP_AUTH_RPT                              -> 0x4B.toByte()
            CMD_APP_AUTH_IND                              -> 0x4B.toByte()
            CMD_APP_AUTH_ACK                              -> 0xBB.toByte()
            CMD_ADD_PRIMING_REQ                           -> 0x1D.toByte()
            CMD_ADD_PRIMING_RES                           -> 0x7D.toByte()
            CMD_ALERT_ALARM_SET_REQ                       -> 0x48.toByte()
            CMD_ALERT_ALARM_SET_RES                       -> 0xA8.toByte()
            else                                          -> 0x00.toByte()
        }

        fun Int.codeToCommand() = when(this) {
            0x11 -> CMD_SET_TIME_REQ
            0x71 -> CMD_SET_TIME_RES
            0x12 -> CMD_SAFETY_CHECK_REQ
            0x72 -> CMD_SAFETY_CHECK_RES
            0x13 -> CMD_BASAL_PROGRAM_REQ1
            0x73 -> CMD_BASAL_PROGRAM_RES1
            0x14 -> CMD_BASAL_PROGRAM_REQ2
            0x74 -> CMD_BASAL_PROGRAM_RES2
            0x15 -> CMD_NOTICE_THRESHOLD_REQ                                                                                //CMD_INSULIN_DEFICIENCY_ALARM_THRESHOLD_REQ
            0x75 -> CMD_NOTICE_THRESHOLD_RES
            0x17 -> CMD_INFUSION_THRESHOLD_REQ
            0x77 -> CMD_INFUSION_THRESHOLD_RES
            0x18 -> CMD_BUZZ_CHANGE_REQ
            0x78 -> CMD_BUZZ_CHANGE_RES
            0x1A -> CMD_NEEDLE_STATUS_REQ
            0x79 -> CMD_NEEDLE_INSERT_RPT
            0x19 -> CMD_NEEDLE_INSERT_ACK
            0x7A -> CMD_CANNULA_INSERTION_RPT_ACK_RES
            0x1B -> CMD_THRESHOLD_SETUP_REQ
            0x7B -> CMD_THRESHOLD_SETUP_RES
            0x1C -> CMD_USAGE_TIME_EXTEND_REQ
            0x7C -> CMD_USAGE_TIME_EXTEND_RES
            0x21 -> CMD_BASAL_CHANGE_REQ1
            0x81 -> CMD_BASAL_CHANGE_RES1
            0x22 -> CMD_BASAL_CHANGE_REQ2
            0x82 -> CMD_BASAL_CHANGE_RES2
            0x23 -> CMD_TEMP_BASAL_REQ
            0x83 -> CMD_TEMP_BASAL_RES
            0x24 -> CMD_IMMED_BOLUS_REQ
            0x84 -> CMD_IMMED_BOLUS_RES
            0x25 -> CMD_EXTENDED_BOLUS_REQ
            0x85 -> CMD_EXTENDED_BOLUS_RES
            0x26 -> CMD_PUMP_STOP_REQ
            0x86 -> CMD_PUMP_STOP_RES
            0x27 -> CMD_PUMP_RESTART_REQ
            0x87 -> CMD_PUMP_RESTART_RES
            0x88 -> CMD_BASAL_RESTART_RPT
            0x29 -> CMD_EXTEND_BOLUS_CANCEL_REQ
            0x89 -> CMD_EXTEND_BOLUS_CANCEL_RES
            0x8A -> CMD_PUMP_STOP_RPT
            0x2A -> CMD_PUMP_STOP_ACK
            0x2C -> CMD_BOLUS_CANCEL_REQ
            0x8C -> CMD_BOLUS_CANCEL_RES
            0x2D -> CMD_TEMP_BASAL_CANCEL_REQ
            0x8D -> CMD_TEMP_BASAL_CANCEL_RES
            0x31 -> CMD_INFUSION_INFO_REQ
            0x91 -> CMD_INFUSION_INFO_RPT
            0x33 -> CMD_PATCH_INFO_REQ
            0x93 -> CMD_PATCH_INFO_RPT1
            0x94 -> CMD_PATCH_INFO_RPT2
            0x35 -> CMD_THRESHOLD_VALUE_REQ
            0x95 -> CMD_THRESHOLD_VALUE_RES
            0x36 -> CMD_PATCH_DISCARD_REQ
            0x96 -> CMD_PATCH_DISCARD_RES
            0x37 -> CMD_BUZZER_CHECK_REQ
            0x97 -> CMD_BUZZER_CHECK_RES
            0x38 -> CMD_PATCH_OPERATIONAL_DATA_REQ
            0x98 -> CMD_PULSE_FINISH_RPT
            0x39 -> CMD_APP_STATUS_IND
            0x99 -> CMD_APP_STATUS_ACK
            0x3A -> CMD_GLUCOSE_MEASUREMENT_ALARM_TIMER_REQ
            0x9A -> CMD_GLUCOSE_MEASUREMENT_ALARM_TIMER_RES
            0x3D -> CMD_GLUCOSE_TIMER_FOR_CGM_REQ
            0x9D -> CMD_GLUCOSE_TIMER_FOR_CGM_RES
            0x9E -> CMD_GLUCOSE_TIMER_RPT
            0x3B -> CMD_MAC_ADDR_REQ
            0x9B -> CMD_MAC_ADDR_RES
            0x3F -> CMD_PATCH_INIT_REQ
            0x9F -> CMD_PATCH_INIT_RES
            0x4D -> CMD_PATCH_RESTORE_REQ
            0xA1 -> CMD_WARNING_MSG_RPT
            0xA2 -> CMD_ALERT_MSG_RPT
            0xA3 -> CMD_NOTICE_MSG_RPT
            0x47 -> CMD_ALARM_CLEAR_REQ
            0xA7 -> CMD_ALARM_CLEAR_RES
            0x9C -> CMD_INFUSION_DELAY_RPT
            0x4D -> CMD_PATCH_RECOVERY_RPT
            0x4A -> CMD_APP_AUTH_KEY_IND
            0x4B -> CMD_APP_AUTH_IND
            0xBB -> CMD_APP_AUTH_ACK
            0xBA -> CMD_APP_AUTH_KEY_ACK
            0x4B -> CMD_APP_AUTH_RPT
            0x1D -> CMD_ADD_PRIMING_REQ
            0x7D -> CMD_ADD_PRIMING_RES
            0x48 -> CMD_ALERT_ALARM_SET_REQ
            0xA8 -> CMD_ALERT_ALARM_SET_RES

            else -> CMD_ELSE
        }
    }
}

internal fun isPatchProtocol(command: Int) = when(command) {
    0x11 -> true
    0x71 -> true
    0x12 -> true
    0x72 -> true
    0x13 -> false
    0x73 -> false
    0x14 -> false
    0x74 -> false
    0x15 -> true
    0x75 -> true
    0x16 -> true
    0x76 -> true
    0x17 -> true
    0x77 -> true
    0x18 -> true
    0x78 -> true
    0x1A -> true
    0x79 -> true
    0x19 -> true
    0x7A -> true
    0x1B -> true
    0x7B -> true
    0x1C -> true
    0x7C -> true
    0x21 -> false
    0x81 -> false
    0x22 -> false
    0x82 -> false
    0x23 -> false
    0x83 -> false
    0x24 -> false
    0x84 -> false
    0x25 -> false
    0x85 -> false
    0x26 -> true
    0x86 -> true
    0x27 -> true
    0x87 -> true
    0x88 -> false
    0x29 -> false
    0x89 -> false
    0x8A-> true
    0x2A -> true
    0x2B -> false
    0x8B -> false
    0x2C -> false
    0x8C -> false
    0x2D -> false
    0x8D -> false
    0x31 -> true
    0x91 -> true
    0x33 -> true
    0x93 -> true
    0x94 -> true
    0x35 -> true
    0x95 -> true
    0x36 -> true
    0x96 -> true
    0x37 -> true
    0x97 -> true
    0x38 -> true
    0x98 -> true
    0x39 -> true
    0x99 -> true
    0x3A -> true
    0x9A -> true
    0x3D -> true
    0x9D -> true
    0x9E -> true
    0x3B -> true
    0x9B -> true
    0x3F -> true
    0x9F -> true
    0x4D -> true
    0xA1 -> true
    0xA2 -> true
    0xA3 -> true
    0x47 -> true
    0xA7 -> true
    0x9C -> false
    0x4D -> true
    0x4A -> true
    0xBA -> true
    0x4B -> true
    0x1D -> true
    0x7D -> true
    0x48 -> true
    0xA8 -> true

    else -> false
}

internal fun isBasalProtocol(command: Int) = when(command) {
    0x11 -> false
    0x71 -> false
    0x12 -> false
    0x72 -> false
    0x13 -> true
    0x73 -> true
    0x14 -> true
    0x74 -> true
    0x15 -> false
    0x75 -> false
    0x16 -> false
    0x76 -> false
    0x17 -> false
    0x77 -> false
    0x18 -> false
    0x78 -> false
    0x1A -> false
    0x79 -> false
    0x19 -> false
    0x7A -> false
    0x1B -> false
    0x7B -> false
    0x1C -> false
    0x7C -> false
    0x21 -> true
    0x81 -> true
    0x22 -> true
    0x82 -> true
    0x23 -> true
    0x83 -> true
    0x24 -> false
    0x84 -> false
    0x25 -> false
    0x85 -> false
    0x26 -> false
    0x86 -> false
    0x27 -> false
    0x87 -> false
    0x88 -> true
    0x29 -> false
    0x89 -> false
    0x8A -> false
    0x2A -> false
    0x2B -> true
    0x8B -> true
    0x2C -> false
    0x8C -> false
    0x2D -> true
    0x8D -> true
    0x31 -> false
    0x91 -> false
    0x33 -> false
    0x93 -> false
    0x94 -> false
    0x35 -> false
    0x95 -> false
    0x36 -> false
    0x96 -> false
    0x37 -> false
    0x97 -> false
    0x38 -> false
    0x98 -> false
    0x39 -> false
    0x99 -> false
    0x3A -> false
    0x9A -> false
    0x3D -> false
    0x9D -> false
    0x9E -> false
    0x3B -> false
    0x9B -> false
    0x3F -> false
    0x9F -> false
    0x4D -> false
    0xA1 -> false
    0xA2 -> false
    0xA3 -> false
    0x47 -> false
    0xA7 -> false
    0x9C -> false
    0x4D -> false
    0x4A -> false
    0xBA -> false
    0x4B -> false
    0x1D -> false
    0x7D -> false
    0x48 -> false
    0xA8 -> false

    else -> false
}

internal fun isBolusProtocol(command: Int) = when(command) {
    0x11 -> false
    0x71 -> false
    0x12 -> false
    0x72 -> false
    0x13 -> false
    0x73 -> false
    0x14 -> false
    0x74 -> false
    0x15 -> false
    0x75 -> false
    0x16 -> false
    0x76 -> false
    0x17 -> false
    0x77 -> false
    0x18 -> false
    0x78 -> false
    0x1A -> false
    0x79 -> false
    0x19 -> false
    0x7A -> false
    0x1B -> false
    0x7B -> false
    0x1C -> false
    0x7C -> false
    0x21 -> false
    0x81 -> false
    0x22 -> false
    0x82 -> false
    0x23 -> false
    0x83 -> false
    0x24 -> true
    0x84 -> true
    0x25 -> true
    0x85 -> true
    0x26 -> false
    0x86 -> false
    0x27 -> false
    0x87 -> false
    0x88 -> false
    0x29 -> true
    0x89 -> true
    0x8A -> false
    0x2A -> false
    0x2B -> false
    0x8B -> false
    0x2C -> true
    0x8C -> true
    0x2D -> false
    0x8D -> false
    0x31 -> false
    0x91 -> false
    0x33 -> false
    0x93 -> false
    0x94 -> false
    0x35 -> false
    0x95 -> false
    0x36 -> false
    0x96 -> false
    0x37 -> false
    0x97 -> false
    0x38 -> false
    0x98 -> false
    0x39 -> false
    0x99 -> false
    0x3A -> false
    0x9A -> false
    0x3D -> false
    0x9D -> false
    0x9E -> false
    0x3B -> false
    0x9B -> false
    0x3F -> false
    0x9F -> false
    0x4D -> false
    0xA1 -> false
    0xA2 -> false
    0xA3 -> false
    0x47 -> false
    0xA7 -> false
    0x9C -> true
    0x4D -> false
    0x4A -> false
    0xBA -> false
    0x4B -> false
    0x1D -> false
    0x7D -> false
    0x48 -> false
    0xA8 -> false

    else -> false
}