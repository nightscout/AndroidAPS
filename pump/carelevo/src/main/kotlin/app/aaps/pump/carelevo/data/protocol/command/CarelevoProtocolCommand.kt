package app.aaps.pump.carelevo.data.protocol.command

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

        fun CarelevoProtocolCommand.commandToCode() = when (this) {
            CMD_SET_TIME_REQ                        -> 0x11.toByte()
            CMD_SET_TIME_RES                        -> 0x71.toByte()
            CMD_SAFETY_CHECK_REQ                    -> 0x12.toByte()
            CMD_SAFETY_CHECK_RES                    -> 0x72.toByte()
            CMD_BASAL_PROGRAM_REQ1                  -> 0x13.toByte()
            CMD_BASAL_PROGRAM_RES1                  -> 0x73.toByte()
            CMD_BASAL_PROGRAM_REQ2                  -> 0x14.toByte()
            CMD_BASAL_PROGRAM_RES2                  -> 0x74.toByte()
            CMD_NOTICE_THRESHOLD_REQ                -> 0x15.toByte()
            CMD_NOTICE_THRESHOLD_RES                -> 0x75.toByte()
            CMD_INFUSION_THRESHOLD_REQ              -> 0x17.toByte()
            CMD_INFUSION_THRESHOLD_RES              -> 0x77.toByte()
            CMD_BUZZ_CHANGE_REQ                     -> 0x18.toByte()
            CMD_BUZZ_CHANGE_RES                     -> 0x78.toByte()
            CMD_NEEDLE_STATUS_REQ                   -> 0x1A.toByte()
            CMD_NEEDLE_INSERT_RPT                   -> 0x79.toByte()
            CMD_NEEDLE_INSERT_ACK                   -> 0x19.toByte()
            CMD_CANNULA_INSERTION_RPT_ACK_RES       -> 0x7A.toByte()
            CMD_THRESHOLD_SETUP_REQ                 -> 0x1B.toByte()
            CMD_THRESHOLD_SETUP_RES                 -> 0x7B.toByte()
            CMD_USAGE_TIME_EXTEND_REQ               -> 0x1C.toByte()
            CMD_USAGE_TIME_EXTEND_RES               -> 0x7C.toByte()
            CMD_BASAL_CHANGE_REQ1                   -> 0x21.toByte()
            CMD_BASAL_CHANGE_RES1                   -> 0x81.toByte()
            CMD_BASAL_CHANGE_REQ2                   -> 0x22.toByte()
            CMD_BASAL_CHANGE_RES2                   -> 0x82.toByte()
            CMD_TEMP_BASAL_REQ                      -> 0x23.toByte()
            CMD_TEMP_BASAL_RES                      -> 0x83.toByte()
            CMD_IMMED_BOLUS_REQ                     -> 0x24.toByte()
            CMD_IMMED_BOLUS_RES                     -> 0x84.toByte()
            CMD_EXTENDED_BOLUS_REQ                  -> 0x25.toByte()
            CMD_EXTENDED_BOLUS_RES                  -> 0x85.toByte()
            CMD_PUMP_STOP_REQ                       -> 0x26.toByte()
            CMD_PUMP_STOP_RES                       -> 0x86.toByte()
            CMD_PUMP_RESTART_REQ                    -> 0x27.toByte()
            CMD_PUMP_RESTART_RES                    -> 0x87.toByte()
            CMD_BASAL_RESTART_RPT                   -> 0x88.toByte()
            CMD_EXTEND_BOLUS_CANCEL_REQ             -> 0x29.toByte()
            CMD_EXTEND_BOLUS_CANCEL_RES             -> 0x89.toByte()
            CMD_PUMP_STOP_RPT                       -> 0x8A.toByte()
            CMD_PUMP_STOP_ACK                       -> 0x2A.toByte()
            CMD_BOLUS_CANCEL_REQ                    -> 0x2C.toByte()
            CMD_BOLUS_CANCEL_RES                    -> 0x8C.toByte()
            CMD_TEMP_BASAL_CANCEL_REQ               -> 0x2D.toByte()
            CMD_TEMP_BASAL_CANCEL_RES               -> 0x8D.toByte()
            CMD_INFUSION_INFO_REQ                   -> 0x31.toByte()
            CMD_INFUSION_INFO_RPT                   -> 0x91.toByte()
            CMD_PATCH_INFO_REQ                      -> 0x33.toByte()
            CMD_PATCH_INFO_RPT1                     -> 0x93.toByte()
            CMD_PATCH_INFO_RPT2                     -> 0x94.toByte()
            CMD_THRESHOLD_VALUE_REQ                 -> 0x35.toByte()
            CMD_THRESHOLD_VALUE_RES                 -> 0x95.toByte()
            CMD_PATCH_DISCARD_REQ                   -> 0x36.toByte()
            CMD_PATCH_DISCARD_RES                   -> 0x96.toByte()
            CMD_BUZZER_CHECK_REQ                    -> 0x37.toByte()
            CMD_BUZZER_CHECK_RES                    -> 0x97.toByte()
            CMD_PATCH_OPERATIONAL_DATA_REQ          -> 0x38.toByte()
            CMD_PULSE_FINISH_RPT                    -> 0x98.toByte()
            CMD_APP_STATUS_IND                      -> 0x39.toByte()
            CMD_APP_STATUS_ACK                      -> 0x99.toByte()
            CMD_GLUCOSE_MEASUREMENT_ALARM_TIMER_REQ -> 0x3A.toByte()
            CMD_GLUCOSE_MEASUREMENT_ALARM_TIMER_RES -> 0x9A.toByte()
            CMD_GLUCOSE_TIMER_FOR_CGM_REQ           -> 0x3D.toByte()
            CMD_GLUCOSE_TIMER_FOR_CGM_RES           -> 0x9D.toByte()
            CMD_GLUCOSE_TIMER_RPT                   -> 0x9E.toByte()
            CMD_MAC_ADDR_REQ                        -> 0x3B.toByte()
            CMD_MAC_ADDR_RES                        -> 0x9B.toByte()
            CMD_PATCH_INIT_REQ                      -> 0x3F.toByte()
            CMD_PATCH_INIT_RES                      -> 0x9F.toByte()
            CMD_PATCH_RESTORE_REQ                   -> 0x4D.toByte()
            CMD_WARNING_MSG_RPT                     -> 0xA1.toByte()
            CMD_ALERT_MSG_RPT                       -> 0xA2.toByte()
            CMD_NOTICE_MSG_RPT                      -> 0xA3.toByte()
            CMD_ALARM_CLEAR_REQ                     -> 0x47.toByte()
            CMD_ALARM_CLEAR_RES                     -> 0xA7.toByte()
            CMD_INFUSION_DELAY_RPT                  -> 0x9C.toByte()
            CMD_PATCH_RECOVERY_RPT                  -> 0xAD.toByte()
            CMD_APP_AUTH_KEY_ACK                    -> 0xBA.toByte()
            CMD_APP_AUTH_RPT                        -> 0x4B.toByte()
            CMD_APP_AUTH_IND                        -> 0x4B.toByte()
            CMD_APP_AUTH_ACK                        -> 0xBB.toByte()
            CMD_ADD_PRIMING_REQ                     -> 0x1D.toByte()
            CMD_ADD_PRIMING_RES                     -> 0x7D.toByte()
            CMD_ALERT_ALARM_SET_REQ                 -> 0x48.toByte()
            CMD_ALERT_ALARM_SET_RES                 -> 0xA8.toByte()
            else                                    -> 0x00.toByte()
        }
    }
}