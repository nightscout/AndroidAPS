package app.aaps.pump.omnipod.common.bledriver.pod.definition

import app.aaps.pump.omnipod.common.bledriver.pod.util.HasValue

enum class AlarmType(override val value: Byte) : HasValue {

    NONE(0x00.toByte()),
    ALARM_PW_FLASH_ERASE(0x01.toByte()),
    ALARM_PW_FLASH_WRITE(0x02.toByte()),
    ALARM_BASAL_CKSUM(0x03.toByte()),
    ALARM_BASAL_PPULSE(0x04.toByte()),
    ALARM_BASAL_STEP(0x05.toByte()),
    ALARM_AUTO_WAKEUP_TIMEOUT(0x06.toByte()),
    ALARM_WIRE_OVERDRIVEN(0x07.toByte()),
    ALARM_BEEP_REP_INVALID_INDEX(0x08.toByte()),
    ALARM_INVALID_REP_PATTERN(0x09.toByte()),
    ALARM_TEMP_BASAL_STEP(0x0a.toByte()),
    ALARM_TEMP_BASAL_CKSUM(0x0b.toByte()),
    ALARM_BOLUS_OVERFLOW(0x0c.toByte()),
    ALARM_COP_RESET(0x0d.toByte()),
    ALARM_ILOP_RESET(0x0e.toByte()),
    ALARM_ILAD_RESET(0x0f.toByte()),
    ALARM_SAWCOP_RESET(0x10.toByte()),
    ALARM_BOLUS_STEP(0x11.toByte()),
    ALARM_LVD_RESET(0x12.toByte()),
    ALARM_INVALID_RF_MSG_LENGTH(0x13.toByte()),
    ALARM_OCCLUDED(0x14.toByte()),
    ALARM_BOLUSPROG_CHKSUM(0x15.toByte()),
    ALARM_BOLUS_LOG(0x16.toByte()),
    ALARM_CRITICAL_VAR(0x17.toByte()),
    ALARM_EMPTY_RESERVOIR(0x18.toByte()),
    ALARM_LOADERR(0x19.toByte()),
    ALARM_PSA_FAILURE(0x1a.toByte()),
    ALARM_TICKCNT_NOT_CLEARED(0x1b.toByte()),
    ALARM_PUMP_EXPIRED(0x1c.toByte()),
    ALARM_COMD_BIT_NOT_SET(0x1d.toByte()),
    ALARM_INVALID_COMD_SET(0x1e.toByte()),
    ALARM_ALERTS_ARRAY_CKSM(0x1f.toByte()),
    ALARM_UNIT_TEST(0x20.toByte()),
    ALARM_TICK_TIME_ERROR(0x21.toByte()),
    ALARM_CRITICAL_HAZARD(0x22.toByte()),
    ALARM_PIEZO_FREQ(0x23.toByte()),
    ALARM_TICKCNT_ERROR_RTC(0x24.toByte()),
    ALARM_TICK_FAILURE(0x25.toByte()),
    ALARM_INVALID(0x26.toByte()),
    ALARM_LUMP_ALERT_PROGRAM(0x27.toByte()),
    ALARM_INVALID_PASS_CODE(0x28.toByte()),
    ALARM_ALERT0(0x29.toByte()),
    ALARM_ALERT1(0x2a.toByte()),
    ALARM_ALERT2(0x2b.toByte()),
    ALARM_ALERT3(0x2c.toByte()),
    ALARM_ALERT4(0x2d.toByte()),
    ALARM_ALERT5(0x2e.toByte()),
    ALARM_ALERT6(0x2f.toByte()),
    ALARM_ALERT7(0x30.toByte()),
    ALARM_ILLEGAL_PUMP_STATE(0x31.toByte()),
    ALARM_COP_TEST_FAILURE(0x32.toByte()),
    ALARM_MCTF(0x33.toByte()),
    ALARM_ILLEGAL_RESET(0x34.toByte()),
    ALARM_VETO_NOT_SET(0x35.toByte()),
    ALARM_ILLEGAL_PIN_RESET(0x36.toByte()),
    ALARM_INVALID_BEEP_PATTERN(0x37.toByte()),
    ALARM_WIRE_STATE_MACHINE(0x38.toByte()),
    ALARM_VETO_TEST_DEFAULT(0x39.toByte()),
    ALARM_ALERT_INVALID_INDEX(0x3a.toByte()),
    ALARM_SAWCOP_TEST_FAIL(0x3b.toByte()),
    ALARM_MCUCOP_TEST_FAIL(0x3c.toByte()),
    ALARM_STEP_SENSOR_SHORTED(0x3d.toByte()),
    ALARM_FLASH_FAILURE(0x3e.toByte()),
    ALARM_SPARE63(0x3f.toByte()),
    ALARM_SS_OPEN_CNT_EXCEEDED(0x40.toByte()),
    ALARM_SS_EXCESSIVE_SUMMED(0x41.toByte()),
    ALARM_SS_MIN_PULSE_TRANSITION(0x42.toByte()),
    ALARM_SS_DEFAULT(0x43.toByte()),
    ALARM_OPEN_WIRE1(0x44.toByte()),
    ALARM_OPEN_WIRE2(0x45.toByte()),
    ALARM_LOADERR_FAILURE(0x46.toByte()),
    ALARM_SAW_VETO_FAILURE(0x47.toByte()),
    ALARM_BAD_RFM_CLOCK(0x48.toByte()),
    ALARM_BAD_TICK_HIGH(0x49.toByte()),
    ALARM_BAD_TICK_PERIOD(0x4a.toByte()),
    ALARM_BAD_TRIM_VALUE(0x4b.toByte()),
    ALARM_BAD_BUS_CLOCK(0x4c.toByte()),
    ALARM_BAD_CAL_MODE(0x4d.toByte()),
    ALARM_SAW_TRIM_ERROR(0x4e.toByte()),
    ALARM_RFM_CRYSTAL_ERROR(0x4f.toByte()),
    ALARM_CALST_TIMEOUT(0x50.toByte()),
    ALARM_TICKCNT_ERROR(0x51.toByte()),
    ALARM_BAD_RFM_XTAL_START(0x52.toByte()),
    ALARM_BAD_RX_SENSENSITIVITY(0x53.toByte()),
    ALARM_BAD_TX_PKT_SIZE(0x54.toByte()),
    ALARM_TICK_LOW_PHASE_EXCEEDED(0x55.toByte()),
    ALARM_TICK_HIGH_PHASE_EXCEEDED(0x56.toByte()),
    ALARM_OCCL_CRITVAR_FAIL(0x57.toByte()),
    ALARM_OCCL_PARAM(0x58.toByte()),
    ALARM_PROG_OCCL_FAIL(0x59.toByte()),
    ALARM_PW_TO_HIGH_FOR_OCCL_DET(0x5a.toByte()),
    ALARM_OCCL_CSUM(0x5b.toByte()),
    ALARM_PRIME_OPEN_CNT_TO_LOW(0x5c.toByte()),
    ALARM_BAD_RF_CDTHR(0x5d.toByte()),
    ALARM_FLASH_NOT_SECURE(0x5e.toByte()),
    ALARM_WIRE_TEST_OPEN_GROUND(0x5f.toByte()),
    ALARM_OCCL_STARTUP1(0x60.toByte()),
    ALARM_OCCL_STARTUP2(0x61.toByte()),
    ALARM_OCCL_EXCESS_TIMEOUTS1(0x62.toByte()),
    ALARM_OCCL_PARAM_INVALID(0x63.toByte()),
    ALARM_SPARE100(0x64.toByte()),
    ALARM_SPARE101(0x65.toByte()),
    ALARM_OCCL_EXCESS_TIMEOUTS2(0x66.toByte()),
    ALARM_OCCL_EXCESS_TIMEOUTS3(0x67.toByte()),
    ALARM_OCCL_NOISY_PULSE_WIDTHS(0x68.toByte()),
    ALARM_OCCL_AT_BOLUS_END(0x69.toByte()),
    ALARM_OCCL_ABOVE_THRESHOLD(0x6a.toByte()),
    ALARM_BASAL_UNDERINFUSION(0x80.toByte()),
    ALARM_BASAL_OVERINFUSION(0x81.toByte()),
    ALARM_TEMP_UNDERINFUSION(0x82.toByte()),
    ALARM_TEMP_OVERINFUSION(0x83.toByte()),
    ALARM_BOLUS_UNDERINFUSION(0x84.toByte()),
    ALARM_BOLUS_OVERINFUSION(0x85.toByte()),
    ALARM_BASAL_OVERINFUSION_PULSE(0x86.toByte()),
    ALARM_TEMP_OVERINFUSION_PULSE(0x87.toByte()),
    ALARM_BOLUS_OVERINFUSION_PULSE(0x88.toByte()),
    ALARM_IMMBOLUS_UNDERINFUSION_PULSE(0x89.toByte()),
    ALARM_EXTBOLUS_OVERINFUSION_PULSE(0x8a.toByte()),
    ALARM_PROGRAM_CSUM(0x8b.toByte()),
    ALARM_UNUSED_140(0x8c.toByte()),
    ALARM_UNRECOGNIZED_PULSE(0x8d.toByte()),
    ALARM_SYNC_WITHOUT_TEMP_ACTIVE(0x8e.toByte()),
    ALARM_INTERLOCK_LOAD(0x8f.toByte()),
    ALARM_ILLEGAL_CHAN_PARAM(0x90.toByte()),
    ALARM_BASAL_PULSE_CHAN_INACTIVE(0x91.toByte()),
    ALARM_TEMP_PULSE_CHAN_INACTIVE(0x92.toByte()),
    ALARM_BOLUS_PULSE_CHAN_INACTIVE(0x93.toByte()),
    ALARM_INT_SEMAPHORE_NOT_SET(0x94.toByte()),
    ALARM_ILLEGAL_INTERLOCK_CHAN(0x95.toByte()),
    ALARM_TERMINATE_BOLUS(0x96.toByte()),
    ALARM_OPEN_TRANSITIONS_COUNT(0x97.toByte()),
    ALARM_SYNC_WITHOUT_CLOSED_LOOP(0x98.toByte()), // O5 only
    ALARM_QN_STATUS_MISMATCH(0x99.toByte()), // O5 only
    ALARM_AP_LOOP_MISMATCH(0x9a.toByte()), // O5 only
    ALARM_BLE_TO(0xa0.toByte()),
    ALARM_BLE_INITIATED(0xa1.toByte()),
    ALARM_BLE_UNK_ALARM(0xa2.toByte()),
    ALARM_ADC_LIB_NOT_INITIALIZED(0xa3.toByte()), // O5 only
    ALARM_ADC_LIB_MEMORY_SIZE(0xa4.toByte()), // O5 only
    ALARM_ADC_LIB_NV_MEMORY_CRC(0xa5.toByte()), // O5 only
    ALARM_BLE_IAAS(0xa6.toByte()),
    ALARM_UNUSED_167(0xa7.toByte()),
    ALARM_CRC_FAILURE(0xa8.toByte()),
    ALARM_BLE_WD_PING_TIMEOUT(0xa9.toByte()),
    ALARM_BLE_EXCESSIVE_RESETS(0xaa.toByte()),
    ALARM_BLE_NAK_ERROR(0xab.toByte()),
    ALARM_BLE_REQ_HIGH_TIMEOUT(0xac.toByte()),
    ALARM_BLE_UNKNOWN_RESP(0xad.toByte()),
    ALARM_BLE_UNUSED_174(0xae.toByte()),
    ALARM_BLE_REQ_STUCK_HIGH(0xaf.toByte()),
    ALARM_BLE_STATE_MACHINE_1(0xb1.toByte()),
    ALARM_BLE_STATE_MACHINE_2(0xb2.toByte()),
    ALARM_BLE_UNUSED_179(0xb3.toByte()),
    ALARM_BLE_ARB_LOST(0xb4.toByte()),
    ALARM_BOLUS_EXTENDED_NOT_ALLOWED(0xb5.toByte()), // O5 only
    ALARM_AGC_IN_OPEN_LOOP(0xb6.toByte()), // O5 only
    ALARM_AGC_BOLUS_EXTENDED_NOT_ALLOWED(0xb7.toByte()), // O5 only
    ALARM_AGC_PULSES_EXCEEDED(0xb8.toByte()), // O5 only
    ALARM_AGC_BOLUS_ALREADY_ACTIVE(0xb9.toByte()), // O5 only
    ALARM_AGC_BOLUS_TOO_EARLY(0xba.toByte()), // O5 only
    ALARM_IMMED_BOLUS_MISMATCH(0xbb.toByte()), // O5 only
    ALARM_AGC_MEAL_CORR_BOLUS_NOT_ZERO(0xbc.toByte()), // O5 only
    ALARM_TEMP_BASAL_NOT_ALLOWED(0xbd.toByte()), // O5 only
    ALARM_BASAL_NOT_ALLOWED(0xbe.toByte()), // O5 only
    ALARM_AGC_BOLUS_TOO_LATE(0xbf.toByte()), // O5 only
    ALARM_BLE_ER48_DUAL_NACK(0xc0.toByte()),
    ALARM_BLE_QN_EXCEED_MAX_RETRY(0xc1.toByte()),
    ALARM_BLE_QN_CRIT_VAR_FAIL(0xc2.toByte()),
    ALARM_BLE_QN_OPT_INTVL_INVALID(0xc3.toByte()),
    ALARM_BLE_QN_CGM_UTC_MISMATCH(0xc4.toByte()), // O5 only
    ALARM_BLE_QN_CGM_TXID_NOT_ALLOWED(0xc5.toByte()), // O5 only
    ALARM_BLE_QN_ALG_NOT_RUN(0xc7.toByte()), // O5 only
    ALARM_BLE_QN_HYPO_IN_OPEN_LOOP(0xc8.toByte()), // O5 only
    ALARM_BLE_QN_ALG_SETUP_FAIL(0xc9.toByte()), // O5 only
    ALARM_BLE_QN_AGC_RUN_TOO_LATE(0xca.toByte()), // O5 only
    ALARM_UNKNOWN_CB(0xcb.toByte()),
    ALARM_UNKNOWN_D4(0xd4.toByte()),
    ALARM_UNKNOWN_D5(0xd5.toByte()),
    ALARM_RESET_FAULT_D6(0xd6.toByte()),
    ALARM_RESET_FAULT_D7(0xd7.toByte()),
    ALARM_UNKNOWN_D8(0xd8.toByte()),
    ALARM_UNKNOWN_D9(0xd9.toByte()),
    ALARM_BLE_AGC_POTENTIAL_DIV_ZERO(0xe1.toByte()), // O5 only
    ALARM_BLE_AGC_INVALID_INPUT_PARAM(0xe2.toByte()), // O5 only
    ALARM_BLE_AGC_INVALID_PARAM(0xe3.toByte()), // O5 only
    ALARM_BLE_AGC_STATE_VECTOR_PARAM(0xe4.toByte()), // O5 only
    ALARM_BLE_AGC_INVALID_ALGO_STATE_PARAM(0xe5.toByte()), // O5 only
    ALARM_BLE_AGC_INVALID_HYPO_SETTING(0xe6.toByte()), // O5 only
    ALARM_BLE_AGC_OUTPUT_OUT_OF_BOUNDS(0xe7.toByte()), // O5 only
    ALARM_BLE_AGC_INVALID_FIRST_RUN_IN_INIT_STATE(0xe8.toByte()), // O5 only
    ALARM_BLE_AGC_INVALID_OFFSET(0xe9.toByte()), // O5 only
    UNKNOWN(0xff.toByte());

    // value is a signed byte, so codes >= 0x80 would print as negative; use this for display instead.
    val code: Int
        get() = value.toInt() and 0xff

    // Readable text for each pod fault code, as reported by the pod firmware.
    // Matches OmnipodKit's FaultEventCode.faultDescription, matched by code value.
    val description: String
        get() = when (this) {
            NONE -> "No fault"
            ALARM_PW_FLASH_ERASE -> "Flash erase failed"
            ALARM_PW_FLASH_WRITE -> "Flash store failed"
            ALARM_BASAL_CKSUM -> "Basal subcommand table corruption"
            ALARM_BASAL_PPULSE -> "Basal pulse table corruption"
            ALARM_BASAL_STEP -> "Basal step corrupt"
            ALARM_AUTO_WAKEUP_TIMEOUT -> "Auto wakeup timeout"
            ALARM_WIRE_OVERDRIVEN -> "Wire overdriven"
            ALARM_BEEP_REP_INVALID_INDEX -> "Invalid beep repeat index"
            ALARM_INVALID_REP_PATTERN -> "Invalid beep repeat pattern"
            ALARM_TEMP_BASAL_STEP -> "Temp Basal Step"
            ALARM_TEMP_BASAL_CKSUM -> "Temp basal subcommand table corruption"
            ALARM_BOLUS_OVERFLOW -> "Bolus overflow"
            ALARM_COP_RESET -> "Reset due to COP"
            ALARM_ILOP_RESET -> "Reset due to illegal opcode"
            ALARM_ILAD_RESET -> "Reset due to illegal address"
            ALARM_SAWCOP_RESET -> "Reset due to SAWCOP"
            ALARM_BOLUS_STEP -> "Bolus step"
            ALARM_LVD_RESET -> "Reset due to LVD"
            ALARM_INVALID_RF_MSG_LENGTH -> "Message length too long"
            ALARM_OCCLUDED -> "Occluded"
            ALARM_BOLUSPROG_CHKSUM -> "Bolus Prog Chksum"
            ALARM_BOLUS_LOG -> "Bolus log"
            ALARM_CRITICAL_VAR -> "Corruption in a validated table"
            ALARM_EMPTY_RESERVOIR -> "Reservoir empty or exceeded maximum pulse delivery"
            ALARM_LOADERR -> "Load error"
            ALARM_PSA_FAILURE -> "PSA failure"
            ALARM_TICKCNT_NOT_CLEARED -> "Tick count not cleared"
            ALARM_PUMP_EXPIRED -> "Exceeded maximum Pod life of 80 hours"
            ALARM_COMD_BIT_NOT_SET -> "Comd bit not set"
            ALARM_INVALID_COMD_SET -> "Invalid comd set"
            ALARM_ALERTS_ARRAY_CKSM -> "Sum mismatch for word_129 table"
            ALARM_UNIT_TEST -> "Validate encoder count error when bolusing"
            ALARM_TICK_TIME_ERROR -> "Bad timer variable state"
            ALARM_CRITICAL_HAZARD -> "Unexpected RTC Modulo Register value during reset"
            ALARM_PIEZO_FREQ -> "Problem in calibrate_timer_case_3"
            ALARM_TICKCNT_ERROR_RTC -> "Tick count error RTC"
            ALARM_TICK_FAILURE -> "Tick failure"
            ALARM_INVALID -> "RTC interrupt handler unexpectedly called"
            ALARM_LUMP_ALERT_PROGRAM -> "Failed to set up 2 hour alert for tank fill operation"
            ALARM_INVALID_PASS_CODE -> "Invalid pass code"
            ALARM_ALERT0 -> "Alert #0 auto-off timeout"
            ALARM_ALERT1 -> "Alert #1 auto-off timeout"
            ALARM_ALERT2 -> "Alert #2 auto-off timeout"
            ALARM_ALERT3 -> "Alert #3 auto-off timeout"
            ALARM_ALERT4 -> "Alert #4 auto-off timeout"
            ALARM_ALERT5 -> "Alert #5 auto-off timeout"
            ALARM_ALERT6 -> "Alert #6 auto-off timeout"
            ALARM_ALERT7 -> "Alert #7 auto-off timeout"
            ALARM_ILLEGAL_PUMP_STATE -> "Incorrect pod state for command or error during insulin command setup"
            ALARM_COP_TEST_FAILURE -> "COP test failure"
            ALARM_MCTF -> "Connected Pod command timeout"
            ALARM_ILLEGAL_RESET -> "Illegal reset"
            ALARM_VETO_NOT_SET -> "Veto not set"
            ALARM_ILLEGAL_PIN_RESET -> "Flash initialization error"
            ALARM_INVALID_BEEP_PATTERN -> "Invalid beep pattern"
            ALARM_WIRE_STATE_MACHINE -> "Wire state machine"
            ALARM_VETO_TEST_DEFAULT -> "Veto test default"
            ALARM_ALERT_INVALID_INDEX -> "Invalid alert index"
            ALARM_SAWCOP_TEST_FAIL -> "SAW reset testing fail"
            ALARM_MCUCOP_TEST_FAIL -> "test in progress"
            ALARM_STEP_SENSOR_SHORTED -> "Step sensor shorted"
            ALARM_FLASH_FAILURE -> "Flash initialization or write error"
            ALARM_SPARE63 -> "Unknown fault"
            ALARM_SS_OPEN_CNT_EXCEEDED -> "Encoder count too high"
            ALARM_SS_EXCESSIVE_SUMMED -> "Encoder count excessive variance"
            ALARM_SS_MIN_PULSE_TRANSITION -> "Encoder count too low"
            ALARM_SS_DEFAULT -> "Encoder count problem"
            ALARM_OPEN_WIRE1 -> "Check voltage open wire 1 problem"
            ALARM_OPEN_WIRE2 -> "Check voltage open wire 2 problem"
            ALARM_LOADERR_FAILURE -> "Problem with LOAD1/LOAD2"
            ALARM_SAW_VETO_FAILURE -> "Problem with LOAD1/LOAD2"
            ALARM_BAD_RFM_CLOCK -> "Bad timer calibration"
            ALARM_BAD_TICK_HIGH -> "Bad timer values: COP timer ratio bad"
            ALARM_BAD_TICK_PERIOD -> "Bad tick period"
            ALARM_BAD_TRIM_VALUE -> "Bad trim value"
            ALARM_BAD_BUS_CLOCK -> "Bad bus clock"
            ALARM_BAD_CAL_MODE -> "Bad cal mode"
            ALARM_SAW_TRIM_ERROR -> "SAW Trim Error"
            ALARM_RFM_CRYSTAL_ERROR -> "RFM Crystal Error"
            ALARM_CALST_TIMEOUT -> "Timer pulse-width modulator overflow"
            ALARM_TICKCNT_ERROR -> "Bad tick count state before starting pump"
            ALARM_BAD_RFM_XTAL_START -> "Bad RFM crystal start"
            ALARM_BAD_RX_SENSENSITIVITY -> "Bad Rx sensitivity"
            ALARM_BAD_TX_PKT_SIZE -> "Packet frame length too long"
            ALARM_TICK_LOW_PHASE_EXCEEDED -> "Tick low phase exceeded"
            ALARM_TICK_HIGH_PHASE_EXCEEDED -> "Tick high phase exceeded"
            ALARM_OCCL_CRITVAR_FAIL -> "Occlusion critical variable fail"
            ALARM_OCCL_PARAM -> "Occlusion param"
            ALARM_PROG_OCCL_FAIL -> "Occlusion prog fail"
            ALARM_PW_TO_HIGH_FOR_OCCL_DET -> "Occlusion check value too high"
            ALARM_OCCL_CSUM -> "Load table corruption"
            ALARM_PRIME_OPEN_CNT_TO_LOW -> "Prime open count too low"
            ALARM_BAD_RF_CDTHR -> "Bad byte_109 value"
            ALARM_FLASH_NOT_SECURE -> "Write flash byte to disable flash security failed"
            ALARM_WIRE_TEST_OPEN_GROUND -> "Two check voltage failures before starting pump"
            ALARM_OCCL_STARTUP1 -> "Occlusion check startup problem 1"
            ALARM_OCCL_STARTUP2 -> "Occlusion check startup problem 2"
            ALARM_OCCL_EXCESS_TIMEOUTS1 -> "Occlusion check excess timeouts 1"
            ALARM_OCCL_PARAM_INVALID -> "Occlusion param invalid"
            ALARM_SPARE100 -> "Unknown fault"
            ALARM_SPARE101 -> "Unknown fault"
            ALARM_OCCL_EXCESS_TIMEOUTS2 -> "Occlusion check excess timeouts 2"
            ALARM_OCCL_EXCESS_TIMEOUTS3 -> "Occlusion check excess timeouts 3"
            ALARM_OCCL_NOISY_PULSE_WIDTHS -> "Occlusion check pulse issue"
            ALARM_OCCL_AT_BOLUS_END -> "Occlusion check bolus problem"
            ALARM_OCCL_ABOVE_THRESHOLD -> "Occlusion check above threshold"
            ALARM_BASAL_UNDERINFUSION -> "Basal under infusion"
            ALARM_BASAL_OVERINFUSION -> "Basal over infusion"
            ALARM_TEMP_UNDERINFUSION -> "Temp basal under infusion"
            ALARM_TEMP_OVERINFUSION -> "Temp basal over infusion"
            ALARM_BOLUS_UNDERINFUSION -> "Bolus under infusion"
            ALARM_BOLUS_OVERINFUSION -> "Bolus over infusion"
            ALARM_BASAL_OVERINFUSION_PULSE -> "Basal over infusion pulse"
            ALARM_TEMP_OVERINFUSION_PULSE -> "Temp basal over infusion pulse"
            ALARM_BOLUS_OVERINFUSION_PULSE -> "Bolus over infusion pulse"
            ALARM_IMMBOLUS_UNDERINFUSION_PULSE -> "Immediate bolus under infusion pulse"
            ALARM_EXTBOLUS_OVERINFUSION_PULSE -> "Extended bolus over infusion pulse"
            ALARM_PROGRAM_CSUM -> "Corruption of tables"
            ALARM_UNUSED_140 -> "Unknown fault"
            ALARM_UNRECOGNIZED_PULSE -> "Bad pulse value"
            ALARM_SYNC_WITHOUT_TEMP_ACTIVE -> "Sync with no temp basal active"
            ALARM_INTERLOCK_LOAD -> "Interlock load"
            ALARM_ILLEGAL_CHAN_PARAM -> "illegal channel parameter"
            ALARM_BASAL_PULSE_CHAN_INACTIVE -> "basal pulse channel inactive"
            ALARM_TEMP_PULSE_CHAN_INACTIVE -> "temp basal channel inactive"
            ALARM_BOLUS_PULSE_CHAN_INACTIVE -> "bolus pulse channel inactive"
            ALARM_INT_SEMAPHORE_NOT_SET -> "Bad table specifier field6 in 1A command"
            ALARM_ILLEGAL_INTERLOCK_CHAN -> "Illegal interlock channel"
            ALARM_TERMINATE_BOLUS -> "Terminate bolus"
            ALARM_OPEN_TRANSITIONS_COUNT -> "Open transitions count"
            ALARM_SYNC_WITHOUT_CLOSED_LOOP -> "Sync without closed loop"
            ALARM_QN_STATUS_MISMATCH -> "QN status mismatch"
            ALARM_AP_LOOP_MISMATCH -> "AP loop mismatch"
            ALARM_BLE_TO -> "BLE timeout"
            ALARM_BLE_INITIATED -> "BLE initiated"
            ALARM_BLE_UNK_ALARM -> "BLE unknown alarm"
            ALARM_ADC_LIB_NOT_INITIALIZED -> "ADC library not initialized"
            ALARM_ADC_LIB_MEMORY_SIZE -> "ADC library memory size"
            ALARM_ADC_LIB_NV_MEMORY_CRC -> "ADC library NV memory CRC"
            ALARM_BLE_IAAS -> "BLE IAAS"
            ALARM_UNUSED_167 -> "Unknown fault"
            ALARM_CRC_FAILURE -> "CRC failure"
            ALARM_BLE_WD_PING_TIMEOUT -> "BLE WD ping timeout"
            ALARM_BLE_EXCESSIVE_RESETS -> "BLE excessive resets"
            ALARM_BLE_NAK_ERROR -> "BLE NAK error"
            ALARM_BLE_REQ_HIGH_TIMEOUT -> "BLE request high timeout"
            ALARM_BLE_UNKNOWN_RESP -> "BLE unknown response"
            ALARM_BLE_UNUSED_174 -> "Unknown fault"
            ALARM_BLE_REQ_STUCK_HIGH -> "BLE request stuck high"
            ALARM_BLE_STATE_MACHINE_1 -> "BLE state machine 1"
            ALARM_BLE_STATE_MACHINE_2 -> "BLE state machine 2"
            ALARM_BLE_UNUSED_179 -> "Unknown fault"
            ALARM_BLE_ARB_LOST -> "BLE arbitration lost"
            ALARM_BLE_ER48_DUAL_NACK -> "BLE dual Nack"
            ALARM_BOLUS_EXTENDED_NOT_ALLOWED -> "Bolus extended not allowed"
            ALARM_AGC_IN_OPEN_LOOP -> "AGC in open loop"
            ALARM_AGC_BOLUS_EXTENDED_NOT_ALLOWED -> "AGC bolus extended not allowed"
            ALARM_AGC_PULSES_EXCEEDED -> "AGC pulses exceeded"
            ALARM_AGC_BOLUS_ALREADY_ACTIVE -> "AGC bolus already active"
            ALARM_AGC_BOLUS_TOO_EARLY -> "AGC bolus too early"
            ALARM_IMMED_BOLUS_MISMATCH -> "Immediate bolus mismatch"
            ALARM_AGC_MEAL_CORR_BOLUS_NOT_ZERO -> "AGC meal correction bolus not zero"
            ALARM_TEMP_BASAL_NOT_ALLOWED -> "Temporary basal not allowed"
            ALARM_BASAL_NOT_ALLOWED -> "Basal not allowed"
            ALARM_AGC_BOLUS_TOO_LATE -> "AGC bolus too late"
            ALARM_BLE_QN_EXCEED_MAX_RETRY -> "BLE QN exceed max retry"
            ALARM_BLE_QN_CRIT_VAR_FAIL -> "BLE QN critical variable fail"
            UNKNOWN -> "Unknown fault code"
            ALARM_BLE_QN_OPT_INTVL_INVALID -> "BLE QN optional interval invalid"
            ALARM_BLE_QN_CGM_UTC_MISMATCH -> "BLE QN CGM UTC mismatch"
            ALARM_BLE_QN_CGM_TXID_NOT_ALLOWED -> "BLE QN CGM TXID not allowed"
            ALARM_BLE_QN_ALG_NOT_RUN -> "BLE QN algorithm not run"
            ALARM_BLE_QN_HYPO_IN_OPEN_LOOP -> "BLE QN hypo in open loop"
            ALARM_BLE_QN_ALG_SETUP_FAIL -> "BLE QN algorithm setup fail"
            ALARM_BLE_QN_AGC_RUN_TOO_LATE -> "BLE QN AGC run too late"
            ALARM_UNKNOWN_CB -> "Unknown fault"
            ALARM_UNKNOWN_D4 -> "Unknown fault"
            ALARM_UNKNOWN_D5 -> "Unknown fault"
            ALARM_RESET_FAULT_D6 -> "Reset fault of unknown origin"
            ALARM_RESET_FAULT_D7 -> "Reset fault of unknown origin"
            ALARM_UNKNOWN_D8 -> "Unknown fault"
            ALARM_UNKNOWN_D9 -> "Unknown fault"
            ALARM_BLE_AGC_POTENTIAL_DIV_ZERO -> "BLE AGC potential divide by zero"
            ALARM_BLE_AGC_INVALID_INPUT_PARAM -> "BLE AGC invalid input parameter"
            ALARM_BLE_AGC_INVALID_PARAM -> "BLE AGC invalid parameter"
            ALARM_BLE_AGC_STATE_VECTOR_PARAM -> "BLE AGC state vector parameter"
            ALARM_BLE_AGC_INVALID_ALGO_STATE_PARAM -> "BLE AGC invalid algorithm state parameter"
            ALARM_BLE_AGC_INVALID_HYPO_SETTING -> "BLE AGC invalid hypo setting"
            ALARM_BLE_AGC_OUTPUT_OUT_OF_BOUNDS -> "BLE AGC output out of bounds"
            ALARM_BLE_AGC_INVALID_FIRST_RUN_IN_INIT_STATE -> "BLE AGC invalid first run in init state"
            ALARM_BLE_AGC_INVALID_OFFSET -> "BLE AGC invalid offset"
        }
}
